package com.aboni.n2kRouter

import android.content.Context
import android.widget.TextView

class N2KDataView(context: Context, ble: BLEThing?) : N2KCardPage(context, ble) {

    constructor(context: Context): this(context, null)

    //region widgets
    private val utcTimeTxt: TextView
        get() = findViewById(R.id.txtTime)
    private val atmoTxtView: TextView
        get() = findViewById(R.id.txtAtmo)
    private val tempTxtView: TextView
        get() = findViewById(R.id.txtTemp)
    private val heapTxtView: TextView
        get() = findViewById(R.id.txtHeap)
    private val rpmTxtView: TextView
        get() = findViewById(R.id.txtRPM)
    private val engineHTxtView: TextView
        get() = findViewById(R.id.txtEngineH)
    private val latTxtView: TextView
        get() = findViewById(R.id.txtLat)
    private val lonTxtView: TextView
        get() = findViewById(R.id.txtLon)
    private val canTxtView: TextView
        get() = findViewById(R.id.txtCan)
    private val canSrcTxtView: TextView
        get() = findViewById(R.id.txtCanSrc)
    private val humTxtView: TextView
        get() = findViewById(R.id.txtHum)
    private val cogTxtView: TextView
        get() = findViewById(R.id.txtCog)
    private val sogTxtView: TextView
        get() = findViewById(R.id.txtSog)
    private val fixTxtView: TextView
        get() = findViewById(R.id.txtGPSFix)
    private val currentTxtView: TextView
        get() = findViewById(R.id.txtCurrent)
    private val voltageTxtView: TextView
        get() = findViewById(R.id.txtVoltage)
    private val socTxtView: TextView
        get() = findViewById(R.id.txtSOC)
    //endregion

    init {
        initView()
        attachCard(R.layout.data_view)
        setTitleResource(R.string.data_card_title)
        setImageResource(android.R.drawable.ic_menu_view)
    }

    override fun onData(data: Data) {
        val noValue = context.getString(R.string.NO_VALUE_STRING)
        utcTimeTxt.text = if (data.utcTime.valid && data.utcTime.value>0) data.utcTime.asTime()!!.toString() else noValue
        atmoTxtView.text = if (data.atmo.valid) formatValue(context, R.string.ATMO_FORMAT, data.atmo.value) else noValue
        tempTxtView.text = if (data.temp.valid) formatValue(context, R.string.TEMPERATURE_FORMAT, data.temp.value) else noValue
        humTxtView.text = if (data.hum.valid) formatValue(context, R.string.HUMIDITY_FORMAT, data.hum.value) else noValue
        latTxtView.text = if (data.lat.valid) formatLatLon(context, data.lat.value, "N", "S") else noValue
        lonTxtView.text = if (data.lon.valid) formatLatLon(context, data.lon.value, "E", "W") else noValue
        sogTxtView.text = if (data.sog.valid) formatValue(context, R.string.SOG_FORMAT, data.sog.value) else noValue
        cogTxtView.text = if (data.cog.valid) formatValue(context, R.string.COG_FORMAT, data.cog.value) else noValue
        heapTxtView.text = if (data.heap.valid) formatValue(context, R.string.HEAP_FORMAT, data.heap.value/1024) else noValue
        rpmTxtView.text = if (data.rpm.valid) formatValue(context, R.string.RPM_FORMAT, data.rpm.value) else noValue
        engineHTxtView.text = if (data.engineHours.valid) formatEngineHours(context, data.engineHours.value) else noValue
        canTxtView.text = if (data.canSentPeriod != -1) formatValue(context,
            R.string.N2K_STATS_FORMAT,
            data.canSentPeriod,
            data.canErrorsPeriod
        ) else noValue
        canSrcTxtView.text = if (data.n2kSrc.valid) formatValue(context,
            R.string.N2K_SOURCE_FORMAT,
            data.n2kSrc.value,
            if (data.canActive.value == 1L) "Ok" else "Ko"
        ) else noValue
        fixTxtView.text = if (data.gpsFix.valid) formatGPSFix(context, data.gpsFix.value) else noValue
        voltageTxtView.text = if (data.volts.valid) formatValue(context, R.string.VOLTS_FORMAT, data.volts.value) else noValue
        currentTxtView.text = if (data.current.valid) formatValue(context, R.string.AMPERE_FORMAT, data.current.value) else noValue
        socTxtView.text = if (data.soc.valid) formatValue(context, R.string.SOC_FORMAT, data.soc.value) else noValue
    }
}