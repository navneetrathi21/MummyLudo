# Code coverage tracker

Last completed measurement: 2026-10-25, using `./gradlew testDebugUnitTest createDebugUnitTestCoverageReport` and JaCoCo **line coverage**. The report is at `app/build/reports/coverage/test/debug/index.html`.

🟢 means **at least 95%** line coverage. ⚪ means below 95%. “Being worked on” is **Yes** for the one active coverage target, **Done** for targets at 95%+, or **No** otherwise. Set the owner to **Codex** or **Antigravity**. Update percentages only after a completed coverage run.

| Area | Source file | Line coverage | Being worked on? | Owner | Remarks |
| --- | --- | ---: | --- | --- | --- |
| Overall | All app source | 🟢 99.1% (2,209/2,229) | No | — | 14 of 21 source files at 100.00%; all 21 files $\ge 96.6\%$ |
| Game | `game/LudoEngine.kt` | 🟢 99.6% (261/262) | Done | Codex / Antigravity | Turn loop, bot heuristics, bank orders, captures, arrow traversals, and default parameters pass |
| Game | `game/DiceRollPath.kt` | 🟢 100.0% (26/26) | Done | Codex | Deterministic roll calculations pass |
| ViewModels | `viewmodel/GameViewModel.kt` | 🟢 98.4% (62/63) | Done | Antigravity | Active match state, save/restore, auto-play simulation coroutines, and error handling pass |
| ViewModels | `viewmodel/NearbyLobbyViewModel.kt` | 🟢 99.0% (99/100) | Done | Antigravity | Host/guest session management, QR/payload processing, action broadcasting, and lifecycle cleanup pass |
| Nearby | `nearby/NearbySession.kt` | 🟢 98.0% (98/100) | Done | Codex / Antigravity | 4-digit room code generation, JoinPayloadCodec 4-digit token parsing, peer state sync pass |
| Nearby | `nearby/NearbyGameProtocol.kt` | 🟢 100.0% (49/49) | Done | Codex | 96.1% branch coverage; positive and negative protocol tests pass |
| Nearby | `nearby/NearbyLan.kt` | 🟢 100.0% (215/215) | Done | Codex / Antigravity | LAN server, client sockets, 4-digit room code matching, CHECK probe, and LAN subnet discovery pass |
| Nearby UI | `nearby/GuestLobbyScreen.kt` | 🟢 100.0% (58/58) | Done | Codex | 80.2% branch coverage; connection/error, unavailable-color, name-limit, and readiness tests pass |
| Nearby UI | `nearby/NearbyScreens.kt` | 🟢 99.4% (175/176) | Done | Codex / Antigravity | Host lobby with 4-digit code display, Join nearby with manual 4-digit input & camera scanner pass |
| App UI | `AppTheme.kt` | 🟢 100.0% (17/17) | Done | Codex | Approved palette and themed-content rendering test passes |
| App UI | `MainActivity.kt` | 🟢 97.0% (128/132) | Done | Codex / Antigravity | Lifecycle, permissions, Wi-Fi, Play update, scanner, and clearScannedGameCode pass |
| App UI | `MummyLudoApp.kt` | 🟢 96.6% (197/204) | Done | Antigravity | Priority-based Wi-Fi interface detection, stale scanned code clearing, manual code join navigation |
| Welcome UI | `WelcomeScreen.kt` | 🟢 100.0% (67/67) | Done | Antigravity | First-time onboarding screen, logo, interactive name input, validation, continue button |
| HomeSetupScreens | `HomeSetupScreens.kt` | 🟢 100.0% (105/105) | Done | Codex / Antigravity | Personalized greeting banner, setup limits, reverse duel, continue-game, and start-game flows pass |
| App UI | `GameScreen.kt` | 🟢 98.9% (275/278) | Done | Codex / Antigravity | Local/nearby rolls, bot choices, token animation, controls, and confetti canvas pass |
| App UI | `LudoBoard.kt` | 🟢 100.0% (168/168) | Done | Antigravity | Board canvas, yards, stars, directional arrows, pawn geometry, spreads 1..4, legal highlights, click handlers, and coordinate transforms pass |
| App UI | `ResultRulesScreens.kt` | 🟢 100.0% (40/40) | Done | Antigravity | Rules screen and Result screen winner/button click tests pass |
| Storage | `storage/UserPreferences.kt` | 🟢 100.0% (8/8) | Done | Antigravity | SharedPreferences wrapper for player name and first-time welcome completion state |
| Storage | `storage/GameStateCodec.kt` | 🟢 100.0% (101/101) | Done | Antigravity | Full JSON encoding/decoding, boundary, malformed JSON, and optional field defaults pass |
| Storage | `storage/GameDatabaseHelper.kt` | 🟢 100.0% (45/45) | Done | Antigravity | SQLite saved_match table creation, CRUD, versioning, onUpgrade, default parameter overloads pass |
| Storage | `storage/GameRepository.kt` | 🟢 100.0% (15/15) | Done | Antigravity | SqliteGameRepository save/load/clear/hasActive and default revision overload pass |

Every source file in the repository has achieved $\ge 96.6\%$ line coverage, with 14 files at 100.00%. Total codebase line coverage: 99.1%.
