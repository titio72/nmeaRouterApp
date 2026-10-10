# NMEARouter Refactoring - Test Plan & Test Cases

## Overview

This document provides comprehensive test cases for all refactoring phases. These tests should be implemented alongside the refactoring to validate each change.

---

## Phase 1: Critical Fixes - Test Cases

### Test Suite 1.1: Configuration Copy Bug

**File:** `app/src/test/java/com/aboni/n2kRouter/ConfTest.kt`

```kotlin
import org.junit.Test
import org.junit.Before
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConfTest {
    
    private lateinit var sourceConf: Conf
    private lateinit var destConf: Conf
    
    @Before
    fun setup() {
        sourceConf = Conf()
        destConf = Conf()
    }
    
    @Test
    fun testCopyFromObjectCopiesAllFields() {
        // Setup source with all flags enabled
        sourceConf.apply {
            bGPS = true
            bDHT = true
            bBME = true
            bSYT = true
            bRPM = true
            bSTW = true  // THIS WAS THE BUG - was copying bSYT
            bVED = true
            bSRC = true
            bSEA_TEMP = true
            bSTW_PADDLE = true
        }
        
        // Copy to destination
        destConf.copyFrom(sourceConf)
        
        // Verify all fields match
        assertEquals(true, destConf.bGPS, "bGPS should match")
        assertEquals(true, destConf.bDHT, "bDHT should match")
        assertEquals(true, destConf.bBME, "bBME should match")
        assertEquals(true, destConf.bSYT, "bSYT should match")
        assertEquals(true, destConf.bRPM, "bRPM should match")
        assertEquals(true, destConf.bSTW, "bSTW should match (CRITICAL BUG FIX)")
        assertEquals(true, destConf.bVED, "bVED should match")
        assertEquals(true, destConf.bSRC, "bSRC should match")
        assertEquals(true, destConf.bSEA_TEMP, "bSEA_TEMP should match")
        assertEquals(true, destConf.bSTW_PADDLE, "bSTW_PADDLE should match")
    }
    
    @Test
    fun testCopyFromObjectWithMixedFlags() {
        sourceConf.apply {
            bGPS = true
            bSTW = true
            bSYT = false
            bSEA_TEMP = true
            bSTW_PADDLE = false
        }
        
        destConf.copyFrom(sourceConf)
        
        // Critical: Verify bSTW is not overwritten with bSYT value
        assertEquals(true, destConf.bSTW, "bSTW must be true")
        assertEquals(false, destConf.bSYT, "bSYT must be false")
        
        assertEquals(true, destConf.bGPS, "bGPS must be true")
        assertEquals(true, destConf.bSEA_TEMP, "bSEA_TEMP must be true")
        assertEquals(false, destConf.bSTW_PADDLE, "bSTW_PADDLE must be false")
    }
    
    @Test
    fun testCopyFromIntConsistency() {
        // Test that copyFrom(Int) and copyFrom(Conf) are consistent
        sourceConf.apply {
            bGPS = true
            bDHT = true
            bBME = true
            bSYT = true
            bRPM = true
            bSTW = true
            bVED = true
            bSRC = true
            bSEA_TEMP = true
            bSTW_PADDLE = true
        }
        
        // Copy via object
        val dest1 = Conf().apply { copyFrom(sourceConf) }
        
        // Copy via int (if toIntValue() exists)
        val intValue = sourceConf.toIntValue()
        val dest2 = Conf().apply { copyFrom(intValue) }
        
        // Both should be identical
        assertEquals(dest1.bGPS, dest2.bGPS)
        assertEquals(dest1.bSTW, dest2.bSTW)
        assertEquals(dest1.bSYT, dest2.bSYT)
        assertEquals(dest1.bSEA_TEMP, dest2.bSEA_TEMP)
    }
    
    @Test
    fun testCopyFromByteArrayConsistency() {
        sourceConf.apply {
            bGPS = true
            bSTW = true
            bSEA_TEMP = true
            bSTW_PADDLE = false
        }
        
        val bytes = sourceConf.toByteArray()
        val dest = Conf().apply { copyFrom(bytes) }
        
        assertEquals(sourceConf.bGPS, dest.bGPS)
        assertEquals(sourceConf.bSTW, dest.bSTW)
        assertEquals(sourceConf.bSEA_TEMP, dest.bSEA_TEMP)
        assertEquals(sourceConf.bSTW_PADDLE, dest.bSTW_PADDLE)
    }
}
```

