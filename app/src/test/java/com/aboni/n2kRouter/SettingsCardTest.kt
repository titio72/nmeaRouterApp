package com.aboni.n2kRouter

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aboni.marine.ui.settings.PENDING_TEXT
import com.aboni.marine.ui.theme.MarineTheme
import com.aboni.marine.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SettingsCardTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val ble = FakeBle()
    private val rpmOn = Conf().apply { bRPM = true }

    private fun state() = SettingsState(rule.activity, ble)

    private fun connected(services: Conf = rpmOn): SettingsState = state().also {
        ble.status(BLELifecycleState.Connected)
        ble.data(dataWithServices(services))
    }

    //region state
    @Test fun connectingShowsTheDeviceName() {
        val s = connected()
        assertEquals("N2K", s.text[Setting.DeviceName])
        assertEquals("N2K", s.deviceValue[Setting.DeviceName])
    }

    @Test fun firstServicesSetTheSwitchesLaterOnesDont() {
        val s = connected()
        assertTrue(s.isOn(Service.RPM))
        ble.data(dataWithServices(Conf()))
        assertTrue(s.isOn(Service.RPM))
        assertFalse(s.isActive(Service.RPM))
        assertTrue(s.isPending(Service.RPM))
    }

    @Test fun flippingASwitchSendsTheWholeConfiguration() {
        val s = connected()
        s.toggle(Service.Log, true)
        assertEquals(listOf("conf 00001000001"), ble.sent)
        assertTrue(s.isPending(Service.Log))
        ble.data(dataWithServices(Conf().apply { bRPM = true; bLOG = true }))
        assertFalse(s.isPending(Service.Log))
    }

    @Test fun disconnectingForgetsTheDevice() {
        val s = connected()
        ble.status(BLELifecycleState.Off)
        assertFalse(s.connected)
        assertNull(s.device)
        assertFalse(s.isActive(Service.RPM))
        assertNull(s.deviceValue[Setting.DeviceName])
    }

    @Test fun validValuesAreSent() {
        val s = connected()
        val typed = mapOf(
            Setting.EngineHours to "12:30", Setting.RPMCalibration to "1200", Setting.RPMAdjustment to "1.25",
            Setting.BatteryCapacity to " 200 ", Setting.STWAdjustment to "0.5", Setting.STWAlpha to "0.2",
            Setting.SeaTempAdjustment to "-1", Setting.SeaTempAlpha to "0.3", Setting.DeviceName to "Boat",
        )
        typed.forEach { (k, v) -> s.text[k] = v; assertNull(s.set(k)) }
        assertEquals(
            listOf("engineHours 12:30", "rpmCal 1200", "rpmAdj 1.25", "battery 200", "stwAdj 0.5", "stwAlpha 0.2",
                "seaTempAdj -1.0", "seaTempAlpha 0.3", "name Boat"),
            ble.sent,
        )
    }

    @Test fun invalidValuesAreRefusedWithTheReason() {
        val s = connected()
        listOf(Setting.EngineHours to "12", Setting.RPMCalibration to "1.5", Setting.BatteryCapacity to "1000",
            Setting.STWAlpha to "abc").forEach { (k, v) ->
            s.text[k] = v
            assertEquals("Invalid value for ${rule.activity.getString(k.label)}", s.set(k))
        }
        assertEquals(emptyList<String>(), ble.sent)
    }

    @Test fun engineHoursParse() {
        assertEquals(12 to 30, parseEngineHours(" 12:30 "))
        assertNull(parseEngineHours("12"))
        assertNull(parseEngineHours("a:30"))
        assertNull(parseEngineHours("1:2:3"))
    }
    //endregion

    //region card
    private fun show(s: SettingsState) = rule.setContent { MarineTheme(ThemeMode.Dark) { SettingsCard(s) } }

    @Test fun nothingCanBeSentWhileDisconnected() {
        show(state())
        rule.onNodeWithText("RPM").assertIsNotEnabled()
    }

    @Test fun anInactiveServiceDoesNotExpand() {
        show(connected(Conf()))
        rule.onNodeWithContentDescription("Show RPM settings").performScrollTo().assertIsNotEnabled()
    }

    @Test fun anActiveServiceExpandsAndItsRowSends() {
        show(connected())
        rule.onNodeWithText("RPM").assertIsOn()
        rule.onNodeWithContentDescription("Show RPM settings").performScrollTo().performClick()
        rule.onNodeWithContentDescription("RPM Cal.").performScrollTo().performTextReplacement("1300")
        rule.onNodeWithContentDescription("Set RPM Cal.").performScrollTo().performClick()
        assertEquals(listOf("rpmCal 1300"), ble.sent)
    }

    @Test fun aRefusedValueIsShownInTheCard() {
        show(connected())
        rule.onNodeWithContentDescription("Show RPM settings").performScrollTo().performClick()
        rule.onNodeWithContentDescription("RPM Cal.").performScrollTo().performTextReplacement("x")
        rule.onNodeWithContentDescription("Set RPM Cal.").performScrollTo().performClick()
        rule.onNodeWithText("Invalid value for RPM Cal.", substring = true).assertIsDisplayed()
    }

    @Test fun aSwitchTheRouterDoesntRunYetSaysSo() {
        show(connected())
        rule.onNodeWithText("System").performClick()
        rule.onNodeWithText("Log").performScrollTo().performClick()
        rule.onNodeWithText(PENDING_TEXT, substring = true).assertIsDisplayed()
    }
    //endregion
}
