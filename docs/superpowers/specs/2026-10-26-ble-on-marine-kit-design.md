# NMEARouter BLE on marine-kit: design

Date: 2026-10-26
Status: draft, waiting for the owner's approval

## Intent

The owner's boat apps (NMEARouter, NMEAWind2, the N2K autopilot remote, NavMonitor) share marine-kit for look and
feel. The BLE connection is the largest piece still copy-pasted: `BLEThing.kt` (about 740 lines) is the Router's own
and differs from NMEAWind2's by 500+ lines. The owner wants to share as much of it as possible through marine-kit.
Nothing is wrong with the Router's BLE today. This is about sharing, not repairing.

The Router, NMEAWind2 and the autopilot all pair with their device using a passkey the user types in the app. So the
passkey flow is shared code and belongs in marine-kit, not in the Router.

## Goals

1. After this work the Router's BLE code is only what is the Router's own: the UUIDs, decoding of `Data` and `Conf`,
   the ASCII command strings, and the heartbeat timer. Scanning, connecting, reconnecting, pairing, the passkey, the
   command queue and their error texts come from marine-kit's `link`.
2. Each behaviour of today's `BLEThing` that the owner relies on still works, as listed under "Behaviour to keep".
3. NMEAWind2 and the autopilot can adopt the same pieces later without changing marine-kit again.
4. The passkey dialog is a marine-kit component that follows all three themes (the current `AlertDialog` does not,
   and a white dialog at night costs night vision).

## Non-goals

- No change to the firmware, the BLE protocol, the UUIDs or the command strings.
- No migration of NMEAWind2 or the autopilot now. They get a `MIGRATION.md` entry and move when the owner decides.
- No WiFi/TCP work, no UDP.
- The Router's screens (Data, Settings) stay as they are in this project, except for the plumbing they use to receive
  data and send commands (see "Router migration").

## Anti-goals (failures even if it technically works)

- Users have to type the passkey again after the update (stored passkeys must keep working).
- The Router asks for a passkey at connect when it did not before.
- The selected device is forgotten (`n2k.data` must keep working, see below).
- Reconnection to the router gets worse than today, or the plain-language error texts get replaced by codes.
- The old `BLEThing` is deleted before the new path has been confirmed on the real router.

## Decisions by the owner

- The passkey flow goes into marine-kit, because the other apps need it too.
- Whether pairing happens at connect or on the first command is not decided for the apps in general. marine-kit gives
  each app an option: the app can force pairing at connect, or leave it until a command needs it. The Router uses
  "on first command", as today.

## Today's Router behaviour, to keep

- It connects and shows data without pairing. Nothing asks for a passkey until the first command is sent.
- The first command asks for the passkey in the app. The app answers Android's pairing request itself with the stored
  passkey, so the system pairing dialog does not appear. It answers only for the device it is talking to.
- Passkeys are stored encrypted (AES-GCM, Android Keystore key alias `n2k.passkey.key`, preferences file
  `n2k.passkeys`, keyed by device address). A value that cannot be decrypted counts as missing.
- When the router refuses a write for security reasons (GATT 5, 15 or 137, or a timeout after a failed pairing), the
  app forgets the passkey, removes the bond, and asks again, telling the user why (including the reason of the last
  failed pairing). Cancelling drops the queued commands and reports "passkey cancelled".
- Commands are written one at a time, queued in order, with a retry when the GATT stack is busy (20 tries, 100 ms), and
  a 15 s timeout when no write result arrives.
- A heartbeat is written to its own characteristic every 2.5 s.
- The chosen device is kept in the file `n2k.data` and reconnected to automatically. Scanning happens on resume and
  from the search icon, filtered by the service UUID.

## Design

Work happens in two repositories. Release order: marine-kit first, then the Router.

### 1. marine-kit `link` (release 0.3.0, additive except one note below)

**Pairing option.** `BleProfile` gets a `pairing` setting with two values: pair at connect (what 0.2.0 does) and pair
when a write needs it. The default is pair at connect, so apps on 0.2.0 behave the same without changes. *(My call; the
owner only asked that apps can choose.)*

**Passkey store.** An interface `PasskeyStore` (get, put, remove by device address) and an Android Keystore
implementation moved from the Router's `PasskeyStore.kt`. Preferences file name and key alias are constructor
parameters, so the Router passes its current ones and its stored passkeys still decrypt.

**Passkey prompt.** An interface the pipe calls when it needs the passkey, taking the device and the reason the
previous one failed (null the first time), returning the passkey or null if the user gave up. marine-kit provides one
implementation, `PasskeyPrompter`, that exposes the pending request as state and takes the answer. The UI shows it.

