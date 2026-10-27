package com.aboni.n2kRouter

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aboni.marine.link.CommandResult
import com.aboni.marine.ui.values.LabeledValue
import com.aboni.marine.ui.values.ValueSize
import com.aboni.marine.ui.values.ValuePanel

/** What the Data card shows, already formatted; null where the router has no value. */
data class DataReadings(
    val atmo: String? = null,
    val temp: String? = null,
    val hum: String? = null,
    val seaTemp: String? = null,
    val rpm: String? = null,
    val engineHours: String? = null,
    val volts: String? = null,
    val current: String? = null,
    val soc: String? = null,
    val heap: String? = null,
    val canStats: String? = null,
    val canSrc: String? = null,
    val lat: String? = null,
    val lon: String? = null,
    val sog: String? = null,
    val stw: String? = null,
    val cog: String? = null,
    val magneticDeviation: String? = null,
    val gpsAccuracy: String? = null,
    val gpsFix: String? = null,
    val utcTime: String? = null,
)

fun dataReadings(context: Context, data: Data): DataReadings {
    fun Data.DoubleValue.fmt(formatId: Int) = if (valid) formatValue(context, formatId, value) else null
    fun Data.IntValue.fmt(formatId: Int) = if (valid) formatValue(context, formatId, value) else null
    return DataReadings(
        atmo = data.atmo.fmt(R.string.ATMO_FORMAT),
        temp = data.temp.fmt(R.string.TEMPERATURE_FORMAT),
        hum = data.hum.fmt(R.string.HUMIDITY_FORMAT),
        seaTemp = data.seaTemp.fmt(R.string.TEMPERATURE_FORMAT),
        rpm = data.rpm.fmt(R.string.RPM_FORMAT),
        engineHours = if (data.engineHours.valid) formatEngineHours(context, data.engineHours.value) else null,
        volts = data.volts.fmt(R.string.VOLTS_FORMAT),
        current = data.current.fmt(R.string.AMPERE_FORMAT),
        soc = data.soc.fmt(R.string.SPEED_FORMAT),
        heap = if (data.heap.valid) formatValue(context, R.string.HEAP_FORMAT, data.heap.value / 1024) else null,
        canStats = if (data.canSentPeriod != -1)
            formatValue(context, R.string.N2K_STATS_FORMAT, data.canSentPeriod, data.canErrorsPeriod) else null,
        canSrc = if (data.n2kSrc.valid) formatValue(context, R.string.N2K_SOURCE_FORMAT,
            data.n2kSrc.value, if (data.canActive.value == 1L) "Ok" else "Ko") else null,
        lat = if (data.lat.valid) formatLatLon(context, data.lat.value, "N", "S") else null,
        lon = if (data.lon.valid) formatLatLon(context, data.lon.value, "E", "W") else null,
        sog = data.sog.fmt(R.string.SOG_FORMAT),
        stw = data.stwPaddle.fmt(R.string.SPEED_FORMAT),
        cog = data.cog.fmt(R.string.COG_FORMAT),
        magneticDeviation = data.magneticDeviation.fmt(R.string.MAGN_DEVIATION_FORMAT),
        gpsAccuracy = if (data.gpsPrecision.isNaN()) null
            else formatValue(context, R.string.GPS_ACCURACY_FORMAT, data.gpsPrecision),
        gpsFix = if (data.gpsFix.valid) formatGPSFix(context, data.gpsFix.value) else null,
        utcTime = if (data.utcTime.valid && data.utcTime.value > 0) data.utcTime.asTime()?.toString() else null,
    )
}

/** The Data card's state: the router's last readings, formatted. Fed by the router's listener on the UI thread. */
class DataState(private val context: Context, ble: BLEThing) : BLEN2KListener {
    var readings: DataReadings by mutableStateOf(DataReadings())
        private set

    init {
        ble.addListener(this)
    }

    override fun onData(data: Data) {
        readings = dataReadings(context, data)
    }

    override fun onStatus(status: BLELifecycleState, scanning: Boolean) {}
    override fun onConf(conf: Conf) {}
    override fun onScan(device: DeviceItem) {}
    override fun onRssi(connectedDevice: DeviceItem, rssi: Int) {}
    override fun onCommandResult(result: CommandResult) {}
}

/**
 * Meteo and engine, power, router and N2K, then navigation: one panel per group, values in the large size since the
 * card has room. Each value reserves the digits of its widest reading, so nothing shifts as values change; the
 * rows fit a 360dp-wide phone at the normal font size.
 */
@Composable
fun DataCard(r: DataReadings) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ValuePanel {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Value(R.string.data_atmo_pressure_label, r.atmo, 6)
                    Value(R.string.data_temperature_label, r.temp, 5)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Value(R.string.data_humidity_label, r.hum, 5)
                    Value(R.string.data_sea_temp_label, r.seaTemp, 5)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Value(R.string.data_rpm_label, r.rpm, 4)
                    Value(R.string.data_engine_h_label, r.engineHours, 8)
                }
            }
        }
        ValuePanel {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Value(R.string.data_voltage_label, r.volts, 5, monospace = false)
                Value(R.string.data_current_label, r.current, 6, monospace = false)
                Value(R.string.data_soc_label, r.soc, 5, monospace = false)
            }
        }
        ValuePanel {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Value(R.string.data_heap_label, r.heap, 4)
                Value(R.string.data_n2k_msg_label, r.canStats, 8)
                Value(R.string.data_n2k_label, r.canSrc, 6)
            }
        }
        ValuePanel {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Value(R.string.data_latitude_label, r.lat, 12)
                    Value(R.string.data_longitude_label, r.lon, 12)
                    Value(R.string.data_magnetic_deviation_label, r.magneticDeviation, 6)
                    Value(R.string.data_gps_fix_label, r.gpsFix, 3, monospace = false)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Value(R.string.data_sog_label, r.sog, 5)
                    Value(R.string.data_stw_label, r.stw, 5)
                    Value(R.string.data_cog_label, r.cog, 6)
                    Value(R.string.data_gps_accuracy_label, r.gpsAccuracy, 5)
                }
            }
            LabeledValue(
                stringResource(R.string.data_time_utc_label), r.utcTime, Modifier.fillMaxWidth(), size = ValueSize.Large,
            )
        }
    }
}

@Composable
private fun Value(label: Int, value: String?, chars: Int, monospace: Boolean = true) =
    LabeledValue(
        stringResource(label), value,
        valueChars = chars, monospace = monospace, size = ValueSize.Large,
    )
