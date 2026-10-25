# Google Play Store Data Safety Guide for KovanLLM

This document provides exact answers for filling out the **Data safety** section in the Google Play Console for **KovanLLM** (`com.aselens.kovanllm`).

---

## 1. Data Collection and Security Overview

- **Does your app collect or share any of the required user data types?**  
  **Yes** (Due to the Google Mobile Ads / AdMob SDK).
- **Is all of the user data collected by your app encrypted in transit?**  
  **Yes** (AdMob uses HTTPS; remote station tunnels use TLS).
- **Do you provide a way for users to request that their data be deleted?**  
  **Yes** (Users can reset device advertising IDs via Android Settings; local chat data can be cleared within the app).

---

## 2. Specific Data Types to Declare

### Category: Device or other identifiers
- **Data type:** Device or other IDs (Advertising ID)
- **Collected?** Yes
- **Shared?** Yes (Shared with third-party service: Google AdMob)
- **Purposes:**
  - Advertising or marketing
  - Analytics & Fraud prevention
- **Is this data ephemeral?** No (processed for ad delivery)
- **Is collection required or optional?** Required for ad-supported tier

### Category: App info and performance
- **Data type:** Crash logs & Diagnostics (AdMob SDK internal telemetry)
- **Collected?** Yes
- **Shared?** Yes (Google AdMob)
- **Purposes:** Analytics, App functionality, Fraud prevention

### Category: Location (Approximate Location)
- **Data type:** Approximate location (derived from IP by AdMob)
- **Collected?** Yes
- **Shared?** Yes (Google AdMob)
- **Purposes:** Advertising or marketing

---

## 3. Data NOT Collected by KovanLLM
Declare **NO** for all of the following:
- Personal info (Name, email, phone number, address) -> **NO**
- Financial info (Credit cards, bank accounts) -> **NO**
- Health & fitness -> **NO**
- Messages & Chats -> **NO** (User LLM chats remain local between device and station; not uploaded to Aselens or any cloud database)
- Photos & Videos -> **NO** (Processed locally on-device)
- Audio files -> **NO** (Transcribed locally on-device)
- Contacts / Calendar -> **NO**
