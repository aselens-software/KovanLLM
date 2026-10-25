import os
import sys
import re
import time
import random
import secrets
import subprocess
import shutil
import httpx
from collections import defaultdict
from typing import Optional, List, Dict, Any

from fastapi import FastAPI, Request, HTTPException, Depends
from fastapi.middleware.cors import CORSMiddleware
from fastapi.middleware.gzip import GZipMiddleware
from fastapi.responses import StreamingResponse, Response, HTMLResponse
from pydantic import BaseModel, Field

from voice_engine import synthesize_with_piper, synthesize_fallback_google

KOVAN_STATION_PORT = 27615

def get_lms_cli_path() -> Optional[str]:
    which_lms = shutil.which("lms")
    if which_lms:
        return which_lms
    home = os.path.expanduser("~")
    candidates = [
        os.path.join(home, ".lmstudio", "bin", "lms.exe" if sys.platform == "win32" else "lms"),
        os.path.join(home, ".cache", "lm-studio", "bin", "lms.exe" if sys.platform == "win32" else "lms"),
        os.path.join(home, "AppData", "Local", "Programs", "LM-Studio", "resources", "bin", "lms.exe"),
    ]
    for c in candidates:
        if os.path.exists(c):
            return c
    return None

def get_subprocess_kwargs() -> dict:
    kwargs = {}
    if sys.platform == "win32":
        kwargs["creationflags"] = 0x08000000  # subprocess.CREATE_NO_WINDOW
    return kwargs

# Determine if Swagger/OpenAPI docs should be visible (disabled by default in production)
is_dev_mode = os.getenv("KOVAN_DEV", "0") == "1"

app = FastAPI(
    title="KovanLLM Station Server",
    version="3.0.0",
    docs_url="/docs" if is_dev_mode else None,
    redoc_url="/redoc" if is_dev_mode else None,
    openapi_url="/openapi.json" if is_dev_mode else None,
)

# G3: Fixed CORS (allow_credentials=False when allow_origins=["*"])
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)

# G12: Response GZip Compression for large model catalogs and payloads
app.add_middleware(GZipMiddleware, minimum_size=1000)

class EngineConfig(BaseModel):
    engine_host: str = "http://127.0.0.1"
    engine_port: int = 58080
    active_model: str = ""
    context_length: int = 4096
    custom_context_length: Optional[int] = None
    gpu_offload: str = "max"  # "off", "0.25", "0.5", "0.75", "max"
    gpu_offload_percent: int = 100  # 0 to 100% graduated slider
    cpu_threads: int = Field(default_factory=lambda: max(1, os.cpu_count() or 4))
    temperature: float = 0.7
    top_p: float = 0.95
    top_k: int = 40
    frequency_penalty: float = 0.0
    presence_penalty: float = 0.0
    max_tokens: int = -1  # -1 for unlimited
    system_role_enabled: bool = False
    system_prompt: str = ""
    language: str = "en"
    theme: str = "dark"  # "dark" or "light"
    pin_required: bool = False

current_config = EngineConfig()

current_pairing_pin = f"{random.randint(100000, 999999)}"
session_auth_token = secrets.token_hex(16)
active_tunnel_url: str = ""

def set_active_tunnel_url(url: str):
    global active_tunnel_url
    active_tunnel_url = url

def get_active_tunnel_url() -> str:
    return active_tunnel_url

def get_pairing_pin() -> str:
    return current_pairing_pin

def get_session_auth_token() -> str:
    return session_auth_token

def generate_new_pairing_pin() -> str:
    global current_pairing_pin, session_auth_token
    current_pairing_pin = f"{random.randint(100000, 999999)}"
    session_auth_token = secrets.token_hex(16)
    return current_pairing_pin

def get_engine_base_url() -> str:
    host = current_config.engine_host.rstrip("/")
    if not host.startswith("http://") and not host.startswith("https://"):
        host = f"http://{host}"
    return f"{host}:{current_config.engine_port}"

# G1: Station Authentication Dependency (Optional based on current_config.pin_required)
def verify_station_auth(request: Request):
    if not current_config.pin_required:
        return

    auth_header = request.headers.get("Authorization")
    token = None
    if auth_header and auth_header.startswith("Bearer "):
        token = auth_header[7:].strip()
    elif "X-Kovan-Token" in request.headers:
        token = request.headers["X-Kovan-Token"].strip()

    if not token or not secrets.compare_digest(token, session_auth_token):
        raise HTTPException(
            status_code=401,
            detail="Unauthorized: Valid KovanLLM station token required."
        )

