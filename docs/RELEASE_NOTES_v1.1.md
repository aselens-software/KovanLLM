# 🐝 KovanLLM v1.1.0 — Cross-Platform Release (Windows, Linux & Android)

**KovanLLM v1.1.0** is here! This release brings major stability enhancements, full native Windows integration with an Inno Setup installer, rich Discord-style markdown rendering in mobile chat, advanced AI inference parameter tuning, and Google Play Store-ready optimizations.

---

## 🌟 What's New

### 📱 Android Mobile Client
- **Discord-Style Rich Markdown:** Full formatting support in chat messages:
  - `**bold**`, `*italic*`, `_italic_`, `***bold italic***`
  - `~~strikethrough~~`, `__underline__`
  - `` `inline monospace code` ``
  - Fenced code blocks (` ``` `) with language tags and a one-tap **Copy Code** button
  - Headings (`# `, `## `, `### `), bullet points (`• `), and blockquotes (`▎ `)
- **Prompt Deduplication on Regenerate:** Fixed an issue where regenerating an assistant response duplicated the user prompt in chat history and sent it twice to the LLM backend.
- **Streamlined Chat Header:** Removed redundant buttons from the top bar for a cleaner, distraction-free conversation interface.
- **Modernized Action Palette:** Prominent blue styling (`#2563EB`) on **"Start Chat"** and **"New Chat"** buttons.
- **14 Professional System Roles:** Expanded preset library covering software engineering, security auditing, summarization, creative writing, data analysis, DevOps, translation, and more.
- **Advanced Model & Inference Controls:** Configure evaluation batch size, physical batch size (ubatch), max concurrent predictions, unified KV cache, context checkpoints, RoPE frequency base/scale, speculative decoding, and flash attention.
- **Local Wi-Fi Cleartext Traffic Fix:** Explicitly enabled local HTTP connections (`http://192.168.x.x:27615`) to eliminate Android's "CLEARTEXT not permitted" restriction.
- **Google Play Store Optimization:** Over 25% DEX code shrinking with custom R8/Proguard rules. Includes official `.aab` bundle and signed `.apk`.

### 🖥️ Desktop Station (Windows & Linux)
- **Windows Port & Native Identity:**
  - Full native Windows support with single-click batch launcher (`run_desktop.bat`).
  - Appears on the Windows taskbar with custom icon and name (**KovanLLM**), replacing generic Python branding.
  - Windows Inno Setup installer wizard script (`installer/kovanllm_setup.iss`).
- **Smooth Window Dragging:** Borderless window can now be freely dragged across screens from anywhere on the header, titlebar, or window background without friction.
- **Pixel-Perfect Vector Icons:** Upgraded desktop icons to high-DPI FontAwesome SVG vectors with automatic aspect-ratio preservation, eliminating icon stretching or distortion.
- **Rock-Solid Tunnel Persistence:**
  - Migrated Cloudflare Quick Tunnel to HTTP/2 TCP multiplexing (`--protocol http2`) to avoid UDP timeouts on CGNAT/home ISPs.
  - Continuous stdout buffer draining to prevent pipe blockages (resolves Cloudflare Error 1033).
  - Tunnel remains active and stable without URL changes as long as the desktop app stays open.
- **Optional 6-Digit PIN Pairing:** Desktop host can enforce PIN verification, preventing unauthorized local network connections.

---

## 📦 Release Assets & Checksums

| Asset | Platform | Description | Size | SHA-256 Checksum |
| :--- | :--- | :--- | :--- | :--- |
| `KovanLLM_Station_v1.1_Setup.exe` | Windows 10/11 | Official Windows Installer (Inno Setup) | ~136 MB | `73a2ad31559da4e46356f76b7b70642e7f5c107a6fd15b562937ec7f8e6c1e52` |
| `KovanLLM.apk` | Android 8.0+ | Production Release APK (Signed, R8 Optimized) | ~9.7 MB | `2f3eed1e058a08a2fd36cc199a916260fb39496248028a7c55782792a4800b9c` |
| `app-release.aab` | Google Play | Android App Bundle (DEX & Resource Optimized) | ~24 MB | `482f5709ef7553d9802fe6ad8c539ac1ba6a514e257287f868e4b42fd206d427` |
| `run_desktop.bat` | Windows | Portable Desktop Station Launcher | — | *(Included in Source)* |
| `kovanllm_setup.iss` | Windows | Inno Setup 6 Installer Script | — | *(Included in Source)* |

---

## 🚀 Installation & Usage

### Windows
#### Option A: Windows Installer (.exe) — Recommended
1. Download `KovanLLM_Station_v1.1_Setup.exe` from the release assets.
2. Run the installer wizard to install KovanLLM Station with Start Menu and Desktop shortcuts.
3. Launch KovanLLM directly from your Start Menu or Desktop.

#### Option B: Portable Batch Launcher
1. Clone or download the repository.
2. Navigate to the `desktop/` directory.
3. Double-click `run_desktop.bat`. The script sets up a `.venv`, installs requirements, and opens the station.

### Linux
```bash
git clone https://github.com/aselens/kovanllm.git
cd kovanllm/desktop
./run_desktop.sh
```

### Android
- **Direct APK Install:** Download `KovanLLM.apk` from the release assets below and install via file manager or ADB:
  ```bash
  adb install KovanLLM.apk
  ```
- **Google Play:** Upload `app-release.aab` to Google Play Console.

---

## 📄 License & Attribution

- **License:** MIT License  
- **Author:** [Aselens Software℠](https://aselens.com)  
- **Repository:** [github.com/aselens/kovanllm](https://github.com/aselens/kovanllm)  
