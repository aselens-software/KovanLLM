import os
import subprocess
import sys
import tempfile
import urllib.parse
import httpx

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
piper_name = "piper.exe" if sys.platform == "win32" else "piper"
PIPER_BIN = os.path.join(BASE_DIR, "bin", "piper", piper_name)

TR_MODEL = os.path.join(BASE_DIR, "models", "tts", "vits-piper-tr_TR-dfki-medium-int8", "tr_TR-dfki-medium.onnx")
TR_CONFIG = os.path.join(BASE_DIR, "models", "tts", "vits-piper-tr_TR-dfki-medium-int8", "tr_TR-dfki-medium.onnx.json")

EN_MODEL = os.path.join(BASE_DIR, "models", "tts", "vits-piper-en_US-amy-medium-int8", "en_US-amy-medium.onnx")
EN_CONFIG = os.path.join(BASE_DIR, "models", "tts", "vits-piper-en_US-amy-medium-int8", "en_US-amy-medium.onnx.json")

def is_piper_available() -> bool:
    if not os.path.exists(PIPER_BIN):
        return False
    if sys.platform != "win32" and not os.access(PIPER_BIN, os.X_OK):
        return False
    return os.path.exists(TR_MODEL)

def synthesize_with_piper(text: str, lang: str = "tr") -> bytes | None:
    """
    Lightweight, background-running neural voice synthesis using Piper VITS.
    Generates natural human voice without cloud dependency or GPU load.
    """
    if not is_piper_available():
        return None

    target_lang = "tr" if lang.lower().startswith("tr") else "en"
    model_path = TR_MODEL if target_lang == "tr" else EN_MODEL
    config_path = TR_CONFIG if target_lang == "tr" else EN_CONFIG

    if not os.path.exists(model_path) or not os.path.exists(config_path):
        return None

    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as out_f:
        out_path = out_f.name

    kwargs = {}
    if sys.platform == "win32":
        kwargs["creationflags"] = 0x08000000

    try:
        proc = subprocess.run(
            [PIPER_BIN, "--model", model_path, "--config", config_path, "--output_file", out_path],
            input=text.strip(),
            text=True,
            capture_output=True,
            timeout=12,
            **kwargs
        )
        if proc.returncode == 0 and os.path.exists(out_path):
            with open(out_path, "rb") as f:
                data = f.read()
                if len(data) > 1000:
                    return data
    except Exception as e:
        print(f"[VoiceEngine] Piper error: {e}")
    finally:
        if os.path.exists(out_path):
            try:
                os.remove(out_path)
            except Exception:
                pass

    return None

async def synthesize_fallback_google(text: str, lang: str = "tr") -> bytes | None:
    """Fallback to Google TTS client if Piper is absent"""
    cleaned = text.strip()
    if not cleaned:
        return None

    target_lang = "tr" if lang.lower().startswith("tr") else "en"
    chunks = []
    if len(cleaned) <= 150:
        chunks = [cleaned]
    else:
        words = cleaned.split()
        cur = []
        cur_len = 0
        for w in words:
            if cur_len + len(w) + 1 > 150:
                if cur:
                    chunks.append(" ".join(cur))
                    cur = []
                    cur_len = 0
            cur.append(w)
            cur_len += len(w) + 1
        if cur:
            chunks.append(" ".join(cur))

    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Referer": "https://translate.google.com/"
    }

    audio_bytes = bytearray()
    try:
        async with httpx.AsyncClient(timeout=8.0) as client:
            for ch in chunks:
                enc = urllib.parse.quote(ch)
                url = f"https://translate.google.com/translate_tts?ie=UTF-8&tl={target_lang}&client=tw-ob&q={enc}"
                r = await client.get(url, headers=headers)
                if r.status_code == 200 and len(r.content) > 100:
                    audio_bytes.extend(r.content)
        if len(audio_bytes) > 100:
            return bytes(audio_bytes)
    except Exception:
        pass
    return None