# G4 & G15: Rate Limiting & Temporary IP Ban Mechanism
RATE_LIMITS = {
    "/v1/chat/completions": 40,        # 40 completions per minute
    "/api/engine/apply-and-reload": 6,  # 6 reloads per minute
    "/api/engine/reload": 6,
    "/api/engine/unload": 10,
    "/api/pair/verify": 10,             # 10 PIN attempts per minute
}
request_history: Dict[str, List[float]] = defaultdict(list)
banned_ips: Dict[str, float] = {}

# G14: HTTP Security Headers and Rate Limit Middleware
@app.middleware("http")
async def rate_limit_and_security_middleware(request: Request, call_next):
    client_ip = request.client.host if request.client else "unknown"
    now = time.time()

    # 1. Check if IP is currently banned
    if client_ip in banned_ips:
        if now < banned_ips[client_ip]:
            return Response(
                status_code=403,
                content='{"error": "Forbidden: IP address temporarily blocked due to rate limit abuse."}',
                media_type="application/json"
            )
        else:
            del banned_ips[client_ip]

    # 2. Record request history (sliding 60s window)
    history = [t for t in request_history[client_ip] if now - t < 60]
    history.append(now)
    request_history[client_ip] = history

    is_local = client_ip in ["127.0.0.1", "localhost", "testclient"]

    # 3. Global burst abuse check (> 120 reqs/min -> ban for 10 minutes)
    if not is_local and len(history) > 120:
        banned_ips[client_ip] = now + 600
        return Response(
            status_code=403,
            content='{"error": "Too many requests. IP blocked for 10 minutes."}',
            media_type="application/json"
        )

    # 4. Route-specific rate limits
    path = request.url.path
    if not is_local and path in RATE_LIMITS:
        limit = RATE_LIMITS[path]
        recent = [t for t in history if now - t < 60]
        if len(recent) > limit:
            return Response(
                status_code=429,
                content='{"error": "Too Many Requests: Rate limit exceeded for this endpoint."}',
                media_type="application/json",
                headers={"Retry-After": "60"}
            )

    response = await call_next(request)

    # 5. Inject Defense-in-Depth Security Headers
    response.headers["X-Content-Type-Options"] = "nosniff"
    response.headers["X-Frame-Options"] = "DENY"
    response.headers["Referrer-Policy"] = "strict-origin-when-cross-origin"
    return response

class VerifyPairRequest(BaseModel):
    pin: str

class ProbeRequest(BaseModel):
    host: str = "127.0.0.1"
    port: int = 58080

# Public Health/Status check (Zero Auth)
@app.get("/api/status")
async def get_status():
    engine_url = get_engine_base_url()
    engine_online = False
    try:
        async with httpx.AsyncClient(timeout=2.0) as client:
            res = await client.get(f"{engine_url}/v1/models")
            engine_online = res.status_code == 200
    except Exception:
        engine_online = False

    return {
        "status": "online",
        "service": "KovanLLM Station",
        "station_port": KOVAN_STATION_PORT,
        "engine_url": engine_url,
        "engine_port": current_config.engine_port,
        "engine_online": engine_online,
        "tunnel_url": active_tunnel_url,
        "active_model": current_config.active_model,
        "system_role_enabled": current_config.system_role_enabled,
        "language": current_config.language,
        "pin_required": current_config.pin_required,
    }

# Pair info: Restrict PIN exposure to local host or authenticated callers
@app.get("/api/pair")
async def get_pair_info(request: Request):
    client_host = request.client.host if request.client else ""
    auth_header = request.headers.get("Authorization", "")
    token = auth_header[7:].strip() if auth_header.startswith("Bearer ") else request.headers.get("X-Kovan-Token", "")
    is_authed = bool(token and secrets.compare_digest(token, session_auth_token))
    is_local = client_host in ["127.0.0.1", "localhost", "testclient"]

    if not is_local and not is_authed:
        raise HTTPException(
            status_code=403,
            detail="Pairing information is restricted to the local station host."
        )

    import urllib.parse
    active_url = active_tunnel_url or f"http://127.0.0.1:{KOVAN_STATION_PORT}"
    deep_link = f"kovanllm://pair?url={urllib.parse.quote(active_url)}&port={KOVAN_STATION_PORT}&token={session_auth_token}"
    return {
        "pin": current_pairing_pin,
        "tunnel_url": active_tunnel_url,
        "station_port": KOVAN_STATION_PORT,
        "engine_port": current_config.engine_port,
        "deep_link": deep_link,
        "token": session_auth_token,
        "pin_required": current_config.pin_required
    }

