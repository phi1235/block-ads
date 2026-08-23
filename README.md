#  ReelGuard - Smart Ad-Blocker & Stream Engine for Facebook Reels

<div align="center">

![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Compose-Material%203-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-green.svg?style=for-the-badge)

**A lightweight, privacy-focused Android application engineered to block Facebook Reels ads seamlessly while preserving infinite scrolling pagination and full comment sections.**

</div>

---

##  Key Features

*  **Zero Video Ads:** Automatically blocks and skips mid-roll and interstitial video ads on Facebook Reels.
*  **Infinite Scrolling:** Fixes the common pagination freeze bug found in traditional DNS blockers—browse past 10, 50, or 100+ videos smoothly.
*  **Full Comment Support:** Keeps GraphQL comment streams intact so you can read and engage without endless loading spinners.
*  **Smart Stream Routing:** Whitelists organic media streams and isolates ad-delivery networks at the DNS layer.
*  **Split-Tunneling & Zero Latency:** Only filters relevant traffic. Banking apps, games, and general browsing run directly via your native connection at maximum speed.
*  **Material 3 UI:** Clean, modern interface with a one-tap power toggle, live blocked-ads counter, and Quick Settings Tile integration.

---

##  How It Works

Traditional DNS ad-blockers often use broad wildcard rules that inadvertently block essential GraphQL feed endpoints, breaking the pagination cursor (`page_info.end_cursor`) and comment loaders.

**ReelGuard** solves this with precision routing:
1. **Ad-Delivery Sinkhole:** Routes ad delivery hostnames (Audience Network, ad CDNs, tracking pixels) to `0.0.0.0`.
2. **Organic Stream Pass-Through:** Forwards feed queries and video CDNs to high-speed upstream DNS (Cloudflare `1.1.1.1`), ensuring an uninterrupted native experience on the official app.

---

##  Getting Started

### 1. Build from Source
```bash
# Clone the repository
git clone https://github.com/phi1235/block-ads.git
cd block-ads

# Build Debug APK
./gradlew assembleDebug
```
The compiled APK will be located at `app/build/outputs/apk/debug/app-debug.apk`.

### 2. Quick Usage
1. Install and launch **ReelGuard** on your Android device.
2. Tap **"BẤM ĐỂ BẬT"** to activate the Smart Shield.
3. Open your official **Facebook app** and enjoy a clean, ad-free Reels feed.

---

