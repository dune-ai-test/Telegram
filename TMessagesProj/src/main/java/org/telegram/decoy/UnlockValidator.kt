package org.telegram.decoy

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object UnlockValidator {

    private const val ACTIVATION_TAP_COUNT = 2
    private const val TAP_WINDOW_MS = 600L

    private var tapCount = 0
    private var lastTapTime = 0L
    var unlockModeActive = false
        private set

    fun handleTap(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastTapTime > TAP_WINDOW_MS) {
            tapCount = 0
        }
        lastTapTime = now
        tapCount++

        if (tapCount >= ACTIVATION_TAP_COUNT) {
            tapCount = 0
            unlockModeActive = !unlockModeActive
            return unlockModeActive
        }
        return false
    }

    fun deactivate() {
        unlockModeActive = false
    }

    fun generateExpectedCode(ctx: Context): String {
        val timestamp = RssSyncWorker.getLastFetchedTimestamp(ctx)
        if (timestamp == 0L) return ""

        val sdf = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
        val dateStr = sdf.format(Date(timestamp))
        val sum = dateStr.sumOf { it - '0' }
        return Integer.toBinaryString(sum)
    }

    fun validate(ctx: Context, input: String): Boolean {
        val expected = generateExpectedCode(ctx)
        if (expected.isEmpty() || input.isEmpty()) return false
        return input.trim() == expected
    }
}