# Pair landing page for deep linking
@app.get("/pair", response_class=HTMLResponse)
async def pair_landing():
    import urllib.parse
    active_url = active_tunnel_url or f"http://127.0.0.1:{KOVAN_STATION_PORT}"
    deep_link = f"kovanllm://pair?url={urllib.parse.quote(active_url)}&port={KOVAN_STATION_PORT}&token={session_auth_token}"
    html = f"""<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>KovanLLM Station Pairing</title>
    <script>
        setTimeout(function() {{
            window.location.href = "{deep_link}";
        }}, 300);
    </script>
    <style>
        body {{ font-family: -apple-system, sans-serif; background: #0B0D14; color: #fff; text-align: center; padding: 40px 20px; }}
        .card {{ background: #161926; border: 1px solid #262B3D; border-radius: 16px; padding: 32px 20px; max-width: 400px; margin: 40px auto; }}
        .btn {{ display: inline-block; background: #D97706; color: white; padding: 14px 28px; text-decoration: none; border-radius: 10px; font-weight: bold; margin-top: 20px; }}
        .hint {{ color: #9CA3AF; font-size: 13px; margin-top: 14px; }}
    </style>
</head>
<body>
    <div class="card">
        <h2>KovanLLM Station</h2>
        <p>Connecting to KovanLLM Mobile App...</p>
        <a class="btn" href="{deep_link}">Open in KovanLLM</a>
        <p class="hint">Station Port: {KOVAN_STATION_PORT}</p>
    </div>
</body>
</html>"""
    return HTMLResponse(content=html)

class PinRequiredRequest(BaseModel):
    pin_required: bool

@app.post("/api/settings/pin-required")
async def set_pin_required(req: PinRequiredRequest):
    current_config.pin_required = req.pin_required
    return {"success": True, "pin_required": current_config.pin_required}

# Public PIN verification to obtain session auth token
@app.post("/api/pair/verify")
async def verify_pair(req: VerifyPairRequest):
    clean_pin = req.pin.replace("-", "").strip()
    if clean_pin == current_pairing_pin:
        return {
            "success": True,
            "auth_token": session_auth_token,
            "active_model": current_config.active_model,
            "station_port": KOVAN_STATION_PORT,
            "message": "Pairing successful!"
        }
    raise HTTPException(status_code=401, detail="Invalid pairing PIN.")

# Protected Configuration Endpoints
@app.get("/api/engine/config", dependencies=[Depends(verify_station_auth)])
async def get_engine_config():
    return current_config

@app.post("/api/engine/config", dependencies=[Depends(verify_station_auth)])
async def update_engine_config(new_config: EngineConfig):
    global current_config
    if new_config.gpu_offload:
        s = new_config.gpu_offload.lower().strip().replace(",", ".")
        if s in ["off", "0", "0%"]:
            new_config.gpu_offload_percent = 0
            new_config.gpu_offload = "off"
        elif s in ["max", "1", "100%", "full"]:
            new_config.gpu_offload_percent = 100
            new_config.gpu_offload = "max"
        elif "/" in s:
            try:
                parts = s.split("/")
                ratio = float(parts[0]) / float(parts[1])
                new_config.gpu_offload_percent = max(0, min(100, int(round(ratio * 100))))
                new_config.gpu_offload = f"{ratio:.2f}"
            except Exception:
                pass
        else:
            try:
                val = float(s.replace("%", "").strip())
                if val <= 1.0:
                    new_config.gpu_offload_percent = max(0, min(100, int(round(val * 100))))
                    new_config.gpu_offload = f"{val:.2f}"
                else:
                    new_config.gpu_offload_percent = max(0, min(100, int(round(val))))
                    new_config.gpu_offload = f"{val/100.0:.2f}"
            except Exception:
                pass
    elif new_config.gpu_offload_percent is not None:
        p = new_config.gpu_offload_percent
        if p <= 0:
            new_config.gpu_offload = "off"
        elif p >= 100:
            new_config.gpu_offload = "max"
        else:
            new_config.gpu_offload = f"{p/100.0:.2f}"

    current_config = new_config
    return {"success": True, "config": current_config}

