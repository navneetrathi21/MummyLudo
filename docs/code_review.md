# Mummy Ludo — Comprehensive Code Review
*Date: 2026-10-24 | Reviewer: Antigravity AI | Version: 0.2.2 (versionCode 4)*

---

## 1. Executive Summary

### Overview
Mummy Ludo is a portrait-first Android Ludo game developed with **Kotlin and Jetpack Compose**. The core game logic is contained in a pure Kotlin engine (`LudoEngine.kt`) that operates on immutable data structures and deterministic state transitions. Local multiplayer is implemented via raw TCP sockets on Wi-Fi with QR code discovery.

### Verdict
- **Core Game Mechanics**: ⭐⭐⭐⭐⭐ (Excellent). `LudoEngine.kt` is clean, robust, deterministic, and highly tested (98.1% line coverage).
- **UI & Presentation**: ⭐⭐⭐⭐☆ (Good). Canvas-based 15×15 board rendering is responsive and sharp; dice trajectory physics (`DiceRollPath.kt`) and token stepping animations are smooth.
- **Architecture & Maintainability**: ⭐⭐⭐☆☆ (Acceptable / Prototype). Significant divergence from `docs/design.md`. All navigation and application state are centralized in a single composable (`MummyLudoApp.kt`) without ViewModels, Room persistence, or SavedStateHandles.
- **Production Readiness**: 🟡 **Caution** for Play Store production release. While local and LAN play function well, game state is lost on process death, TCP sockets lack TLS, and several Compose UI screens have 0% automated test coverage.

### Top 5 Critical Findings
1. **Zero Game Persistence (High Impact)**: No Room database or SavedStateHandle is implemented. Any process recreation (screen rotation, incoming call, memory pressure) resets the match.
2. **Orphaned Dead Code (`ResultScreen`)**: In `ResultRulesScreens.kt`, `ResultScreen` is marked `private` and never invoked; match completion is handled inline in `GameScreen.kt`.
3. **Blocking Sleep on Sync Thread**: `NearbyLanHost.startGame()` executes `Thread.sleep(50)` within a `@Synchronized` block that can block caller threads.
4. **Unencrypted Plaintext LAN Transport**: `NearbyLan.kt` uses raw unencrypted TCP sockets on port `42871`, deviating from the design specification's TLS requirement.
5. **Architectural Divergence from Design Spec**: `docs/design.md` prescribes a multi-module architecture with Room, ViewModels, and Navigation Compose. The current code is a single `:app` module using `when(page)` switching and `mutableStateOf`.

---

## 2. Findings by Severity

### 🔴 Critical Issues (Must Address Before Production Release)

