# Privacy Policy for Mummy Ludo

**Effective Date:** October 25, 2026  
**Last Updated:** October 25, 2026  

Thank you for playing **Mummy Ludo** ("we", "our", or "the app"). We respect your privacy and are committed to protecting it. This Privacy Policy explains our practices regarding user information and device permissions.

---

### 1. Summary

- **No Personal Data Collected:** We do not collect, transmit, store, track, or sell your personal information, name, email address, phone number, device identifiers, or usage analytics.
- **Offline & Local-Only:** All single-player games, bot matches, and match saves run locally on your device using an internal SQLite database.
- **Direct LAN Multiplayer:** Multiplayer matches occur directly between devices connected to the same local Wi-Fi or mobile hotspot. No gameplay data is sent through external game servers or third-party cloud providers.

---

### 2. Permissions Used and Why

Mummy Ludo requests only the minimal Android permissions necessary to support optional peer-to-peer local multiplayer features:

#### a. Camera (`android.permission.CAMERA`)
- **Purpose:** Used solely for scanning a host player's 4-digit QR code to automatically connect to a nearby multiplayer game.
- **Handling:** Camera frames are analyzed in real time on your device using the open-source ZXing library. No photos or videos are ever captured, recorded, saved to storage, or transmitted off your device.
- **Optional:** You can decline camera permission and still join nearby games by typing the 4-digit game code manually, or enjoy single-player and pass-and-play matches.

#### b. Wi-Fi and Location Permissions (`ACCESS_WIFI_STATE`, `ACCESS_NETWORK_STATE`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `NEARBY_WIFI_DEVICES`)
- **Purpose:** On Android 8.0 through Android 14+, the Android operating system requires location permission to allow apps to read the name (SSID) of the currently connected Wi-Fi network.
- **Handling:** We use this strictly to display your Wi-Fi name on the host lobby screen so other players know which Wi-Fi network or hotspot to join.
- **No Location Tracking:** We do **not** access your GPS coordinates, determine your geographical location, track your movement, or record location history. If location permission is withheld, the app gracefully falls back to displaying "Same Wi-Fi Network".

#### c. Network Access (`android.permission.INTERNET`)
- **Purpose:** Required by the Android networking stack to open standard local TCP/IP sockets over your local Wi-Fi/hotspot network (e.g. `192.168.x.x` on port 42871) for host-client game communication.
- **Handling:** Socket communication is strictly peer-to-peer within your local area network (LAN).

---

### 3. Local Data Storage

- **Saved Matches:** If you leave an active game, the match state is saved locally on your device in a private SQLite database so you can resume playing later. This data never leaves your device.
- **Preferences:** Your preferred player name is stored locally in Android `SharedPreferences`.

---

### 4. Third-Party Services and Analytics

Mummy Ludo does **not** include:
- Third-party advertising networks (no ads).
- Third-party analytics or crash reporting SDKs (no Firebase, no Mixpanel, no Facebook SDK).
- User registration, login, or social network integrations.

---

### 5. Children's Privacy

Mummy Ludo is a family-friendly board game suitable for all ages. We do not knowingly collect any personal information from children or any other users.

---

### 6. Security

Because all game logic runs locally on your device and multiplayer communication occurs strictly across your private local Wi-Fi network, your match data is not exposed to the public internet.

---

### 7. Changes to This Privacy Policy

We may update this Privacy Policy from time to time. Any changes will be posted in this file and in the app's repository.

---

### 8. Contact Us

If you have any questions or feedback regarding this Privacy Policy, please contact:

- **Developer:** Mummy Ludo Team
- **Email:** support@mummyludo.app *(or your developer email address in Play Console)*
- **GitHub Repository:** [https://github.com/navneetrathi21/MummyLudo](https://github.com/navneetrathi21/MummyLudo)
