package com.aboni.n2kRouter

import android.content.Context
import android.util.AttributeSet

class SignalStrengthView(context: Context, attributeSet: AttributeSet): androidx.appcompat.widget.AppCompatImageView(context, attributeSet) {

    private val strenghtIcons = arrayOf(
        R.drawable.signal_0,
        R.drawable.signal_1,
        R.drawable.signal_2,
        R.drawable.signal_3,
        R.drawable.signal_4
    )

    init {
        refreshImage()
    }

    var strength: Int = -100
        set(value) {
            if (value<-100 || value>0) throw RuntimeException("$value is not a valid db value")
            field = value
            refreshImage()
        }

    private fun refreshImage() {
        val i = if (strength < -98) 0
            else if (strength < -90) 1
            else if (strength < -77) 2
            else if (strength < -66) 3
            else 4
        setImageResource(strenghtIcons[i])
    }
}