# G8: CLI Parameter Sanitization
MODEL_KEY_REGEX = re.compile(r"^[a-zA-Z0-9][a-zA-Z0-9_\.\-/]*$")

def load_or_reload_model(model_key: str, context_length: int, gpu_offload: Any) -> Dict[str, Any]:
    if not model_key or not MODEL_KEY_REGEX.match(model_key) or model_key.startswith("-"):
        return {"success": False, "message": "Invalid model identifier."}

    lms_path = get_lms_cli_path()
    if not lms_path:
        return {"success": False, "message": "lms command line tool not found"}

    # 1. Unload all currently loaded models first
    try:
        subprocess.run([lms_path, "unload", "--all"], capture_output=True, text=True, timeout=15, **get_subprocess_kwargs())
    except Exception:
        pass

    # 2. Parse GPU ratio safely
    gpu_param = "max"
    s_gpu = str(gpu_offload).lower().strip().replace(",", ".")
    if s_gpu in ["off", "0", "0%"]:
        gpu_param = "off"
    elif s_gpu in ["max", "1", "100%", "full"]:
        gpu_param = "max"
    elif "/" in s_gpu:
        try:
            parts = s_gpu.split("/")
            num = float(parts[0].strip())
            den = float(parts[1].strip())
            if den > 0:
                ratio = num / den
                if ratio >= 0.999:
                    gpu_param = "max"
                elif ratio <= 0.001:
                    gpu_param = "off"
                else:
                    gpu_param = f"{ratio:.2f}"
        except Exception:
            gpu_param = "max"
    else:
        try:
            val = float(s_gpu.replace("%", "").strip())
            if val > 1.0:
                val = val / 100.0
            if val >= 0.999:
                gpu_param = "max"
            elif val <= 0.001:
                gpu_param = "off"
            else:
                gpu_param = f"{val:.2f}"
        except Exception:
            gpu_param = "max"

    # Synchronize current_config
    if gpu_param == "off":
        current_config.gpu_offload = "off"
        current_config.gpu_offload_percent = 0
    elif gpu_param == "max":
        current_config.gpu_offload = "max"
        current_config.gpu_offload_percent = 100
    else:
        current_config.gpu_offload = gpu_param
        current_config.gpu_offload_percent = max(0, min(100, int(round(float(gpu_param) * 100))))

    cmd = [
        lms_path, "load", model_key,
        "-y",
        "--gpu", gpu_param,
        "-c", str(context_length)
    ]
    try:
        proc = subprocess.run(cmd, capture_output=True, text=True, timeout=120, **get_subprocess_kwargs())
        if proc.returncode == 0:
            return {
                "success": True,
                "message": f"Model '{model_key}' successfully loaded (Context: {context_length}, GPU: {gpu_param}).",
                "stdout": proc.stdout.strip()
            }
        else:
            return {
                "success": False,
                "message": f"lms load error (code {proc.returncode}): {proc.stderr.strip() or proc.stdout.strip()}"
            }
    except Exception as e:
        return {"success": False, "message": "Failed to load model due to an internal execution error."}

class ReloadRequest(BaseModel):
    model: Optional[str] = None
    context_length: Optional[int] = None
    gpu_offload: Optional[str] = None
    gpu_offload_percent: Optional[int] = None

@app.post("/api/engine/apply-and-reload", dependencies=[Depends(verify_station_auth)])
@app.post("/api/engine/reload", dependencies=[Depends(verify_station_auth)])
async def apply_and_reload(req: Optional[ReloadRequest] = None):
    global current_config
    model_key = (req.model if req and req.model else current_config.active_model) or "qwen2.5-3b-instruct-abliterated"
    ctx = (req.context_length if req and req.context_length else current_config.context_length) or 4096

    if req and req.gpu_offload_percent is not None:
        p = req.gpu_offload_percent
        if p <= 0:
            gpu = "off"
        elif p >= 100:
            gpu = "max"
        else:
            gpu = f"{p / 100.0:.2f}"
    elif req and req.gpu_offload:
        gpu = req.gpu_offload
    else:
        gpu = current_config.gpu_offload or "max"

    current_config.active_model = model_key
    current_config.context_length = ctx

    res = load_or_reload_model(model_key, ctx, gpu)
    return {
        "success": res.get("success", False),
        "message": res.get("message", ""),
        "config": current_config
    }

