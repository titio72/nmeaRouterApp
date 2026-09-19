# NMEARouter Refactoring - File Impact Map

This document shows exactly which files are affected and the specific line numbers for each refactoring phase.

---

## Phase 1: Critical Fixes - File Impact

### File 1.1: Conf.kt
**Location:** `app/src/main/java/com/aboni/n2kRouter/Conf.kt`

| Issue | Lines | Change | Priority |
|-------|-------|--------|----------|
| Copy bug in copyFrom(Conf) | 21 | `bSTW = c.bSYT` → `bSTW = c.bSTW` | CRITICAL |
| Missing fields in copyFrom(Conf) | 20-30 | Add `bSEA_TEMP` and `bSTW_PADDLE` | CRITICAL |
| Redundant semicolon | 40 | Remove `;` from line 40 | LINT |

**Before:**
```kotlin
17: fun copyFrom(c: Conf) {
18:     bGPS = c.bGPS
19:     bSYT = c.bSYT
20:     bBME = c.bBME
21:     bDHT = c.bDHT
22:     bRPM = c.bRPM
23:     bSTW = c.bSYT  // ❌ BUG: should be c.bSTW
24:     bVED = c.bVED
25:     bSRC = c.bSRC
26: }
...
40: bSTW_PADDLE = (v and 0x0200) != 0;  // ❌ Redundant semicolon
```

**After:**
```kotlin
17: fun copyFrom(c: Conf) {
18:     bGPS = c.bGPS
19:     bSYT = c.bSYT
20:     bBME = c.bBME
21:     bDHT = c.bDHT
22:     bRPM = c.bRPM
23:     bSTW = c.bSTW  // ✅ FIXED
24:     bVED = c.bVED
25:     bSRC = c.bSRC
26:     bSEA_TEMP = c.bSEA_TEMP  // ✅ ADDED
27:     bSTW_PADDLE = c.bSTW_PADDLE  // ✅ ADDED
28: }
...
40: bSTW_PADDLE = (v and 0x0200) != 0  // ✅ Semicolon removed
```

---

### File 1.2: MainActivity.kt
**Location:** `app/src/main/java/com/aboni/n2kRouter/MainActivity.kt`

| Issue | Lines | Change | Priority |
|-------|-------|--------|----------|
| Timer not cancelled | 114-118 | Add `timer.cancel()` in onDestroy | CRITICAL |

**Before (lines 113-119):**
```kotlin
113: override fun onDestroy() {
114:     ble.disconnect()
115:     super.onDestroy()
116: }
```

**After:**
```kotlin
113: override fun onDestroy() {
114:     timer.cancel()  // ✅ ADDED
115:     ble.disconnect()
116:     super.onDestroy()
117: }
```

---

### File 1.3: Data.kt
**Location:** `app/src/main/java/com/aboni/n2kRouter/Data.kt`

| Issue | Lines | Change | Priority |
|-------|-------|--------|----------|
| Missing offset assignment | 194 | `n2kSrc.parse()` → `offset = n2kSrc.parse()` | CRITICAL |
| Empty data handling | 104 | Add bounds check at start | HIGH |

**Before (lines 190-196):**
```kotlin
190:             offset = soc.parse(data, offset)
191:             n2kSrc.parse(data, offset)  // ❌ Missing offset assignment
192:         }
```

**After:**
```kotlin
190:             offset = soc.parse(data, offset)
191:             offset = n2kSrc.parse(data, offset)  // ✅ FIXED
192:         }
```

**Before (line 104):**
```kotlin
104: fun parse(data: ByteArray) {
105:     var offset = 0
106:     if (data[0].toInt()>=10) {  // ❌ No bounds check
```

**After:**
```kotlin
104: fun parse(data: ByteArray) {
105:     if (data.isEmpty()) {  // ✅ ADDED
106:         appendLog("ERROR: Empty data received")
107:         return
108:     }
109:     
110:     var offset = 0
111:     if (data[0].toInt() >= 10) {
```

---

## Phase 2: Lint Cleanup - File Impact

### File 2.1: BLEThing.kt
**Location:** `app/src/main/java/com/aboni/n2kRouter/BLEThing.kt`

