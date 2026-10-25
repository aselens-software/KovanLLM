# KovanLLM Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build KovanLLM — a secure, zero-port-forward local LLM companion agent and mobile client featuring real-time GPU/VRAM hardware telemetry, OpenAI-compatible SSE streaming chat, token/s metrics, and remote model ejection.

**Architecture:** A lightweight Python FastAPI companion daemon runs on the host machine, serving hardware metrics (RTX 3070 VRAM, temperature, power) and acting as an authenticated reverse proxy for LM Studio/Ollama with SSE streaming. The mobile client (React Native Expo / TypeScript) connects over local Wi-Fi or encrypted tunnels (Tailscale/Cloudflare), stores chat history locally in SQLite, and renders Markdown with tabular hardware gauges.

**Tech Stack:** Python 3 (FastAPI, Uvicorn, httpx, pydantic), React Native Expo (TypeScript, expo-sqlite, lucide-react-native, react-native-markdown-display).

**Spec:** `docs/superpowers/specs/2026-10-24-kovanllm-design.md`

## Global Constraints

- **Host Python:** Use `uv` or system Python 3 (`python3 -m venv`) with minimal dependencies.
- **Hardware Telemetry:** Must never crash if `nvidia-smi` is busy or on non-NVIDIA systems; graceful fallback to CPU/RAM metrics.
- **Zero Port Forwarding:** Design for LAN IP or encrypted P2P tunnels (Tailscale/Cloudflare) with API Key authentication.
- **Local Privacy:** SQLite on the mobile device stores all message history; the host agent retains zero chat logs.
- **Interface Polish:** Dark theme (#090A0F, #13151D), concentric border radii, tabular numbers (`tabular-nums`) for token/s and VRAM stats.

## Review Focus

1. **SSE Streaming Disconnects:** Mobile network switches from Wi-Fi to LTE during stream; the client must gracefully finalize the partial message without locking the UI.
2. **GPU Metric Extraction Failures:** When `nvidia-smi` locks temporarily under heavy inference load, the metrics endpoint must return cached or fallback values within 200ms rather than hanging.
3. **Model Eject While Generating:** Ejecting a model during an active inference stream must safely cancel the active stream and trigger VRAM release.
4. **Large Markdown & Code Blocks:** Mobile chat list rendering long Python/JSON snippets must not drop frame rate or overflow horizontally.
5. **Wake-on-LAN Packet Handling:** Sending WOL magic packet with invalid MAC must return a clear validation error without throwing an uncaught socket exception.

---

### Task 1: Host Agent Skeleton, Config & API Key Auth

**Files:**
- Create: `server/app/config.py`
- Create: `server/app/auth.py`
- Create: `server/app/main.py`
- Create: `server/requirements.txt`
- Test: `server/tests/test_auth.py`

**Interfaces:**
- Produces: `verify_api_key(request)` dependency, `Settings` pydantic model, `/api/status` endpoint returning health and auth status.

- [ ] **Step 1: Write the failing auth test**

```python
# server/tests/test_auth.py
from fastapi.testclient import TestClient
from app.main import app

client = TestClient(app)

def test_status_without_key_when_key_required():
    response = client.get("/api/status", headers={"X-API-Key": "wrong-key"})
    assert response.status_code == 401

def test_status_with_valid_key():
    response = client.get("/api/status", headers={"X-API-Key": "kovan-secret-key"})
    assert response.status_code == 200
    assert response.json()["status"] == "ok"
```

- [ ] **Step 2: Run test to verify it fails**

Run: `pytest server/tests/test_auth.py -v`
Expected: FAIL (No module / server not created)

- [ ] **Step 3: Implement config, auth and FastAPI app**

Implement `Settings` in `server/app/config.py` reading `KOVAN_API_KEY`, `LM_STUDIO_URL` (default `http://127.0.0.1:1234`), and `PORT` (default `8443`). Implement API key verification in `auth.py` and register `/api/status` in `main.py`.

- [ ] **Step 4: Run test to verify it passes**

Run: `pytest server/tests/test_auth.py -v`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add server/
git commit -m "feat(server): initialize host agent skeleton with api key auth"
```

---

### Task 2: GPU and Hardware Telemetry Service

**Files:**
- Create: `server/app/metrics.py`
- Modify: `server/app/main.py`
- Test: `server/tests/test_metrics.py`

**Interfaces:**
- Produces: `get_system_metrics() -> dict` with keys `gpu`, `system`, and `llm_engine`.

- [ ] **Step 1: Write the failing telemetry test**

```python
# server/tests/test_metrics.py
from app.metrics import get_system_metrics

def test_get_system_metrics_structure():
    metrics = get_system_metrics()
    assert "gpu" in metrics
    assert "system" in metrics
    assert "ram_used_mb" in metrics["system"]
    assert "vram_total_mb" in metrics["gpu"]
```

- [ ] **Step 2: Run test to verify it fails**

Run: `pytest server/tests/test_metrics.py -v`
Expected: FAIL

- [ ] **Step 3: Implement `get_system_metrics()` in `server/app/metrics.py`**

Parse `nvidia-smi --query-gpu=name,temperature.gpu,memory.used,memory.total,power.draw,power.limit,utilization.gpu --format=csv,noheader,nounits` with timeout=1.0s. Add CPU/RAM reading via `psutil` or `/proc/meminfo`. Handle non-NVIDIA or error cases gracefully with fallback values.

- [ ] **Step 4: Run test to verify it passes**

Run: `pytest server/tests/test_metrics.py -v`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add server/app/metrics.py server/tests/test_metrics.py server/app/main.py
git commit -m "feat(server): add hardware telemetry service for nvidia gpu and system stats"
```

---

### Task 3: OpenAI Proxy, Model Eject & Wake-on-LAN

**Files:**
- Create: `server/app/llm_proxy.py`
- Create: `server/app/system_ops.py`
- Modify: `server/app/main.py`
- Test: `server/tests/test_proxy.py`

**Interfaces:**
- Produces: `POST /v1/chat/completions` (streaming proxy), `GET /v1/models`, `POST /api/models/unload`, `POST /api/system/wake`.

- [ ] **Step 1: Write the failing proxy & system ops test**

```python
# server/tests/test_proxy.py
from fastapi.testclient import TestClient
from app.main import app

client = TestClient(app)

def test_eject_model_endpoint():
    response = client.post("/api/models/unload", headers={"X-API-Key": "kovan-secret-key"})
    assert response.status_code in [200, 502]
```

- [ ] **Step 2: Run test to verify it fails**

Run: `pytest server/tests/test_proxy.py -v`
Expected: FAIL

- [ ] **Step 3: Implement reverse proxy & eject logic**

Implement `llm_proxy.py` using `httpx.AsyncClient` to forward `/v1/chat/completions` as a streaming `StreamingResponse` (SSE) and `/v1/models`. Implement `system_ops.py` to trigger model eject (calling `lms unload --all` or LM Studio eject endpoint) and Wake-on-LAN magic packet broadcaster.

- [ ] **Step 4: Run test to verify it passes**

Run: `pytest server/tests/test_proxy.py -v`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add server/app/llm_proxy.py server/app/system_ops.py server/tests/test_proxy.py server/app/main.py
git commit -m "feat(server): add openai reverse proxy, model eject and wake-on-lan controller"
```

---

### Task 4: Mobile Client Scaffold, Dark Theme & SQLite Storage

**Files:**
- Create: `mobile/package.json`
- Create: `mobile/src/theme/colors.ts`
- Create: `mobile/src/storage/db.ts`
- Create: `mobile/src/types/index.ts`
- Test: `mobile/__tests__/db.test.ts`

**Interfaces:**
- Produces: `initDatabase()`, `saveMessage(msg)`, `getMessages(chatId)`, `getChats()`, `saveSettings(settings)`, `getSettings()`.

- [ ] **Step 1: Write the failing storage test**

```typescript
// mobile/__tests__/db.test.ts
import { initDatabase, saveMessage, getMessages } from '../src/storage/db';

test('database saves and retrieves chat messages', async () => {
  await initDatabase();
  await saveMessage({ id: '1', chatId: 'c1', role: 'user', content: 'Merhaba', createdAt: Date.now() });
  const messages = await getMessages('c1');
  expect(messages.length).toBe(1);
  expect(messages[0].content).toBe('Merhaba');
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm test -- mobile/__tests__/db.test.ts`
Expected: FAIL

- [ ] **Step 3: Implement SQLite storage and theme**

Set up `expo-sqlite` database with `chats`, `messages`, and `settings` tables. Define dark theme tokens in `mobile/src/theme/colors.ts` adhering to `make-interfaces-feel-better` and `frontend-ui-engineering` guidelines.

- [ ] **Step 4: Run test to verify it passes**

Run: `npm test -- mobile/__tests__/db.test.ts`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add mobile/
git commit -m "feat(mobile): scaffold mobile client with dark theme and sqlite storage"
```

---

### Task 5: Hardware Dashboard Screen & Telemetry Gauges

**Files:**
- Create: `mobile/src/components/HardwareGauge.tsx`
- Create: `mobile/src/components/VramBar.tsx`
- Create: `mobile/src/screens/DashboardScreen.tsx`
- Create: `mobile/src/api/client.ts`
- Test: `mobile/__tests__/dashboard.test.tsx`

**Interfaces:**
- Produces: `DashboardScreen` component, `fetchMetrics(serverUrl, apiKey) -> Promise<Metrics>`.

- [ ] **Step 1: Write dashboard test**

```typescript
// mobile/__tests__/dashboard.test.tsx
import { parseVramPercentage } from '../src/components/VramBar';

test('calculates correct vram percentage and format', () => {
  const pct = parseVramPercentage(1777, 8192);
  expect(pct).toBe('21.7%');
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm test -- mobile/__tests__/dashboard.test.tsx`
Expected: FAIL

- [ ] **Step 3: Implement HardwareGauge, VramBar and DashboardScreen**

Build live gauges with tabular numbers (`tabular-nums`), ping calculation, GPU temperature colored badge (<60°C green, <75°C yellow, >=75°C red), power draw indicator, model eject button, and active model card.

- [ ] **Step 4: Run test to verify it passes**

Run: `npm test -- mobile/__tests__/dashboard.test.tsx`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add mobile/src/components/ mobile/src/screens/DashboardScreen.tsx mobile/src/api/
git commit -m "feat(mobile): add hardware telemetry dashboard and vram visualizer"
```

---

### Task 6: Chat Interface, SSE Streaming & Token/s Counter

**Files:**
- Create: `mobile/src/components/ChatMessage.tsx`
- Create: `mobile/src/components/ChatInput.tsx`
- Create: `mobile/src/screens/ChatScreen.tsx`
- Create: `mobile/src/api/stream.ts`
- Test: `mobile/__tests__/stream.test.ts`

**Interfaces:**
- Produces: `streamChatCompletion(params, onChunk, onDone, onError)`, `ChatMessage` with code copy and token/s display.

- [ ] **Step 1: Write streaming parser test**

```typescript
// mobile/__tests__/stream.test.ts
import { parseSSEChunk } from '../src/api/stream';

test('parses openai standard sse data chunk', () => {
  const raw = 'data: {"choices":[{"delta":{"content":"Kovan"}}]}\n\n';
  const token = parseSSEChunk(raw);
  expect(token).toBe('Kovan');
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm test -- mobile/__tests__/stream.test.ts`
Expected: FAIL

- [ ] **Step 3: Implement SSE streaming and Chat UI**

Implement `streamChatCompletion` with token-per-second measurement: calculate `tokensReceived / elapsedSeconds` and render badge under streaming messages. Render Markdown code blocks with copy-to-clipboard button. Implement expanding input field and abort controller for stopping generation.

- [ ] **Step 4: Run test to verify it passes**

Run: `npm test -- mobile/__tests__/stream.test.ts`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add mobile/src/components/ChatMessage.tsx mobile/src/components/ChatInput.tsx mobile/src/screens/ChatScreen.tsx mobile/src/api/stream.ts
git commit -m "feat(mobile): add sse streaming chat interface with live token/s meter"
```

---

### Task 7: System Prompts Drawer, Network Settings & Verification

**Files:**
- Create: `mobile/src/components/PromptTemplatesDrawer.tsx`
- Create: `mobile/src/components/SettingsModal.tsx`
- Modify: `mobile/App.tsx`
- Create: `scripts/start_host.sh`
- Create: `README.md`
- Test: `tests/integration/test_e2e_flow.py`

**Interfaces:**
- Produces: Complete working application with prompt selector (Kodlayıcı, Çevirmen, Özetleyici), server connection configuration (LAN IP / Tailscale / Cloudflare), and unified start script.

- [ ] **Step 1: Write integration test**

```python
# tests/integration/test_e2e_flow.py
# Verify end-to-end status, metrics, and proxy headers
from fastapi.testclient import TestClient
from server.app.main import app

client = TestClient(app)

def test_full_agent_api():
    res = client.get("/api/metrics", headers={"X-API-Key": "kovan-secret-key"})
    assert res.status_code == 200
    assert "gpu" in res.json()
```

- [ ] **Step 2: Run test to verify it fails**

Run: `pytest tests/integration/test_e2e_flow.py`
Expected: FAIL

- [ ] **Step 3: Implement Settings, Prompt Templates and end-to-end glue**

Wire `SettingsModal` for switching between Wi-Fi and Tailscale/Tunnel host URLs. Add system prompt selector chips. Create `scripts/start_host.sh` for single-command server launch. Update documentation in `README.md`.

- [ ] **Step 4: Run test to verify it passes**

Run: `pytest tests/integration/test_e2e_flow.py`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add mobile/ App.tsx scripts/ README.md tests/
git commit -m "feat: complete kovanllm end-to-end integration and documentation"
```
