package com.aboni.n2kRouter

import android.content.Context
import android.content.res.ColorStateList
import android.widget.Toast
import android.widget.Toast.LENGTH_LONG
import com.google.android.material.switchmaterial.SwitchMaterial
import androidx.core.graphics.toColorInt

class N2KSettingsView(context: Context, ble: BLEThing?) : N2KCardPage(context, ble) {

    constructor(context: Context): this(context, null)

    //region widgets
    private val sectionGPS: ServiceSectionView
        get() = findViewById(R.id.sectionGPS)
    private val sectionRPM: ServiceSectionView
        get() = findViewById(R.id.sectionRPM)
    private val sectionVED: ServiceSectionView
        get() = findViewById(R.id.sectionVED)
    private val sectionSTWPaddle: ServiceSectionView
        get() = findViewById(R.id.sectionSTWPaddle)
    private val sectionSeaTemp: ServiceSectionView
        get() = findViewById(R.id.sectionSeaTemp)

    private val switchGPS: SwitchMaterial
        get() = sectionGPS.toggle
    private val switchBME: SwitchMaterial
        get() = findViewById(R.id.checkBoxEnableBME)
    private val switchDHT: SwitchMaterial
        get() = findViewById(R.id.checkBoxEnableDHT)
    private val switchSTW: SwitchMaterial
        get() = findViewById(R.id.checkBoxEnableSTW)
    private val switchVED: SwitchMaterial
        get() = sectionVED.toggle
    private val switchSYT: SwitchMaterial
        get() = findViewById(R.id.checkBoxEnableSysTime)
    private val switchRPM: SwitchMaterial
        get() = sectionRPM.toggle
    private val switchSTWPaddle: SwitchMaterial
        get() = sectionSTWPaddle.toggle
    private val switchWaterTemp: SwitchMaterial
        get() = sectionSeaTemp.toggle
    private val switchKeepN2KSrc: SwitchMaterial
        get() = findViewById(R.id.checkBoxKeepN2KSrc)
    private val switchLog: SwitchMaterial
        get() = findViewById(R.id.checkBoxEnableLog)

    private val rowDeviceName: SettingRowView
        get() = findViewById(R.id.rowDeviceName)
    private val rowEngineHours: SettingRowView
        get() = findViewById(R.id.rowEngineHours)
    private val rowRPMCalibration: SettingRowView
        get() = findViewById(R.id.rowRPMCalibration)
    private val rowRPMAdjustment: SettingRowView
        get() = findViewById(R.id.rowRPMAdjustment)
    private val rowBatteryCapacity: SettingRowView
        get() = findViewById(R.id.rowBatteryCapacity)
    private val rowSTWAdjustment: SettingRowView
        get() = findViewById(R.id.rowSTWAdjustment)
    private val rowSTWAlpha: SettingRowView
        get() = findViewById(R.id.rowSTWAlpha)
    private val rowSeaTempAdjustment: SettingRowView
        get() = findViewById(R.id.rowSeaTempAdjustment)
    private val rowSeaTempAlpha: SettingRowView
        get() = findViewById(R.id.rowSeaTempAlpha)

    private val allRows: List<SettingRowView>
        get() = listOf(
            rowDeviceName, rowEngineHours, rowRPMCalibration, rowRPMAdjustment, rowBatteryCapacity,
            rowSTWAdjustment, rowSTWAlpha, rowSeaTempAdjustment, rowSeaTempAlpha
        )

    private val allToggles: List<SwitchMaterial>
        get() = listOf(
            switchGPS, switchSYT, switchSTW, switchDHT, switchBME, switchRPM, switchVED,
            switchSTWPaddle, switchWaterTemp, switchKeepN2KSrc, switchLog
        )
    //endregion

