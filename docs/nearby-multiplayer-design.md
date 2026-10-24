# Mummy Ludo — Nearby multiplayer design

Status: planning specification; agreed connection and disconnect flow, with proposed engineering defaults for review. No multiplayer implementation is claimed by this document.

Parent documents: [Design and engineering specification](./design.md), [Product plan and rulebook](./product-plan.md).

## 1. Scope and decisions

Players together at a physical location use separate Android phones on a shared Wi-Fi network. One phone hosts the game server; other phones join by scanning the game's QR code. All phones show the same board state, dice results, token movement, and match outcome. Internet access and user accounts are not required.

Agreed behavior:

- The host can open Android Wi-Fi settings from the lobby to share the connected network using Android's own QR screen.
- Other players scan that Wi-Fi QR themselves, outside the game's join scanner, then return to Mummy Ludo and scan its separate game QR.
- A disconnected player gets a 60-second recovery window. After it expires, the host chooses End game or Replace with bot.
- Reclaiming a seat after bot replacement is deferred.
- Preserve the approved board artwork and existing gameplay rules and animations.

Proposed initial defaults carried forward from planning:

- One human per phone, with up to four human participants including the host.
- Each participant chooses their own available color or legal opposite pair; the host controls color mode, bot seats, and starting the match.
- Freeze gameplay during disconnect recovery. There is no manual Pause action.
- No host migration. If the host does not return within 60 seconds, the session ends.
- An intentionally closed match cannot be resumed from Home; temporary reconnect applies only to the still-running session.

Out of scope: internet matchmaking, cloud servers, accounts, chat, automatic Wi-Fi joining, collecting router passwords, app-created hotspots, spectators, host migration, persistent match restoration after process death, and replacing a bot with a returning human.

## 2. Screens and joining flow

First-slice emulator references: [Play nearby](../artifacts/nearby-multiplayer/01-play-nearby.png) and [Host lobby](../artifacts/nearby-multiplayer/02-host-lobby.png). These are implementation checkpoints; the reviewed screen concepts remain the intended direction for later lobby, gameplay, and recovery states.

Home retains shared-device play and adds **Play nearby**. Nearby entry offers **Host game** and **Join game**.

| Screen or state | Contents and actions |
|---|---|
| Host lobby | Player names, connection/readiness, colors, bot seats, Share Wi-Fi, Join game QR, Start game, Close lobby |
| Join scanner | Camera view for Mummy Ludo QR, camera permission explanation, Back |
| Connection check | Connecting to host, Retry, Open Wi-Fi settings, Scan again |
| Guest lobby | Own name/color selection, other seats, Ready, Leave lobby |
| Nearby game | Approved board, active player, dice bank, synchronized animation, local-player controls |
| Recovery overlay | Disconnected player names and shared 60-second countdown; gameplay disabled |
| Host decision overlay | After timeout: Replace with bot or End game; guests see Waiting for host |
| Completed board | Rankings/winner, continuing confetti, Close game in the usual action area |

Host flow:

1. Select Host game and enter a display name.
2. Check Wi-Fi availability and obtain any applicable local-network permission.
3. Start the local server and show the lobby/game QR.
4. Optionally select Share Wi-Fi, display Android's QR, and return to this same lobby.
5. Configure colors/bots, wait for connected humans to be ready, and select Start game.

Guest flow:

1. If needed, scan the host's Android Wi-Fi QR using the phone's camera/system scanner and accept joining the network.
2. Open Mummy Ludo, select Join game, and scan the game's QR.
3. Validate QR format, Wi-Fi transport, host identity, and protocol/rules compatibility.
4. Join the lobby, enter a display name, select an available color, and tap Ready.
5. Receive the initial match snapshot when the host starts.

### Wi-Fi settings handoff

**Share Wi-Fi** opens the supported Android Wi-Fi settings intent. Brief guidance before leaving says: “Select your connected network, then Share or QR code.” The exact labels and availability vary by device. We cannot promise to open the sharing QR directly on every phone.

The app does not request, extract, store, or generate the router password. Android handles QR generation and any authentication required to display it. Returning from settings preserves the host lobby and rechecks connectivity. Backgrounding for this flow is not treated as deliberately closing the lobby.

A matching network name alone does not establish connectivity. Joining succeeds only after a handshake with the expected host over Wi-Fi. Guest networks may isolate devices, even when both phones show the same Wi-Fi name; show “Cannot reach the host. Check the Wi-Fi connection; this network may block connections between devices.” Offer Retry and Wi-Fi settings without claiming to know the exact failure cause.

