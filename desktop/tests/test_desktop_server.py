import sys
import os
import pytest
from fastapi.testclient import TestClient

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from server import app, get_pairing_pin, get_session_auth_token, KOVAN_STATION_PORT, current_config, load_or_reload_model

client = TestClient(app)

def test_status_endpoint():
    res = client.get("/api/status")
    assert res.status_code == 200
    data = res.json()
    assert data["status"] == "online"
    assert data["station_port"] == 27615
    assert "engine_port" in data

def test_security_headers_present():
    res = client.get("/api/status")
    assert res.headers.get("x-content-type-options") == "nosniff"
    assert res.headers.get("x-frame-options") == "DENY"
    assert res.headers.get("referrer-policy") == "strict-origin-when-cross-origin"

def test_pairing_flow():
    pair_res = client.get("/api/pair")
    assert pair_res.status_code == 200
    pin = pair_res.json()["pin"]
    assert len(pin) == 6

    invalid_verify = client.post("/api/pair/verify", json={"pin": "000000"})
    assert invalid_verify.status_code == 401

    valid_verify = client.post("/api/pair/verify", json={"pin": pin})
    assert valid_verify.status_code == 200
    data = valid_verify.json()
    assert data["success"] is True
    assert "auth_token" in data
    assert data["auth_token"] == get_session_auth_token()

def test_auth_blocks_unauthorized_requests():
    current_config.pin_required = True
    try:
        # Calling protected endpoints without token must return 401 Unauthorized
        res_cfg = client.get("/api/engine/config")
        assert res_cfg.status_code == 401

        res_reload = client.post("/api/engine/apply-and-reload", json={"context_length": 4096})
        assert res_reload.status_code == 401

        res_chat = client.post("/v1/chat/completions", json={"messages": [{"role": "user", "content": "hi"}]})
        assert res_chat.status_code == 401
    finally:
        current_config.pin_required = False

def test_optional_pin_mode():
    current_config.pin_required = False
    res = client.get("/api/engine/config")
    assert res.status_code == 200

    toggle_res = client.post("/api/settings/pin-required", json={"pin_required": True})
    assert toggle_res.status_code == 200
    assert toggle_res.json()["pin_required"] is True
    assert current_config.pin_required is True

    assert client.get("/api/engine/config").status_code == 401

    toggle_res2 = client.post("/api/settings/pin-required", json={"pin_required": False})
    assert toggle_res2.status_code == 200
    assert toggle_res2.json()["pin_required"] is False
    assert current_config.pin_required is False

def test_auth_allows_valid_token():
    token = get_session_auth_token()
    headers = {"Authorization": f"Bearer {token}"}

    res_cfg = client.get("/api/engine/config", headers=headers)
    assert res_cfg.status_code == 200
    assert "engine_port" in res_cfg.json()

def test_engine_config_endpoints():
    token = get_session_auth_token()
    headers = {"Authorization": f"Bearer {token}"}

    new_cfg = {
        "engine_host": "http://127.0.0.1",
        "engine_port": 58080,
        "active_model": "qwen2.5-3b-instruct-abliterated",
        "context_length": 8192,
        "gpu_offload": "0.5",
        "cpu_threads": 6,
        "temperature": 0.4,
        "top_p": 0.9,
        "top_k": 50,
        "frequency_penalty": 0.1,
        "presence_penalty": 0.1,
        "max_tokens": 2048,
        "system_role_enabled": False,
        "system_prompt": "Optional system prompt",
        "language": "en"
    }
    update_res = client.post("/api/engine/config", json=new_cfg, headers=headers)
    assert update_res.status_code == 200

    get_res = client.get("/api/engine/config", headers=headers)
    assert get_res.status_code == 200
    cfg = get_res.json()
    assert cfg["engine_port"] == 58080
    assert cfg["context_length"] == 8192
    assert cfg["system_role_enabled"] is False

def test_ssrf_protection_on_probe():
    token = get_session_auth_token()
    headers = {"Authorization": f"Bearer {token}"}

    # Probing external IP or cloud metadata must be blocked with 400
    bad_probe = client.post("/api/engine/probe", json={"host": "169.254.169.254", "port": 80}, headers=headers)
    assert bad_probe.status_code == 400

    bad_probe2 = client.post("/api/engine/probe", json={"host": "attacker.com", "port": 80}, headers=headers)
    assert bad_probe2.status_code == 400

    # Localhost probe is accepted
    local_probe = client.post("/api/engine/probe", json={"host": "127.0.0.1", "port": 58080}, headers=headers)
    assert local_probe.status_code == 200

def test_model_key_sanitization():
    # Attempting flag injection in model key
    res = load_or_reload_model("--dangerous-flag", 4096, "max")
    assert res["success"] is False
    assert "Invalid model" in res["message"]

def test_multimodal_payload_handling():
    token = get_session_auth_token()
    headers = {"Authorization": f"Bearer {token}"}

    payload = {
        "model": "qwen2.5-3b-instruct-abliterated",
        "messages": [
            {
                "role": "user",
                "content": [
                    {"type": "text", "text": "Analyze this image"},
                    {"type": "image_url", "image_url": {"url": "data:image/jpeg;base64,abc123xyz"}}
                ]
            }
        ],
        "stream": False
    }
    res = client.post("/v1/chat/completions", json=payload, headers=headers)
    assert res.status_code in [200, 502]

def test_natural_tts_endpoint():
    token = get_session_auth_token()
    headers = {"Authorization": f"Bearer {token}"}

    res = client.get("/api/tts?text=Merhaba%20KovanLLM&lang=tr", headers=headers)
    assert res.status_code in [200, 500]
    if res.status_code == 200:
        assert res.headers["content-type"] in ["audio/wav", "audio/mpeg"]
        assert len(res.content) > 100

def test_apply_and_reload_endpoint():
    token = get_session_auth_token()
    headers = {"Authorization": f"Bearer {token}"}

    res = client.post("/api/engine/apply-and-reload", json={"context_length": 8192, "gpu_offload": "max"}, headers=headers)
    assert res.status_code == 200
    data = res.json()
    assert "config" in data
    assert data["config"]["context_length"] == 8192

def test_pair_landing_page():
    res = client.get("/pair")
    assert res.status_code == 200
    assert "kovanllm://pair" in res.text
    assert "KovanLLM Station" in res.text

def test_advanced_inference_settings_endpoint():
    token = get_session_auth_token()
    headers = {"Authorization": f"Bearer {token}"}

    payload = {
        "batch_size": 1024,
        "physical_batch_size": 512,
        "max_concurrent_predictions": 8,
        "unified_kv_cache": True,
        "context_checkpoints": True,
        "rope_freq_base": 50000.0,
        "rope_freq_scale": 2.0,
        "speculative_decoding_enabled": True,
        "speculative_draft_tokens": 8,
        "flash_attention": True
    }
    res = client.post("/api/engine/config", json=payload, headers=headers)
    assert res.status_code == 200
    data = res.json()
    assert data["success"] is True
    cfg = data["config"]
    assert cfg["batch_size"] == 1024
    assert cfg["physical_batch_size"] == 512
    assert cfg["max_concurrent_predictions"] == 8
    assert cfg["unified_kv_cache"] is True
    assert cfg["context_checkpoints"] is True
    assert cfg["rope_freq_base"] == 50000.0
    assert cfg["rope_freq_scale"] == 2.0
    assert cfg["speculative_decoding_enabled"] is True
    assert cfg["speculative_draft_tokens"] == 8
    assert cfg["flash_attention"] is True