# G2: SSRF Protection on Probe Endpoint (Strict Localhost/Loopback only)
ALLOWED_PROBE_HOSTS = {"127.0.0.1", "localhost", "0.0.0.0", "::1"}

@app.post("/api/engine/probe", dependencies=[Depends(verify_station_auth)])
async def probe_engine(req: ProbeRequest):
    host_clean = req.host.strip().lower()
    if host_clean.startswith("http://"):
        host_clean = host_clean[7:]
    elif host_clean.startswith("https://"):
        host_clean = host_clean[8:]
    host_clean = host_clean.split("/")[0].split(":")[0]

    if host_clean not in ALLOWED_PROBE_HOSTS:
        raise HTTPException(
            status_code=400,
            detail="Forbidden probe target. Engine probe is strictly restricted to local host interfaces."
        )

    target_url = f"http://{host_clean}:{req.port}/v1/models"
    start_time = os.times().elapsed
    try:
        async with httpx.AsyncClient(timeout=3.0) as client:
            res = await client.get(target_url)
            elapsed_ms = int((os.times().elapsed - start_time) * 1000)
            if res.status_code == 200:
                data = res.json()
                models = [m.get("id") for m in data.get("data", []) if m.get("id")]
                return {
                    "online": True,
                    "latency_ms": max(1, elapsed_ms),
                    "model_count": len(models),
                    "models": models
                }
            return {"online": False, "status_code": res.status_code}
    except Exception:
        return {"online": False, "error": "Connection refused by local model engine."}

@app.post("/api/engine/unload", dependencies=[Depends(verify_station_auth)])
async def unload_engine_model():
    """Unload model from GPU VRAM and system memory"""
    lms_path = get_lms_cli_path()
    if lms_path and os.path.exists(lms_path):
        try:
            result = subprocess.run(
                [lms_path, "unload", "--all"],
                capture_output=True,
                text=True,
                timeout=10,
                **get_subprocess_kwargs()
            )
            return {
                "success": result.returncode == 0,
                "message": "GPU memory released successfully.",
                "details": result.stdout.strip()
            }
        except Exception:
            pass

    # Fallback to REST endpoint if engine supports it
    engine_url = get_engine_base_url()
    try:
        async with httpx.AsyncClient(timeout=4.0) as client:
            res = await client.post(f"{engine_url}/api/v0/model/unload", json={})
            if res.status_code == 200:
                return {"success": True, "message": "GPU memory released via API."}
    except Exception:
        pass

    return {
        "success": True,
        "message": "Unload signal processed."
    }

@app.get("/api/models", dependencies=[Depends(verify_station_auth)])
@app.get("/v1/models", dependencies=[Depends(verify_station_auth)])
async def list_models():
    engine_url = get_engine_base_url()
    target = f"{engine_url}/v1/models"
    try:
        async with httpx.AsyncClient(timeout=4.0) as client:
            res = await client.get(target)
            if res.status_code == 200:
                return res.json()
    except Exception:
        pass

    # Fallback checking lms ps / ls if available
    lms_path = get_lms_cli_path()
    if lms_path and os.path.exists(lms_path):
        try:
            ps_res = subprocess.run([lms_path, "ps", "--json"], capture_output=True, text=True, timeout=3, **get_subprocess_kwargs())
            if ps_res.returncode == 0:
                import json
                items = json.loads(ps_res.stdout)
                models = [{"id": it["modelKey"], "object": "model"} for it in items if "modelKey" in it]
                if models:
                    return {"object": "list", "data": models}
        except Exception:
            pass

    return {
        "object": "list",
        "data": [{"id": current_config.active_model or "qwen2.5-3b-instruct-abliterated", "object": "model"}],
        "offline": True
    }

