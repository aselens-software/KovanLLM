# 🐝 KovanLLM: Desktop & Standalone Mobile Local LLM Station (v1.1)

[![License: MIT](https://img.shields.io/badge/License-MIT-amber.svg)](https://opensource.org/licenses/MIT)
[![Platform](https://img.shields.io/badge/Platform-Windows%20%7C%20Linux%20%7C%20Android-orange)](https://github.com/aselens-software/kovanllm)
[![Version](https://img.shields.io/badge/Version-v1.1-blue)](https://github.com/aselens-software/kovanllm/releases)
[![Author](https://img.shields.io/badge/Created%20by-Aselens%20Software%E2%84%A0-black)](https://aselens.com)

**KovanLLM** connects local large language models running on your home or workstation PC (LM Studio, Ollama, llama.cpp, etc.) directly to your Android device **without third-party VPNs, port forwarding, or complicated network configuration**. 

It operates as a lightweight desktop station service (PyQt6 + FastAPI) on Windows & Linux, paired with a modern native Android client (Kotlin + Jetpack Compose).

**Created by [Aselens Software℠](https://aselens.com)** • Licensed under the [MIT License](LICENSE)

---

## 🌟 Key Features

### 1. 🖥️ Cross-Platform Desktop Station (Windows & Linux)
- **Native Windows Support:** Instant double-click launcher (`run_desktop.bat`) and Inno Setup installer wizard (`installer/kovanllm_setup.iss`). Appears with its own pinned "KovanLLM" identity and custom icon on the Windows taskbar.
- **Linux Integration:** Runs via `./run_desktop.sh` with native `.desktop` application menu launcher.
- **Dedicated Station Port (`27615`):** Decoupled from model engines, serving as an intelligent local gateway, health monitor, and tunneling proxy.
- **Seamless Tunneling Fallback:** Automated encrypted Cloudflare Quick Tunnel and `localhost.run` SSH fallback. Works entirely offline over local Wi-Fi LAN when no internet access is present.
- **Optional 6-Digit PIN Security:** Desktop host can enforce 6-digit authentication for incoming client pairing.

### 2. 📱 Native Android Client (Google Play Ready)
- **Optimized Android App Bundle (`.aab`):** R8/Proguard rules delivering over 25% DEX code shrinking and resource optimization for Google Play store guidelines.
- **Full Cleartext HTTP LAN Support:** Connect to any local IP (`192.168.x.x`, `10.x.x.x`) on Port 27615 without security policy restrictions.
- **Discord-Style Markdown Rendering:** Full markdown support for both user prompts and model responses:
  - `**bold**` & `*italic*` / `_italic_`
  - `~~strikethrough~~` & `__underline__`
  - `***bold italic***`
  - `` `inline monospace code` ``
  - Fenced code blocks (` ``` `) with language detection and one-tap code copying
  - Headings (`# `, `## `, `### `), bullet points, and blockquotes (`> `)
- **Dedicated Blue "+ New Chat" Action:** Prominently styled blue session creation button in both the sessions drawer and the top action bar.
- **14 Professional System Roles:**
  1. *General Assistant*
  2. *Software Engineer*
  3. *Code Reviewer & Security Auditor*
  4. *Language Specialist & Polyglot*
  5. *Executive Summarizer*
  6. *Creative Writer & Storyteller*
  7. *Academic Researcher & Scientist*
  8. *Data Analyst & SQL Maestro*
  9. *DevOps & Cloud Architect*
  10. *Cyber Security Specialist*
  11. *Math & Logic Tutor*
  12. *Executive Business Strategist*
  13. *Product Manager & UX Designer*
  14. *Turkish Language & TDK Editor*
- **Advanced Model & Inference Controls:**
  - **Evaluation Batch Size:** Fine-tune batch processing (`128`, `256`, `512`, `1024`, `2048` tokens).
  - **Physical Batch Size (ubatch):** Hardware micro-batch execution (`64`, `128`, `256`, `512`).
  - **Max Concurrent Predictions:** Configurable parallel inference limit (`1`, `2`, `4`, `8`).
  - **Unified KV Cache:** Single memory pool allocation for attention key-values.
  - **Context Checkpoints / KV Shift:** Stateful context cache rotation.
  - **RoPE Frequency Base & Scale:** Custom rotary positional embedding parameters.
  - **Speculative Decoding:** Accelerated generation with configurable draft token lengths.
  - **Flash Attention:** Low-latency GPU memory attention acceleration.
- **Integrated QR Scanner:** Google Play Services ML Kit Barcode Scanner scans desktop pairing QR codes in milliseconds.
- **Live Voice & Audio Playback:** Voice recognition and natural device text-to-speech with stream interruption support.
- **WhatsApp-Style Media Previews:** Inline image and text file preview directly in conversation bubbles.
- **AdMob & GDPR/KVKK Compliant:** Integrated AdMob banner & inline chat interstitials with Google User Messaging Platform (UMP) consent.

---

## 📁 Repository Structure

```
kovanllm/
├── android/                    # Native Android App (Kotlin + Jetpack Compose)
│   ├── app/
│   │   ├── build.gradle.kts    # R8 minification, AdMob & ML Kit configuration
│   │   ├── proguard-rules.pro  # DEX shrinking and keep rules
│   │   └── src/main/java/com/aselens/kovanllm/
│   │       ├── MainActivity.kt
│   │       ├── ads/            # AdMob Banner, InlineBox, Interstitial & Consent
│   │       ├── data/           # Repository, LiveVoice, I18n, Presets, Models
│   │       ├── qr/             # Play Services QR Code Scanner
│   │       └── ui/             # ChatScreen, ModelSettingsScreen, StationLauncherScreen
│   └── gradlew
├── desktop/                    # Desktop Station Service (PyQt6 + FastAPI)
│   ├── desktop_gui.py          # Station GUI (Tabs: Pairing, Engine, Hardware, Advanced)
│   ├── server.py               # Station FastAPI backend (Port 27615)
│   ├── tunnel.py               # Multi-provider encrypted tunnel manager
│   ├── voice_engine.py         # Neural TTS & fallback voice engine
│   ├── run_desktop.bat         # Single-click Windows launcher
│   ├── run_desktop.sh          # Linux runner script
│   ├── assets/                 # Icons, SVG assets, and logos
│   └── tests/                  # Pytest station test suite (14 passing tests)
├── installer/
│   └── kovanllm_setup.iss      # Inno Setup 6 Windows installer script
├── docs/                       # Screenshots, brand assets, and store compliance documents
├── requirements.txt            # Python dependencies
├── LICENSE                     # MIT License
└── README.md
```

---

## 🚀 Quickstart Guide

### 1. Running on Windows
#### Option A: Quick Batch Launcher
1. Clone or download the repository:
   ```cmd
   git clone https://github.com/aselens/kovanllm.git
   cd kovanllm\desktop
   ```
2. Double-click `run_desktop.bat`. The script automatically sets up a `.venv` virtual environment, installs dependencies, and launches the station.

#### Option B: Inno Setup Windows Installer
1. Open [Inno Setup 6](https://jrsoftware.org/isdl.php).
2. Compile `installer/kovanllm_setup.iss`.
3. Run the resulting `dist/KovanLLM_Station_v1.1_Setup.exe` to install KovanLLM into your Program Files with Start Menu and Desktop shortcuts.

### 2. Running on Linux
```bash
git clone https://github.com/aselens/kovanllm.git
cd kovanllm/desktop
./run_desktop.sh
```

### 3. Building & Installing Android App
#### Generate Release AAB for Google Play:
```bash
cd android
./gradlew bundleRelease
```
Output artifact: `android/app/build/outputs/bundle/release/app-release.aab`

#### Build Local APK & Install via ADB:
```bash
./build_apk.sh
adb install KovanLLM.apk
```

---

## 🔒 Security & Privacy

- **100% Local Inference:** Conversations, system prompts, and media stay strictly between your phone and your workstation PC.
- **Station PIN Authentication:** Optional 6-digit pairing code prevents unauthorized access on shared local Wi-Fi networks.
- **Privacy Standard Compliance:** AdMob integrations utilize Google UMP (User Messaging Platform) for transparent GDPR/KVKK consent.

---

## ☕ Support the Project

If AselCam makes your meetings, streams, or workflows better, consider supporting ongoing development:

<p align="center">
  <a href="https://www.buymeacoffee.com/aselens" target="_blank">
    <img src="https://cdn.buymeacoffee.com/buttons/v2/default-yellow.png" alt="Buy Me a Coffee" style="height: 60px !important;width: 217px !important;">
  </a>
</p>

---

## 📄 License & Attribution

This project is licensed under the [MIT License](LICENSE).

Copyright (c) 2026 **Aselens Software℠** ([https://aselens.com](https://aselens.com))  
All rights reserved.