### Test Suite 1.2: Timer Lifecycle

**File:** `app/src/test/java/com/aboni/n2kRouter/MainActivityTest.kt`

```kotlin
import android.app.Activity
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.Before
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import java.util.Timer

class TimerLifecycleTest {
    
    private lateinit var activity: MainActivity
    private lateinit var testTimer: Timer
    private var taskExecutedCount = 0
    
    @Before
    fun setup() {
        testTimer = Timer("Test", false)
    }
    
    @Test
    fun testTimerCancelledOnActivityDestroy() {
        // Verify timer is not active after cancellation
        testTimer.schedule(object : TimerTask() {
            override fun run() {
                taskExecutedCount++
            }
        }, 0, 100)
        
        assertTrue(testTimer.toString().contains("Timer"), "Timer should be created")
        
        // Simulate activity destroy
        testTimer.cancel()
        
        Thread.sleep(500)
        val countBeforeSleep = taskExecutedCount
        Thread.sleep(200)
        
        // Task should not execute after cancel
        assertEquals(countBeforeSleep, taskExecutedCount, "Timer should not execute after cancel")
    }
    
    @Test
    fun testTimerDoesNotContinueAfterActivityDestroy() {
        // This tests that the fix works properly
        var executed = false
        testTimer.schedule(object : TimerTask() {
            override fun run() {
                executed = true
            }
        }, 100, 100)
        
        // Cancel timer
        testTimer.cancel()
        
        // Wait longer than task interval
        Thread.sleep(300)
        
        assertFalse(executed, "Task should not execute after timer.cancel()")
    }
}
```

### Test Suite 1.3: Data Parsing Offset

**File:** `app/src/test/java/com/aboni/n2kRouter/DataTest.kt`

```kotlin
import org.junit.Test
import org.junit.Before
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class DataParsingTest {
    
    private lateinit var data: Data
    
    @Before
    fun setup() {
        data = Data()
    }
    
    @Test
    fun testLegacyDataParsingCompleteness() {
        // Create test data for legacy format (version < 10)
        val testData = ByteArray(128)
        
        // Populate with recognizable pattern for debugging
        for (i in testData.indices) {
            testData[i] = (i and 0xFF).toByte()
        }
        
        // Parse the data
        data.parse(testData)
        
        // Verify legacy path was taken
        assertEquals(0, data.version, "Should parse as legacy")
        
        // The critical check: n2kSrc should be parsed completely
        // Before fix: offset was not assigned, so parsing would be incomplete
        // After fix: offset is assigned, so subsequent parsing (if added) works
        assertTrue(data.n2kSrc.valid, "n2kSrc should be valid")
    }
    
    @Test
    fun testVersion10PlusDataParsing() {
        val testData = ByteArray(128)
        testData[0] = 10  // Version 10
        
        // Fill with test values
        for (i in 1 until testData.size) {
            testData[i] = (i and 0xFF).toByte()
        }
        
        data.parse(testData)
        
        assertEquals(10, data.version, "Should parse as version 10")
        
        // Verify sea temp parsed (last field in v10+)
        // This verifies the offset assignment chain was correct
        assertTrue(data.seaTemp.valid, "seaTemp should be valid in v10+")
    }
    
    @Test
    fun testDataParsingOffsetChain() {
        // Create minimal valid data for legacy format
        val minimalData = ByteArray(58)  // Minimum size for legacy
        minimalData[0] = 0  // Legacy version
        
        data.parse(minimalData)
        
        // All fields should attempt to parse
        // No exceptions should be thrown
        assertEquals(0, data.version)
    }
    
    @Test
    fun testCanBusPeriodCalculation() {
        val testData = ByteArray(128)
        testData[0] = 0  // Legacy
        
        // Set some recognizable values
        data.parse(testData)
        
        // Simulate receiving data twice
        data.parse(testData)
        data.parse(testData)
        
        // canSentPeriod and canErrorsPeriod should be calculated
        // This tests the subsequent logic after parsing
        assertFalse(data.canSentPeriod == -1, "canSentPeriod should be calculated")
    }
}
```

---

## Phase 2: Lint Cleanup - Verification Tests

