package com.aboni.n2kRouter

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/**
 * A label stacked above a value. Replaces the former lblXXX/txtXXX TextView pairs.
 * Attributes: label, text (default value), valueWidth, monospace (default true), alignStart (default false).
 */
class LabeledValueView(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {

    private val labelView = TextView(context)
    private val valueView = TextView(context)

    var label: CharSequence?
        get() = labelView.text
        set(value) { labelView.text = value }

    var text: CharSequence?
        get() = valueView.text
        set(value) { valueView.text = value }

    init {
        orientation = VERTICAL
        var alignStart = false
        var monospace = true
        var valueWidth = LayoutParams.WRAP_CONTENT
        val a = context.obtainStyledAttributes(attrs, R.styleable.LabeledValueView)
        try {
            labelView.text = a.getText(R.styleable.LabeledValueView_label)
            valueView.text = a.getText(R.styleable.LabeledValueView_text) ?: context.getString(R.string.NO_VALUE_STRING)
            alignStart = a.getBoolean(R.styleable.LabeledValueView_alignStart, false)
            monospace = a.getBoolean(R.styleable.LabeledValueView_monospace, true)
            valueWidth = a.getLayoutDimension(R.styleable.LabeledValueView_valueWidth, valueWidth)
        } finally {
            a.recycle()
        }
        val textGravity = if (alignStart) Gravity.START else Gravity.END
        gravity = textGravity
        valueView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        valueView.gravity = textGravity
        if (monospace) valueView.typeface = Typeface.MONOSPACE
        addView(labelView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        addView(valueView, LayoutParams(valueWidth, LayoutParams.WRAP_CONTENT))
    }
}
