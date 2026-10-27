package com.aboni.n2kRouter

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aboni.marine.ui.theme.MarineTheme
import com.aboni.marine.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class DataCardTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val context get() = rule.activity

    private fun Data.DoubleValue.set(v: Double) { valid = true; value = v }
    private fun Data.IntValue.set(v: Long) { valid = true; value = v }

    @Test fun noDataMeansNoValues() {
        assertEquals(DataReadings(), dataReadings(context, Data()))
    }

    @Test fun valuesAreFormattedAsBefore() {
        val d = Data().apply {
            atmo.set(1013.25); temp.set(21.04); rpm.set(1800); engineHours.set(2 * 3600 + 5 * 60)
            lat.set(43.5); lon.set(-10.25); cog.set(215.0); heap.set(204800)
            n2kSrc.set(22); canActive.set(1); canSentPeriod = 1200; canErrorsPeriod = 3
            gpsFix.set(3)
        }
        val r = dataReadings(context, d)
        assertEquals("1013.3", r.atmo)
        assertEquals("21.0", r.temp)
        assertEquals("1800", r.rpm)
        assertEquals("2:05", r.engineHours)
        assertEquals("043°30.000'N", r.lat)
        assertEquals("010°15.000'W", r.lon)
        assertEquals("215.0°", r.cog)
        assertEquals("200", r.heap)
        assertEquals("22/Ok", r.canSrc)
        assertEquals("1,200/3", r.canStats)
        assertEquals("3D", r.gpsFix)
        assertEquals(null, r.sog)
    }

    @Test fun stateFollowsTheRouter() {
        val ble = FakeBle()
        val state = DataState(context, ble)
        ble.data(Data().apply { rpm.set(900) })
        assertEquals("900", state.readings.rpm)
    }

    @Test fun cardShowsLabelsValuesAndNoValue() {
        rule.setContent {
            MarineTheme(ThemeMode.Night) { DataCard(DataReadings(rpm = "1800")) }
        }
        rule.onNodeWithText("RPM").assertIsDisplayed()
        rule.onNodeWithText("1800").assertIsDisplayed()
        rule.onNodeWithText("Atmo (mbar)").assertIsDisplayed()
    }
}