#### C-01: No State Restoration on Process Recreation
- **Location**: [`MummyLudoApp.kt:32-49`](file:///Users/titan/Documents/MummyLudo/app/src/main/java/com/mummyludo/app/MummyLudoApp.kt#L32-L49)
- **Problem**: Match state `var game by remember { mutableStateOf<GameState?>(null) }` resides solely in Compose memory. If Android terminates the process while the user answers a call or backgrounds the app, all game progress is discarded.
- **Design Spec Violation**: `docs/design.md` §9 and `docs/product-plan.md` §1 explicitly mandate match save/resume via Room.
- **Remedy**: Introduce a lightweight persistence repository (Room or DataStore) that serializes `GameState` after every committed move.

#### C-02: Thread.sleep(50) in Synchronized Host Method
- **Location**: [`NearbyLan.kt:87-102`](file:///Users/titan/Documents/MummyLudo/app/src/main/java/com/mummyludo/app/nearby/NearbyLan.kt#L87-L102)
- **Problem**: `startGame()` is `@Synchronized` and calls `Thread.sleep(50)` directly on the calling thread (which can be the Android UI thread if triggered from Compose button callback `onStart = { hostSession?.startGame() }`).
- **Remedy**: Offload start sequence broadcasting to a coroutine with non-blocking `delay(50)`.

#### C-03: Plaintext Socket Protocol (No TLS / Authentication)
- **Location**: [`NearbyLan.kt:26-29`](file:///Users/titan/Documents/MummyLudo/app/src/main/java/com/mummyludo/app/nearby/NearbyLan.kt#L26-L29), [`NearbyLan.kt:156`](file:///Users/titan/Documents/MummyLudo/app/src/main/java/com/mummyludo/app/nearby/NearbyLan.kt#L156)
- **Problem**: The socket connection operates in plaintext over TCP port `42871`. Anyone on the shared Wi-Fi can snoop or inject commands.
- **Design Spec Violation**: `docs/nearby-multiplayer-design.md` §4 specifies persistent TLS WebSocket connections with ephemeral certificates bound to QR code fingerprints.
- **Remedy**: Upgrade socket communication to TLS with certificate pinning or authenticate every frame via HMAC with the `joinToken`.

#### C-04: Disconnect Recovery / Bot Replacement Missing in LAN Mode
- **Location**: [`NearbyLan.kt:76-82`](file:///Users/titan/Documents/MummyLudo/app/src/main/java/com/mummyludo/app/nearby/NearbyLan.kt#L76-L82)
- **Problem**: When a client socket closes, the player is immediately deleted from the lobby: `lobby.copy(players = lobby.players.filterNot { it.id == playerId })`. In an active game, this abruptly removes the seat rather than honoring the 60-second reconnect window or transitioning the seat to a bot.
- **Design Spec Violation**: `docs/nearby-multiplayer-design.md` §7 specifies a 60-second recovery timeout and host decision to replace with bot.
- **Remedy**: Track connection status as `NearbyConnectionState.DISCONNECTED`, freeze game inputs, and start a 60-second recovery timer before bot replacement.

---

### 🟡 Warnings (Technical Debt & Code Smells)

#### W-01: God Composable (`MummyLudoApp.kt`)
- **Location**: [`MummyLudoApp.kt:22-220`](file:///Users/titan/Documents/MummyLudo/app/src/main/java/com/mummyludo/app/MummyLudoApp.kt#L22-L220)
- **Problem**: `MummyLudoApp` contains 17 state properties, handles network server lifecycles, parses local IP addresses, orchestrates auto-play simulation coroutines, and manages page switching.
- **Impact**: Difficult to unit test UI flows in isolation; business logic is coupled to Compose composition.
- **Recommendation**: Extract a `GameViewModel` and `NearbyLobbyViewModel` to hoist state and business logic out of composables.

#### W-02: Dead Code — Unused `ResultScreen`
- **Location**: [`ResultRulesScreens.kt:20-40`](file:///Users/titan/Documents/MummyLudo/app/src/main/java/com/mummyludo/app/ResultRulesScreens.kt#L20-L40)
- **Problem**: `ResultScreen` is declared `private` and is not called from anywhere in the codebase. Results are rendered directly inside `GameScreen.kt:209-211`.
- **Recommendation**: Either delete `ResultScreen` or make it internal and test it to resolve the test coverage deficit.

#### W-03: Greedy Bot AI (No Lookahead)
- **Location**: [`LudoEngine.kt:206-223`](file:///Users/titan/Documents/MummyLudo/app/src/main/java/com/mummyludo/app/game/LudoEngine.kt#L206-L223)
- **Problem**: `chooseBest()` scores only the immediate move outcome (Finished: 10,000, Opponents: 8,000, Home lane: 5,000, Safe: 3,000, Enter: 2,000). It does not compute probability-weighted next-turn risk.
- **Impact**: Bot performs well enough for casual play, but falls short of the bounded look-ahead tree search outlined in `docs/design.md` §8.

#### W-04: Unbounded Raw Thread Spawning in `NearbyLan.kt`
- **Location**: [`NearbyLan.kt:35-37`](file:///Users/titan/Documents/MummyLudo/app/src/main/java/com/mummyludo/app/nearby/NearbyLan.kt#L35-L37), [`NearbyLan.kt:41`](file:///Users/titan/Documents/MummyLudo/app/src/main/java/com/mummyludo/app/nearby/NearbyLan.kt#L41)
- **Problem**: Uses `kotlin.concurrent.thread(...)` directly for every incoming connection rather than an `ExecutorService` or Coroutine `Dispatchers.IO`.
- **Impact**: Potential resource leak if multiple connection attempts occur in rapid succession.

#### W-05: Missing Dark Theme
- **Location**: [`AppTheme.kt:20-30`](file:///Users/titan/Documents/MummyLudo/app/src/main/java/com/mummyludo/app/AppTheme.kt#L20-L30)
- **Problem**: Only `lightColorScheme` is defined. The app forces light mode regardless of device settings.

---

### 🟢 Strengths (Notable Design Patterns)

1. **Pure Kotlin Engine (`LudoEngine.kt`)**: Zero dependencies on Android or Compose. Highly deterministic, easily testable on the JVM, and completely thread-safe due to immutable `GameState` copies.
2. **Strict Host-Authoritative LAN Protocol**: In `NearbyGameProtocol.kt:59-77`, all game actions from remote guests are validated against `isAuthorizedGameAction`. Guests can never submit fabricated dice numbers—rolls are always generated authoritatively by the host.
3. **Clean Canvas Board Geometry (`LudoBoard.kt`)**: Crisp vector rendering of the 15×15 board using pure math and zero bitmap assets. Dynamically scales from 320dp upward.
4. **Mathematical Dice Trajectory (`DiceRollPath.kt`)**: Uses parametric cubic Bezier and sinusoidal equations for arc, wobble, phase, and rotational spin, yielding realistic 3D tumble aesthetics.
5. **QA Injectable Dice Sequences**: `MainActivity.kt` provides an intentional debug backdoor (`qaDiceSequence` intent extra) enabling repeatable automated UI end-to-end testing.

---

## 3. File-by-File Review

| File | Rating | Key Responsibilities | Primary Review Notes |
|---|:---:|---|---|
| [`MainActivity.kt`](file:///Users/titan/Documents/MummyLudo/app/src/main/java/com/mummyludo/app/MainActivity.kt) | ⭐⭐⭐⭐☆ | Activity bootstrap, Play in-app updates, QR scanner, Wi-Fi SSID | Clean separation of permission launchers. Well tested (96.9% line coverage). |
| [`MummyLudoApp.kt`](file:///Users/titan/Documents/MummyLudo/app/src/main/java/com/mummyludo/app/MummyLudoApp.kt) | ⭐⭐⭐☆☆ | App-level navigation, state holder, LAN coordinator | Monolithic state management. Needs refactoring into ViewModels. |
| [`AppTheme.kt`](file:///Users/titan/Documents/MummyLudo/app/src/main/java/com/mummyludo/app/AppTheme.kt) | ⭐⭐⭐⭐☆ | Color tokens, Material 3 theme | Minimal and clear. Needs dark theme support. |
| [`GameScreen.kt`](file:///Users/titan/Documents/MummyLudo/app/src/main/java/com/mummyludo/app/GameScreen.kt) | ⭐⭐⭐⭐☆ | Gameplay board container, dice overlay, bot timing | Excellent animation choreography; bot pacing feels natural. |
| [`HomeSetupScreens.kt`](file:///Users/titan/Documents/MummyLudo/app/src/main/java/com/mummyludo/app/HomeSetupScreens.kt) | ⭐⭐⭐⭐☆ | Home and setup screen UI | Clean composables, good test tags. |
| [`LudoBoard.kt`](file:///Users/titan/Documents/MummyLudo/app/src/main/java/com/mummyludo/app/LudoBoard.kt) | ⭐⭐⭐⭐⭐ | 15×15 canvas drawing, token pawns | Flawless canvas math, multi-pawn spread handling is clean. |
| [`ResultRulesScreens.kt`](file:///Users/titan/Documents/MummyLudo/app/src/main/java/com/mummyludo/app/ResultRulesScreens.kt) | ⭐⭐⭐☆☆ | Rules overview, Result screen | `ResultScreen` is private dead code. |
| [`DiceRollPath.kt`](file:///Users/titan/Documents/MummyLudo/app/src/main/java/com/mummyludo/app/game/DiceRollPath.kt) | ⭐⭐⭐⭐⭐ | Parametric trajectory math | 100% test coverage, elegant math model. |
| [`LudoEngine.kt`](file:///Users/titan/Documents/MummyLudo/app/src/main/java/com/mummyludo/app/game/LudoEngine.kt) | ⭐⭐⭐⭐⭐ | Core game logic, rules, legal moves, bot | 98.1% coverage. Fast, deterministic, and accurate. |
| [`GuestLobbyScreen.kt`](file:///Users/titan/Documents/MummyLudo/app/src/main/java/com/mummyludo/app/nearby/GuestLobbyScreen.kt) | ⭐⭐⭐⭐⭐ | Guest waiting lobby | 100% line coverage. Mutual color exclusion logic is solid. |
| [`NearbyGameProtocol.kt`](file:///Users/titan/Documents/MummyLudo/app/src/main/java/com/mummyludo/app/nearby/NearbyGameProtocol.kt) | ⭐⭐⭐⭐⭐ | Sealed actions, wire codecs, authorization | 100% line coverage. Host validation prevents cheating. |
| [`NearbyLan.kt`](file:///Users/titan/Documents/MummyLudo/app/src/main/java/com/mummyludo/app/nearby/NearbyLan.kt) | ⭐⭐⭐☆☆ | Socket server/client, framing | Thread-safe writer; lacks TLS, has blocking sleep in `startGame`. |
| [`NearbyScreens.kt`](file:///Users/titan/Documents/MummyLudo/app/src/main/java/com/mummyludo/app/nearby/NearbyScreens.kt) | ⭐⭐⭐⭐⭐ | Host lobby, Join screen, QR rendering | 100% line coverage. QR rendering is memoized cleanly. |
| [`NearbySession.kt`](file:///Users/titan/Documents/MummyLudo/app/src/main/java/com/mummyludo/app/nearby/NearbySession.kt) | ⭐⭐⭐⭐⭐ | Lobby coordinator, URL codec, Wi-Fi helper | 100% line coverage. Pure state machine with SecureRandom tokens. |

---

## 4. Architecture Gap Analysis

| Layer | `docs/design.md` Specification | Actual Implementation | Impact & Technical Debt |
|---|---|---|---|
| **State Management** | Android `ViewModel` + `StateFlow` + `collectAsStateWithLifecycle` | Inlined `mutableStateOf` inside `MummyLudoApp.kt` | Logic tied to composition lifecycle; cannot survive process death. |
| **Persistence** | Room database (`saved_match`, `match_result`, `save_backup`) with schema migrations | None. State only in RAM. | Complete match loss if app is closed or background-killed. |
| **Navigation** | Jetpack Navigation Compose (`NavHost`, routes: `home`, `setup`, `game/{id}`) | Enum-based `when(page)` conditional rendering | Simple and lightweight, but lacks deep-link navigation or back-stack history. |
| **Module Structure** | Multi-module (`:core:model`, `:core:engine`, `:core:bot`, `:feature:game`) | Single monolithic `:app` module | Faster build times now, but boundaries are not enforced by Gradle compiler. |
| **Engine Contract** | `GameEngine` interface with `dispatch(state, command): Transition` | Concrete `LudoEngine` with imperative mutating methods returning `GameState` | Direct calls work well, but lack decoupled event stream for telemetry/analytics. |
| **Bot Architecture** | `BotPolicy` interface with search budget and look-ahead tree search | Greedy `chooseBest()` heuristic built into engine | Bot moves instantly with simple priority weights; no search tree. |

---

## 5. Security Assessment

1. **Local Network Sockets**:
   - Communication on port `42871` is completely unencrypted. Anyone on the same Wi-Fi subnet can sniff packet contents.
   - Mitigation: Add lightweight symmetric AES encryption using the `joinToken` from the QR code as the pre-shared key (PSK).
2. **Denial of Service / Unbounded Reads**:
   - In `NearbyLan.kt:45`, `reader.readLine()` reads arbitrary line lengths without a character limit. A rogue client sending a continuous stream without a newline could trigger an OutOfMemoryError.
   - Mitigation: Add a bounded reader limit (e.g. max 4096 bytes per message).
3. **Input Validation**:
   - `NearbyGameActionCodec` safely uses `runCatching` and bounds checking (e.g. `require(it.tokenIds.size in 1..2)`).
   - `JoinPayloadCodec` enforces strict length checks on session ID (8..80 chars) and token (12..200 chars).
   - Overall input decoding is safe from deserialization attacks.

---

## 6. Test Coverage Assessment

- **Current Line Coverage**: **58.8%** (961 / 1,635 lines).
- **Fully Covered ($\ge 95\%$)**:
  - `LudoEngine.kt`: 98.1%
  - `DiceRollPath.kt`: 100.0%
  - `NearbySession.kt`: 100.0%
  - `NearbyGameProtocol.kt`: 100.0%
  - `NearbyLan.kt`: 100.0%
  - `GuestLobbyScreen.kt`: 100.0%
  - `NearbyScreens.kt`: 100.0%
  - `AppTheme.kt`: 100.0%
  - `MainActivity.kt`: 96.9%
- **Untested (0.0%)**:
  - `MummyLudoApp.kt` (0 / 188 lines)
  - `GameScreen.kt` (0 / 263 lines)
  - `LudoBoard.kt` (0 / 168 lines)
  - `HomeSetupScreens.kt` (0 / 84 lines)
  - `ResultRulesScreens.kt` (0 / 40 lines)

**Risk Area**: The interactive gameplay loop (rolling, moving, bot pacing) has no Robolectric or Compose UI tests. Regressions in UI animations or token click targeting won't be caught by the test runner.

---

## 7. Multi-Agent Handoff Guidelines

### Before Modifying Code:
1. **Read Documentation**:
   - First read [`docs/agent_handoff.md`](file:///Users/titan/Documents/MummyLudo/docs/agent_handoff.md) for quick-start conventions.
   - Refer to [`docs/deep_technical_reference.md`](file:///Users/titan/Documents/MummyLudo/docs/deep_technical_reference.md) for coordinate geometry, wire protocol, and engine invariants.
2. **Run Existing Tests**:
   ```bash
   ./gradlew testDebugUnitTest
   ```
3. **Check JaCoCo Line Coverage**:
   ```bash
   ./gradlew testDebugUnitTest createDebugUnitTestCoverageReport
   ```
   Open `app/build/reports/coverage/test/debug/index.html`.

### Sharp Edges — Do NOT Break:
- **Track Cell 51 Skip**: `advanceProgress()` in `LudoEngine.kt` skips step 51 when moving from track into home lane. Do not alter this offset without updating `movementAnimationProgresses()` and `LudoBoard.kt`.
- **Three-Sixes Cancellation**: Cancelling three consecutive sixes drops **two** previous sixes from the bank (`diceBank.dropLast(2)`) because the third six was not yet added to the bank.
- **Host Authoritative Rule**: Guests in LAN play **never** send dice values; the host commits all dice values. Never add a roll value parameter to client action packets.