@app.post("/v1/chat/completions", dependencies=[Depends(verify_station_auth)])
async def chat_completions(request: Request):
    try:
        body = await request.json()
    except Exception:
        raise HTTPException(status_code=400, detail="Malformed JSON request body.")

    engine_url = get_engine_base_url()
    target = f"{engine_url}/v1/chat/completions"

    # Set model if missing
    if not body.get("model") or body.get("model") == "default":
        body["model"] = current_config.active_model or "qwen2.5-3b-instruct-abliterated"

    # Apply inference parameters if not explicitly provided in the request
    if "temperature" not in body and current_config.temperature is not None:
        body["temperature"] = current_config.temperature
    if "top_p" not in body and current_config.top_p is not None:
        body["top_p"] = current_config.top_p
    if "frequency_penalty" not in body and current_config.frequency_penalty != 0.0:
        body["frequency_penalty"] = current_config.frequency_penalty
    if "presence_penalty" not in body and current_config.presence_penalty != 0.0:
        body["presence_penalty"] = current_config.presence_penalty
    if "max_tokens" not in body and current_config.max_tokens > 0:
        body["max_tokens"] = current_config.max_tokens

    def _is_non_empty_content(c):
        if c is None:
            return False
        if isinstance(c, str):
            return bool(c.strip())
        if isinstance(c, list):
            return len(c) > 0
        return True

    # System role handling: ONLY inject when explicitly enabled and non-empty
    messages = body.get("messages", [])
    if current_config.system_role_enabled and current_config.system_prompt.strip():
        if not any(m.get("role") == "system" for m in messages):
            messages.insert(0, {"role": "system", "content": current_config.system_prompt.strip()})
            body["messages"] = messages
    else:
        # Strip out empty or forced system prompts if user turned system role OFF
        body["messages"] = [m for m in messages if m.get("role") != "system" or _is_non_empty_content(m.get("content"))]

    is_stream = body.get("stream", False)

    if is_stream:
        async def stream_generator():
            client = httpx.AsyncClient(timeout=600.0)
            try:
                async with client.stream("POST", target, json=body, headers={"Content-Type": "application/json"}) as r:
                    if r.status_code != 200:
                        yield 'data: {"error": "Local model engine returned an error during streaming."}\n\n'
                        return
                    async for chunk in r.aiter_bytes():
                        yield chunk
            except Exception:
                yield 'data: {"error": "Connection lost with local model engine."}\n\n'
            finally:
                await client.aclose()

        return StreamingResponse(
            stream_generator(),
            media_type="text/event-stream",
            headers={
                "Cache-Control": "no-cache",
                "Connection": "keep-alive",
                "X-Accel-Buffering": "no"
            }
        )
    else:
        try:
            async with httpx.AsyncClient(timeout=600.0) as client:
                res = await client.post(target, json=body)
                return res.json()
        except Exception:
            raise HTTPException(status_code=502, detail="Cannot connect to local model engine.")

@app.get("/api/tts", dependencies=[Depends(verify_station_auth)])
async def get_tts(text: str, lang: str = "tr"):
    """
    Lightweight, zero-model natural neural TTS endpoint.
    Streams natural human-sounding voice audio in real-time for any language (tr, en, etc.).
    """
    cleaned = text.strip()
    if not cleaned:
        raise HTTPException(status_code=400, detail="Text cannot be empty")

    target_lang = "tr" if lang.lower().startswith("tr") else "en"

    # 1. Primary: Local Lightweight Piper Neural Voice Engine
    wav_bytes = synthesize_with_piper(cleaned, target_lang)
    if wav_bytes and len(wav_bytes) > 1000:
        return Response(
            content=wav_bytes,
            media_type="audio/wav",
            headers={
                "Cache-Control": "public, max-age=86400",
                "Accept-Ranges": "bytes"
            }
        )

    # 2. Fallback: Google Neural TTS Client
    mp3_bytes = await synthesize_fallback_google(cleaned, target_lang)
    if mp3_bytes and len(mp3_bytes) > 100:
        return Response(
            content=mp3_bytes,
            media_type="audio/mpeg",
            headers={
                "Cache-Control": "public, max-age=86400",
                "Accept-Ranges": "bytes"
            }
        )

    raise HTTPException(status_code=500, detail="Failed to synthesize natural speech")
