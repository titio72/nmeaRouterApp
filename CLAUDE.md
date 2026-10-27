# AGENTS.md

## Purpose
- For **feature-delivery agents** on `NMEARouter`: make small, safe BLE + UI changes quickly.
- The app works in pair with the firmware in https://github.com/titio72/n2k_router_arduino
- Main module: `:app`; entry point: `app/src/main/java/com/aboni/n2kRouter/MainActivity.kt`.

## Fast model
- `MainActivity` owns one `LinkBleThing` (a `BLEThing` on marine-kit's `PipeLink`/`BlePipe`) and a marine-kit `CardPager` with three Compose cards: `DataCard`, `SettingsCard`, `DevicesCard`.
- Each card has a state holder (`DataState`, `SettingsState`, `DevicesState`) registered as `BLEN2KListener`; it lives in the Activity, so typed values and open sections survive swiping.
- BLE lifecycle: `Off -> Connect -> Connected` as the screens see it (`BLEN2KListener.kt`); the status bar and Devices card use the link's own `LinkStatus` (`LinkBleThing.linkStatus`). Scanning, connecting, reconnecting, pairing and the passkey are marine-kit's (`marine-kit/link`, pinned submodule).
- Scan filters by service UUID `4fafc201-1fb5-459e-8fcc-c5c9c331914b`; selected address drives connect.

## Where to change
- **Telemetry payload field**: `Data.parse(...)` (`Data.kt`) -> `DataReadings`/`dataReadings(...)` and layout in `DataCard.kt` -> formatting in `res/values/strings.xml`.
- **Settings toggle / feature bit**: extend indexes and serialization in `Conf.kt`, then add a `Service`/`Setting` entry and place it in `SettingsCard.kt`.
- **Command write behavior**: update ASCII command handling in `LinkBleThing.kt` (`N...`, `T...`, `H...`, `t...`, heartbeat `h`); payload decoding and channels in `RouterProtocol.kt`.
- **Scan/connect UX**: `DevicesCard.kt` (marine-kit `DeviceList`) + the scaffold's status bar in `MainActivity.kt`.

## BLE protocol guardrails
- Characteristic UUIDs in `RouterProtocol.kt` (`ROUTER_PROFILE`) are contract-critical: conf `beb5483e-36e1-4688-b7f5-ea07361b26a8`, data `55da66c7-801f-498d-b652-c57cb3f1b590`, cmd `68ad1094-0989-4e22-9f21-4df7ef390803`.
- Keep `Data.parse(...)` backward-compatible for legacy + `version >= 10` layouts unless task says otherwise.
- Keep persisted selected device behavior (`n2k.data` via `FileDeviceStore`) and stored passkeys (`n2k.passkeys`, key alias `n2k.passkey.key`, via marine-kit's `KeystorePasskeyStore`).

## Conventions
- Use `appendLog("...")` (`Utils.kt`, tag `ABN2K`) for BLE traces.
- Listener callbacks arrive on the UI thread (`LinkBleThing` runs on `lifecycleScope`); state holders write Compose state directly.
- Reuse `Utils.kt` format helpers (`formatValue`, `formatEngineHours`, `formatLatLon`).
- UI stack is Compose on marine-kit (`MarineTheme`, `MarineScaffold`, `CardPager`, `LabeledValue`, `SettingRow`, `SettingSwitch`, `ServiceSection`). A missing component goes into marine-kit, not the app.

## Boundaries + handoff checks
- Permissions + request flow: `app/src/main/AndroidManifest.xml` and `BLEApp.kt` (`grantBluetoothCentralPermissions(...)`).
- BLE reliability (timeouts, error 133, reconnect backoff) lives in marine-kit: change it there, with tests, never in the app.
- Verify before handoff:
```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug
```

## Rules
- Do not commit: the user will stage and commit changes.
- Add tests whenever there is opportunity.
- Tests new features and changes.
- Before major changes, challenge the user decisions and propose better alternatives.