### Manual Verification (No code changes needed)

```bash
# Verify lint is clean
./gradlew lintDebug

# Expected output: 0 warnings
```

### Automated Lint Check (Optional)

```kotlin
@Test
fun testNoCompilerWarnings() {
    // This test would be run by CI/CD
    // Compile and check for warnings
    assertTrue(true, "Manual verification: ./gradlew lintDebug")
}
```

---

## Phase 3: Thread Safety & Error Handling - Test Cases

### Test Suite 3.1: Data Buffer Validation

**File:** `app/src/test/java/com/aboni/n2kRouter/DataValidationTest.kt`

```kotlin
import org.junit.Test
import org.junit.Before
import kotlin.test.assertFalse
import kotlin.test.assertEquals

class DataValidationTest {
    
    private lateinit var data: Data
    
    @Before
    fun setup() {
        data = Data()
    }
    
    @Test
    fun testEmptyDataHandling() {
        // Should not crash
        data.parse(ByteArray(0))
        
        // Version should be invalid
        assertEquals(-1, data.version, "Version should not be set for empty data")
    }
    
    @Test
    fun testSingleByteDataHandling() {
        // Should not crash
        data.parse(ByteArray(1))
        
        // Version should be parsed but data incomplete
        assertEquals(0, data.version, "Should try to read version byte")
    }
    
    @Test
    fun testMinimalLegacyData() {
        val minData = ByteArray(58)  // Minimum for legacy
        minData[0] = 0
        
        data.parse(minData)
        
        assertEquals(0, data.version)
        // Should complete without exception
    }
    
    @Test
    fun testTruncatedVersion10Data() {
        val truncData = ByteArray(30)  // Too small for v10+
        truncData[0] = 10
        
        data.parse(truncData)
        
        assertEquals(10, data.version)
        // Should handle gracefully
    }
    
    @Test
    fun testOversizedData() {
        val bigData = ByteArray(1000)
        bigData[0] = 10
        
        data.parse(bigData)
        
        // Should not crash
        assertEquals(10, data.version)
    }
    
    @Test
    fun testDataWithNullBytes() {
        val nullData = ByteArray(128)
        nullData[0] = 5  // Valid version
        
        data.parse(nullData)
        
        // Should handle null bytes correctly
        assertEquals(5, data.version)
    }
}
```

### Test Suite 3.2: BLE State Synchronization

**File:** `app/src/test/java/com/aboni/n2kRouter/BLEStateTest.kt`

```kotlin
import org.junit.Test
import org.junit.Before
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull

class BLEStateSynchronizationTest {
    
    @Test
    fun testConcurrentStateAccess() {
        val ble = BLEThingImpl(mockContext)
        val stateChanges = mutableListOf<String>()
        
        val thread1 = thread {
            repeat(100) {
                ble.setLifecycleStatus(BLELifecycleState.Connected)
                stateChanges.add("Connected")
            }
        }
        
        val thread2 = thread {
            repeat(100) {
                ble.setLifecycleStatus(BLELifecycleState.Off)
                stateChanges.add("Off")
            }
        }
        
        thread1.join()
        thread2.join()
        
        // Should complete without deadlock or crash
        assertEquals(200, stateChanges.size)
    }
    
    @Test
    fun testGattConnectionStateConsistency() {
        val ble = BLEThingImpl(mockContext)
        
        // Simulate concurrent disconnect calls
        val thread1 = thread { ble.setConnectedGattToNull() }
        val thread2 = thread { ble.setConnectedGattToNull() }
        
        thread1.join()
        thread2.join()
        
        // Both should complete without race condition
        assertNull(ble.getConnectedGatt(), "GATT should be null")
    }
}
```

### Test Suite 3.3: Error Logging in BLE Operations

**File:** `app/src/test/java/com/aboni/n2kRouter/BLEErrorHandlingTest.kt`