**Pairing request.** While a BLE connection exists and its policy needs it, the pipe listens for Android's pairing
request for that device only, and answers with the stored passkey (the same high-priority receiver and
`setPin` + abort as `BLEThing`). Requests for other devices are left to the system.

**Writes.** `BleConnection.write` fails with an `IOException` subclass that carries the GATT status. The pipe
serializes writes. When the status is a security one (5, 15, 137) or a write times out after a failed pairing, the pipe
forgets the passkey, removes the bond, asks the prompt again (with the reason, from `gattStatusText` and the last
bond failure from `bondFailureText`) and retries the same write. Cancelled prompt: queued writes fail with a
"passkey cancelled" error. Busy retries and the timeout (15 s) are kept; the timeout becomes a profile setting.

**Command results.** `CommandResult` gets two members: passkey cancelled, and passkey rejected (with the reason text).
`PipeLink.send` maps the new pipe errors to them. This is the one source-breaking change: any app with an exhaustive
`when` over `CommandResult` must handle the new members. It goes in `docs/MIGRATION.md` under 0.3.0, with before and
after code.

**Radio.** `AndroidBleRadio` gets what the above needs: the pairing-request receiver, bond removal (reflection on the
hidden `removeBond`, as `BLEThing` does), and the last bond failure reason.

**Tests.** `FakeBleRadio` learns to script a security refusal and a silent write. New tests cover: no pairing at
connect when the policy says on demand, first write asks the prompt, the answer is stored and used, a wrong passkey
(security refusal) clears the store and asks again with the reason, cancel fails the queued writes with the right
result, writes stay in order, a busy radio is retried, a silent write times out, and a pairing request for another
device is not answered. The Keystore implementation cannot run under Robolectric: its logic is tested against an
in-memory store, and the encryption is checked on the device.

### 2. marine-kit `ui`

`PasskeyDialog`: numeric input, shows the device name and the failure reason, follows Light, Dark and Night.
`MarineScaffold` takes an optional prompter and shows the dialog when there is a pending request. The demo app shows
it in all three themes, driven by its fake link. Added to `GUIDELINES.md` section 7.

### 3. Router migration

- A `Protocol` for the Router: channels for conf, data and command (heartbeat is a fourth write channel); `decode`
  keeps `Data.parse(...)` and `Conf` unchanged, so the legacy and `version >= 10` layouts stay compatible; `encode`
  produces today's ASCII commands (`N...`, `T...`, `H...`, `t...`, `h`). The router sends no reply to commands, so
  the result of a command is "Sent" once written.
- A `BleProfile` with the four UUIDs from `BLEThing.kt` and the pair-on-demand policy.
- A `DeviceStore` backed by the existing `n2k.data` file, so the persisted selection keeps working as is.
- The Router's passkey store uses its current preferences file and key alias.
- The heartbeat stays a small timer in `MainActivity` that sends through the link and ignores the result (no snackbar
  every 2.5 s). *(My call.)*
- `BLEN2KListener` stays as a thin adapter on top of the link at first, so `N2KDataView`, `N2KSettingsView` and
  `N2KScanView` do not change in the same step. Moving them to observe the link's state directly is a later cleanup.
  *(My call, to keep this step reviewable.)*
- `BLEThing.kt`, `PasskeyStore.kt` and the passkey `AlertDialog` in `MainActivity` are removed in the final commit,
  after the confirmation below.

### 4. Release and rollout

1. marine-kit: implement, test, update README/MIGRATION/GUIDELINES, tag 0.3.0 (the owner tags and pushes).
2. Router: move the submodule to 0.3.0, migrate behind the existing adapters.
3. Confirmed on the real router, on the phone, before deleting the old code: first pairing; stored passkey after
   restarting the app; wrong passkey (retry with the reason); cancel; changing a setting and seeing "Sent"; router
   power-cycled while connected (reconnect); phone out of range and back; selecting another router in the list.
4. Delete `BLEThing` and friends. The owner stages and commits, as always.

## Constraints

- Router code rules (`CLAUDE.md`): XML + custom views, no Compose for feature work (marine-kit components hosted in
  `ComposeView` are fine, as for the status bar and device list); `appendLog(...)` for BLE traces; UI writes on the UI
  thread; the owner stages and commits; tests for new behaviour.
- marine-kit rules: new tokens or components go into marine-kit with tests; JDK 21 locally; `./gradlew build` there.
- BLE reliability paths (timeouts, error 133) are not to be changed beyond moving them; the kit's existing
  reconnect-with-backoff replaces the Router's reconnect on scan result. This is a behaviour difference to watch
  during the on-boat checks.

## Left to the builder

Class and file names inside marine-kit; the exact shape of the pipe's write path; how the passkey timeout/busy
settings are named; how the `BLEN2KListener` adapter is structured; which Router commands become typed values and
which stay strings.
