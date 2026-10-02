package org.yb.secondwind.ui

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
private val dayFmt = SimpleDateFormat("EEE", Locale.getDefault())
private val dateFmt = SimpleDateFormat("d MMM", Locale.getDefault())
private val fullFmt: DateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)

private fun startOfDay(t: Long): Long = Calendar.getInstance().apply {
    timeInMillis = t
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

/** Compact list timestamp: 14:02 · Yesterday · Mon · 28 Sep. */
fun fmtWhen(t: Long, now: Long = System.currentTimeMillis()): String {
    val today = startOfDay(now)
    val d = Date(t)
    return when {
        t >= today -> timeFmt.format(d)
        t >= today - 86_400_000L -> "Yesterday"
        t >= today - 6 * 86_400_000L -> dayFmt.format(d)
        else -> dateFmt.format(d)
    }
}

/** Human age for "as of" labels: just now · 5 min ago · 2 h ago · yesterday 18:40 · 28 Sep 09:15. */
fun fmtAge(t: Long, now: Long = System.currentTimeMillis()): String {
    if (t <= 0) return "never"
    val s = (now - t) / 1000
    val today = startOfDay(now)
    return when {
        s < 60 -> "just now"
        s < 3600 -> "${s / 60} min ago"
        t >= today -> "${s / 3600} h ago"
        t >= today - 86_400_000L -> "yesterday ${timeFmt.format(Date(t))}"
        else -> "${dateFmt.format(Date(t))} ${timeFmt.format(Date(t))}"
    }
}

fun fmtFull(t: Long): String = fullFmt.format(Date(t))
