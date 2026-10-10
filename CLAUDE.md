# AGENTS.md

## Purpose
- For **feature-delivery agents** on `NMEARouter`: make small, safe BLE + UI changes quickly.
- The app works in pair with the firmware in https://github.com/titio72/n2k_router_arduino
- Main module: `:app`; entry point: `app/src/main/java/com/aboni/n2kRouter/MainActivity.kt`.

## Fast model
- `MainActivity` owns one `BLEThingImpl` and a `ViewPager2` with `N2KDataView`, `N2KSettingsView`, `N2KScanView`.
- Pages extend `N2KCardPage`; `attachCard(...)` registers each page as `BLEN2KListener`.
- BLE lifecycle: `Off -> Connect -> Discover -> Connected` (`BLEN2KListener.kt`).
- Scan filters by service UUID `4fafc201-1fb5-459e-8fcc-c5c9c331914b`; selected address drives connect.

## Where to change
- **Telemetry payload field**: `Data.parse(...)` (`Data.kt`) -> UI bind in `N2KDataView.kt` -> formatting in `res/values/strings.xml`.
- **Settings toggle / feature bit**: extend indexes and serialization in `Conf.kt`, then wire UI in `N2KSettingsView.kt`.
- **Command write behavior**: update ASCII command handling in `BLEThing.kt` (`N...`, `T...`, `H...`, `t...`, heartbeat `h`).
- **Scan/connect UX**: `N2KScanView.kt` (list/select/highlight) + footer status in `MainActivity.kt`.

## BLE protocol guardrails
- Characteristic UUIDs in `BLEThing.kt` are contract-critical: conf `beb5483e-36e1-4688-b7f5-ea07361b26a8`, data `55da66c7-801f-498d-b652-c57cb3f1b590`, cmd `68ad1094-0989-4e22-9f21-4df7ef390803`.
- Keep `Data.parse(...)` backward-compatible for legacy + `version >= 10` layouts unless task says otherwise.
- Keep persisted selected device behavior (`n2k.data` via `BLEThingImpl.saveToFile/readFromFile`).

## Conventions
- Use `appendLog("...")` (`Utils.kt`, tag `ABN2K`) for BLE traces.
- BLE callback UI writes must stay on UI thread (`post { ... }` / `runOnUiThread { ... }`).
- Reuse `Utils.kt` format helpers (`formatValue`, `formatEngineHours`, `formatLatLon`).
- UI stack is XML + custom views; do not introduce Compose UI for feature work.

## Boundaries + handoff checks
- Permissions + request flow: `app/src/main/AndroidManifest.xml` and `BLEApp.kt` (`grantBluetoothCentralPermissions(...)`).
- Avoid changing BLE reliability TODO paths in `BLEThing.kt` (timeout/error 133) unless task is BLE stability.
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