# BLE on marine-kit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the Router's BLE pairing, passkey, command queue and reconnect handling into marine-kit 0.3.0, and run the Router on it.

**Architecture:** `BlePipe` gets a pairing policy, a passkey store/prompt and a serialized, security-aware write path, all driven through the `BleRadio`/`BleConnection` interfaces so they are unit-tested with `FakeBleRadio`. `AndroidBleRadio` implements the new radio calls. The Router gets a `Protocol` and a `BLEThing` implementation (`LinkBleThing`) on top of `PipeLink`, so its pages keep using `BLEThing`/`BLEN2KListener` unchanged until the old `BLEThingImpl` is deleted.

**Tech Stack:** Kotlin, kotlinx-coroutines (+ test), Android BLE, Android Keystore, Compose (marine-kit `ui` only), JUnit4, Robolectric.

**Spec:** `docs/superpowers/specs/2026-10-26-ble-on-marine-kit-design.md`

## Global Constraints

- marine-kit work happens in the `marine-kit/` submodule (a full clone). Run its tests from `marine-kit/` with `./gradlew :link:testDebugUnitTest :ui:testDebugUnitTest` (JDK 21). Router checks: `./gradlew assembleDebug testDebugUnitTest lintDebug` from the repo root.
- The owner stages, commits, tags and pushes. Steps that say "commit point" mean: stop and tell the owner the files, do not run `git commit`.
- Characteristic UUIDs are contract-critical: service `4fafc201-1fb5-459e-8fcc-c5c9c331914b`, conf `beb5483e-36e1-4688-b7f5-ea07361b26a8`, data `55da66c7-801f-498d-b652-c57cb3f1b590`, cmd `68ad1094-0989-4e22-9f21-4df7ef390803`, heartbeat `31a627d4-90cd-43df-8c0d-460e77fd294b`.
- `Data.parse(...)` stays backward-compatible for legacy and `version >= 10` layouts.
- The chosen device stays in the file `n2k.data`. Stored passkeys stay readable: preferences file `n2k.passkeys`, Keystore alias `n2k.passkey.key`.
- Command timeout 15 s; busy retry 20 x 100 ms; security statuses are GATT 5, 15 and 137.
- Pairing default in marine-kit stays at connect (0.2.0 behaviour); the Router sets on demand.
- Router UI stays XML + custom views; marine-kit components only via `ComposeView`. BLE traces use `appendLog`. UI writes on the UI thread.
- Do not touch the BLE reliability TODO paths (timeout / error 133) in `BLEThing.kt` while it still exists.
- Spec refinement found while planning: the heartbeat characteristic needs no pairing and bypasses the command queue today. So pairing on demand applies only to writes on channels listed in a new `BleProfile.securedWrites`; other channels (heartbeat) write directly and never prompt.

## Review Focus

- Two commands sent before the user answers the passkey prompt: the prompt appears once, both commands then go out in order.
- Prompt cancelled with commands queued: every queued command reports passkey cancelled, and the next command asks again (no stuck queue).
- The link drops while the prompt is open: the prompt is dismissed and queued commands report not connected, not a hang.
- A stored passkey that cannot be decrypted counts as missing and prompts, instead of crashing.
- A pairing request for a different device while connected is left to the system, never answered.
- The heartbeat never prompts, even with no stored passkey, and its failures are not shown to the user.

---

## Part A: marine-kit (in `marine-kit/`)

### Task 1: Passkey types and `PasskeyPrompter`

**Files:**
- Create: `link/src/main/kotlin/com/aboni/marine/link/ble/Passkey.kt`
- Test: `link/src/test/kotlin/com/aboni/marine/link/ble/PasskeyPrompterTest.kt`

