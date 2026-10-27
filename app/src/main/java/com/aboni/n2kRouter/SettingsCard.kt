package com.aboni.n2kRouter

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.aboni.marine.link.CommandResult
import com.aboni.marine.ui.settings.ServiceSection
import com.aboni.marine.ui.settings.SettingRow
import com.aboni.marine.ui.settings.SettingSwitch
import kotlin.reflect.KMutableProperty1

/** A service of the router: its switch and the bit in [Conf] it stands for. */
enum class Service(val label: Int, val bit: KMutableProperty1<Conf, Boolean>) {
    GPS(R.string.gps_switch, Conf::bGPS),
    SysTime(R.string.time_switch, Conf::bSYT),
    STW(R.string.stw_switch, Conf::bSTW),
    BME(R.string.bme_switch, Conf::bBME),
    DHT(R.string.dht_switch, Conf::bDHT),
    RPM(R.string.rpm_switch, Conf::bRPM),
    VED(R.string.ved_switch, Conf::bVED),
    STWPaddle(R.string.stw_paddle_switch, Conf::bSTW_PADDLE),
    SeaTemp(R.string.water_temp_switch, Conf::bSEA_TEMP),
    KeepN2KSrc(R.string.keep_n2k_switch, Conf::bSRC),
    Log(R.string.log_switch, Conf::bLOG),
}

/** A value the router stores, set from its own row. */
enum class Setting(val label: Int, val keyboard: KeyboardType) {
    DeviceName(R.string.device_name_edit_label, KeyboardType.Text),
    EngineHours(R.string.engine_hours_edit_label, KeyboardType.Text),
    RPMCalibration(R.string.rpm_calibration_edit_label, KeyboardType.Number),
    RPMAdjustment(R.string.config_rpm_adjustment, KeyboardType.Decimal),
    BatteryCapacity(R.string.battery_capacity_edit_label, KeyboardType.Number),
    STWAdjustment(R.string.stw_adjustment_edit_label, KeyboardType.Decimal),
    STWAlpha(R.string.stw_alpha_edit_label, KeyboardType.Decimal),
    SeaTempAdjustment(R.string.sea_temp_adjustment_edit_label, KeyboardType.Decimal),
    SeaTempAlpha(R.string.sea_temp_alpha_edit_label, KeyboardType.Decimal),
}

/** The expandable groups of the card, in order. */
enum class Section { System, GPS, Meteo, RPM, VED, STWPaddle, SeaTemp }

private fun Conf.copy(change: Conf.() -> Unit = {}): Conf = Conf().also { it.copyFrom(this); it.change() }

/**
 * The Settings card's state, kept outside the card so typed values and open sections survive swiping away.
 * Services: [requested] is what the switches show, [device] what the router reports it runs (null when not
 * connected). A flipped switch goes to the router right away. Fed by the router's listener on the UI thread.
 */
class SettingsState(private val context: Context, private val ble: BLEThing) : BLEN2KListener {
    var connected by mutableStateOf(false)
        private set
    var requested by mutableStateOf(Conf())
        private set
    var device by mutableStateOf<Conf?>(null)
        private set

    /** What the user typed in each row. */
    val text = mutableStateMapOf<Setting, String>()
    /** Each row's value as the router reports it; missing means no value. */
    val deviceValue = mutableStateMapOf<Setting, String>()
    val expanded = mutableStateMapOf<Section, Boolean>()

    /** The first services reported after connecting set the switches; later ones only say what the router runs. */
    private var resetServices = true

    init {
        ble.addListener(this)
    }

    fun isOn(s: Service) = s.bit.get(requested)

    /** The router runs the service. */
    fun isActive(s: Service) = device?.let { s.bit.get(it) } == true

    /** The switch asks for something the router doesn't run yet. */
    fun isPending(s: Service) = device?.let { s.bit.get(it) != isOn(s) } == true

    fun toggle(s: Service, on: Boolean) {
        requested = requested.copy { s.bit.set(this, on) }
        ble.saveConfiguration(requested)
    }

    /** Sends the typed value of [s]; returns why it was refused, or null when it went out. */
    fun set(s: Setting): String? {
        val t = text[s].orEmpty().trim()
        val sent = when (s) {
            Setting.DeviceName -> { ble.saveDeviceName(t); true }
            Setting.EngineHours -> parseEngineHours(t)?.let { (h, m) -> ble.saveEngineHours(h, m) } != null
            Setting.RPMCalibration -> t.toIntOrNull()?.let(ble::saveRPMCalibration) != null
            Setting.BatteryCapacity -> parseBatteryCapacity(t)?.let(ble::saveBatteryCapacity) != null
            Setting.RPMAdjustment -> t.toDoubleOrNull()?.let(ble::saveRPMAdjustment) != null
            Setting.STWAdjustment -> t.toDoubleOrNull()?.let(ble::saveSTWAdjustment) != null
            Setting.STWAlpha -> t.toDoubleOrNull()?.let(ble::saveSTWAlpha) != null
            Setting.SeaTempAdjustment -> t.toDoubleOrNull()?.let(ble::saveSeaTempAdjustment) != null
            Setting.SeaTempAlpha -> t.toDoubleOrNull()?.let(ble::saveSeaTempAlpha) != null
        }
        return if (sent) null else context.getString(R.string.invalid_value, context.getString(s.label))
    }

