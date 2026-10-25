# Google Play Store Listing & Submission Guide

This document contains all the copy-pasteable metadata, policy answers, graphics checklist, and instructions needed to submit **Mummy Ludo** to the **Google Play Console**.

---

## 1. App Details

| Field | Content | Character Limit |
|---|---|---|
| **App Name** | `Mummy Ludo: Offline & Nearby` | Max 30 chars (29 chars) |
| **Short Description** | `Fast offline Ludo with dice banking, smart bots & zero-setup local Wi-Fi play.` | Max 80 chars (78 chars) |

### Alternative Short Descriptions (Optional)
- *Option 2:* `Classic Ludo with dice bank rules, AI bots, and easy local Wi-Fi multiplayer.` (76 chars)
- *Option 3:* `Play Ludo offline with smart bots or with friends nearby via 4-digit room code.` (78 chars)

---

## 2. Full Description (Max 4,000 characters)

```markdown
Experience the beloved classic board game with exciting modern twists! Mummy Ludo brings you fast-paced, strategic Ludo gameplay whether you're playing solo, passing the phone with family, or connecting multiple phones seamlessly over local Wi-Fi.

⭐ KEY FEATURES

🎲 UNIQUE DICE BANK & COMBO RULES
Rolling a 6 keeps your turn alive! Bank your consecutive rolls and strategically decide the order in which to deploy your banked dice. Stack double tokens for extra defense, sprint across the board using special shortcut arrows, and capture opponent pawns to earn bonus rolls!

📱 INSTANT NEARBY WI-FI MULTIPLAYER
Play together on separate phones with zero internet required!
• 4-Digit Room Codes: Join games instantly with simple 4-digit codes.
• QR Code Scanning: Point your camera at the host's screen to connect in seconds.
• Automatic Subnet Discovery: Seamless zero-configuration connection over your shared Wi-Fi or mobile hotspot.
• Interactive Color & Readiness: Choose your color (Red, Green, Yellow, Blue) and toggle ready status in real-time.

🤖 SMART OFFLINE AI BOTS
No internet? No problem! Challenge intelligent AI bots across 2, 3, or 4-player match setups. Perfect for quick commutes, flights, or relaxing at home.

🔄 REVERSE DUEL MODE
Looking for a fresh challenge? Flip the track! In Reverse Duel mode, tokens navigate the board counter-clockwise, creating exciting new tactics and unexpected capture opportunities.

💾 LOCAL MATCH CONTINUATION
Never lose your progress! If you need to step away, your match is saved automatically so you can resume right where you left off.

✨ CLEAN & MODERN DESIGN
• Beautiful, minimalist board aesthetics built with Jetpack Compose.
• Smooth dice rolling and token movement animations.
• Celebration confetti overlays for match winners.
• 100% ad-free experience with zero distracting banners or interruptions.

🔒 PRIVACY FOCUSED
No accounts, no sign-ups, and no personal data tracking. Gameplay occurs entirely on your device and across your local home network.

Download Mummy Ludo today and roll the dice with friends and family!
```

---

## 3. Store Categorization & Tags

- **Application Type:** Game
- **Category:** Games > Board *(or Casual)*
- **Tags (Select up to 5):**
  1. `Board`
  2. `Casual`
  3. `Multiplayer`
  4. `Offline`
  5. `Dice`

---

## 4. Graphic Assets Checklist

All required graphical assets are pre-generated in the repository under [`play-store-assets/`](play-store-assets/):

| Asset | File Location | Requirements | Play Console Status |
|---|---|---|---|
| **App Icon** | `play-store-assets/icon-512.png` | 512 x 512 px, 32-bit PNG with alpha, max 1MB | Ready ✅ |
| **Feature Graphic** | `play-store-assets/feature-graphic-1024x500.png` | 1024 x 500 px, PNG or JPEG, max 15MB | Ready ✅ |
| **Phone Screenshots** | `play-store-assets/phone-screenshots/*.png` | 1080 x 2400 px (7 high-res screens) | Ready ✅ |

### Screenshot Sequence:
1. `01-home.png` — Clean Home Screen with Play Offline & Play Nearby
2. `02-match-setup.png` — Interactive Match Setup (Players, Bots, Reverse Duel)
3. `03-game-board.png` — Vibrant Game Board with custom tokens
4. `04-dice-roll.png` — Animated 3D dice rolling
5. `05-dice-bank.png` — Strategic Dice Bank selection
6. `06-arrow-move.png` — Shortcut arrow traversal
7. `07-bot-turn.png` — Adaptive AI bot moves

