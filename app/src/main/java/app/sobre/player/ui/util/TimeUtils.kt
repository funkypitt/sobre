package app.sobre.player.ui.util

import java.util.concurrent.TimeUnit

fun relativeTime(timestampMs: Long): String {
    if (timestampMs <= 0) return ""
    val now = System.currentTimeMillis()
    val diff = now - timestampMs
    return when {
        diff < TimeUnit.MINUTES.toMillis(1) -> "a l'instant"
        diff < TimeUnit.HOURS.toMillis(1) -> {
            val m = TimeUnit.MILLISECONDS.toMinutes(diff)
            "il y a ${m} min"
        }
        diff < TimeUnit.DAYS.toMillis(1) -> {
            val h = TimeUnit.MILLISECONDS.toHours(diff)
            "il y a ${h} h"
        }
        diff < TimeUnit.DAYS.toMillis(30) -> {
            val d = TimeUnit.MILLISECONDS.toDays(diff)
            "il y a ${d} j"
        }
        diff < TimeUnit.DAYS.toMillis(365) -> {
            val m = TimeUnit.MILLISECONDS.toDays(diff) / 30
            "il y a ${m} mois"
        }
        else -> {
            val y = TimeUnit.MILLISECONDS.toDays(diff) / 365
            "il y a ${y} an${if (y > 1) "s" else ""}"
        }
    }
}

fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val hours = totalSec / 3600
    val minutes = (totalSec % 3600) / 60
    val seconds = totalSec % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}