Different frequency bands on the same reachable LAN are acceptable. A manually configured phone hotspot is also acceptable if local communication works. No automatic network switching or public-internet fallback is included.

## 3. Lobby ownership and starting

- The host is also a player. Lobby configuration is authoritative on the host.
- Color selections are requests, not immediate ownership claims. The host accepts the first valid request and broadcasts the result; a losing simultaneous request refreshes available choices.
- Two-color mode retains opposite pairs Red+Yellow and Green+Blue and is available only when the existing setup rules permit it.
- Bots occupy only unclaimed seats; existing mandatory opposite-bot behavior applies when starting with a single human.
- Changing color mode, ownership, or bot configuration clears readiness so all humans confirm the final setup.
- Start requires valid existing setup rules and every connected human to be ready. The host is the only device allowed to start.
- Once started, new participants cannot join. Only authenticated participants recovering within their existing window may reconnect.
- A lobby departure releases that participant's selection and readiness; the in-game 60-second bot-replacement policy begins only after match start.
- Closing the lobby informs guests and invalidates its join QR. Creating another lobby creates a new session identity and credentials.

## 4. Authority and software boundaries

Use a host-authoritative session. All accepted commands pass through a serialized host coordinator and the same game engine used by shared-device play. Remote phones request actions; they cannot set positions, generate authoritative dice, grant bonuses, or advance turns.

Proposed responsibilities, adapted to the repository's actual package structure during implementation:

| Component | Responsibility |
|---|---|
| NearbySessionCoordinator | Host/client lifecycle, lobby state, recovery, terminal state |
| LocalTransport | Wi-Fi-bound connections, framing, heartbeats, retries, teardown |
| SessionProtocol | Versioned requests, snapshots, events, acknowledgements |
| HostGameController | Participant authorization, command deduplication, engine dispatch, bot scheduling |
| ClientGameStore | Ordered replica, revision checks, snapshot recovery |
| SharedAnimationCoordinator | Host-time mapping, event scheduling, local rendering |
| Nearby UI | Entry, scanner, lobby, recovery overlays; reuse existing board components |

Keep Android networking outside the pure engine. Both the host's UI and remote clients use the same command validation path. Only the host runs bots and commits dice. A client may render legal highlights but cannot override the host's validation.

Proposed transport: persistent TLS WebSocket connections on the local Wi-Fi network. The host uses an ephemeral certificate whose fingerprint is bound to the scanned QR. This requires no public certificate service. Choose a maintained Android-compatible implementation during coding, after checking minimum-API and lifecycle behavior.

The QR contains a format version, session identifier, local endpoint/port, short-lived join credential, and host certificate fingerprint. It contains no router password. Treat scanned content as untrusted: validate sizes, scheme, endpoint type, and versions before connecting. Avoid arbitrary URL opening, external-network fallback, and logging join secrets. Lobby closure/start prevents new credential-based admissions. Established participants receive separate reconnect credentials.

## 5. Message and state contract

Every gameplay request includes session ID, authenticated participant identity, unique command ID, and expected state revision. A move identifies its token IDs and dice-bank entry ID, not just a face value: two equal dice results remain separate bank entries.

Representative messages:

- Lobby: Join, ChooseColor, SetReady, ConfigureBots, StartMatch, LeaveLobby.
- Gameplay: RequestRoll, SelectBankEntry, MoveUnit, LeaveMatch.
- Host output: CommandAccepted/Rejected, LobbySnapshot, GameTransition, StateSnapshot, RecoveryStatus, ParticipantReplaced, SessionEnded.
- Transport: Ping/Pong, Reconnect, RequestSnapshot, StateAcknowledged.

Each accepted transition carries a monotonically increasing revision, ordered event IDs, resulting public state/hash, and presentation metadata. Requests are deduplicated by command ID; retransmission returns the previous outcome and never rolls or moves again. Reject wrong-turn, wrong-owner, stale-revision, malformed, and out-of-phase input explicitly.

Clients apply events in order, ignore duplicates, and request a full snapshot on a gap or hash mismatch. Snapshots include positions/groups, ownership, current player/phase, bank entry IDs and values, pending bonus rolls, six streak, ranks, recovery state, and revision. They exclude future RNG state and private session credentials.

Allow only one in-flight gameplay action per participant UI. Display input as pending until accepted. Do not optimistically move a token or reveal an uncommitted die value.