---

## 5. Content Rating Questionnaire (IARC)

Fill out the IARC questionnaire in Google Play Console with the following answers:

- **Category:** Game
- **Violence:** No (Does the game contain violence against humans, animals, or fantasy characters? -> No)
- **Fear / Horror:** No
- **Sexuality / Nudity:** No
- **Simulated Gambling:** No (Does the game teach or encourage gambling? -> No)
- **Language:** No (Does the game contain offensive or crude language? -> No)
- **Controlled Substances:** No (Does the game reference drugs, alcohol, or tobacco? -> No)
- **Miscellaneous:**
  - Can users share personal information? -> **No**
  - Does the game share the physical location of the user with others? -> **No**
  - Does the game allow users to purchase digital goods? -> **No**
- **Resulting Rating:** **PEGI 3** (Europe) / **ESRB Everyone** (US) / **USK 0** (Germany) / **Everyone** globally.

---

## 6. Data Safety Section

Google Play requires a completed Data Safety form. Answer as follows:

| Question | Answer | Notes |
|---|---|---|
| **Does your app collect or share any user data?** | **No** | The app does not collect, log, or transmit personal data to any external server. |
| **Is all user data encrypted in transit?** | **Yes** (or N/A) | All network traffic is local-only. |
| **Do you provide a way for users to request data deletion?** | **Yes** | Users can delete all local data simply by clearing app data in Android Settings or uninstalling. |
| **Target Age Groups** | **13 and older** (or **All Ages**) | |

### Device Permissions Disclosures (if prompted):
- **Camera (`android.permission.CAMERA`):**
  - *Data collected:* **None**.
  - *Purpose:* Ephemeral on-device QR code scanning to connect peer-to-peer over local Wi-Fi. Video frames are never stored, transmitted, or logged.
- **Location (`ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`):**
  - *Data collected:* **None**.
  - *Purpose:* Required by Android 12+ system APIs to query the connected Wi-Fi SSID for peer discovery. Physical GPS coordinates are never accessed.

---

## 7. Privacy Policy URL

Provide the URL to your hosted `PRIVACY_POLICY.md`. If hosting on GitHub:
```
https://github.com/navneetrathi21/MummyLudo/blob/main/PRIVACY_POLICY.md
```
*(Or the equivalent GitHub Pages URL if enabled).*

---

## 8. Release Notes (What's New in v0.2.3)

### English (en-US)
```
What's new in version 0.2.3:
• 4-Digit Game Codes: Quickly join nearby games with simple numeric codes.
• QR Code Scanning: Instant one-tap join by scanning the host's screen.
• Interactive Color Selection: Pick your color and ready up on both host and guest screens.
• Personalized Onboarding: Set your player name on first launch.
• Real-time Wi-Fi Network Name display with Settings shortcut.
• Complete match state persistence: resume saved games anytime.
```

---

## 9. Publishing Steps (Google Play Console)

1. **Create Release App Bundle:**
   ```bash
   ./scripts/build_play_store_release.sh
   ```
   Or:
   ```bash
   export JAVA_HOME=/opt/homebrew/opt/openjdk@17
   ./gradlew bundleRelease
   ```
   The production `.aab` file is generated at:
   `app/build/outputs/bundle/release/app-release.aab`

2. **Open Google Play Console:**
   - Go to [play.google.com/console](https://play.google.com/console).
   - Select your developer account and click **Create app**.
   - Enter **Mummy Ludo**, select **Game**, **Free**, and accept the declarations.

3. **Complete App Set-up:**
   - **Privacy Policy:** Paste your `PRIVACY_POLICY.md` URL.
   - **App Access:** Select "All functionality is available without special access".
   - **Ads:** Select "No, my app does not contain ads".
   - **Content Rating:** Complete questionnaire using the answers in Section 5 above.
   - **Target Audience:** Select 13+ (or all ages).
   - **Data Safety:** Complete questionnaire using the answers in Section 6 above.

4. **Main Store Listing:**
   - Copy-paste the **App Name**, **Short Description**, and **Full Description** from Sections 1 and 2.
   - Upload `play-store-assets/icon-512.png`.
   - Upload `play-store-assets/feature-graphic-1024x500.png`.
   - Upload screenshots from `play-store-assets/phone-screenshots/`.

5. **Create First Release:**
   - Under **Release > Testing > Internal testing** (or **Production**):
   - Click **Create new release**.
   - Upload `app/build/outputs/bundle/release/app-release.aab`.
   - Enter the release notes from Section 8.
   - Click **Save** and **Review release**.
   - Submit for review!