**Interfaces:**
- Produces, all in package `com.aboni.marine.link.ble`:
  - `interface PasskeyStore { fun get(id: String): String?; fun put(id: String, passkey: String); fun remove(id: String) }`
  - `data class PasskeyRequest(val device: DeviceInfo, val failure: String?)`
  - `interface PasskeyPrompt { suspend fun ask(device: DeviceInfo, failure: String?): String?; fun cancel() {} }` (`ask` returns null when the user gave up; `cancel()` abandons a pending ask, used when the link drops)
  - `class PasskeyPrompter : PasskeyPrompt` with `val pending: StateFlow<PasskeyRequest?>`, `fun answer(passkey: String?)`, `fun dismiss()` (answers null; `cancel()` calls it).
  - `class PasskeyCancelledException : IOException` ("Passkey cancelled"); `class PasskeyRejectedException(val reason: String) : IOException`; `class BleWriteException(val status: Int, message: String) : IOException`; `class BleWriteTimeoutException : IOException`. (In package `com.aboni.marine.link.ble`.)

- [ ] **Step 1: Write failing tests** in `PasskeyPrompterTest`: `askSetsPendingAndReturnsTheAnswer`; `answerNullMeansUserGaveUp`; `dismissWhileAskingReturnsNullAndClearsPending`; `answerWithNothingPendingIsIgnored`; `secondAskWhileOneIsPendingWaitsForItsTurn` (second `ask` starts after the first is answered, asserting `pending.value` order).
- [ ] **Step 2: Run** `./gradlew :link:testDebugUnitTest --tests '*PasskeyPrompterTest*'`. Expected: FAIL (types missing).
- [ ] **Step 3: Implement** the types in `Passkey.kt`. `PasskeyPrompter` holds a `Mutex` so asks are one at a time and a `CompletableDeferred<String?>` per ask.
- [ ] **Step 4: Run** the same command. Expected: PASS.
- [ ] **Step 5: Commit point** (owner): `Passkey.kt`, `PasskeyPrompterTest.kt`.

### Task 2: Pairing policy and radio API

**Files:**
- Modify: `link/.../ble/BleProfile.kt`, `link/.../ble/BleRadio.kt`, `link/.../ble/BlePipe.kt` (`session`), `link/src/test/.../ble/FakeBleRadio.kt`
- Test: `link/src/test/.../ble/BlePipeTest.kt` (extend)

