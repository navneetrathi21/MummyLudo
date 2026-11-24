# Code coverage tracker

Last completed measurement: 2026-11-24, using `./gradlew testDebugUnitTest createDebugUnitTestCoverageReport` and JaCoCo **line coverage**. The report is at `app/build/reports/coverage/test/debug/index.html`.

🟢 means **at least 95%** line coverage. ⚪ means below 95%. “Being worked on” is **Yes** for the one active coverage target, **Done** for targets at 95%+, or **No** otherwise. Set the owner to **Codex** or **Antigravity**. Update percentages only after a completed coverage run.

| Area | Source file | Line coverage | Being worked on? | Owner | Remarks |
| --- | --- | ---: | --- | --- | --- |
| Overall | All app source | 🟢 98.7% (3,631/3,678) | No | — | 11 of 22 source files at 100.00%; all 22 files $\ge 95.45\%$ |
| Game | `game/LudoEngine.kt` | 🟢 99.6% (281/282) | Done | Codex / Antigravity | Turn loop, friendly-fire immunity for teammates, own-color movement enforcement, team victory resolution, bot heuristics, bank orders, captures, arrow traversals |
| Game | `game/DiceRollPath.kt` | 🟢 100.0% (26/26) | Done | Codex | Deterministic roll calculations pass |
| ViewModels | `viewmodel/GameViewModel.kt` | 🟢 98.4% (61/62) | Done | Antigravity | Active match state, save/restore for local and hosted games, auto-play simulation, and error handling pass |
| ViewModels | `viewmodel/NearbyLobbyViewModel.kt` | 🟢 97.9% (187/191) | Done | Antigravity | Host/guest session management, QR/payload processing, action broadcasting, chat state & dispatch, bot and team manipulation methods, and lifecycle cleanup pass |
| Nearby | `nearby/NearbySession.kt` | 🟢 97.9% (140/143) | Done | Codex / Antigravity | AOE2 4-slot coordinator, NearbyChatMessage model, bot management (addBot, removeBot, updateBot), team assignments, 4-digit room code generation, readiness color locking pass |
| Nearby | `nearby/NearbyGameProtocol.kt` | 🟢 100.0% (49/49) | Done | Codex | 96.1% branch coverage; positive and negative protocol tests pass |
| Nearby | `nearby/NearbyLan.kt` | 🟢 95.7% (376/393) | Done | Codex / Antigravity | Background worker socket writing, team & bot serialization, CHAT packet protocol with public and team-only forwarding, host bot addition/removal over LAN, socket reuse |
| Nearby UI | `nearby/ChatComponents.kt` | 🟢 99.5% (441/443) | Done | Antigravity | ChatButtonWithBadge (unread badge), FloatingChatToast (auto-dismiss), ChatBottomSheet (channel tabs, message bubbles, quick reactions, text field), LobbyChatSection |
| Nearby UI | `nearby/GuestLobbyScreen.kt` | 🟢 99.5% (213/214) | Done | Codex / Antigravity | AOE2 4-slot layout, team selector chips, interactive color selection circles, ready button toggle, lobby chat feed, ready-locked profile state pass |
| Nearby UI | `nearby/NearbyScreens.kt` | 🟢 99.8% (546/547) | Done | Codex / Antigravity | AOE2 4-slot lobby with Add Bot controls, bot color & team selectors, remove bot, host team chips, lobby chat feed, match summary card, 4-digit room code QR consistency |
| App UI | `AppTheme.kt` | 🟢 100.0% (17/17) | Done | Codex | Approved palette and themed-content rendering test passes |
| App UI | `MainActivity.kt` | 🟢 95.5% (147/154) | Done | Codex / Antigravity | Lifecycle, permissions, direct Wi-Fi SSID detection, Play Store listing intent, App Settings launcher pass |
| App UI | `MummyLudoApp.kt` | 🟢 97.1% (239/246) | Done | Antigravity | Screen-lock survival with rememberSaveable, auto-resume active match, chat forwarding between ViewModel and screens, AOE2 bot & team event delegation |
| Welcome UI | `WelcomeScreen.kt` | 🟢 100.0% (67/67) | Done | Antigravity | First-time onboarding screen, logo, interactive name input, validation, continue button |
| HomeSetupScreens | `HomeSetupScreens.kt` | 🟢 100.0% (110/110) | Done | Codex / Antigravity | Personalized greeting banner, setup limits, reverse duel, continue-game, review / rating, and start-game flows pass |
| Game UI | `GameScreen.kt` | 🟢 99.2% (370/373) | Done | Codex / Antigravity | Screen awake lock (FLAG_KEEP_SCREEN_ON), team badge & turn display, in-game chat toggle button & unread badge, floating chat toast, chat bottom sheet, player-turn colored Roll dice button |
| Game UI | `LudoBoard.kt` | 🟢 100.0% (168/168) | Done | Antigravity | Board canvas, yards, stars, directional arrows, pawn geometry, spreads 1..4, legal highlights, click handlers, and coordinate transforms pass |
| App UI | `ResultRulesScreens.kt` | 🟢 100.0% (22/22) | Done | Antigravity | Result screen team victory banner, winner ranking and button click tests pass |
| Storage | `storage/UserPreferences.kt` | 🟢 100.0% (8/8) | Done | Antigravity | SharedPreferences wrapper for player name and first-time welcome completion state |
| Storage | `storage/GameStateCodec.kt` | 🟢 100.0% (103/103) | Done | Antigravity | Full JSON encoding/decoding with seat.team support, boundary, malformed JSON, and optional field defaults pass |
| Storage | `storage/GameDatabaseHelper.kt` | 🟢 100.0% (45/45) | Done | Antigravity | SQLite saved_match table creation, CRUD, versioning, onUpgrade, default parameter overloads pass |
| Storage | `storage/GameRepository.kt` | 🟢 100.0% (15/15) | Done | Antigravity | SqliteGameRepository save/load/clear/hasActive and default revision overload pass |

Every source file in the repository has achieved $\ge 95.45\%$ line coverage, with 11 files at 100.00%. Total codebase line coverage: 98.72%.
