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