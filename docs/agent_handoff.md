# Mummy Ludo — Agent Handoff & Onboarding Guide

**Document Purpose**: Primary onboarding guide for AI agents working on the Mummy Ludo codebase. Read this document first before exploring files or proposing changes.  
**Companion Document**: [Deep Technical Reference](file:///Users/titan/.gemini/antigravity/brain/ac6755a7-a49a-469b-8520-59891276f1e4/deep_technical_reference.md)

---

## 1. TL;DR (Executive Summary)

- **Pure Kotlin Game Engine**: `LudoEngine.kt` is a deterministic, framework-free state machine operating on immutable `GameState` data classes; all game rules (dice banking, bonus accumulation, three-sixes voiding, pairs, arrows, and captures) are fully isolated from Android/Compose.
- **Architectural Reality vs. Design Spec**: Despite `design.md` envisioning a multi-module app with ViewModels, Room database, and Jetpack Navigation, the production codebase is a **monolithic single-module app (`:app`)** where `MummyLudoApp.kt` manages all navigation and game state in Compose `mutableStateOf` without persistence or ViewModels.
- **Token Progress Coordinates**: Progress runs from `YARD = -1`, shared track `0..51` (relative to each color's starting offset), home lane `52..56`, to `FINISHED = 57`. `advanceProgress()` intentionally skips step 51 (transitions from $\le 50$ directly to $\ge 52$).
- **LAN Multiplayer via TCP Sockets**: Local multiplayer uses plain TCP sockets on port `42871` with line-delimited Base64-URL payloads (`mummyludo://join` QR code payload), operating under a strict host-authoritative model where the host commits dice and validates all moves.
- **Coverage & Test State**: Overall unit test line coverage stands at **58.8%** (961/1,635 lines) after recent `MainActivity` refactoring (previously 47.1% at `AppTheme`). Engine (`98.1%`), path math (`100%`), and Nearby networking/UI (`100%`) are green ($\ge 95\%$), while main game Compose screens remain at 0% unit coverage.

---

## 2. Project Identity

- **Application Name**: Mummy Ludo
- **Package / Namespace**: `com.mummyludo.app`
- **Application ID**: `com.mummyludo.app` (Debug suffix: `.debug`)
- **Version**: `versionName = "0.2.2"`, `versionCode = 4`
- **SDK Targets**: `minSdk = 26` (Android 8.0 Oreo), `compileSdk = 36`, `targetSdk = 36`
- **Repository URL**: `https://github.com/navneetrathi21/MummyLudo.git`
- **Primary Tech Stack**:
  - Kotlin 2.x
  - Jetpack Compose (BOM `2026.03.01`) + Material 3
  - Coroutines (`kotlinx-coroutines-android`)
  - Google Play In-App Updates (`com.google.android.play:app-update-ktx:2.1.0`)
  - ZXing QR Code Generation & Scanning (`core:3.5.3`, `zxing-android-embedded:4.3.0`)
  - JaCoCo (`jacoco` Gradle plugin for line/branch test coverage)
  - JUnit 4 + Robolectric `4.17` + Compose UI Test

---

## 3. Repository Status

- **Branch**: `main` (tracked to `origin/main`, up to date)
- **Commit History**: 4 foundational commits on main:
  1. `e112d0c` — *Build first playable Mummy Ludo Android app*
  2. `1c8b274` — *Fix dice animation to show committed roll*
  3. `bf70206` — *Align turn flow animations and board with approved design*
  4. `6d00b5a` — *Prepare Mummy Ludo for Play Store release*
- **Working Tree**: Contains uncommitted modularization refactor (splitting former 1,118-line `MainActivity.kt` into dedicated screen and theme files, plus JaCoCo setup and comprehensive test suites).

---

## 4. File Map

All production source code lives under `app/src/main/java/com/mummyludo/app/`:

| File | Package | Responsibility |
|---|---|---|
| [`LudoEngine.kt`](file:///Users/titan/Documents/MummyLudo/app/src/main/java/com/mummyludo/app/game/LudoEngine.kt) | `com.mummyludo.app.game` | Pure Kotlin rules engine, deterministic state transitions, legal move generation, pairs, arrows, captures, ranking, and bot simulation. |
| [`DiceRollPath.kt`](file:///Users/titan/Documents/MummyLudo/app/src/main/java/com/mummyludo/app/game/DiceRollPath.kt) | `com.mummyludo.app.game` | Trajectory math for the animated transient 3D-effect die rolling across the board (arc, wobble, phase, and rotation). |
| [`GameViewModel.kt`](file:///Users/titan/Documents/MummyLudo/app/src/main/java/com/mummyludo/app/viewmodel/GameViewModel.kt) | `com.mummyludo.app.viewmodel` | ViewModel managing active game state, turn updates, auto-play simulation coroutines, and match persistence. |
| [`NearbyLobbyViewModel.kt`](file:///Users/titan/Documents/MummyLudo/app/src/main/java/com/mummyludo/app/viewmodel/NearbyLobbyViewModel.kt) | `com.mummyludo.app.viewmodel` | ViewModel orchestrating local Wi-Fi LAN multiplayer, host/guest sessions, QR payload discovery, and action broadcasts. |
| [`MummyLudoApp.kt`](file:///Users/titan/Documents/MummyLudo/app/src/main/java/com/mummyludo/app/MummyLudoApp.kt) | `com.mummyludo.app` | Top-level UI scaffold and screen router; delegates domain logic to `GameViewModel` and `NearbyLobbyViewModel`. |
| [`MainActivity.kt`](file:///Users/titan/Documents/MummyLudo/app/src/main/java/com/mummyludo/app/MainActivity.kt) | `com.mummyludo.app` | Android Activity lifecycle, Play In-App Update management (`AppUpdateManager`), ZXing QR scanner launcher, and Wi-Fi SSID resolution. |
| [`GameScreen.kt`](file:///Users/titan/Documents/MummyLudo/app/src/main/java/com/mummyludo/app/GameScreen.kt) | `com.mummyludo.app` | Active board screen; handles dice rolling overlay animations, token step-by-step movement delays, bot pacing, and confetti on completion. |
| [`LudoBoard.kt`](file:///Users/titan/Documents/MummyLudo/app/src/main/java/com/mummyludo/app/LudoBoard.kt) | `com.mummyludo.app` | Custom Compose `Canvas` rendering of 15×15 grid, colored yards, inset homes, track cells, star safe squares, short arrows, and pawn drawings. |
| [`HomeSetupScreens.kt`](file:///Users/titan/Documents/MummyLudo/app/src/main/java/com/mummyludo/app/HomeSetupScreens.kt) | `com.mummyludo.app` | `HomeScreen` (New Game, Play Nearby, How to Play) and `SetupScreen` (player/bot counts 1..3, reverse duel opposite colors). |
| [`ResultRulesScreens.kt`](file:///Users/titan/Documents/MummyLudo/app/src/main/java/com/mummyludo/app/ResultRulesScreens.kt) | `com.mummyludo.app` | `RulesScreen` (6-point rules overview card) and unused/private `ResultScreen` (results handled inline in `GameScreen`). |
| [`AppTheme.kt`](file:///Users/titan/Documents/MummyLudo/app/src/main/java/com/mummyludo/app/AppTheme.kt) | `com.mummyludo.app` | Color palette (`Cream`, `Ink`, `Sage`, `Red`, `Green`, `Yellow`, `Blue`) and Material3 `MummyLudoTheme`. |
| [`NearbyLan.kt`](file:///Users/titan/Documents/MummyLudo/app/src/main/java/com/mummyludo/app/nearby/NearbyLan.kt) | `com.mummyludo.app.nearby` | Host socket server (`ServerSocket` on port 42871) and client socket thread (`Socket`) using Base64 wire protocol. |
| [`NearbySession.kt`](file:///Users/titan/Documents/MummyLudo/app/src/main/java/com/mummyludo/app/nearby/NearbySession.kt) | `com.mummyludo.app.nearby` | `NearbyLobbyCoordinator`, lobby state models, SSID cleaner, Wi-Fi permission helper, and `mummyludo://join` URI codec. |
| [`NearbyGameProtocol.kt`](file:///Users/titan/Documents/MummyLudo/app/src/main/java/com/mummyludo/app/nearby/NearbyGameProtocol.kt) | `com.mummyludo.app.nearby` | Sealed actions (`Roll`, `SelectDie`, `Move`), event codecs, and host authorization checker (`isAuthorizedGameAction`). |
| [`NearbyScreens.kt`](file:///Users/titan/Documents/MummyLudo/app/src/main/java/com/mummyludo/app/nearby/NearbyScreens.kt) | `com.mummyludo.app.nearby` | Entry screen, Host Lobby screen with ZXing QR code display, and Join Nearby screen with camera trigger. |
| [`GuestLobbyScreen.kt`](file:///Users/titan/Documents/MummyLudo/app/src/main/java/com/mummyludo/app/nearby/GuestLobbyScreen.kt) | `com.mummyludo.app.nearby` | Guest waiting lobby: name input, color selection with mutual exclusion, and ready state toggle. |

---

## 5. Architecture: Reality vs. Ideal Spec

> [!WARNING]
> Do NOT assume the architectural layers described in `docs/design.md` exist in the code. `design.md` was an initial design target; the current codebase was built as a clean, self-contained single-activity Compose application.

### How the App Actually Works:
1. **Single Activity (`MainActivity`)**: Initializes Play In-App Updates, handles Android permission callbacks (Camera, Wi-Fi), and injects optional QA dice sequences via Intent extras (`qaDiceSequence`).
2. **Central State Owner (`MummyLudoApp`)**:
   - Navigation is controlled by an internal enum:
     ```kotlin
     private enum class Page { HOME, SETUP, GAME, RULES, NEARBY, HOST_LOBBY, JOIN_NEARBY, GUEST_LOBBY }
     ```
   - Navigation is executed via a top-level `when (page)` block.
   - All critical domain state (`game: GameState?`, `nearbyLobby`, `clientLobby`, `hostSession`, `clientSession`, `nearbyEvent`) lives in Compose `remember { mutableStateOf(...) }` inside `MummyLudoApp`.
   - **No ViewModels**: Screen composables directly take state and lambda callbacks.
   - **No Room Database / Persistence**: Matches are transient and stored in memory. Closing an active match discards it.
   - **No Jetpack Navigation Component**: No `NavHost` or route strings like `game/{matchId}`.
3. **Back Navigation**:
   - Managed via Compose `BackHandler`.
   - On `Page.HOME`, pressing back once prompts `"Press back again to exit"`; pressing again within 2,000 ms triggers `onExitApp()` (`activity.finish()`).
   - On other pages, back unwinds cleanly to parent pages (`Page.NEARBY` or `Page.HOME`).
   - On `Page.GAME`, hardware back triggers the "Close this game?" confirmation dialog.

---

## 6. Game Engine Deep Dive

The game engine is implemented in [`LudoEngine.kt`](file:///Users/titan/Documents/MummyLudo/app/src/main/java/com/mummyludo/app/game/LudoEngine.kt).

### 6.1 State & Coordinate Invariants
- **Token Progress Space**:
  - `YARD = -1`: Token in yard; requires a roll of `6` to deploy (`enters = true`, moving to progress `0`).
  - `0..51`: Color-relative track positions (52 shared track cells).
  - `52..56`: 5-cell colored home lane (`lane = progress - 51`, ranging 1 to 5).
  - `FINISHED = 57`: Finished token; exact roll required to land on 57.
- **Track Offset Math**:
  ```kotlin
  val offsets = mapOf(RED to 0, GREEN to 13, YELLOW to 26, BLUE to 39)
  fun globalTrack(color: LudoColor, progress: Int): Int =
      if (progress in 0..51) (offsets.getValue(color) + progress) % 52 else -1
  ```
- **Protected Squares (8 total)**:
  - 4 Starting blocks: global track indices `0` (Red), `13` (Green), `26` (Yellow), `39` (Blue).
  - 4 Star squares: global track indices `8`, `21`, `34`, `47`.
- **The Step 51 Skip Rule**:
  ```kotlin
  fun advanceProgress(current: Int, steps: Int): Int {
      val rawDestination = current + steps
      return if (current <= 50 && rawDestination >= 51) rawDestination + 1 else rawDestination
  }
  ```
  A token at relative progress 50 stepping 1 reaches 52 (the first block of the home lane). Relative step 51 does not exist in playable routes.

### 6.2 Dice Banking & Bonus Flow
- Consecutive rolls of `6` accumulate in `diceBank: List<Int>` and keep `awaitingRoll = true`.
- **Third Consecutive 6**: Discards the 3 sixes (`diceBank = state.diceBank.dropLast(2)` since the 3rd six was not yet appended), resets `consecutiveSixes = 0`, and grants another roll. Non-six values and queued bonus rolls survive.
- **Roll Selection**: When rolling completes (`awaitingRoll = false`), if `diceBank.size == 1`, it is auto-selected into `state.roll`. If multiple dice are banked, the player selects one die, then picks a legal token.
- **Bonus Credits (`pendingBonus`)**: Earned by:
  - Landing on an arrow tail (`+1`)
  - Capturing opponent token(s) (`+1`)
  - Finishing a token (`+1`)
- Bonus rolls are rolled immediately into the dice bank before or after moves.

### 6.3 Pairs (Doubles) Mechanic
- Unprotected squares: 2 same-color tokens automatically form a pair (stored in `pairs: Set<String>` as `"R0:R1"` via `pairKey()`).
- Pairs can move **only on even dice** (2, 4, 6) and advance `die / 2` steps (1, 2, or 3 cells).
- Landing on a protected square **dissolves** the pair in state, but two tokens on that square can be picked together again on a subsequent even roll.
- Doubles can finish together if halved movement lands exactly on `FINISHED (57)`.

### 6.4 Capture Matrix
Evaluated at the final destination (after arrow traversal) on non-protected track cells `0..51`:

| Attacker | Defender Stack | Capture Outcome |
|---|---|---|
| 1 Single | 1 Single | Single defender captured to yard |
| 1 Single | 2 (1 Pair) | **No capture** (pair blocks single) |
| 1 Single | 3 (1 Pair + 1 Single) | **Single captured**, pair remains |
| 1 Single | 4 (2 Pairs) | **No capture** |
| 2 (Pair) | 1 Single | Single defender captured to yard |
| 2 (Pair) | 2 (1 Pair) | **Both defenders captured** |
| 2 (Pair) | 3 (1 Pair + 1 Single) | **The pair is captured**; single remains |
| 2 (Pair) | 4 (2 Pairs) | **All 4 defenders captured** in one event |

---

## 7. Nearby Multiplayer Architecture

Nearby multiplayer enables cross-device play over local Wi-Fi without internet access.

```
       Host Device (NearbyLanHost)                 Guest Device (NearbyLanClient)
   ┌───────────────────────────────────┐       ┌───────────────────────────────────┐
   │  ServerSocket bound to :42871     │       │  Connects to Host IP:42871        │
   │  Displays QR: mummyludo://join?...│ <───> │  Scans QR with Camera/ZXing       │
   │                                   │       │                                   │
   │  Authoritative GameState & Engine │       │  Sends: JOIN, PROFILE, ACTION     │
   │  Broadcasts: LOBBY, START, EVENT  │ ────> │  Receives: LOBBY, START, EVENT    │
   └───────────────────────────────────┘       └───────────────────────────────────┘
```

1. **Discovery & Connection**:
   - Host binds to TCP port `42871`.
   - Host renders QR code encoded as:
     `mummyludo://join?v=1&session=<hex>&host=<ip>&port=42871&token=<hex>`
   - Guest scans QR code via ZXing camera scanner, parsing `JoinPayload`.
2. **Wire Framing**:
   - Line-delimited (`\n`), pipe-separated (`|`).
   - Payload values are UTF-8 encoded with `Base64.getUrlEncoder().withoutPadding()`.
3. **Protocol Messages**:
   - Client $\to$ Host:
     - `JOIN|<b64-token>|<b64-playerId>|<b64-name>`
     - `PROFILE|<b64-playerId>|<b64-name>|<COLOR_OR_DASH>|<readyBool>`
     - `ACTION|<b64-encodedAction>` (`ROLL`, `DIE|<index>`, `MOVE|<id1,id2>`)
   - Host $\to$ Client:
     - `LOBBY|<semi-colon-separated-records>`
     - `START|<color,b64(name),botBool;...>`
     - `EVENT|<b64(sequence~rollValue~encodedAction)>`
     - `ERROR|<b64-message>`
4. **Host Authority**:
   - Clients never dictate rolls. The host generates all random values via `LudoEngine.generateRoll()` and broadcasts `NearbyGameEvent(sequence, action, rollValue)`.
   - Actions from guests are strictly validated on the host via `isAuthorizedGameAction(state, playerColor, action)` before dispatching.

---

## 8. Design vs. Implementation Gaps

When comparing [`docs/design.md`](file:///Users/titan/Documents/MummyLudo/docs/design.md) to the actual codebase, keep these differences in mind:

| Feature Area | Design Spec (`design.md`) | Current Implementation |
|---|---|---|
| **Architecture** | Multi-module Gradle project (`:core:engine`, `:data`, `:feature:*`) | Single `:app` module; package-based separation (`game`, `nearby`). |
| **State Management** | `ViewModel` + `StateFlow` + MVI `UiAction` / `UiState` | Compose `mutableStateOf` within composables; no ViewModels. |
| **Navigation** | Jetpack Navigation Compose with serializable route IDs | In-memory `when(page)` state machine inside `MummyLudoApp.kt`. |
| **Persistence** | Room database (`saved_match`, migrations, backup) | **No persistence**. Game state is lost on process kill or match exit. |
| **Bot System** | Heuristic lookahead with search budget (`BotPolicy`) | Greedy rule evaluation in `LudoEngine.chooseBest()`. |
| **Multiplayer Transport** | TLS WebSockets with ephemeral certificates | Plain TCP sockets on port `42871` with Base64-URL text framing. |
| **Disconnect Recovery** | 60-second recovery window with bot replacement prompt | Socket disconnect drops client; no countdown or bot takeover. |
| **Two-Color Play** | 2 colors per player (8 tokens each) in Solo/2P | Disabled in setup; all participants play 1 color (4 tokens). |
| **Result Screen** | Separate `results/{matchId}` route | Stay on board; inline winner banner and looping `Canvas` confetti. |
| **Sound & Haptics** | Audio feedback & haptic ticks on dice/moves | Not yet implemented. |

---

## 9. Test Coverage Status

As documented in [`CODE_COVERAGE.md`](file:///Users/titan/Documents/MummyLudo/CODE_COVERAGE.md):

- **Overall App Line Coverage**: **58.8%** (961 / 1,635 lines)
- **Quality Gate**: $\ge 95\%$ line coverage per file is marked 🟢 Done.

| Area | Source File | Line Coverage | Status | Remarks |
|---|---|---:|:---:|---|
| **Game** | `game/LudoEngine.kt` | **98.1%** (257/262) | 🟢 | Exhaustive rule & property tests |
| **Game** | `game/DiceRollPath.kt` | **100.0%** (26/26) | 🟢 | 4/4 branches covered |
| **Nearby** | `nearby/NearbySession.kt` | **100.0%** (91/91) | 🟢 | Codec & coordinator tests |
| **Nearby** | `nearby/NearbyGameProtocol.kt` | **100.0%** (49/49) | 🟢 | 96.1% branch coverage |
| **Nearby** | `nearby/NearbyLan.kt` | **100.0%** (160/160) | 🟢 | Host/client socket tests |
| **Nearby UI** | `nearby/GuestLobbyScreen.kt` | **100.0%** (58/58) | 🟢 | 80.2% branch coverage |
| **Nearby UI** | `nearby/NearbyScreens.kt` | **100.0%** (100/100) | 🟢 | Host, Join, QR, validation |
| **App UI** | `AppTheme.kt` | **100.0%** (17/17) | 🟢 | Palette and theme rendering |
| **App UI** | `MainActivity.kt` | **96.9%** (125/129) | 🟢 | Robolectric activity lifecycle & handlers |
| **App UI** | `MummyLudoApp.kt` | **0.0%** (0/188) | ⚪ | Active target for UI coverage |
| **App UI** | `HomeSetupScreens.kt` | **0.0%** (0/84) | ⚪ | Next UI target |
| **App UI** | `GameScreen.kt` | **0.0%** (0/263) | ⚪ | Uncovered |
| **App UI** | `LudoBoard.kt` | **0.0%** (0/168) | ⚪ | Uncovered |
| **App UI** | `ResultRulesScreens.kt` | **0.0%** (0/40) | ⚪ | Uncovered |

---

## 10. Sharp Edges & Gotchas

1. **The Step 51 Pitfall**:
   Never calculate token destinations with naive `current + steps`. Always invoke `advanceProgress(current, steps)`. If you bypass this, tokens crossing into home will land on track position 51 instead of home lane position 52.
2. **Third Six Voiding Drop Count**:
   In `commitRoll()`, voiding three consecutive sixes does:
   ```kotlin
   diceBank = state.diceBank.dropLast(2)
   ```
   This is intentional because the 3rd rolled six was not yet appended to `diceBank`. Dropping 2 items removes the first two sixes of that streak.
3. **Pairs Set Serialization**:
   `pairs: Set<String>` uses sorted keys like `"G0:G2"`. When checking or splitting pairs, always use `pairKey(ids)`. On protected squares, pairs are cleared from state (`pairs = pairs - key`).
4. **Nearby Roll Authority**:
   Never allow a guest client to dispatch a roll value. In `NearbyGameProtocol.kt`, `isAuthorizedGameAction` rejects any action out of turn, and `NearbyGameEventCodec` requires roll values to be provided only by the host on `Roll` actions.
5. **Compose Animation State Race**:
   In `GameScreen.kt`, user clicks on tokens and dice buttons are guarded by `rolling` and `moving` boolean flags. Modifying these coroutine routines without keeping those guards will allow double-moves and race conditions.
6. **Wi-Fi SSID Permission Caveat**:
   On Android 12+ (API 31/33), reading the Wi-Fi SSID via `NetworkCapabilities` or `WifiInfo` returns `<unknown ssid>` unless `ACCESS_FINE_LOCATION` and `NEARBY_WIFI_DEVICES` permissions are granted. `cleanWifiSsid()` handles this gracefully.

---

## 11. How to Build & Test

Execute all commands from the repository root (`/Users/titan/Documents/MummyLudo`):

- **Run JVM Unit Tests**:
  ```bash
  ./gradlew testDebugUnitTest
  ```
- **Generate JaCoCo Coverage Report**:
  ```bash
  ./gradlew testDebugUnitTest createDebugUnitTestCoverageReport
  ```
  *Output HTML report*: `app/build/reports/coverage/test/debug/index.html`
- **Build Debug APK**:
  ```bash
  ./gradlew assembleDebug
  ```
  *Output APK*: `app/build/outputs/apk/debug/app-debug.apk`
- **Build Release Bundle (AAB)**:
  ```bash
  ./gradlew bundleRelease
  ```
  *(Requires signing environment variables `MUMMY_LUDO_RELEASE_STORE_FILE`, etc., if signing configured).*
- **Run Lint / Static Checks**:
  ```bash
  ./gradlew lintDebug
  ```

---

## 12. Open Work Items (Product Plan Roadmap)

Per [`docs/product-plan.md`](file:///Users/titan/Documents/MummyLudo/docs/product-plan.md) and [`docs/nearby-multiplayer-design.md`](file:///Users/titan/Documents/MummyLudo/docs/nearby-multiplayer-design.md):

1. **Complete Unit Test Coverage on Main UI Composables**:
   Bring `MummyLudoApp.kt`, `HomeSetupScreens.kt`, `GameScreen.kt`, and `LudoBoard.kt` to $\ge 95\%$ coverage to pass the repository quality gate.
2. **Match Persistence (Room / DataStore)**:
   Add local match persistence so returning users can resume an interrupted match via a "Continue Game" button on Home.
3. **Two-Color Play (8 Tokens/Side)**:
   Implement the approved two-color mode for Solo and 2-Player matches (Red+Yellow vs. Green+Blue).
4. **Nearby Reconnection & Bot Replacement**:
   Implement the 60-second recovery timer for disconnected LAN clients and the host prompt to replace a disconnected player with a bot.
5. **Sound Effects & Haptics**:
   Integrate light haptic ticks for dice rolls and pawn movements, plus sound effects for captures and finishes.
