# Changelog - MummyLudo

All notable changes to this project will be recorded here.

## [Initial Assessment] - 2024-10-23
- **Action**: Performed comprehensive codebase exploration.
- **Summary**: 
    - Identified core game engine (`LudoEngine`) and Nearby Connections networking layer.
    - Verified existence of Game State, Token, and PlayerType models.
    - Confirmed Local Multiplayer support via `NearbyGameProtocol`.
    - Identified the project structure and key files for Game and Nearby functionality.
- **Agent**: Gemma 4

## [Unreleased — after 0.2.2] - 2026-10-23
- Android Back now navigates from setup, rules, and Nearby screens to their parent screen instead of closing the app.
- Back in a game opens the existing close-game confirmation.
- On Home, the first Back press shows an exit prompt; a second press within two seconds closes the app. Visiting another screen resets this timer.
- Split the former 1,118-line `MainActivity.kt` into activity, navigation, theme, setup, gameplay, board, and result/rules files without changing gameplay behavior.
- Standardized source indentation to four spaces and enabled JaCoCo unit-test coverage reporting for debug builds. No test files were changed.
- Initial unit-test line coverage was 35.2% overall (game 97.6%, Nearby 62.4%, Android UI 0%). The requested >95% whole-app coverage is not yet met.
- Added `CODE_COVERAGE.md` to track per-file line coverage, the 95% green threshold, active-work status, and Codex/LocalLLM ownership before choosing the next coverage target.
- Added positive and negative dice-animation path tests; `DiceRollPath.kt` now has 100% line and branch coverage (26/26 lines, 4/4 branches). Overall local unit-test line coverage is 35.3%.
- Added positive and negative Nearby game-protocol tests; `NearbyGameProtocol.kt` now has 100% line coverage (49/49) and 96.1% branch coverage (98/102). Overall measured line coverage is 35.5%. The measurement excluded an unrelated, unfinished screen-test draft that currently prevents the main workspace unit-test suite from compiling.
- Added Nearby LAN tests for host profile updates, invalid join tokens, and unreachable hosts. `NearbyLan.kt` now has 100% line coverage (160/160); branch coverage is 64.3%. Overall measured line coverage is 36.2%. The separate unfinished screen-test draft remains excluded from this measurement.
- Added JVM Compose tests and Compose-compatible JaCoCo instrumentation for the Nearby guest lobby. `GuestLobbyScreen.kt` now has 100% line coverage (58/58) and 80.2% branch coverage; overall measured line coverage is 39.8%. `NearbyScreens.kt` is the next active coverage target.
- Added JVM Compose tests for the Nearby entry, host lobby, and join screens, including invalid join data and disabled-start behavior. `NearbyScreens.kt` now has 100% line coverage (100/100) and 66.1% branch coverage; overall measured line coverage is 46.0%. All Nearby UI source files are now above the 95% target.
- Added a JVM Compose theme test covering the approved application palette and themed-content rendering. `AppTheme.kt` now has 100% line coverage (17/17); overall measured line coverage is 47.1%. `MainActivity.kt` is the next active coverage target.
- Refactored `MainActivity` callbacks into focused, testable handlers and extracted the Play update dialog. Added JVM/Robolectric coverage for lifecycle startup, QA dice parsing, camera and Wi-Fi permissions, scanner results, modern and legacy Wi-Fi names, Play update availability/download/resume, dialog actions, and listener cleanup. `MainActivity.kt` now has 96.9% line coverage (125/129) and 70.3% branch coverage; overall measured line coverage is 58.8%.
- Implemented local match persistence and continuation (Option A):
  - Created `GameStateCodec` with strict JSON serialization/deserialization for all `GameState` fields, tokens, pairs, and dice bank.
  - Created `GameDatabaseHelper` (`SQLiteOpenHelper`) implementing the `saved_match` schema with versioning and atomic transactional writes.
  - Created `SqliteGameRepository` domain abstraction for saving, loading, clearing, and checking active games.
  - Enhanced `HomeScreen` with a dominant **Continue game** button when an active saved game is present.
  - Integrated `GameRepository` into `MummyLudoApp` to persist every local turn transition, resume saved matches, and clear state on completion/discard.
  - Added comprehensive unit tests in `GameStateCodecTest`, `SqliteGameRepositoryTest`, `HomeSetupScreensTest`, and `MummyLudoAppTest`.
