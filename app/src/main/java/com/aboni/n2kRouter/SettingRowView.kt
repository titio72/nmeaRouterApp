package com.aboni.n2kRouter

import android.content.Context
import android.text.InputType
import android.util.AttributeSet
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout

/**
 * A single device setting: label, current value read from the device, an edit field and its own save button.
 * Attributes: rowLabel, rowSaveDescription, rowShowValue (default true), android:inputType.
 */
class SettingRowView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    ConstraintLayout(context, attrs) {

    private val labelView: TextView
    private val valueView: TextView
    private val edit: EditText
    private val saveButton: ImageButton

    init {
        inflate(context, R.layout.view_setting_row, this)
        labelView = findViewById(R.id.settingLabel)
        valueView = findViewById(R.id.settingValue)
        edit = findViewById(R.id.settingEdit)
        saveButton = findViewById(R.id.settingSave)

        val a = context.obtainStyledAttributes(attrs, R.styleable.SettingRowView)
        try {
            labelView.text = a.getString(R.styleable.SettingRowView_rowLabel)
            saveButton.contentDescription = a.getString(R.styleable.SettingRowView_rowSaveDescription)
            valueView.visibility =
                if (a.getBoolean(R.styleable.SettingRowView_rowShowValue, true)) VISIBLE else GONE
            edit.inputType = a.getInt(R.styleable.SettingRowView_android_inputType, InputType.TYPE_CLASS_TEXT)
        } finally {
            a.recycle()
        }
        // rows share view ids, so their state must not be saved/restored by id
        isSaveFromParentEnabled = false
    }

    val label: CharSequence
        get() = labelView.text

    /** The trimmed content of the edit field. */
    var text: String
        get() = edit.text.toString().trim()
        set(v) = edit.setText(v)

    /** The current value as reported by the device. */
    var valueText: CharSequence
        get() = valueView.text
        set(v) {
            valueView.text = v
        }

    fun setSaveButtonEnabled(enabled: Boolean) {
        saveButton.isEnabled = enabled
    }

    fun setOnSaveListener(l: () -> Unit) = saveButton.setOnClickListener { l() }
}
