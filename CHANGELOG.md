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