- The local debug build and unit tests pass. Emulator testing was not run at the user's request; these changes have not been included in a Play Store release yet.
- Added JVM Compose coverage for complete application navigation, setup constraints, saved-game continuation and invalid-save recovery, local roll persistence, automatic completion, nearby host and guest cleanup, successful loopback joining, synchronized start/events, and host-authoritative action handling. `MummyLudoApp.kt` now has 96.3% line coverage (211/219), while `HomeSetupScreens.kt` reached 100% (99/99). Overall measured line coverage is 80.6%; `GameScreen.kt` is the next active target.
- Added JVM Compose coverage for GameScreen human and bot turns, local and Nearby dice/token actions, one-square-at-a-time movement, dice-bank selection, completion controls, and continuous confetti. Extracted deterministic confetti geometry from drawing code for focused testing. `GameScreen.kt` now has 96.4% line coverage (266/276) and 80.7% branch coverage; overall measured line coverage is 85.8%. `LudoBoard.kt` is the next active target.

## [0.2.3] - 2026-10-25
### Added
- **4-Digit Game Code**:
  - Replaced long session and token hex strings with clean, memorable 4-digit numeric room codes (e.g., `4821`).
  - Prominently displayed the 4-digit code on the Host screen directly below the QR code for easy verbal sharing.
- **Manual 4-Digit Code Entry & Subnet Discovery**:
  - Added a 4-digit numeric code input field and "Join with code" action to `JoinNearbyScreen`.
  - Implemented automatic zero-configuration LAN discovery (`discoverHostByRoomCode` using lightweight `CHECK` socket probes) to locate the host's IP on the local Wi-Fi network without requiring camera access.
  - Scanning via camera auto-populates the 4-digit code and presents a one-tap confirmation card.
- **First-Time Welcome Screen & Player Name Onboarding**:
  - Added `WelcomeScreen` on first app launch prompting for player name, backed by Android `SharedPreferences` (`UserPreferences`).
  - Propagated custom player name across Home screen greeting, Match Setup, duel cards, and Nearby sessions instead of generic "Player 1".
- **MVVM Architecture Modularization**:
  - Decoupled `MummyLudoApp.kt` into dedicated `GameViewModel` and `NearbyLobbyViewModel` components for active match state and Nearby multiplayer lobby coordination.
- **Color Selection & Ready Toggle in Nearby Lobby (Host & Guest)**:
  - Added interactive color pickers for both Host and Guest lobbies, showing all 4 Ludo colors (Red, Green, Yellow, Blue) and automatically disabling colors already claimed by other players or bots.
  - Added interactive Ready toggle buttons on both screens (`"✓ Ready"` / `"Not ready"` for Host; `"I'm ready"` / `"Not ready"` for Guest).
  - Wired real-time profile updates (`name`, `color`, `ready`) across the LAN network so player badges, color choices, and ready states update instantly for all connected peers.
  - Host "Start game" button activates only when the lobby requirements are satisfied (`canStart`: at least 2 players, all players ready, distinct colors).
  - Added smooth vertical scrolling to `HostLobbyScreen` to ensure the entire player roster, QR card, and Start button are fully accessible across all screen sizes.

### Fixed
- **Nearby QR Code "Code Has Expired" Bug**:
  - Resolved false expiration errors caused by stale scanned QR codes persisting across navigation by introducing `clearScannedGameCode()` / `onClearScannedCode`.
  - Prioritized local Wi-Fi (`wlan0`) and hotspot (`ap0`) interfaces over cellular data (`rmnet`) in `localWifiAddress()`, preventing unroutable cellular IPs from being encoded into join QR codes.
  - Enhanced `NearbyLanHost` to accept both 4-digit room codes and session tokens with clean whitespace trimming.
- **Host Screen Wi-Fi Display & SSID Detection**:
  - Restored real Wi-Fi SSID detection on Android 12+ by querying `WifiManager.connectionInfo` directly and adding `ACCESS_COARSE_LOCATION` to the manifest.
  - When the Wi-Fi name is known, displays the actual network name (e.g., `Family Wi-Fi · All players must join this Wi-Fi`).
  - When location permission is missing, displays a clear explanation (`Location permission is needed to show the Wi-Fi name.`) and provides an `"Enable location in Settings"` button taking the user directly to the app's permission settings in Android system settings.
  - When location permission is granted but Android hides the SSID (e.g. system Location toggle off), gracefully falls back to `Same Wi-Fi Network · All players must join the same Wi-Fi or hotspot`.
- **End-to-End Playable Server Verification**:
  - Added comprehensive LAN socket server integration test verifying multi-turn gameplay across `NearbyLanHost` and `NearbyLanClient` with alternating turns, socket actions (`Roll`, `SelectDie`, `Move`), server-side action validation (`isAuthorizedGameAction`), and synchronized state resolution across peers.
  - Verified full UI-level guest turn dispatch over the network in `MummyLudoAppTest`.

