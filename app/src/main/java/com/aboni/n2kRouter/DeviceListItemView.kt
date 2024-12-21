package com.aboni.n2kRouter

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView

class DeviceListItemView: FrameLayout {

    private val imageView: ImageView get() = findViewById(R.id.listItemGlyph)
    private val textView: TextView get() = findViewById(R.id.listItemText)
    private val idTextView: TextView get() = findViewById(R.id.idText)
    private val rssiTextView: TextView get() = findViewById(R.id.rssiText)
    private val rssiImage: SignalStrengthView get() = findViewById(R.id.rssiIcon)
    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) {
        init()
    }

    private fun init() {
        inflate(context, R.layout.device_list_item, this);
    }

    var highlighted: Boolean = false
        set (v) {
            field = v
            imageView.setImageResource(if (v) R.drawable.ic_bt_on else R.drawable.ic_bt_off)
        }

    var deviceItem: DeviceItem = NO_DEVICE
        set(value) {
            field = value
            textView.text = value.name
            idTextView.text = value.id
            rssiTextView.text = String.format("%d", value.rssi)
            rssiImage.strength = value.rssi
        }
}