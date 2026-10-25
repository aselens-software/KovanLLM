# 📦 KovanLLM Installation & Release Files

This directory contains the compiled and packaged release binaries for **KovanLLM (v1.1)** across Windows and Android.

---

## 📂 Files in this Directory

| File | Platform | Type | Size | Description |
| :--- | :--- | :--- | :--- | :--- |
| **`KovanLLM_Station_v1.1_Setup.exe`** | Windows 10/11 | Inno Setup Installer | ~136 MB | Standalone Windows installer with Start Menu & Desktop shortcuts, offline neural voice engine, and uninstaller. |
| **`KovanLLM.apk`** | Android 8.0+ | Direct Install APK | ~9.7 MB | Production-signed release APK with R8 minification and resource shrinking for direct sideloading. |
| **`KovanLLM_v1.1.aab`** | Google Play | Android App Bundle | ~24 MB | Optimized App Bundle for publishing to Google Play Console with >25% DEX shrinking. |
| **`SHA256SUMS.txt`** | All | Checksums | <1 KB | SHA-256 cryptographic hashes for verifying package integrity. |

---

## 🚀 Quick Install Instructions

### 1. Windows Installation
- Double-click **`KovanLLM_Station_v1.1_Setup.exe`**.
- Follow the wizard steps to install KovanLLM Station.
- Launch from your Start Menu or Desktop.

### 2. Android Installation
- **Sideload APK:** Transfer **`KovanLLM.apk`** to your phone or install via ADB:
  ```bash
  adb install KovanLLM.apk
  ```
- **Google Play:** Upload **`KovanLLM_v1.1.aab`** to your Google Play Console release track.

---

## 🔒 Verification
To verify the integrity of the downloaded files:
```bash
sha256sum -c SHA256SUMS.txt
```
