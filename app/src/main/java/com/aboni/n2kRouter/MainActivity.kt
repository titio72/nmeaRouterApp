package com.aboni.n2kRouter

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import com.aboni.marine.link.CommandResult
import com.aboni.marine.link.LinkStatus
import com.aboni.marine.link.ble.PasskeyPrompter
import com.aboni.marine.ui.settings.PasskeyDialog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.aboni.marine.ui.status.StatusBar
import com.aboni.marine.ui.theme.MarineTheme
import com.aboni.marine.ui.theme.ResolvedTheme
import com.aboni.marine.ui.theme.ThemeMode
import com.aboni.marine.ui.theme.ThemeStore
import com.aboni.marine.ui.theme.next
import com.aboni.marine.ui.theme.resolveTheme
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import kotlinx.coroutines.channels.Channel
import java.util.Timer
import java.util.TimerTask

private const val DEVICES_PAGE = 2

class MainActivity : BLEN2KListener, BLEApp() {

    private val passkeyPrompter = PasskeyPrompter()
    private val ble: LinkBleThing by lazy { LinkBleThing.create(applicationContext, lifecycleScope, passkeyPrompter) }
    private val pageAdapter: N2KPagerAdapter by lazy { N2KPagerAdapter(this, ble, linkStatus, themeStore.mode.value) }
    private val timer = Timer("BLE connection check", false)
    private val themeStore: ThemeStore by lazy { ThemeStore(this) }
    private val linkStatus = mutableStateOf(LinkStatus())
    private val feedback = Channel<String>(Channel.BUFFERED)

    //region Widgets References
    private val pager: ViewPager2
        get() = findViewById(R.id.pagerView)
    private val statusBar: ComposeView
        get() = findViewById(R.id.statusBar)
    //endregion

    //region pages
    private class N2KPagerAdapter(context: Context, ble: BLEThing, status: State<LinkStatus>, themeMode: ThemeMode): RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        class N2KViewPlaceHolder(context: Context): FrameLayout(context) {

            private var theView: N2KCardPage?= null

            fun loadContent(v: N2KCardPage) {
                v.tag = "card_content"
                addView(v)
                theView = v
            }
        }

        class N2KViewHolder(val view: N2KViewPlaceHolder): RecyclerView.ViewHolder(view)

        private val scanView:N2KScanView = N2KScanView(context, ble, status, themeMode)
        private val settingsView: N2KSettingsView = N2KSettingsView(context, ble)
        private val dataView: N2KDataView = N2KDataView(context, ble)
        private val views = arrayOf( dataView, settingsView, scanView ) // DEVICES_PAGE is scanView

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val v = N2KViewPlaceHolder(parent.context)
            v.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            return N2KViewHolder(v)
        }

        override fun getItemCount(): Int {
            return views.size
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (position>=itemCount) throw RuntimeException("Invalid pager position $position")
            val v = views[position]
            (holder as N2KViewHolder).view.loadContent(v)
            v.onActivate()
        }
    }
    //endregion

    //region activity
    override fun onResume() {
        super.onResume()
        grantBluetoothCentralPermissions(AskType.InsistUntilSuccess) {
            success -> appendLog("BLE Permission: $success")
            if (success) {
                ble.startScan()
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        val systemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val theme = resolveTheme(themeStore.mode.value, systemDark)
        setTheme(xmlThemeRes(theme))
        super.onCreate(savedInstanceState)
        // System bar icons follow the app theme, not the system one.
        val bars = if (theme == ResolvedTheme.Light) SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
            else SystemBarStyle.dark(Color.TRANSPARENT)
        enableEdgeToEdge(bars, bars)
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        ble.addListener(this)

        pager.adapter = pageAdapter
        setupStatusBar()
        lifecycleScope.launch { ble.linkStatus.collect { linkStatus.value = it } }

        timer.schedule(object: TimerTask() {
            override fun run() {
                ble.sendHeartbeat()
            }
        }, 0, 2500)

        appendLog("MainActivity.onCreate")
    }

    override fun onDestroy() {
        timer.cancel()
        ble.disconnect()
        ble.release()
        super.onDestroy()
    }
    //endregion

    //region UI Interaction
    private fun setupStatusBar() {
        val initialMode = themeStore.mode.value
        statusBar.setContent {
            val mode by themeStore.mode.collectAsState()
            // The XML screens take their theme at inflation: recreate to follow a new choice.
            LaunchedEffect(mode) { if (mode != initialMode) recreate() }
            val snackbar = remember { SnackbarHostState() }
            LaunchedEffect(snackbar) { for (msg in feedback) snackbar.showSnackbar(msg) }
            MarineTheme(mode) {
                val passkeyRequest by passkeyPrompter.pending.collectAsState()
                passkeyRequest?.let { PasskeyDialog(it, onAnswer = passkeyPrompter::answer) }
                Column {
                    SnackbarHost(snackbar)
                    StatusBar(
                        status = linkStatus.value,
                        onSettings = { pager.currentItem = DEVICES_PAGE },
                        onCycleTheme = { themeStore.set(mode.next()) },
                    )
                }
            }
        }
    }

    override fun onStatus(status: BLELifecycleState, scanning: Boolean) {
        // the status bar follows the link's own status
    }

    override fun onConf(conf: Conf) {
        // do nothing
    }

    override fun onData(data: Data) {
        // do nothing
    }

    override fun onScan(device: DeviceItem) {
        // the Devices card shows scan results
    }

    override fun onRssi(connectedDevice: DeviceItem, rssi: Int) {
        // shown by the status bar and the Devices card
    }

    override fun onCommandResult(result: CommandResult) {
        // A snackbar instead of a Toast: it follows the theme, a Toast flashes system colours at night.
        feedback.trySend(result.text)
    }
    //endregion
}