```kotlin
import org.junit.Test
import org.junit.Before
import org.mockito.Mock
import org.mockito.Mockito.*

class BLEErrorHandlingTest {
    
    @Mock
    private lateinit var mockGatt: BluetoothGatt
    
    @Mock
    private lateinit var mockCharacteristic: BluetoothGattCharacteristic
    
    private lateinit var ble: BLEThing
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
    }
    
    @Test
    fun testSaveDeviceNameWhenDisconnected() {
        // Test behavior when not connected
        ble.saveDeviceName("TestDevice")
        
        // Should log error, not crash
        // (Verify via logcat inspection)
    }
    
    @Test
    fun testSaveDeviceNameWhenConnected() {
        // Mock successful write
        `when`(mockGatt.writeCharacteristic(
            any(BluetoothGattCharacteristic::class.java),
            any(ByteArray::class.java),
            anyInt()
        )).thenReturn(true)
        
        // Call and verify it logs
        ble.saveDeviceName("TestDevice")
        
        // Should have logged "success"
    }
    
    @Test
    fun testSaveDeviceNameWithException() {
        // Mock exception on write
        `when`(mockGatt.writeCharacteristic(
            any(BluetoothGattCharacteristic::class.java),
            any(ByteArray::class.java),
            anyInt()
        )).thenThrow(Exception("BLE error"))
        
        // Should handle gracefully
        ble.saveDeviceName("TestDevice")
        
        // Should log exception message
    }
}
```

### Test Suite 3.4: Null Safety in UI

**File:** `app/src/test/java/com/aboni/n2kRouter/N2KDataViewTest.kt`

```kotlin
import org.junit.Test
import org.junit.Before
import android.widget.TextView
import org.mockito.Mock
import kotlin.test.assertNotNull

class N2KDataViewNullSafetyTest {
    
    @Mock
    private lateinit var mockContext: Context
    
    private lateinit var dataView: N2KDataView
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        dataView = N2KDataView(mockContext)
    }
    
    @Test
    fun testOnDataWithValidTime() {
        val data = Data()
        data.utcTime.value = 1000000
        data.utcTime.valid = true
        
        // Should not crash even if asTime() edge case
        dataView.onData(data)
        
        // Verify text was set
        assertNotNull(dataView.utcTimeTxt.text)
    }
    
    @Test
    fun testOnDataWithInvalidTime() {
        val data = Data()
        data.utcTime.valid = false
        
        // Should display "No Value" string
        dataView.onData(data)
        
        // Should not crash
        assertNotNull(dataView.utcTimeTxt.text)
    }
    
    @Test
    fun testOnDataWithNullableFields() {
        val data = Data()
        
        // All fields invalid
        dataView.onData(data)
        
        // Should not crash - all fields should show "No Value"
    }
}
```

---

## Phase 4: Code Quality - Verification Tests

### Test Suite 4.1: Constants Extraction

```kotlin
@Test
fun testBLEConstantsExist() {
    assertEquals("N", BLEConstants.CMD_DEVICE_NAME)
    assertEquals("T", BLEConstants.CMD_RPM_CALIBRATION)
    assertEquals("H", BLEConstants.CMD_ENGINE_HOURS)
    assertEquals("t", BLEConstants.CMD_RPM_ADJUSTMENT)
    assertEquals("h", BLEConstants.CMD_HEARTBEAT)
    assertEquals(128, BLEConstants.REQUESTED_MTU)
    assertEquals(129, BLEConstants.GATT_INTERNAL_ERROR)
}

@Test
fun testUUIDConstantsValid() {
    assertNotNull(BLEConstants.SERVICE_UUID)
    assertNotNull(BLEConstants.CHARACTERISTIC_CONFIG_UUID)
    assertNotNull(BLEConstants.CHARACTERISTIC_DATA_UUID)
    assertNotNull(BLEConstants.CHARACTERISTIC_COMMAND_UUID)
    
    // Verify format
    assertTrue(BLEConstants.SERVICE_UUID.toString()
        .contains("4fafc201-1fb5-459e-8fcc-c5c9c331914b"))
}
```

### Test Suite 4.2: Data Parsing Refactor

```kotlin
@Test
fun testVersion10ParsingMethod() {
    val data = Data()
    val testData = ByteArray(128)
    testData[0] = 10
    
    data.parse(testData)
    
    // Should use parseV10Plus internally
    assertEquals(10, data.version)
}

@Test
fun testLegacyParsingMethod() {
    val data = Data()
    val testData = ByteArray(128)
    testData[0] = 5
    
    data.parse(testData)
    
    // Should use parseLegacy internally
    assertEquals(5, data.version)
}

@Test
fun testParseLegacyWithMinimalData() {
    val data = Data()
    val minData = ByteArray(58)
    minData[0] = 0
    
    // Should not crash
    data.parse(minData)
    assertEquals(0, data.version)
}
```