    override fun onStatus(status: BLELifecycleState, scanning: Boolean) {
        connected = status == BLELifecycleState.Connected
        if (connected) {
            val name = ble.getConnectedDevice()?.name ?: ""
            text[Setting.DeviceName] = name
            deviceValue[Setting.DeviceName] = name
        } else {
            text[Setting.DeviceName] = ""
            deviceValue.remove(Setting.DeviceName)
            device = null
            resetServices = true
        }
    }

    override fun onConf(conf: Conf) {
        requested = conf.copy()
        device = conf.copy()
    }

    override fun onData(data: Data) {
        val svc = data.getServices()
        if (svc.valid) {
            val c = Conf().apply { copyFrom(svc.value.toInt()) }
            if (resetServices) {
                resetServices = false
                requested = c.copy()
            }
            device = c
        }
        fun show(s: Setting, v: String?) { if (v == null) deviceValue.remove(s) else deviceValue[s] = v }
        fun Data.DoubleValue.fmt(formatId: Int) = if (valid) formatValue(context, formatId, value) else null
        show(Setting.RPMCalibration, if (data.rpm.valid) formatValue(context, R.string.RPM_FORMAT, data.rpm.value) else null)
        show(Setting.EngineHours, if (data.engineHours.valid) formatEngineHours(context, data.engineHours.value) else null)
        show(Setting.RPMAdjustment, data.rpmAdj.fmt(R.string.RPM_ADJ_FORMAT))
        show(Setting.BatteryCapacity, if (data.batteryCapacity.valid)
            formatValue(context, R.string.BATTERY_CAPACITY_FORMAT, data.batteryCapacity.value) else null)
        show(Setting.STWAlpha, data.stwPaddleAlpha.fmt(R.string.STW_PADDLE_ALPHA_FORMAT))
        show(Setting.STWAdjustment, data.stwPaddleAdjustment.fmt(R.string.STW_PADDLE_ADJUSTMENT_FORMAT))
        show(Setting.SeaTempAlpha, data.seaTempAlpha.fmt(R.string.SEA_TEMP_ALPHA_FORMAT))
        show(Setting.SeaTempAdjustment, data.seaTempAdjustment.fmt(R.string.SEA_TEMP_ADJUSTMENT_FORMAT))
    }

    override fun onScan(device: DeviceItem) {}
    override fun onRssi(connectedDevice: DeviceItem, rssi: Int) {}
    override fun onCommandResult(result: CommandResult) {}
}

/** Engine hours typed as "h:mm"; null if it isn't. */
fun parseEngineHours(text: String): Pair<Int, Int>? {
    val t = text.trim().split(":")
    if (t.size != 2) return null
    val h = t[0].toIntOrNull() ?: return null
    val m = t[1].toIntOrNull() ?: return null
    return h to m
}

@Composable
fun SettingsCard(state: SettingsState) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Group(state, Section.System, R.string.system_section_title) {
            Row(state, Setting.DeviceName)
            Switch(state, Service.KeepN2KSrc)
            Switch(state, Service.Log)
        }
        Group(state, Section.GPS, toggle = Service.GPS) {
            Switch(state, Service.SysTime)
            Switch(state, Service.STW)
        }
        Group(state, Section.Meteo, R.string.meteo_section_title) {
            Switch(state, Service.BME)
            Switch(state, Service.DHT)
        }
        Group(state, Section.RPM, toggle = Service.RPM) {
            Row(state, Setting.EngineHours)
            Row(state, Setting.RPMCalibration)
            Row(state, Setting.RPMAdjustment)
        }
        Group(state, Section.VED, toggle = Service.VED) {
            Row(state, Setting.BatteryCapacity)
        }
        Group(state, Section.STWPaddle, toggle = Service.STWPaddle) {
            Row(state, Setting.STWAdjustment)
            Row(state, Setting.STWAlpha)
        }
        Group(state, Section.SeaTemp, toggle = Service.SeaTemp) {
            Row(state, Setting.SeaTempAdjustment)
            Row(state, Setting.SeaTempAlpha)
        }
    }
}

/** A section: a plain group under [title], or the service [toggle] with its settings. */
@Composable
private fun Group(
    state: SettingsState,
    section: Section,
    title: Int? = null,
    toggle: Service? = null,
    content: @Composable () -> Unit,
) {
    ServiceSection(
        title = stringResource(toggle?.label ?: title!!),
        expanded = state.expanded[section] == true,
        onExpandedChange = { state.expanded[section] = it },
        checked = toggle?.let(state::isOn),
        onCheckedChange = { on -> toggle?.let { state.toggle(it, on) } },
        enabled = state.connected,
        pending = toggle?.let(state::isPending) == true,
        active = toggle?.let(state::isActive) == true,
    ) { content() }
}

@Composable
private fun Switch(state: SettingsState, s: Service) {
    SettingSwitch(
        stringResource(s.label),
        checked = state.isOn(s),
        onCheckedChange = { state.toggle(s, it) },
        enabled = state.connected,
        pending = state.isPending(s),
    )
}

@Composable
private fun Row(state: SettingsState, s: Setting) {
    SettingRow(
        stringResource(s.label),
        deviceValue = state.deviceValue[s],
        text = state.text[s].orEmpty(),
        onTextChange = { state.text[s] = it },
        onSet = { state.set(s) },
        enabled = state.connected,
        keyboardType = s.keyboard,
    )
}