| Issue | Lines | Change | Priority |
|-------|-------|--------|----------|
| Unused parameter | 428 | `catch (e: FileNotFoundException)` → `catch (_: FileNotFoundException)` | LINT |

**Before (line 428):**
```kotlin
428: } catch (e: FileNotFoundException) {
429:     return null
430: }
```

**After:**
```kotlin
428: } catch (_: FileNotFoundException) {
429:     return null
430: }
```

---

## Phase 3: Thread Safety & Error Handling - File Impact

### File 3.1: Data.kt - Validation & Refactoring
**Location:** `app/src/main/java/com/aboni/n2kRouter/Data.kt`

| Issue | Lines | Change | Priority |
|-------|-------|--------|----------|
| Buffer validation | 104-110 | Add size checks for all versions | HIGH |
| Extract v10+ parsing | 114-152 | Move to parseV10Plus() method | MEDIUM |
| Extract legacy parsing | 153-175 | Move to parseLegacy() method | MEDIUM |
| Extract period calc | 176-188 | Move to updateCanBusPeriods() | MEDIUM |

**Before:** Single monolithic parse() function (104-200)

**After:** 
```kotlin
fun parse(data: ByteArray) {
    // Validation (104-115)
    if (data.isEmpty()) { ... return }
    if (data.size < 2) { ... return }
    
    version = data[0].toInt()
    
    // Version routing
    if (version >= 10) {
        parseV10Plus(data)
    } else {
        parseLegacy(data)
    }
    
    updateCanBusPeriods()
}

private fun parseV10Plus(data: ByteArray) {
    if (data.size < 62) { ... return }
    // Original v10+ parsing code
}

private fun parseLegacy(data: ByteArray) {
    if (data.size < 58) { ... return }
    // Original legacy parsing code
}

private fun updateCanBusPeriods() {
    // Original period calculation code
}
```

---

### File 3.2: BLEThing.kt - State Synchronization
**Location:** `app/src/main/java/com/aboni/n2kRouter/BLEThing.kt`

| Issue | Location | Change | Priority |
|--------|----------|--------|----------|
| Add synchronized methods | New | Add @Synchronized getters/setters | HIGH |
| Sync state mutations | ~380 | Replace direct assignments with method calls | HIGH |
| Add error logging | ~420-450 | Wrap writeCharacteristic calls with logging | HIGH |

**Add new synchronized methods:**
```kotlin
// Around line 50 (in class scope)

@Synchronized
private fun setConnectedGattToNull() {
    connectedGatt = null
    characteristicConf = null
    characteristicData = null
    characteristicCommand = null
}

@Synchronized
private fun setConnectedGatt(gatt: BluetoothGatt?) {
    connectedGatt = gatt
}

@Synchronized
private fun setLifecycleStatus(newStatus: BLELifecycleState) {
    if (lifecycleStatus != newStatus) {
        appendLog("BLE lifecycle: ${lifecycleStatus} -> $newStatus")
        lifecycleStatus = newStatus
        notifyListeners { it.onStatus(newStatus, isScanning) }
    }
}
```

**Update saveDeviceName() (search for pattern):**
```kotlin
// BEFORE (~line 410)
override fun saveDeviceName(n: String) {
    characteristicCommand?.let {
        connectedGatt?.writeCharacteristic(...)
    }
}

// AFTER
override fun saveDeviceName(n: String) {
    if (!isConnected()) {
        appendLog("ERROR: saveDeviceName called but not connected")
        return
    }
    
    characteristicCommand?.let { cmd ->
        connectedGatt?.let { gatt ->
            try {
                val success = gatt.writeCharacteristic(
                    cmd,
                    ("N$n").toByteArray(Charsets.UTF_8),
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                )
                appendLog("saveDeviceName: write ${if(success) "queued" else "failed"} for '$n'")
            } catch (e: Exception) {
                appendLog("ERROR: saveDeviceName exception: ${e.message}")
            }
        } ?: appendLog("ERROR: saveDeviceName no GATT connection")
    } ?: appendLog("ERROR: saveDeviceName no command characteristic")
}
```