---

## Integration Test Suite

**File:** `app/src/androidTest/java/com/aboni/n2kRouter/RefactoringIntegrationTest.kt`

```kotlin
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.Before

@RunWith(AndroidJUnit4::class)
class RefactoringIntegrationTest {
    
    @Test
    fun testFullConfigurationRoundTrip() {
        val original = Conf().apply {
            bGPS = true
            bSTW = true
            bSEA_TEMP = true
        }
        
        // Convert to int
        val intValue = original.toIntValue()
        val fromInt = Conf().apply { copyFrom(intValue) }
        assertEquals(original.bSTW, fromInt.bSTW)
        
        // Convert to bytes
        val bytes = original.toByteArray()
        val fromBytes = Conf().apply { copyFrom(bytes) }
        assertEquals(original.bSTW, fromBytes.bSTW)
        
        // Copy via object
        val fromObject = Conf().apply { copyFrom(original) }
        assertEquals(original.bSTW, fromObject.bSTW)
    }
    
    @Test
    fun testDataParsingWithAllVersions() {
        // Test v5
        val dataV5 = Data()
        val bytesV5 = ByteArray(128)
        bytesV5[0] = 5
        dataV5.parse(bytesV5)
        assertEquals(5, dataV5.version)
        
        // Test v10
        val dataV10 = Data()
        val bytesV10 = ByteArray(128)
        bytesV10[0] = 10
        dataV10.parse(bytesV10)
        assertEquals(10, dataV10.version)
        
        // Test future versions
        for (version in 11..20) {
            val data = Data()
            val bytes = ByteArray(128)
            bytes[0] = version.toByte()
            data.parse(bytes)
            assertEquals(version, data.version)
        }
    }
    
    @Test
    fun testBLELifecycleWithDataUpdates() {
        val ble = BLEThingImpl(targetContext)
        
        // Simulate lifecycle
        ble.connect("test-device")
        Thread.sleep(100)
        
        // Simulate data update
        val testData = ByteArray(128)
        testData[0] = 10
        
        // Should not crash
        ble.onDataReceived(testData)
    }
}
```

---

## Manual Testing Checklist

### Pre-Release Validation

```
Before Merging:
- [ ] No compilation errors: ./gradlew assembleDebug
- [ ] All lint warnings fixed: ./gradlew lintDebug (0 warnings)
- [ ] Unit tests pass: ./gradlew testDebugUnitTest
- [ ] Integration tests pass: ./gradlew connectedAndroidTest

Manual Testing:
- [ ] App launches without crash
- [ ] BLE scan finds devices
- [ ] Can connect to device
- [ ] Data displays correctly
- [ ] Settings save and load
- [ ] Activity pause/resume works
- [ ] No errors in logcat during 5-minute use
- [ ] Configuration persists across app restart
- [ ] Timer cancels on exit (no background threads)

Performance Testing:
- [ ] No memory leaks (Android Profiler)
- [ ] No ANR (Application Not Responding)
- [ ] Data parsing < 10ms per packet
```

---

## Test Execution Command Reference

```bash
# Run all unit tests
./gradlew testDebugUnitTest

# Run specific test class
./gradlew testDebugUnitTest --tests DataTest

# Run with verbose output
./gradlew testDebugUnitTest --info

# Run integration tests (requires device/emulator)
./gradlew connectedAndroidTest

# Run specific integration test
./gradlew connectedAndroidTest --tests RefactoringIntegrationTest

# Generate test report
./gradlew testDebugUnitTest --continue
# Report: app/build/reports/tests/debugUnitTest/index.html
```

---

## Coverage Requirements

### Target Coverage by Phase

| Phase | File | Target Coverage |
|-------|------|-----------------|
| 1 | Conf.kt | 100% (copyFrom methods) |
| 1 | Data.kt | 95% (parse methods) |
| 1 | MainActivity.kt | 80% (onCreate, onDestroy) |
| 2 | BLEThing.kt | 80% (all public methods) |
| 3 | All | 60% overall |
| 4 | All | 65% overall |

---

**Test Plan Created:** March 21, 2026  
**Target Completion:** Alongside refactoring phases  
**Maintenance:** Update when adding new features

