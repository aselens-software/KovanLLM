# 🐝 KovanLLM: Masaüstü & Bağımsız Mobil Yerel LLM İstasyonu (v1.1)

[![License: MIT](https://img.shields.io/badge/License-MIT-amber.svg)](https://opensource.org/licenses/MIT)
[![Platform](https://img.shields.io/badge/Platform-Windows%20%7C%20Linux%20%7C%20Android-orange)](https://github.com/aselens/kovanllm)
[![Version](https://img.shields.io/badge/Version-v1.1-blue)](https://github.com/aselens/kovanllm/releases)
[![Created By](https://img.shields.io/badge/Created%20by-Aselens%20Software%E2%84%A0-black)](https://aselens.com)

**KovanLLM**, evinizdeki veya iş yerinizdeki bilgisayarınızda çalışan yerel büyük dil modellerini (LM Studio, Ollama, llama.cpp vb.) telefonunuza **harici hiçbir VPN, port yönlendirme veya karmaşık ağ ayarı gerektirmeden** bağlayan, masaüstünde istasyon servisi (PyQt6 + FastAPI), Android'de ise modern bir yerel uygulama (Native Jetpack Compose) olarak çalışan açık kaynaklı ekosistemdir.

**Created by [Aselens Software℠](https://aselens.com)** • Lisans: [MIT License](LICENSE)

---

## 🌟 Temel Özellikler

### 1. 🖥️ Çapraz Platform Masaüstü İstasyonu (Windows & Linux)
- **Windows Desteği:** `run_desktop.bat` veya Inno Setup kurulum sihirbazı (`KovanLLM_Station_v1.1_Setup.exe`) ile tek tıkla kurulum ve çalışma. Windows görev çubuğunda "KovanLLM" kimliği ve özel ikon ile görünür.
- **Linux Desteği:** `./run_desktop.sh` ve `.desktop` başlatıcı entegrasyonu.
- **Bağımsız İstasyon Portu (`27615`):** Sabit bir yerel motora bağımlı olmadan dinamik port üzerinden çalışır.
- **Otomatik Tünel Fallback:** Görünmez Cloudflare Quick Tunnel ve `localhost.run` SSH tüneli desteği. Tünel olmasa bile yerel Wi-Fi ağı üzerinden kesintisiz çalışır.
- **Canlı Model & Donanım Yönetimi:** Dereceli GPU yük aktarımı (%0 - %100 slider), bağlam uzunluğu (context length), CPU çekirdek sayısı, sistem rolü ve VRAM temizleme.
- **İsteğe Bağlı PIN Güvenliği:** Masaüstünden isteğe bağlı olarak 6 haneli PIN doğrulaması açılıp kapatılabilir.

### 2. 📱 Android Uygulaması (Google Play Hazır & Optimize)
- **Google Play AAB & DEX Optimizasyonu:** R8/Proguard kuralları ile %25+ DEX optimizasyonu yapılmış, Google Play uyumlu `.aab` (Android App Bundle).
- **Hafif & Doğal Ses (Live Voice):** Cihazın yerel TTS altyapısıyla entegre, token üretilirken eşzamanlı olarak metni seslendiren akıcı sesli sohbet.
- **Dahili QR Kod Tarayıcı:** Google Play Services ML Kit Barkod Tarayıcı ve yerel kamera ile masaüstü ekranındaki QR kodu tek dokunuşla tarayıp anında bağlanma.
- **Medya & Dosya Önizleme:** WhatsApp benzeri medya önizleme (görseller) ve `.txt` gibi metin belgelerinin sohbet içi önizlemesi.
- **Mesaj Kuyruğu (Queue):** Model düşünürken kullanıcının yazdığı yeni mesajları otomatik sıraya alma ve mevcut işlem bitince kesintisiz devam etme.
- **Otomatik Sohbet Başlığı:** Gönderilen ilk mesaja göre otomatik başlık üretimi.
- **AdMob Entegrasyonu:** Banner ve sohbet içi kutu reklam desteği (Google UMP onay formu altyapısıyla GDPR/KVKK uyumlu).
- **Marka & Tasarım:** Özel amber/turuncu vurgu rengi (`#D97706`), profesyonel FontAwesome ikonları, karanlık ve aydınlık tema.

---

## 📁 Proje Dizin Yapısı

```
kovanllm/
├── android/                    # Bağımsız Android Uygulaması (Kotlin + Jetpack Compose)
│   ├── app/
│   │   ├── build.gradle.kts    # R8 minify & shrinkResources, AdMob & ML Kit
│   │   ├── proguard-rules.pro  # DEX optimizasyonu ve keep kuralları
│   │   └── src/main/java/com/aselens/kovanllm/
│   │       ├── MainActivity.kt
│   │       ├── ads/            # AdMob Banner, InlineBox, Interstitial & Consent
│   │       ├── data/           # Repository, LiveVoice, I18n, Modeller
│   │       ├── qr/             # Play Services QR Kod Tarayıcı
│   │       └── ui/             # ChatScreen, ModelSettings, StationLauncher
│   └── gradlew
├── desktop/                    # Masaüstü İstasyon Servisi (PyQt6 + FastAPI)
│   ├── desktop_gui.py          # İstasyon GUI penceresi (KovanLLM, PIN, Donanım)
│   ├── server.py               # FastAPI İstasyon backend'i (Port 27615)
│   ├── tunnel.py               # Cloudflare & localhost.run çoklu tünel yöneticisi
│   ├── voice_engine.py         # Piper VITS & hafif ses sentezi altyapısı
│   ├── run_desktop.bat         # Windows için tek tıkla başlatma betiği
│   ├── run_desktop.sh          # Linux için başlatma betiği
│   └── assets/                 # Logolar, FontAwesome SVG ikonları, icon.ico
├── installer/
│   └── kovanllm_setup.iss      # Inno Setup 6 Windows kurulum betiği
├── docs/                       # Görsel varlıklar, logolar ve belgeler
├── requirements.txt            # Python bağımlılıkları
├── LICENSE                     # MIT Lisansı
└── README.md
```

---

## 🚀 Kurulum ve Çalıştırma

### 1. Windows Üzerinde Çalıştırma
#### Yöntem A: Hızlı Başlatma
1. Depoyu klonlayın veya indirin:
   ```cmd
   git clone https://github.com/aselens/kovanllm.git
   cd kovanllm\desktop
   ```
2. `run_desktop.bat` dosyasına çift tıklayın. Sanal ortamı otomatik oluşturup bağımlılıkları kuracak ve KovanLLM İstasyonunu başlatacaktır.

#### Yöntem B: Inno Setup Kurulum Paketi
1. [Inno Setup 6](https://jrsoftware.org/isdl.php) yüklü ise:
2. `installer/kovanllm_setup.iss` dosyasını derleyin.
3. Üretilen `dist/KovanLLM_Station_v1.1_Setup.exe` kurulum sihirbazını çalıştırarak Windows Program Menüsü ve Masaüstü kısayollarını oluşturun.

### 2. Linux Üzerinde Çalıştırma
```bash
git clone https://github.com/aselens/kovanllm.git
cd kovanllm/desktop
./run_desktop.sh
```

### 3. Android Uygulamasını Derleme & Yükleme
#### Google Play İçin AAB Üretimi (Optimize Edilmiş):
```bash
cd android
./gradlew bundleRelease
```
Çıktı dosyası: `android/app/build/outputs/bundle/release/app-release.aab`

#### Yerel APK Derleme ve Telefona Yükleme:
```bash
./build_apk.sh
adb install KovanLLM.apk
```

---

## 🔒 Güvenlik & Gizlilik

- **Yerel Veri Gizliliği:** Sohbetleriniz ve model ağırlıklarınız yalnızca yerel bilgisayarınızda işlenir.
- **PIN Eşleştirme:** İsteğe bağlı olarak masaüstünde PIN koruması etkinleştirildiğinde, doğru 6 haneli PIN girilmedikçe mobil istemciye erişim izni verilmez.
- **AdMob UMP Uyumluluğu:** Reklam gösterimleri öncesinde Google Consent SDK üzerinden kullanıcı rıza formu açılır.

---

## 📄 Lisans ve Haklar

Bu proje [MIT Lisansı](LICENSE) altında açık kaynak olarak yayınlanmıştır.

Copyright (c) 2026 **Aselens Software℠** ([https://aselens.com](https://aselens.com))
Tüm hakları saklıdır.