## 6. Synchronized board and animations

All phones use the approved board orientation and geometry initially; personal board rotation is deferred. Normalize animation coordinates to board size so different screen dimensions do not change paths.

For each dice roll, the host commits the result once, then sends that result with a presentation-only random seed, animation version, start time, and duration. Every device renders the same randomized path/intermediate faces and settles on the same committed number. Presentation randomness never influences gameplay randomness.

Estimate host-clock offset with ping round trips using monotonic clocks. Initially allow a tunable approximately 200 ms scheduling lead on a healthy LAN. Token moves include ordered visual waypoints and capture/arrow/finish events. Animations run locally, not as streamed video or per-frame network traffic.

- Preserve cell-by-cell movement and approved arrow routes, including the visual 48 → 49 → 50 → 52 home-entry traversal.
- Preserve the capture return route followed by a straight, fixed-size segment into the exact yard circle.
- Preserve dice overlay timing, result reveal, immediate bonus-roll availability after move presentation, and human-like bot pacing.
- Enable the next action according to the host's presentation deadline, not whichever phone finishes rendering first. Client animation acknowledgements do not indefinitely block progress.
- A late client catches up to the current event position or snaps to an authoritative stable snapshot. It must not queue minutes of old animation or repeat sounds/bonuses.
- Shared animation timing is best-effort; identical logical outcomes are mandatory. Individual frame rendering is not guaranteed to be simultaneous.
- Only the active human's device has interactive roll/dice/token controls. Other phones show concise player status and the same bank/results.

### Rules preserved; precedence over stale design text

Nearby mode changes transport, not the rulebook. In particular:

- Arrows follow the existing cross-color applicability rules and grant the same bonuses to humans and bots.
- An arrow plus a capture earns two rolls; one capture event earns one roll regardless of token count.
- Earned arrow/capture/home rolls are available immediately after resolution, before spending remaining bank entries. Do not use the older design section 7.4 instruction to empty the bank first.
- Three consecutive sixes cancel those three values and the player rolls again; other bank values and earned credits survive.
- A single bank entry is automatically selected; multiple entries require selection before choosing a token.
- Preserve doubles, safe squares, home finishing, ranking, and current route step counts. Extra visual waypoints do not cost extra dice steps.
- Reaching home grants an extra roll unless the match has completed.
- On completion, all phones remain on the final board with looping confetti and Close game. Do not navigate to the older separate Results route.

Older design sections describing saved-match continuation or forced update restoration do not authorize restoration of a terminated nearby session.

## 7. Disconnection state machine

Proposed detection default: heartbeat every 2 seconds; mark disconnected on an explicit transport failure or 6 seconds without a response. The agreed 60-second recovery window starts when disconnection is detected, not at the last packet. Tune detection through physical-device testing without changing the recovery window.

1. The host freezes new game commands and bot scheduling and broadcasts the affected player and deadline. All connected phones show the same remaining time.
2. Any already committed transition remains valid. Finish or reconcile its presentation to that stable state; never undo/reroll it. Unaccepted requests remain unapplied.
3. Preserve ownership, tokens, bank entries, six streak, pending bonuses, ranks, and turn phase.
4. The disconnected client retries automatically while the session remains alive. Reconnect authenticates the same participant, reconciles uncertain command IDs, and fetches a full snapshot.
5. Before the deadline, successful recovery resumes once all disconnected participants are restored. This is recovery, not a new seat admission.
6. At the deadline, stop accepting automatic seat recovery and show the host End game / Replace with bot. Guests remain blocked with Waiting for host; there is no automatic bot replacement.
7. Replacement revokes the human's control/reconnect credential and transfers all owned colors and current state to a bot. It creates no fresh roll, turn reset, or token reset. The bot resumes through normal scheduling once recovery holds are cleared.
8. End game broadcasts a terminal reason and returns participants to an ended-session screen with a Home action.

For simultaneous disconnects, keep separate deadlines and resolve every affected participant; gameplay stays frozen until none remain unresolved. A reconnect arriving at the deadline is serialized by the host: it succeeds only if accepted before that participant's deadline.

An intentional guest departure uses a confirmation explaining the effect; it enters the same 60-second policy for this first version. Intentional host closure ends the match immediately after confirmation. If the host becomes unreachable, guests freeze, retry for 60 seconds, and end locally if it does not return. No guest may elect itself host.

After MatchCompleted, connectivity loss does not trigger replacement or alter rankings. Each phone retains its received final snapshot and confetti until its user closes. The host closing a completed board does not dismiss the completed board on other phones.