    private var switchTintList: ColorStateList? = null
    private val switchTintListDirty: ColorStateList = ColorStateList(
        arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked)
        ),
        intArrayOf(
            // Color when the switch is checked
            "#FF0000".toColorInt(),
            // Color when the switch is unchecked
            "#AA0000".toColorInt()
        )
    )

    init {
        initView()
        attachCard(R.layout.settings_view)
        setTitleResource(R.string.settings_card_title)
        setImageResource(android.R.drawable.ic_menu_edit)

        switchTintList = switchGPS.trackTintList
        enableButtons(false)
        allToggles.forEach { it.setOnCheckedChangeListener { _, _ -> onToggleChanged() } }
        rowDeviceName.setOnSaveListener { ble?.saveDeviceName(rowDeviceName.text) }
        rowEngineHours.setOnSaveListener { onSaveEngineHoursClick() }
        rowRPMCalibration.setOnSaveListener {
            saveInt(rowRPMCalibration) { ble?.saveRPMCalibration(it) }
        }
        rowRPMAdjustment.setOnSaveListener {
            saveDecimal(rowRPMAdjustment) { ble?.saveRPMAdjustment(it) }
        }
        rowBatteryCapacity.setOnSaveListener {
            val ah = parseBatteryCapacity(rowBatteryCapacity.text)
            if (ah == null) showInvalidValue(rowBatteryCapacity) else ble?.saveBatteryCapacity(ah)
        }
        rowSTWAdjustment.setOnSaveListener {
            saveDecimal(rowSTWAdjustment) { ble?.saveSTWAdjustment(it) }
        }
        rowSTWAlpha.setOnSaveListener {
            saveDecimal(rowSTWAlpha) { ble?.saveSTWAlpha(it) }
        }
        rowSeaTempAdjustment.setOnSaveListener {
            saveDecimal(rowSeaTempAdjustment) { ble?.saveSeaTempAdjustment(it) }
        }
        rowSeaTempAlpha.setOnSaveListener {
            saveDecimal(rowSeaTempAlpha) { ble?.saveSeaTempAlpha(it) }
        }
    }

    private fun enableButtons(enable: Boolean) {
        allToggles.forEach { it.isEnabled = enable }
        allRows.forEach { it.setSaveButtonEnabled(enable) }
    }

    var resetServices = true

    /** Services as last reported by the device, null when not connected. */
    private var deviceConf: Conf? = null

    /** True while the switches are being set from the device, so that it is not echoed back as a change. */
    private var showingDeviceConf = false

    override fun onStatus(status: BLELifecycleState, scanning: Boolean) {
        post {
            enableButtons(status == BLELifecycleState.Connected)
            if (status == BLELifecycleState.Connected) {
                val name = ble?.getConnectedDevice()?.name ?: ""
                rowDeviceName.text = name
                rowDeviceName.valueText = name
            } else {
                rowDeviceName.text = ""
                rowDeviceName.valueText = ""
                deviceConf = null
                syncSectionsActive(null)
                resetServices = true
            }
        }
    }

    override fun onData(data: Data) {
        post {
            val svc = data.getServices()
            if (svc.valid) {
                val c = Conf()
                c.copyFrom(svc.value.toInt())
                if (resetServices) {
                    resetServices = false
                    showConf(c)
                }
                onDeviceConf(c)
            }
            val noValue = noValueStr(context)
            rowRPMCalibration.valueText = if (data.rpm.valid) formatValue(
                context,
                R.string.RPM_FORMAT,
                data.rpm.value
            ) else noValue
            rowEngineHours.valueText = if (data.engineHours.valid) formatEngineHours(
                context,
                data.engineHours.value
            ) else noValue
            rowRPMAdjustment.valueText = formatDouble(data.rpmAdj, R.string.RPM_ADJ_FORMAT, noValue)
            rowBatteryCapacity.valueText = if (data.batteryCapacity.valid) formatValue(
                context,
                R.string.BATTERY_CAPACITY_FORMAT,
                data.batteryCapacity.value
            ) else noValue
            rowSTWAlpha.valueText =
                formatDouble(data.stwPaddleAlpha, R.string.STW_PADDLE_ALPHA_FORMAT, noValue)
            rowSTWAdjustment.valueText =
                formatDouble(data.stwPaddleAdjustment, R.string.STW_PADDLE_ADJUSTMENT_FORMAT, noValue)
            rowSeaTempAlpha.valueText =
                formatDouble(data.seaTempAlpha, R.string.SEA_TEMP_ALPHA_FORMAT, noValue)
            rowSeaTempAdjustment.valueText =
                formatDouble(data.seaTempAdjustment, R.string.SEA_TEMP_ADJUSTMENT_FORMAT, noValue)
        }
    }

    override fun onScan(device: DeviceItem) {
       // do nothing
    }

    override fun onConf(conf: Conf) {
        post {
            showConf(conf)
            onDeviceConf(conf)
        }
    }

    private fun formatDouble(v: Data.DoubleValue, formatId: Int, noValue: String): String =
        if (v.valid) formatValue(context, formatId, v.value) else noValue

    /** Services the device runs: they unlock the sections and tell which toggles are still waiting for it. */
    private fun onDeviceConf(c: Conf) {
        deviceConf = c
        syncSectionsActive(c)
        tintIfDirty(switchGPS, c.bGPS)
        tintIfDirty(switchBME, c.bBME)
        tintIfDirty(switchDHT, c.bDHT)
        tintIfDirty(switchSTW, c.bSTW)
        tintIfDirty(switchSYT, c.bSYT)
        tintIfDirty(switchRPM, c.bRPM)
        tintIfDirty(switchVED, c.bVED)
        tintIfDirty(switchKeepN2KSrc, c.bSRC)
        tintIfDirty(switchSTWPaddle, c.bSTW_PADDLE)
        tintIfDirty(switchWaterTemp, c.bSEA_TEMP)
        tintIfDirty(switchLog, c.bLOG)
    }

    private fun tintIfDirty(s: SwitchMaterial, saved: Boolean) {
        s.trackTintList = if (s.isChecked == saved) switchTintList else switchTintListDirty
    }

    /** Sections of services the device is not running (or all of them, if [saved] is null) get locked. */
    private fun syncSectionsActive(saved: Conf?) {
        sectionGPS.setActive(saved?.bGPS == true)
        sectionRPM.setActive(saved?.bRPM == true)
        sectionVED.setActive(saved?.bVED == true)
        sectionSTWPaddle.setActive(saved?.bSTW_PADDLE == true)
        sectionSeaTemp.setActive(saved?.bSEA_TEMP == true)
    }

    private fun showConf(conf: Conf) {
        showingDeviceConf = true
        try {
            switchBME.isChecked = conf.bBME
            switchDHT.isChecked = conf.bDHT
            switchSYT.isChecked = conf.bSYT
            switchGPS.isChecked = conf.bGPS
            switchRPM.isChecked = conf.bRPM
            switchSTW.isChecked = conf.bSTW
            switchVED.isChecked = conf.bVED
            switchKeepN2KSrc.isChecked = conf.bSRC
            switchSTWPaddle.isChecked = conf.bSTW_PADDLE
            switchWaterTemp.isChecked = conf.bSEA_TEMP
            switchLog.isChecked = conf.bLOG
        } finally {
            showingDeviceConf = false
        }
    }

    /** A toggle was flipped by the user: the new configuration goes to the device right away. */
    private fun onToggleChanged() {
        if (showingDeviceConf) return
        val c = Conf()
        c.bBME = switchBME.isChecked
        c.bDHT = switchDHT.isChecked
        c.bGPS = switchGPS.isChecked
        c.bRPM = switchRPM.isChecked
        c.bSTW = switchSTW.isChecked
        c.bSYT = switchSYT.isChecked
        c.bVED = switchVED.isChecked
        c.bSRC = switchKeepN2KSrc.isChecked
        c.bSTW_PADDLE = switchSTWPaddle.isChecked
        c.bSEA_TEMP = switchWaterTemp.isChecked
        c.bLOG = switchLog.isChecked
        ble?.saveConfiguration(c)
        deviceConf?.let { onDeviceConf(it) }
    }

    private fun onSaveEngineHoursClick() {
        val t = rowEngineHours.text.split(":")
        val h = t.getOrNull(0)?.toIntOrNull()
        val m = t.getOrNull(1)?.toIntOrNull()
        if (t.size != 2 || h == null || m == null) {
            showInvalidValue(rowEngineHours)
            return
        }
        ble?.saveEngineHours(h, m)
    }

    private fun saveInt(row: SettingRowView, save: (Int) -> Unit) {
        val v = row.text.toIntOrNull()
        if (v == null) showInvalidValue(row) else save(v)
    }

    private fun saveDecimal(row: SettingRowView, save: (Double) -> Unit) {
        val v = row.text.toDoubleOrNull()
        if (v == null) showInvalidValue(row) else save(v)
    }

    private fun showInvalidValue(row: SettingRowView) {
        Toast.makeText(context, context.getString(R.string.invalid_value, row.label), LENGTH_LONG).show()
    }
}
