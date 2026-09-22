package com.aboni.n2kRouter

import android.content.Context
import android.transition.TransitionManager
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isNotEmpty
import com.google.android.material.switchmaterial.SwitchMaterial

/**
 * A service row: a toggle plus, when the service has settings, a chevron that expands them.
 * Children declared in XML are the section's settings and are placed in the expandable body.
 *
 * The toggle is the requested value; [setActive] takes the value the device actually runs with. The body can be
 * expanded only while the service is active on the device.
 *
 * With sectionHasToggle="false" the section is a plain group (a title instead of the toggle): it is always
 * active and the whole header expands it.
 */
class ServiceSectionView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    LinearLayout(context, attrs) {

    val toggle: SwitchMaterial
    private val chevron: ImageView
    private val body: LinearLayout
    private var bodyReady = false

    var isServiceActive = false
        private set
    var isExpanded = false
        private set

    init {
        orientation = VERTICAL
        inflate(context, R.layout.view_service_section, this)
        toggle = findViewById(R.id.sectionToggle)
        chevron = findViewById(R.id.sectionChevron)
        val label: TextView = findViewById(R.id.sectionLabel)
        body = findViewById(R.id.sectionBody)
        bodyReady = true

        val a = context.obtainStyledAttributes(attrs, R.styleable.ServiceSectionView)
        try {
            val title = a.getString(R.styleable.ServiceSectionView_sectionTitle)
            toggle.text = title
            if (!a.getBoolean(R.styleable.ServiceSectionView_sectionHasToggle, true)) {
                toggle.visibility = GONE
                label.text = title
                label.visibility = VISIBLE
                isServiceActive = true
                findViewById<View>(R.id.sectionHeader).setOnClickListener { setExpanded(!isExpanded) }
            }
        } finally {
            a.recycle()
        }
        chevron.setOnClickListener { setExpanded(!isExpanded) }
        // sections share view ids, so their state must not be saved/restored by id
        isSaveFromParentEnabled = false
        updateChevron()
    }

    override fun addView(child: View, index: Int, params: ViewGroup.LayoutParams) {
        if (bodyReady) body.addView(child, index, params) else super.addView(child, index, params)
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        updateChevron()
    }

    /** Sets whether the device runs the service; an inactive service is collapsed and cannot be expanded. */
    fun setActive(active: Boolean) {
        if (active == isServiceActive) return
        isServiceActive = active
        if (!active) setExpanded(false)
        updateChevron()
    }

    fun setExpanded(expanded: Boolean) {
        val target = expanded && isServiceActive && body.isNotEmpty()
        if (target == isExpanded) return
        isExpanded = target
        (parent as? ViewGroup)?.let { TransitionManager.beginDelayedTransition(it) }
        body.visibility = if (target) VISIBLE else GONE
        chevron.animate().rotation(if (target) 180f else 0f).setDuration(150).start()
    }

    private fun updateChevron() {
        chevron.visibility = if (body.isNotEmpty()) VISIBLE else GONE
        chevron.isEnabled = isServiceActive
        chevron.alpha = if (isServiceActive) 1f else 0.3f
    }
}