## 8. Android lifecycle and permissions

- Ask for camera permission only when opening the join scanner; denial leaves a clear settings/retry/back path.
- Request local-network/Wi-Fi permissions according to Android version, target SDK, and APIs actually used. In particular, account for ACCESS_LOCAL_NETWORK when targeting Android 17/API 37 or later.
- Use the supported Wi-Fi settings intent with availability checks and a general-settings fallback.
- Preserve live session ownership outside Activity recreation. Going to Wi-Fi settings or a short app switch does not equal Leave/Close.
- Plan a user-visible foreground service for hosting, with service type and platform eligibility verified during implementation. Do not claim this guarantees survival of force-stop or process death. Keep the screen awake during active board play.
- Recheck endpoint/address on return from settings. If the host changes networks in the lobby, refresh the endpoint/QR and require guests to reconnect. An active-match network change uses the recovery/end policy; seamless network migration is deferred.
- Defer optional update installation/restart until the nearby session ends. If a mandatory update must interrupt, explain that it ends the session; do not promise cross-process match restoration.
- Wi-Fi credentials are never handled by Mummy Ludo. Keep session/reconnect credentials in memory and redact them from logs.

## 9. Verification and acceptance criteria

No implementation or test execution is part of this documentation task. The eventual release requires automated coverage plus multi-phone validation.

| Test area | Required evidence |
|---|---|
| Lobby | Two/four phones, simultaneous color claims, opposite pairs, bot fill, readiness reset, start permissions, lobby full/closed |
| Wi-Fi flow | Share Wi-Fi opens settings; return preserves lobby; joining without internet; wrong Wi-Fi, isolated guest LAN, denied permissions |
| Protocol | Duplicate commands/events, stale revisions, missed events, invalid ownership, incompatible version, invalid/expired QR, fingerprint mismatch |
| Rules | Same seeded command sequence produces the same state as shared-device engine; all bonus rules, triples of six, doubles, home routes and rankings |
| Presentation | Same committed dice face/path, cell-by-cell movement, cross-color arrows, seamless captures, host and guest input gating, all-device bot animation |
| Recovery | Loss during roll/move/bonus, reconnect before 60 s, exactly-at-deadline behavior, unresolved host choice, two simultaneous losses |
| Replacement | Entire state and opposite pair inherited; no reroll/reset; old client cannot reclaim/control seat |
| Host lifetime | Settings handoff, activity recreation, background/foreground, host network loss, process kill, intentional closure |
| Completion | Final state identical, confetti continues, Close game local after completion, no replacement prompts for a completed match |

For state synchronization tests, assert equal revisions and public-state hashes after each accepted transition, not just visually similar screenshots. Fault-inject latency, disconnects, duplicate delivery, and stream reconnection. Include 2-, 3-, and 4-phone sessions and mixed human/bot matches on real devices across at least two manufacturers; emulator-only validation is insufficient for Wi-Fi settings and lifecycle behavior.

Performance acceptance: four phones sustain correct ordered state, show visibly coordinated dice/moves on a healthy LAN, and recover without duplicate actions. Measure frame smoothness and animation start skew before finalizing timing thresholds; approximately 100 ms start skew on a healthy LAN is an initial target, not a guaranteed network property.

## 10. Implementation sequence after design review

1. Lock protocol/state contract and host coordinator; test authorization, duplicate input, and deterministic transitions.
2. Build nearby entry, Wi-Fi settings handoff, QR joining, secure transport, and lobby.
3. Connect the existing board/engine and host-run bots; verify replicated state before animation polish.
4. Add shared dice/movement schedules and catch-up behavior.
5. Add the 60-second recovery flow, host choice, replacement, and lifecycle handling.
6. Run automated regression tests and physical-device journeys; record screenshots and synchronized gameplay clips for review.

These are planning milestones, not permission to begin coding in this documentation task.

## 11. Platform references

- [Android common settings intents](https://developer.android.com/guide/components/intents-common#Settings): supported entry into Wi-Fi settings.
- [Settings API reference](https://developer.android.com/reference/android/provider/Settings#ACTION_WIFI_SETTINGS): ACTION_WIFI_SETTINGS contract; no universal public connected-network sharing-QR shortcut.
- [Local-network permissions](https://developer.android.com/privacy-and-security/local-network-permission): target/API-dependent network access requirements.

Recheck platform requirements and foreground-service eligibility when implementation begins.
