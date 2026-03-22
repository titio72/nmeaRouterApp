package com.aboni.n2kRouter

import android.content.Context
import android.content.res.ColorStateList
import android.text.Editable
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import android.widget.Toast.LENGTH_LONG
import androidx.compose.runtime.structuralEqualityPolicy
import com.google.android.material.switchmaterial.SwitchMaterial
import androidx.core.graphics.toColorInt

class N2KSettingsView(context: Context, ble: BLEThing?) : N2KCardPage(context, ble) {

    constructor(context: Context): this(context, null)

    //region widgets
    private val buttonSave: ImageButton
        get() = findViewById(R.id.buttonSave)
    private val buttonSaveDeviceName: ImageButton
        get() = findViewById(R.id.buttonSaveDeviceName)
    private val buttonSaveEngineHours: ImageButton
        get() = findViewById(R.id.buttonSaveEngineHours)
    private val buttonSaveRPMAdjustment: ImageButton
        get() = findViewById(R.id.buttonSaveRPMAdjustment)
    private val switchGPS: SwitchMaterial
        get() = findViewById(R.id.checkBoxEnableGPS)
    private val switchBME: SwitchMaterial
        get() = findViewById(R.id.checkBoxEnableBME)
    private val switchDHT: SwitchMaterial
        get() = findViewById(R.id.checkBoxEnableDHT)
    private val switchSTW: SwitchMaterial
        get() = findViewById(R.id.checkBoxEnableSTW)
    private val switchVED: SwitchMaterial
        get() = findViewById(R.id.checkBoxEnableVED)
    private val switchSYT: SwitchMaterial
        get() = findViewById(R.id.checkBoxEnableSysTime)
    private val switchRPM: SwitchMaterial
        get() = findViewById(R.id.checkBoxEnableRPM)
    private val switchKeepN2KSrc: SwitchMaterial
        get() = findViewById(R.id.checkBoxKeepN2KSrc)
    private val switchSTWPaddle: SwitchMaterial
        get() = findViewById(R.id.checkBoxEnableSTWPaddle)
    private val switchWaterTemp: SwitchMaterial
        get() = findViewById(R.id.checkBoxEnableWaterTemp)
    private val editDeviceName: EditText
        get() = findViewById(R.id.editDeviceName)
    private val editEngineHours: EditText
        get() = findViewById(R.id.editEngineHours)
    private val editRPMCalibration: EditText
        get() = findViewById(R.id.editRPMCalibration)
    private val editRPMAdjustment: EditText
        get() = findViewById(R.id.editRPMAdjustment)
    private val engineHTxtView: TextView
        get() = findViewById(R.id.txtEngineH_Settings)
    private val rpmTxtView: TextView
        get() = findViewById(R.id.txtRpm_Settings)
    private val rpmAdjTxtView: TextView
        get() = findViewById(R.id.txtRpmAdj_Settings)
    private val deviceNameTxtView: TextView
        get() = findViewById(R.id.txtDeviceName_Settings)

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
        buttonSave.setOnClickListener { v -> onSaveClick(v) }
        buttonSaveDeviceName.setOnClickListener { onSaveDeviceNameClick() }
        buttonSaveEngineHours.setOnClickListener { onSaveEngineHoursClick() }
        buttonSaveRPMAdjustment.setOnClickListener { onSaveRPMAdjustment() }
    }

    private fun enableButtons(enable: Boolean) {
        buttonSave.isEnabled = enable
        buttonSaveEngineHours.isEnabled = enable
        buttonSaveDeviceName.isEnabled = enable
        buttonSaveRPMAdjustment.isEnabled = enable
    }

    var resetServices = true

    override fun onStatus(status: BLELifecycleState, scanning: Boolean) {
        post {
            enableButtons(status == BLELifecycleState.Connected)
            if (status == BLELifecycleState.Connected) {
                editDeviceName.text = ble?.getConnectedDevice()?.name?.toEditable() ?: "".toEditable()
                deviceNameTxtView.text = ble?.getConnectedDevice()?.name?.toEditable() ?: "".toEditable()
            } else {
                editDeviceName.text = "".toEditable()
                deviceNameTxtView.text = "".toEditable()
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
                    syncConfSwitch(c, false)
                }

                switchGPS.trackTintList =
                    if (switchGPS.isChecked == c.bGPS) switchTintList else switchTintListDirty
                switchBME.trackTintList =
                    if (switchBME.isChecked == c.bBME) switchTintList else switchTintListDirty
                switchDHT.trackTintList =
                    if (switchDHT.isChecked == c.bDHT) switchTintList else switchTintListDirty
                switchSTW.trackTintList =
                    if (switchSTW.isChecked == c.bSTW) switchTintList else switchTintListDirty
                switchSYT.trackTintList =
                    if (switchSYT.isChecked == c.bSYT) switchTintList else switchTintListDirty
                switchRPM.trackTintList =
                    if (switchRPM.isChecked == c.bRPM) switchTintList else switchTintListDirty
                switchVED.trackTintList =
                    if (switchVED.isChecked == c.bVED) switchTintList else switchTintListDirty
                switchKeepN2KSrc.trackTintList =
                    if (switchKeepN2KSrc.isChecked == c.bSRC) switchTintList else switchTintListDirty
                switchSTWPaddle.trackTintList =
                    if (switchSTWPaddle.isChecked == c.bSTW_PADDLE) switchTintList else switchTintListDirty
                switchWaterTemp.trackTintList =
                    if (switchWaterTemp.isChecked == c.bSEA_TEMP) switchTintList else switchTintListDirty

            }
            val noValue = noValueStr(context)
            rpmTxtView.text = if (data.rpm.valid) formatValue(
                context,
                R.string.RPM_FORMAT,
                data.rpm.value
            ) else noValue
            engineHTxtView.text = if (data.engineHours.valid) formatEngineHours(
                context,
                data.engineHours.value
            ) else noValue
            rpmAdjTxtView.text = if (data.rpmAdj.valid) formatValue(
                context,
                R.string.RPM_ADJ_FORMAT,
                data.rpmAdj.value
            ) else noValue
        }
    }

    override fun onScan(device: DeviceItem) {
       // do nothing
    }

    override fun onConf(conf: Conf) {
        post { syncConfSwitch(conf, false) }
    }

    private fun syncConfSwitch(conf: Conf, writeConf: Boolean) {
        if (writeConf) {
            conf.bBME = switchBME.isChecked
            conf.bDHT = switchDHT.isChecked
            conf.bGPS = switchGPS.isChecked
            conf.bRPM = switchRPM.isChecked
            conf.bSTW = switchSTW.isChecked
            conf.bSYT = switchSYT.isChecked
            conf.bVED = switchVED.isChecked
            conf.bSRC = switchKeepN2KSrc.isChecked
            conf.bSTW_PADDLE = switchSTWPaddle.isChecked
            conf.bSEA_TEMP = switchWaterTemp.isChecked
        } else {
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
        }
    }

    private fun onSaveClick(v: View) {
        if (v.id == R.id.buttonSave) {
            val c = Conf()
            syncConfSwitch(c, true)
            ble?.saveConfiguration(c)
        }
    }

    private fun onSaveDeviceNameClick() {
        val n = editDeviceName.text
        ble?.saveDeviceName(n.toString())
    }

    private fun onSaveEngineHoursClick() {
        val n = editEngineHours.text
        val t = n.split(":")
        if (t.size!=2) return
        try {
            ble?.saveEngineHours(t[0].toInt(), t[1].toInt())
        } catch (_: Exception) {
            Toast.makeText(context, "Error reading engine hours $n", LENGTH_LONG).show()
        }
    }

    private fun onSaveRPMAdjustment() {
        try {
            // consider first the calibration value, then the adjustment
            val rpmCal = editRPMCalibration.text
            val rpmAdj = editRPMAdjustment.text
            if (rpmCal.isNotEmpty() && rpmCal.toString().toIntOrNull() != null) {
                ble?.saveRPMCalibration(rpmCal.toString().toInt())
            } else if (rpmAdj.isNotEmpty() && rpmAdj.toString().toDoubleOrNull() != null) {
                val d = editRPMAdjustment.text.toString().toDouble()
                ble?.saveRPMAdjustment(d)
            }
        } catch (_: Exception) {
            // do nothing
        }
    }

    //region extensions
    private fun String.toEditable(): Editable =  Editable.Factory.getInstance().newEditable(this)
    //endregion
}