**Interfaces:**
- Produces: `enum class PairingPolicy { AtConnect, OnDemand }`; `BleProfile` gains, at the end with defaults, `pairing: PairingPolicy = PairingPolicy.AtConnect`, `securedWrites: Set<Int> = emptySet()`, `writeTimeoutMs: Long = 15_000`.
- `BleConnection` gains `fun setPasskey(passkey: String?)` (the passkey used to answer Android's pairing request for this device; null = none), `val lastBondFailure: String?` (plain-language reason of the last failed pairing, null if none since a success), `fun removeBond()`.
- `FakeConnection` gains matching recording fields (`passkeys` list, `removedBonds` count, settable `lastBondFailure`) and a scripting hook `var writeFailure: (UUID) -> IOException?` called by `write` before recording.

- [ ] **Step 1: Write failing tests** in `BlePipeTest`: `atConnectPolicyBondsBeforeSetup` (existing behaviour, unbonded connection, `bonds == 1`, regression guard); `onDemandPolicyDoesNotBondAtConnect` (unbonded connection reaches `PipeState.Up` with `bonds == 0` and `setups == 1`).
- [ ] **Step 2: Run** `./gradlew :link:testDebugUnitTest --tests '*BlePipeTest*'`. Expected: FAIL (does not compile).
- [ ] **Step 3: Implement** the profile fields, the three `BleConnection` members, the fake, and in `BlePipe.session`: bond at connect only when `profile.pairing == AtConnect`.
- [ ] **Step 4: Run** the same command. Expected: all `BlePipeTest` PASS.
- [ ] **Step 5: Commit point** (owner).

### Task 3: Secured, serialized write path

**Files:**
- Modify: `link/.../ble/BlePipe.kt` (constructor, `write`), `link/src/test/.../ble/BlePipeTest.kt`

**Interfaces:**
- Consumes: Task 1 types, Task 2 profile and connection API.
- Produces: `BlePipe(scope, radio, profile, store, passkeys: PasskeyStore? = null, prompt: PasskeyPrompt? = null)`. `write(p)` behaviour:
  - Writes are serialized (one `Mutex`), in call order.
  - For a channel in `profile.securedWrites` under `OnDemand`: before writing, ensure a passkey (`passkeys.get(id)`, else `prompt.ask(device, null)`; null answer throws `PasskeyCancelledException`; no `prompt` throws `PasskeyCancelledException`), store it with `put`, and call `conn.setPasskey(...)`.
  - A `BleWriteException` with status 5, 15 or 137, or a `BleWriteTimeoutException` while `conn.lastBondFailure != null`, is a security refusal: `passkeys.remove`, `conn.removeBond()`, `conn.setPasskey(null)`, ask again with a failure text built from `gattStatusText(status)` plus `lastBondFailure` (e.g. "authentication failed (the passkey is wrong)"), then retry the same write. With no `prompt`, throw `PasskeyRejectedException(reason)`.
  - Other `IOException`s propagate. Unsecured channels write directly with no passkey logic.
  - When the session ends while a prompt is pending, the pipe calls `prompt.cancel()`.

- [ ] **Step 1: Write failing tests** (FakeConnection with scripted `writeFailure`; a recording `PasskeyPrompt`; `MemPasskeys : PasskeyStore`): `firstSecuredWriteAsksAndStoresPasskey`; `storedPasskeyIsUsedWithoutAsking`; `twoSecuredWritesBeforeAnswerAskOnceAndGoOutInOrder`; `securityRefusalClearsStoreRemovesBondAndAsksWithReason`; `wrongPasskeyThenRightOneRetriesTheSameWrite`; `cancelledPromptFailsWriteWithPasskeyCancelledAndNextWriteAsksAgain`; `noPromptConfiguredFailsSecuredWriteWithCancelled`; `noPromptConfiguredAndRefusedFailsWithRejectedCarryingReason`; `silentWriteAfterFailedPairingIsTreatedAsSecurityRefusal`; `unsecuredChannelNeverAsksOrTouchesStore`; `sessionDropWhilePromptPendingCancelsPrompt`; `otherIOExceptionsPropagateUnchanged`.
- [ ] **Step 2: Run** `./gradlew :link:testDebugUnitTest --tests '*BlePipeTest*'`. Expected: FAIL.
- [ ] **Step 3: Implement** as specified above in `BlePipe`; keep the existing "Not connected" check first.
- [ ] **Step 4: Run** the same command. Expected: all PASS.
- [ ] **Step 5: Commit point** (owner).

### Task 4: `CommandResult` members and `PipeLink` mapping

**Files:**
- Modify: `link/.../CommandResult.kt`, `link/.../PipeLink.kt` (`send`), `docs/MIGRATION.md`
- Test: `link/src/test/.../PipeLinkTest.kt` (extend; use the existing fake pipe there)

**Interfaces:**
- Produces: `CommandResult.PasskeyCancelled` (data object, `ok = false`, `text = "Passkey cancelled"`) and `CommandResult.PasskeyRejected(val reason: String)` (`ok = false`, `text = "Passkey rejected: $reason"`). `PipeLink.send` maps `PasskeyCancelledException` and `PasskeyRejectedException` thrown by `pipe.write` to these; other `IOException` keeps mapping to `TransportError`.

- [ ] **Step 1: Write failing tests** in `PipeLinkTest`: `passkeyCancelledFromPipeBecomesPasskeyCancelled`; `passkeyRejectedFromPipeBecomesPasskeyRejectedWithReason`; `otherIOExceptionStillTransportError`.
- [ ] **Step 2: Run** `./gradlew :link:testDebugUnitTest --tests '*PipeLinkTest*'`. Expected: FAIL.
- [ ] **Step 3: Implement** the members and the mapping. Add the `0.3.0` entry to `docs/MIGRATION.md`: the two new `CommandResult` members (before/after of an exhaustive `when`), the new `BleProfile` fields, `BleConnection` additions (only matters to apps with their own `BleRadio`), `BlePipe` new optional parameters.
- [ ] **Step 4: Run** `./gradlew :link:testDebugUnitTest`. Expected: PASS.
- [ ] **Step 5: Commit point** (owner).

### Task 5: Android radio and Keystore store

**Files:**
- Modify: `link/.../ble/AndroidBleRadio.kt`
- Create: `link/.../ble/KeystorePasskeyStore.kt`
- Test: none possible under Robolectric (Android Keystore and the Bluetooth stack are not available); verified on the device in Task 11. The store's logic around it is covered by Task 3's in-memory tests.

**Interfaces:**
- Produces: `KeystorePasskeyStore(context: Context, prefsName: String = "marine_passkeys", keyAlias: String = "marine.passkey.key") : PasskeyStore`: AES-GCM, key in the Android Keystore, value `Base64(iv + ciphertext)` keyed by address; a value that cannot be decrypted is removed and reads as null. Same algorithm and format as the Router's current `PasskeyStore.kt` (copy its logic), so a store opened with `prefsName = "n2k.passkeys"` and `keyAlias = "n2k.passkey.key"` reads the passkeys the Router already saved.
- `AndroidBleConnection` implements: `write` throws `BleWriteException(status, "Write failed: ${gattStatusText(status)}")` for a non-success status and `BleWriteTimeoutException` when the op times out, using `profile.writeTimeoutMs` (pass it in at `setup`); `setPasskey` stores the passkey; a `BroadcastReceiver` for `ACTION_PAIRING_REQUEST` (priority `SYSTEM_HIGH_PRIORITY`, exported) registered for the life of the connection answers with `device.setPin(...)` and `abortBroadcast()` only for this connection's address and variants PIN/passkey (value 1), and only when a passkey is set; `lastBondFailure` is updated from `ACTION_BOND_STATE_CHANGED` (BOND_NONE after BOND_BONDING sets `bondFailureText(reason)`, BOND_BONDED clears); `removeBond()` calls the hidden `removeBond` by reflection, logging failure rather than throwing. The receiver is unregistered in `close()`. Keep busy-write retry: 20 attempts, 100 ms apart, before "Bluetooth is busy".

- [ ] **Step 1: Implement** the above, copying the Router's receiver and removal logic from `BLEThing.kt` (`pairingReceiver`, `removeBond`) and its Keystore code from `PasskeyStore.kt`.
- [ ] **Step 2: Run** `./gradlew :link:testDebugUnitTest :ui:testDebugUnitTest`. Expected: PASS (nothing regressed, it compiles).
- [ ] **Step 3: Commit point** (owner).

### Task 6: `PasskeyDialog`, scaffold hook, demo

**Files:**
- Create: `ui/src/main/kotlin/com/aboni/marine/ui/settings/PasskeyDialog.kt`, `ui/src/test/kotlin/com/aboni/marine/ui/settings/PasskeyDialogTest.kt`
- Modify: `ui/.../MarineScaffold.kt`, `demo/src/main/kotlin/com/aboni/marine/demo/MainActivity.kt`, `docs/GUIDELINES.md` (section 7)

**Interfaces:**
- Produces: `@Composable fun PasskeyDialog(request: PasskeyRequest, onAnswer: (String?) -> Unit)`: material `AlertDialog` like `TransportDialog`, title "Passkey", message with the device's `displayName`, the `failure` text above the field when not null, a numeric password-style field, OK (disabled while empty) and Cancel; both `onAnswer(null)` for Cancel and dismiss. `MarineScaffold` gains `passkeys: PasskeyPrompter? = null` and shows `PasskeyDialog` while `passkeys.pending` is not null, answering through `passkeys.answer`.

- [ ] **Step 1: Write failing tests** (Compose test, as in `TransportDialogTest`): `showsDeviceNameAndNoFailureOnFirstAsk`; `showsFailureReasonOnRetry`; `okIsDisabledUntilTextIsEntered`; `okAnswersWithTheTypedPasskey`; `cancelAnswersNull`; `scaffoldShowsDialogWhenPrompterHasPendingRequest`.
- [ ] **Step 2: Run** `./gradlew :ui:testDebugUnitTest --tests '*PasskeyDialogTest*'`. Expected: FAIL.
- [ ] **Step 3: Implement** the dialog and the scaffold parameter. The demo's fake link triggers a request (first ask, then a retry with a failure text) from a button on its screen, shown in all three themes.
- [ ] **Step 4: Run** `./gradlew :ui:testDebugUnitTest`. Expected: PASS. Add the passkey rows to `GUIDELINES.md` section 7 (asked in the app, themed dialog, numeric field, reason shown on retry).
- [ ] **Step 5: Commit point** (owner).

### Task 7: marine-kit release preparation

**Files:** Modify `README.md` (link API summary: pairing policy, passkey), `docs/MIGRATION.md` (check 0.3.0 entry is complete).

- [ ] **Step 1: Run** `./gradlew build` in `marine-kit/`. Expected: BUILD SUCCESSFUL, all tests pass.
- [ ] **Step 2: Install** the demo (`./gradlew :demo:installDebug`) and check the passkey dialog in Light, Dark and Night on the phone.
- [ ] **Step 3: Hand over**: owner reviews, commits, tags `0.3.0` and pushes marine-kit.

---

## Part B: Router (repo root)

### Task 8: Router protocol

**Files:**
- Create: `app/src/main/java/com/aboni/n2kRouter/RouterProtocol.kt`
- Test: `app/src/test/java/com/aboni/n2kRouter/RouterProtocolTest.kt`
- Modify: `marine-kit` submodule pointer to tag `0.3.0` (owner pushes the tag first); `app/build.gradle.kts` also depends on `com.github.titio72.marine-kit:link`.

**Interfaces:**
- Produces: `object RouterChannels { const val CONF = 1; const val DATA = 2; const val CMD = 3; const val HEARTBEAT = 4 }`; `data class RouterCommand(val channel: Int, val payload: String)`; `class RouterState(val data: Data, val conf: Conf, val hostVersion: Int, val revision: Long)`; `object RouterProtocol : Protocol<RouterState, RouterCommand, Nothing>` with `replies = null`; `decode` updates the shared mutable `Data`/`Conf` (`Data.parse`, `Conf.copyFrom(ByteArray)`), sets `hostVersion` from `bytes[0]` on the data channel, and returns `Inbound.Data(RouterState(..., revision + 1))` (a new object each time so the flow emits); `encode(seq, cmd)` returns `Packet(cmd.channel, cmd.payload.toByteArray(Charsets.UTF_8))`; `val ROUTER_PROFILE: BleProfile` with the five UUIDs from the constraints: notify `{data -> DATA}`, readOnConnect `{conf -> CONF, data -> DATA}`, write `{CMD -> cmd, HEARTBEAT -> heartbeat}`, `mtu = 128`, `pairing = OnDemand`, `securedWrites = {CMD}`.

- [ ] **Step 1: Write failing tests**: `dataPacketUpdatesDataAndHostVersion` (reuse a sample byte array from `DataTest`); `legacyAndVersion10DataLayoutsStillParse`; `confPacketUpdatesConf`; `eachDecodeReturnsANewStateObject`; `unknownChannelIsIgnored` (null); `encodeKeepsPayloadAndChannel` (e.g. `"T1200"` on `CMD`); `heartbeatIsChannelFourWithPayloadH`; `profileUsesTheContractUuids`; `profileIsOnDemandWithOnlyCmdSecured`.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests '*RouterProtocolTest*'`. Expected: FAIL.
- [ ] **Step 3: Implement** as above.
- [ ] **Step 4: Run** the same command. Expected: PASS.
- [ ] **Step 5: Commit point** (owner), including the submodule pointer.

### Task 9: `LinkBleThing`, device store, passkey store

**Files:**
- Create: `app/src/main/java/com/aboni/n2kRouter/LinkBleThing.kt`, `FileDeviceStore.kt`
- Modify: `BLEN2KListener.kt` (`onCommandResult(result: com.aboni.marine.link.CommandResult)`), `N2KCardPage.kt` and any page overriding it, `Utils.kt` (remove `describeCommandResult` and its now-unused `gattStatusName`/`bondFailureName` if nothing else uses them), `res/values/strings.xml` (remove unused `cmd_*`, `passkey_message*` once lint says so)
- Test: `app/src/test/java/com/aboni/n2kRouter/LinkBleThingTest.kt`, `FileDeviceStoreTest.kt`

**Interfaces:**
- Consumes: Task 8 (`RouterProtocol`, `ROUTER_PROFILE`, `RouterCommand`, `RouterState`).
- Produces:
  - `class FileDeviceStore(private val dir: File, name: String = "n2k.data") : DeviceStore`: `load()` returns the file content or null when missing; `save(id)` writes it (UTF-8, same bytes as `BLEThingImpl.saveToFile`). The app passes `context.filesDir`.
  - `class LinkBleThing(context: Context, scope: CoroutineScope, pipe: Pipe? = null, prompter: PasskeyPrompter) : BLEThing`. Without `pipe` it builds `BlePipe(scope, AndroidBleRadio(context), ROUTER_PROFILE, FileDeviceStore(context.filesDir), KeystorePasskeyStore(context, "n2k.passkeys", "n2k.passkey.key"), prompter)`. It wraps the pipe in `PipeLink(scope, pipe, RouterProtocol)`. `BLEThing` members map: `data`/`conf` from the shared `RouterState` (empty objects until the first packet); `saveX(...)` build the same strings as `BLEThingImpl` (`N`, `T`, `H`=h*3600+m*60, `t`/`s`/`a`/`w`/`x` = `(value*100).toInt()`, `B`, `S`+`String(conf.toByteArray())` with `conf.copyFrom(conf)` first) and `link.send(RouterCommand(CMD, ...))`, reporting the result to listeners on the UI thread; `sendHeartbeat()` sends `RouterCommand(HEARTBEAT, "h")` and ignores the result; `startScan()` = `link.scan()`, `stopScan()` no-op; `setDeviceToConnect(id)` = `link.select(id)`; `getDeviceToConnect()` = `link.chosen.value`; `connect()` = `link.start()`; `disconnect()` = `link.stop()`; `refreshConnection()` = `link.start()`; `getStatus()` maps `LinkState`: Connected/Stale -> `Connected`, Connecting/Pairing -> `Connect`, Idle/Scanning -> `Off`; `getConnectedDevice()` = `DeviceItem` from `status.device` while Connected/Stale/Connecting/Pairing, else null; `release()` = `link.close()`. Listener events: `onStatus` on each mapped state change (and `scanning=true` for `DEVICE_SCAN_MS` after `startScan()`); `onScan(DeviceItem)` for each device in `link.devices` whose rssi or name changed; `onRssi` on rssi change of the connected device; `onConf`/`onData` on each new state; `onCommandResult` with the kit's `CommandResult`. When the link leaves Connected/Stale, call `prompter.dismiss()`.

- [ ] **Step 1: Write failing tests** with a fake `Pipe` (inbound `MutableSharedFlow`, scripted status, recording `write`), `runTest`: `commandStringsMatchTheOldImplementation` (one assertion per `save*`, including `saveEngineHours(2, 30)` -> `"H9000"` and `saveRPMAdjustment(1.25)` -> `"t125"`); `saveConfigurationSendsSPlusConfBytes`; `heartbeatIsWrittenOnChannelFourAndNeverReportsAResult`; `sentResultIsReportedToListeners`; `statusMapping` for every `LinkState`; `getConnectedDeviceIsNullWhenIdle`; `selectingADevicePassesItToTheLinkAndGetDeviceToConnectReturnsIt`; `dataPacketReachesOnData`; `leavingConnectedDismissesThePrompter`. In `FileDeviceStoreTest` (temp dir): `loadReturnsNullWhenMissing`; `saveThenLoadRoundTrips`; `readsAFileWrittenByTheOldImplementationFormat` (plain UTF-8 address, no newline).
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests '*LinkBleThingTest*' --tests '*FileDeviceStoreTest*'`. Expected: FAIL.
- [ ] **Step 3: Implement** as above. `onCommandResult` change touches every `BLEN2KListener` implementer; fix them (`MainActivity` shows `result.text` in the snackbar).
- [ ] **Step 4: Run** `./gradlew assembleDebug testDebugUnitTest`. Expected: BUILD SUCCESSFUL, tests PASS.
- [ ] **Step 5: Commit point** (owner).

### Task 10: `MainActivity` wiring

**Files:** Modify `MainActivity.kt`, `res/layout/activity_main.xml` (none expected), `app/src/test/...` none new (manual check in Task 11).

- [ ] **Step 1: Replace** `BLEThingImpl(this)` with `LinkBleThing(applicationContext, lifecycleScope, prompter = passkeyPrompter)`, where `passkeyPrompter = PasskeyPrompter()` is an activity field. Remove `askPasskey`, `setPasskeyRequestHandler` and the `AlertDialog`/`EditText`/`InputType` imports. In the status-bar `ComposeView` content, show `PasskeyDialog` while `passkeyPrompter.pending` is not null (inside `MarineTheme`), answering via `passkeyPrompter.answer`.
- [ ] **Step 2: Keep** the heartbeat `Timer` (2.5 s) calling `ble.sendHeartbeat()`; keep `ble.startScan()` after the Bluetooth permission is granted in `onResume`.
- [ ] **Step 3: Run** `./gradlew assembleDebug testDebugUnitTest lintDebug`. Expected: BUILD SUCCESSFUL, no new lint issues.
- [ ] **Step 4: Commit point** (owner).

### Task 11: On-device confirmation, then removal of the old code

**Files:** Delete `BLEThing.kt` (`BLEThingImpl` and the `PasskeyRequestHandler` alias; the `BLEThing` interface and `BLELifecycleState` move to `BLEN2KListener.kt` or `LinkBleThing.kt`), `PasskeyStore.kt`; remove now-unused strings; update `CLAUDE.md` ("Command write behavior" and "BLE protocol guardrails" now point at `RouterProtocol.kt`/`LinkBleThing.kt`).

- [ ] **Step 1: Install** (`./gradlew installDebug`) on the phone with the real router and check each item, writing down the result of each for the owner:
  1. Fresh start with the existing stored passkey: connects, shows data, no prompt, a setting change shows "Sent".
  2. Clear the app's passkeys (or reinstall): the first setting change shows the themed dialog; a wrong passkey shows the dialog again with the reason; the right one sends the command.
  3. Cancel the dialog: the snackbar says "Passkey cancelled"; the next change asks again.
  4. Router power-cycled while connected: the bar shows searching/connecting, then it reconnects by itself and data resumes.
  5. Phone out of range and back: same.
  6. Devices page: selecting another router connects to it; reopening the app remembers it (`n2k.data`).
  7. Switch the theme while the dialog is open and in Night: the dialog is red on black.
  8. Heartbeat: the router's own link-alive indicator (if it has one) stays up; no snackbar every 2.5 s.
- [ ] **Step 2: Only if every item passed**, delete the old code as listed and run `./gradlew assembleDebug testDebugUnitTest lintDebug`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 3: Commit point** (owner).
