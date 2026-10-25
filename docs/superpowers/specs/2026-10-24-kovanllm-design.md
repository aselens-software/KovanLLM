# KovanLLM: Sistem Mimarisi ve Tasarım Dokümanı (Design Spec)

**Tarih:** 2026-10-24  
**Proje Adı:** KovanLLM  
**Hedef:** Evdeki güçlü GPU donanımını (RTX 3070, LM Studio / llama.cpp / Ollama) Android mobil cihaza sıfır port açma riskiyle, P2P şifreli tünelle bağlayan; gerçek zamanlı telemetri, model yönetimi ve akıcı SSE streaming sohbet sunan tam teşekküllü yerel yapay zeka ekosistemi.

---

## 1. Mimarinin Amacı ve Hedefleri

- **Güvenlik (Zero-Port-Forward):** Modemde port açmadan (Tailscale VPN, Cloudflare Tunnel veya E2EE WebSocket/WebRTC) güvenli erişim.
- **Sıfır Algılanan Gecikme (SSE Streaming):** Token'ların harf harf akması, anlık `tok/s` üretim hızı göstergesi.
- **Canlı Donanım Telemetrisi:** GPU sıcaklığı (°C), VRAM kullanımı (GB), sistem RAM ve anlık güç tüketimi (Watt), makineye olan gecikme (ping ms).
- **Uzaktan Donanım & Model Kontrolü:** Model değiştirme (unload / load), GPU'yu boşa çıkarıp soğutma (Eject Model) ve Wake-on-LAN hazırlığı.
- **Yerel Gizlilik:** Sohbet geçmişinin sunucuda değil, telefondaki yerel SQLite veritabanında saklanması.
- **Üst Düzey Arayüz & Etkileşim:** `make-interfaces-feel-better` ve `frontend-ui-engineering` prensiplerine uygun; karanlık mod (dark theme), eşmerkezli yuvarlaklıklar (concentric radius), tabular rakamlar (`tabular-nums`), dokunsal mikro animasyonlar.

---

## 2. Sistem Bileşenleri

```
┌────────────────────────────────────────────────────────┐
│                   MOBİL İSTEMCİ                        │
│   (Android: React Native Expo / TypeScript)            │
│   ├── Dashboard & Donanım Telemetrisi                  │
│   │   ├── GPU Sıcaklık, VRAM Barı, Watt, Ping         │
│   │   └── Model Seçici, Eject & Context Length         │
│   ├── Akıcı Sohbet Arayüzü (Chat View)                 │
│   │   ├── SSE Streaming (Harf harf akış)               │
│   │   ├── Markdown & Kod Renklendirme                  │
│   │   ├── Token/s Canlı Metrik Göstergesi              │
│   │   └── Sistem İstemleri (System Prompts Çekmecesi) │
│   └── Yerel Depolama (SQLite / expo-sqlite)            │
│       └── Konuşma geçmişi, ayarlar, API anahtarı       │
└──────────────────────────┬─────────────────────────────┘
                           │
       (Şifreli Tünel: Tailscale / Cloudflare / Yerel Wi-Fi)
       (Auth: X-API-Key veya Bearer Token)
                           │
┌──────────────────────────▼─────────────────────────────┐
│              EV BİLGİSAYARI (HOST AGENT)               │
│   (Python FastAPI / Uvicorn Servisi)                   │
│   ├── Güvenlik & Doğrulama Middleware (API Key)        │
│   ├── Donanım Metrik Servisi (nvidia-smi / pynvml)     │
│   │   └── GPU Temp, VRAM, Power, RAM, CPU              │
│   ├── Sistem Kontrolü & Eject                          │
│   │   └── lms CLI / REST API entegrasyonu              │
│   └── OpenAI Uyumlu Ters Proxy (Reverse Proxy)         │
│       ├── /v1/chat/completions (SSE Stream)            │
│       └── /v1/models                                   │
└──────────────────────────┬─────────────────────────────┘
                           │ (Localhost: 127.0.0.1:1234)
┌──────────────────────────▼─────────────────────────────┐
│          YEREL LLM MOTORU (LM Studio / Ollama)         │
└────────────────────────────────────────────────────────┘
```

---

## 3. API Sözleşmesi (Companion Host Agent)

### 3.1. Telemetri ve Durum
- `GET /api/status`: Sunucu durumu, çalışma süresi, LM Studio bağlantı durumu.
- `GET /api/metrics`:
  ```json
  {
    "gpu": {
      "name": "NVIDIA GeForce RTX 3070",
      "temp_c": 54,
      "vram_used_mb": 1777,
      "vram_total_mb": 8192,
      "vram_pct": 21.7,
      "power_watts": 41.2,
      "power_limit_watts": 240.0,
      "utilization_pct": 12
    },
    "system": {
      "ram_used_mb": 8192,
      "ram_total_mb": 31800,
      "ram_pct": 25.8,
      "cpu_pct": 6.5
    },
    "llm_engine": {
      "status": "online",
      "loaded_model": "qwen2.5-coder-7b-instruct",
      "port": 1234
    }
  }
  ```

### 3.2. Model ve Donanım Yönetimi
- `GET /api/models`: LM Studio veya Ollama'daki mevcut modelleri listeler.
- `POST /api/models/load`: Belirtilen modeli VRAM'e yükler.
- `POST /api/models/unload` veya `POST /api/system/eject`: Aktif modeli VRAM'den tamamen boşaltır, kartı boşa çıkarıp soğumaya alır.
- `POST /api/system/wake`: Wake-on-LAN sinyali için yapılandırma.

### 3.3. OpenAI Standart Endpointleri (Doğrudan Tünellenen)
- `POST /v1/chat/completions`: `stream: true` destekli SSE akışı. Header'da anlık token hızını ölçmek için chunk aktarım süreleri ölçülür.
- `GET /v1/models`: OpenAI standart model listesi.

---

## 4. Mobil Arayüz ve Tasarım Kuralları

- **Tasarım Dili:** Koyu arayüz (Pure OLED Siyah `#090A0F`, Koyu Grafit `#13151D`, Vurgu `#3B82F6` / `#10B981` yeşil donanım durumu).
- **Rakamlar:** Anlık token/s, VRAM ve gecikme sayaçlarında zıplamayı önlemek için `tabular-nums` kullanımı.
- **Eşmerkezli Köşeler (Concentric Radius):** Dış kart `16px` ise iç buton/rozet `10px` + `padding 6px`.
- **Hafif Dokunma Geri Bildirimi:** Butonlara basıldığında `scale(0.97)` mikro yay animasyonu.
- **Kod Blokları:** Sözdizimi renklendirme (syntax highlighting), dil etiketi ve tek tıkla kopyalama butonu.