- **Google Play Store Upload Readiness**:
  - Bumped application version to `versionCode = 5` and `versionName = "0.2.3"` in `app/build.gradle.kts`.
  - Added support for local `keystore.properties` alongside environment variables for flexible release signing.
  - Created `PRIVACY_POLICY.md` detailing on-device permissions (`CAMERA`, `LOCATION` / Wi-Fi) and confirming no personal data collection or tracking.
  - Created `PLAY_STORE_LISTING.md` with complete copy-pasteable store metadata (App Name, Short/Full Description, IARC Content Rating questionnaire answers, Data Safety questionnaire answers, and Release Notes).
  - Added release automation scripts: `scripts/build_play_store_release.sh` (runs tests and builds release AAB) and `scripts/generate_upload_keystore.sh` (generates standard 2048-bit RSA upload keystore).
  - Successfully generated production release Android App Bundle (`app/build/outputs/bundle/release/app-release.aab`).

## [0.2.4] - 2026-10-28
### Fixed
- **Nearby LAN Socket Connection Drops**:
  - Fixed `"Connection to the host was lost"` error when selecting color or updating profile in Nearby multiplayer: offloaded all socket I/O in `NearbyLanClient` and `NearbyLanHost` (`updateProfile`, `sendGameAction`, `publishLobby`, `startGame`, `broadcastGameEvent`) to dedicated background worker executors, eliminating `android.os.NetworkOnMainThreadException` on Android devices.
- **Readiness Color & Profile Locking**:
  - Prevented ready players from changing color or profile settings: on both `GuestLobbyScreen` and `HostLobbyScreen`, color pickers and name inputs are disabled when marked "Ready", with clear visual indicators ("Locked (ready)" and "Unready to change name or color").
  - Enforced protocol-level guards in `NearbyLanHost` and `NearbyLobbyCoordinator.chooseColor` / `rename` so color and profile alterations are rejected by the server while a player is in the ready state.

### Changed
- Bumped application version to `versionCode = 6` and `versionName = "0.2.4"` in `app/build.gradle.kts` for Google Play Store upload.

### Verification & Metrics
- All 161 unit tests passing with 0 failures (100% pass rate).
- Whole-app line coverage reached **98.81%** (2,497 / 2,527 lines covered across all 21 source files; every file $\ge 96.0\%$).
- Production release bundle built and verified (`app/build/outputs/bundle/release/app-release.aab`, 7.9 MB, versionCode 6).
- Debug APK built and verified (`app/build/outputs/apk/debug/app-debug.apk`).

## [0.2.5] - 2026-10-30
### Fixed
- **4-Digit Room Code & QR Code Synchronization**:
  - Fixed room code mismatch where scanning the Host's QR code populated a raw session hex snippet instead of the 4-digit code shown on the host display: `HostLobbyScreen` now explicitly encodes the shared 4-digit `lobby.roomCode` into the QR payload (`JoinPayload`), ensuring scanning and verbal 4-digit sharing match identically.
  - Updated `NearbyLanHost.handle` to accept room codes, session IDs, and join tokens interchangeably, preventing "code expired" or token rejection errors across both QR and manual connection paths.
- **LAN Host Subnet Discovery ("Server Not Found" Fix)**:
  - Fixed manual 4-digit code entry failing with "No game found with code ... on this Wi-Fi" (`discoverHostByRoomCode`): replaced unthrottled 254-thread concurrent socket flood with prioritized subnet probing.
  - Probes default gateway (`.1`) first (crucial for mobile hotspot hosts), then prioritizes adjacent IPs surrounding the client's current IP (where DHCP typically allocates leases), and chunks remaining subnet scans into batches of 24 sockets with increased timeout (600ms) to ensure ARP resolution and TCP handshakes succeed without socket descriptor exhaustion.
- **Dedicated In-Game "Quit App" Option**:
  - Added a dedicated "Quit" action in the active game header next to "Close", allowing players to completely quit and exit the application via an explicit confirmation dialog.
  - Added a direct "Quit app" option to the existing match discard ("Close") dialog, giving players the choice to either return to the main menu or exit the app immediately.

### Changed
- Bumped application version to `versionCode = 7` and `versionName = "0.2.5"` in `app/build.gradle.kts` for Google Play Store upload.

### Verification & Metrics
- All 164 unit tests passing with 0 failures (100% pass rate).
- Whole-app line coverage reached **98.87%** (2,542 / 2,571 lines covered across all 21 source files; every file $\ge 96.0\%$).



