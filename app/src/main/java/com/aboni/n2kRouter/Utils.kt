package com.aboni.n2kRouter

import android.content.Context
import android.util.Log
import kotlin.math.abs

fun appendLog(message: String)
{
    Log.i("ABN2K", message)
}
fun noValueStr(context: Context): String {
    return context.getString(R.string.NO_VALUE_STRING)
}

fun formatValue(context: Context, formatId: Int, vararg args: Any?): String {
    return context.getString(formatId).format(*args)
}

fun formatLatLon(ctx: Context, x: Double, pos: String, neg: String): String {
    val p = if (x>=0) pos else neg
    val xx = abs(x)
    val deg = xx.toInt()
    val f = (xx - deg) * 60.0
    return formatValue(ctx, R.string.LAT_LON_FORMAT, deg, f, p)
}

fun formatEngineHours(ctx: Context, seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return formatValue(ctx, R.string.ENGINE_HOURS_FORMAT, h, m)
}

fun formatGPSFix(ctx: Context, fix: Long): String {
    return when(fix) {
        0L -> ":("
        1L -> "DR" // dead reckoning
        2L -> "2D"
        3L -> "3D"
        4L -> "3D+"
        5L -> "3D+" // GNSS + dead reckoning
        else -> noValueStr(ctx)
    }

}

fun gattStatusName(status: Int): String = when (status) {
    1 -> "invalid handle"
    2 -> "read not permitted"
    3 -> "write not permitted"
    5 -> "insufficient authentication"
    6 -> "request not supported"
    7 -> "invalid offset"
    8 -> "insufficient authorization"
    13 -> "invalid attribute length"
    14 -> "unlikely error"
    15 -> "insufficient encryption"
    19 -> "peer terminated the connection"
    22 -> "connection terminated by local host"
    128 -> "no resources"
    133 -> "generic GATT error"
    137 -> "authentication failed"
    200 -> "write not allowed"
    201 -> "another write is in progress"
    else -> "status $status"
}

/** Reason of a failed pairing, from BluetoothDevice.EXTRA_REASON (UNBOND_REASON_*). */
fun bondFailureName(reason: Int): String = when (reason) {
    1 -> "authentication failed, the passkey is wrong"
    2 -> "the device rejected the pairing"
    3 -> "pairing cancelled"
    4 -> "the device is not ready to pair or did not answer"
    5 -> "a Bluetooth scan is in progress"
    6 -> "pairing timed out"
    7 -> "too many attempts, wait a bit"
    8 -> "the device cancelled the pairing"
    9 -> "the bond was removed"
    else -> "unknown reason $reason"
}

fun describeCommandResult(ctx: Context, r: CommandResult): String = when (r.kind) {
    CommandResult.Kind.Sent -> ctx.getString(R.string.cmd_sent)
    CommandResult.Kind.NotConnected -> ctx.getString(R.string.cmd_not_connected)
    CommandResult.Kind.PasskeyCancelled -> ctx.getString(R.string.cmd_passkey_cancelled)
    CommandResult.Kind.PasskeyRejected -> ctx.getString(
        R.string.cmd_passkey_rejected,
        gattStatusName(r.code),
        if (r.bondFailure == CommandResult.NO_BOND_FAILURE) ctx.getString(R.string.cmd_no_pairing_attempt)
        else bondFailureName(r.bondFailure)
    )
    CommandResult.Kind.GattError -> ctx.getString(R.string.cmd_gatt_error, gattStatusName(r.code), r.code)
    CommandResult.Kind.Timeout -> ctx.getString(R.string.cmd_timeout)
}
