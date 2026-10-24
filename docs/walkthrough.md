# Walkthrough: Game State Persistence & Match Continuation (Option A)

We have implemented local match persistence and resumption so interrupted or backgrounded matches survive process termination and can be explicitly resumed via **"Continue game"** on the Home screen.

---

## 1. Components Created

### Storage & Serialization (`com.mummyludo.app.storage`)

| File | Purpose |
|---|---|
| [`GameStateCodec.kt`](file:///Users/titan/Documents/MummyLudo/app/src/main/java/com/mummyludo/app/storage/GameStateCodec.kt) | Pure JSON serializer & deserializer for `GameState`. Safely handles all 12 state fields, nested `Seat` and `Token` models, pairs set (`R0:R1`), dice bank, and rankings with strict invariant bounds checking. |
| [`GameDatabaseHelper.kt`](file:///Users/titan/Documents/MummyLudo/app/src/main/java/com/mummyludo/app/storage/GameDatabaseHelper.kt) | `SQLiteOpenHelper` managing `mummyludo.db`. Implements the `saved_match` table with schema versioning, revision counters, timestamping, and atomic transactional writes (`CONFLICT_REPLACE`). |
| [`GameRepository.kt`](file:///Users/titan/Documents/MummyLudo/app/src/main/java/com/mummyludo/app/storage/GameRepository.kt) | Domain interface (`saveActiveGame`, `loadActiveGame`, `clearActiveGame`, `hasActiveGame`) and `SqliteGameRepository` implementation. |

---

## 2. UI & Navigation Updates

### Home & Setup Screens
- **[`HomeSetupScreens.kt`](file:///Users/titan/Documents/MummyLudo/app/src/main/java/com/mummyludo/app/HomeSetupScreens.kt)**:
  - Updated `HomeScreen` to accept `hasSavedGame: Boolean` and `onContinueGame: () -> Unit`.
  - When a saved game exists: displays a prominent **"Continue game"** primary button (`testTag("home_continue_game")`) above **"New game"** (which switches to an outlined button).
  - When no saved game exists: displays **"New game"** as the primary button as before.

### App State Orchestration
- **[`MummyLudoApp.kt`](file:///Users/titan/Documents/MummyLudo/app/src/main/java/com/mummyludo/app/MummyLudoApp.kt)**:
  - Added optional `providedRepository: GameRepository? = null` parameter for testability and DI.
  - Automatically queries `repository.hasActiveGame()` when on `Page.HOME`.
  - Tapping **"Continue game"** loads the saved state and navigates directly to `Page.GAME`.
  - Starting a new match saves the fresh `GameState` to `GameRepository`.
  - Every subsequent turn/move transition (`onState`, `onAutoPlay`) updates the database with the latest state.
  - Discarding the game (`onClose`) or finishing the match clears the saved state from storage.

---

## 3. Automated Test Coverage

The following test suites were added and updated:

1. **[`GameStateCodecTest.kt`](file:///Users/titan/Documents/MummyLudo/app/src/test/java/com/mummyludo/app/storage/GameStateCodecTest.kt)**:
   - Initial game state round-trip test.
   - Complex mid-game round-trip test (tokens in yard/track/home, pairs, dice bank, active roll, bonus credits).
   - Completed match state round-trip test.
   - Corrupted and malformed JSON resilience test (returns `null` safely without crashing).

2. **[`SqliteGameRepositoryTest.kt`](file:///Users/titan/Documents/MummyLudo/app/src/test/java/com/mummyludo/app/storage/SqliteGameRepositoryTest.kt)**:
   - Empty repository assertions.
   - Save and load round-trip validation.
   - Sequential turn overwrite validation.
   - `clearActiveGame` deletion verification.
   - Completed match filtering verification.

3. **[`HomeSetupScreensTest.kt`](file:///Users/titan/Documents/MummyLudo/app/src/test/java/com/mummyludo/app/HomeSetupScreensTest.kt)**:
   - Verifies `HomeScreen` without saved game renders **New game** as the primary button.
   - Verifies `HomeScreen` with saved game renders **Continue game** button.
   - Verifies `SetupScreen` interactions (humans/bots count toggles, reverse duel, start button).

4. **[`MummyLudoAppTest.kt`](file:///Users/titan/Documents/MummyLudo/app/src/test/java/com/mummyludo/app/MummyLudoAppTest.kt)**:
   - Added `continueGameLoadsSavedMatchAndResumesPlay` test verifying end-to-end match continuation from the Home screen into the active game board.
