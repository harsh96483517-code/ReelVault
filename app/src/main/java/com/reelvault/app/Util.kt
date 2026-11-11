package com.reelvault.app

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun fmtDur(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}

fun fmtSize(bytes: Long): String {
    val mb = bytes / 1048576.0
    return if (mb >= 1024) "%.2f GB".format(mb / 1024) else "%.1f MB".format(mb)
}

fun fmtDate(epochSeconds: Long): String =
    SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(epochSeconds * 1000))

class PlayRequest(val uris: List<String>, val start: Int, val autoNext: Boolean = false)