**Apply same pattern to:**
- `saveConfiguration()`
- `saveEngineHours()`
- `saveRPMCalibration()`
- `saveRPMAdjustment()`
- `sendHeartbeat()`

---

### File 3.3: N2KDataView.kt
**Location:** `app/src/main/java/com/aboni/n2kRouter/N2KDataView.kt`

| Issue | Lines | Change | Priority |
|--------|--------|--------|----------|
| Remove !! operator | 66-67 | Use safe navigation `?.` | HIGH |

**Before (line 66-68):**
```kotlin
utcTimeTxt.text = if (data.utcTime.valid && data.utcTime.value>0) 
    data.utcTime.asTime()!!.toString()  // ❌ Unsafe
    else noValue
```

**After:**
```kotlin
val timeStr = if (data.utcTime.valid && data.utcTime.value > 0) {
    data.utcTime.asTime()?.toString() ?: noValue  // ✅ Safe
} else {
    noValue
}
utcTimeTxt.text = timeStr
```

**Search for other !! operators in:**
- Line 61-88: Review all data field assignments
- Apply same pattern to any other `!!` operators

---

## Phase 4: Code Quality & Maintainability - File Impact

### File 4.1: BLEThing.kt - Constants Extraction
**Location:** `app/src/main/java/com/aboni/n2kRouter/BLEThing.kt`

| Change | Location | Type |
|--------|----------|------|
| Add constants object | Top of file or companion object | NEW |
| Update command strings | ~420-450 | UPDATE |
| Update UUID strings | ~50-70 | UPDATE |

**Add new constants (top of file or in companion object):**
```kotlin
object BLEConstants {
    // Command prefixes
    const val CMD_DEVICE_NAME = "N"
    const val CMD_RPM_CALIBRATION = "T"
    const val CMD_ENGINE_HOURS = "H"
    const val CMD_RPM_ADJUSTMENT = "t"
    const val CMD_HEARTBEAT = "h"
    
    // BLE Configuration
    const val REQUESTED_MTU = 128
    const val GATT_INTERNAL_ERROR = 129
    const val HEARTBEAT_INTERVAL_MS = 2500L
    
    // Service and Characteristic UUIDs (verify actual values from code)
    val SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
    val CHARACTERISTIC_CONFIG_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
    val CHARACTERISTIC_DATA_UUID = UUID.fromString("55da66c7-801f-498d-b652-c57cb3f1b590")
    val CHARACTERISTIC_COMMAND_UUID = UUID.fromString("68ad1094-0989-4e22-9f21-4df7ef390803")
}
```

**Update usages (search and replace):**
```
"N$n"                           → "\"${BLEConstants.CMD_DEVICE_NAME}$n\""
"T" + calibration               → "BLEConstants.CMD_RPM_CALIBRATION + calibration"
"H" + engineHours               → "BLEConstants.CMD_ENGINE_HOURS + engineHours"
"t" + adjustment                → "BLEConstants.CMD_RPM_ADJUSTMENT + adjustment"
"h"                             → "BLEConstants.CMD_HEARTBEAT"
2500L (interval)                → "BLEConstants.HEARTBEAT_INTERVAL_MS"
128 (MTU)                       → "BLEConstants.REQUESTED_MTU"
129 (GATT error)                → "BLEConstants.GATT_INTERNAL_ERROR"
```

---

### File 4.2: Data.kt - Parse Method Refactoring
**Location:** `app/src/main/java/com/aboni/n2kRouter/Data.kt`

Already covered in Phase 3.1, but refactoring summary:

| Before | After | Impact |
|--------|-------|--------|
| 1 parse() = 100+ lines | Split into 4 methods | Clearer, more testable |
| parse(data: ByteArray) | parse(data: ByteArray) | Entry point (unchanged interface) |
| (combined v10+ logic) | parseV10Plus(data: ByteArray) | NEW method |
| (combined legacy logic) | parseLegacy(data: ByteArray) | NEW method |
| (combined period logic) | updateCanBusPeriods() | NEW method |

---

