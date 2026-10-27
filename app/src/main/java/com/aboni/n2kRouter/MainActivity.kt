package com.aboni.n2kRouter

import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import com.aboni.marine.link.CommandResult
import com.aboni.marine.link.ble.PasskeyPrompter
import com.aboni.marine.ui.LocalMarineSnackbar
import com.aboni.marine.ui.MarineScaffold
import com.aboni.marine.ui.cards.CardAction
import com.aboni.marine.ui.cards.CardPage
import com.aboni.marine.ui.cards.CardPager
import com.aboni.marine.ui.theme.MarineTheme
import com.aboni.marine.ui.theme.ResolvedTheme
import com.aboni.marine.ui.theme.ThemeStore
import com.aboni.marine.ui.theme.next
import com.aboni.marine.ui.theme.resolveTheme
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.Timer
import java.util.TimerTask

/** Data, Settings, Devices; the status bar's ⚙ jumps to Devices. */
private const val PAGES = 3
private const val DEVICES_PAGE = PAGES - 1

class MainActivity : BLEN2KListener, BLEApp() {

    private val passkeyPrompter = PasskeyPrompter()
    private val ble: LinkBleThing by lazy { LinkBleThing.create(applicationContext, lifecycleScope, passkeyPrompter) }
    private val timer = Timer("BLE connection check", false)
    private val themeStore: ThemeStore by lazy { ThemeStore(this) }
    private val feedback = Channel<String>(Channel.BUFFERED)

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

    override fun onCreate(savedInstanceState: Bundle?) {
        val systemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val theme = resolveTheme(themeStore.mode.value, systemDark)
        // The window behind the Compose content starts in the user's theme: no white flash at night.
        setTheme(xmlThemeRes(theme))
        super.onCreate(savedInstanceState)
        edgeToEdge(theme)

        ble.addListener(this)
        val data = DataState(this, ble)
        val settings = SettingsState(this, ble)
        val devices = DevicesState(ble)

        setContent {
            val mode by themeStore.mode.collectAsState()
            val resolved = resolveTheme(mode, isSystemInDarkTheme())
            LaunchedEffect(resolved) { edgeToEdge(resolved) }
            val status by ble.linkStatus.collectAsState()
            val pager = rememberPagerState { PAGES }
            val scope = rememberCoroutineScope()
            MarineTheme(mode) {
                MarineScaffold(
                    status = status,
                    onStatusSettings = { scope.launch { pager.animateScrollToPage(DEVICES_PAGE) } },
                    onCycleTheme = { themeStore.set(mode.next()) },
                    passkeys = passkeyPrompter,
                ) { padding ->
                    // Command results in the scaffold's snackbar: it follows the theme, a Toast flashes system colours at night.
                    val snackbar = LocalMarineSnackbar.current
                    LaunchedEffect(snackbar) { for (msg in feedback) snackbar.showSnackbar(msg) }
                    CardPager(
                        listOf(
                            CardPage("data", stringResource(R.string.data_card_title),
                                CardAction.icon(android.R.drawable.ic_menu_view, stringResource(R.string.data_card_title))) {
                                DataCard(data.readings)
                            },
                            CardPage("settings", stringResource(R.string.settings_card_title),
                                CardAction.icon(android.R.drawable.ic_menu_edit, stringResource(R.string.settings_card_title))) {
                                SettingsCard(settings)
                            },
                            CardPage("devices", stringResource(R.string.scan_card_title),
                                CardAction.icon(android.R.drawable.ic_menu_search, "Scan for devices", devices::scan)) {
                                DevicesCard(devices, status)
                            },
                        ),
                        Modifier.fillMaxSize().padding(padding),
                        state = pager,
                    )
                }
            }
        }

        timer.schedule(object: TimerTask() {
            override fun run() {
                ble.sendHeartbeat()
            }
        }, 0, 2500)

        appendLog("MainActivity.onCreate")
    }

    /** System bar icons follow the app theme, not the system one. */
    private fun edgeToEdge(theme: ResolvedTheme) {
        val bars = if (theme == ResolvedTheme.Light) SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
            else SystemBarStyle.dark(Color.TRANSPARENT)
        enableEdgeToEdge(bars, bars)
    }

    override fun onDestroy() {
        timer.cancel()
        ble.disconnect()
        ble.release()
        super.onDestroy()
    }
    //endregion

    //region UI Interaction
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
        feedback.trySend(result.text)
    }
    //endregion
}