### File 4.3: Conf.kt - Remove Unused Function
**Location:** `app/src/main/java/com/aboni/n2kRouter/Conf.kt`

| Issue | Lines | Decision |
|--------|--------|----------|
| toByteArray() never called | 61-70 | Remove or document (TBD) |

**Option A: Remove**
```kotlin
// DELETE lines 61-70:
// fun toByteArray(): ByteArray { ... }
```

**Option B: Document for future**
```kotlin
/**
 * Serializes configuration to byte array for firmware storage.
 * 
 * Currently unused - retained for future device firmware update feature.
 * TODO: Remove in v2.0 if firmware serialization not implemented.
 * 
 * @return ByteArray with configuration flags as ASCII '0'/'1' characters
 */
fun toByteArray(): ByteArray {
    // ... existing code
}
```

---

### File 4.4: Conf.kt - Consistency Review
**Location:** `app/src/main/java/com/aboni/n2kRouter/Conf.kt`

| Issue | Location | Change |
|--------|----------|--------|
| Verify copyFrom(Int) matches other methods | 27-40 | Cross-check all 10 flags |
| Document missing fields | 17-25 | Add comment explaining which fields to include |

**Verify these are all consistent:**
```kotlin
// copyFrom(Conf) - line 17
// copyFrom(Int) - line 27  
// copyFrom(ByteArray) - line 42
// toByteArray() - line 61 (if kept)

// All should handle these 10 fields consistently:
// bGPS, bDHT, bBME, bSYT, bRPM, bSTW, bVED, bSRC, bSEA_TEMP, bSTW_PADDLE
```

---

## File Modification Summary Table

| Phase | File | Changes | Lines Changed | Risk |
|-------|------|---------|---------------|----|
| 1.1 | Conf.kt | Bug fix + add fields | 21, 26-27 | Very Low |
| 1.2 | MainActivity.kt | Add timer cancel | 114 | Very Low |
| 1.3 | Data.kt | Fix offset + add validation | 104-110, 191 | Very Low |
| 2.1 | BLEThing.kt | Fix param name | 428 | Very Low |
| 2.2 | Conf.kt | Remove semicolon | 40 | Very Low |
| 3.1 | Data.kt | Refactor parse methods | 104-200 | Low |
| 3.2 | BLEThing.kt | Add sync + logging | 50-70, 410-450+ | Low |
| 3.3 | N2KDataView.kt | Safe navigation | 66-88 | Very Low |
| 4.1 | BLEThing.kt | Extract constants | 1-50, 420-450+ | Low |
| 4.2 | Data.kt | Method extraction | (same lines as 3.1) | Low |
| 4.3 | Conf.kt | Remove/document unused | 61-70 | Very Low |
| 4.4 | Conf.kt | Consistency check | 17-70 | Very Low |

---

## Total File Count Affected

- **Total files modified:** 4
- **Total lines modified:** ~200-250
- **Files with multiple phases:** 3 (Conf.kt, Data.kt, BLEThing.kt)

---

## Critical Dependencies

1. **Phase 1 → All others:** Must complete critical fixes first
2. **Phase 3.1 → 3.2:** Data validation should precede BLE thread safety
3. **Phase 4.1 → Phase 3.2:** Constants available for error logging updates
4. **All phases → Testing:** Each phase has matching test cases

---

## Verification Checkpoints

After each phase:

```bash
# After Phase 1
./gradlew assembleDebug
./gradlew testDebugUnitTest

# After Phase 2
./gradlew lintDebug          # Should show 0 warnings

# After Phase 3
./gradlew testDebugUnitTest  # All data + thread tests pass

# After Phase 4
./gradlew build              # Full build with constants
```

---

## Rollback Strategy

Each phase is independently deployable:

- **Phase 1:** Deploy immediately (fixes critical bugs)
- **Phase 2:** Deploy with Phase 1 (cosmetic)
- **Phase 3:** Deploy separately (safety improvements)
- **Phase 4:** Deploy separately (refactoring)

If Phase N breaks something, rollback that phase only and continue others.

---

**Impact Map Created:** March 21, 2026  
**Last Updated:** March 21, 2026

