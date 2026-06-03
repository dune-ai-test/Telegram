package org.telegram.decoy

import android.content.Context
import java.util.Calendar
import java.util.Locale

object UnlockValidator {

    private const val ACTIVATION_TAP_COUNT = 2
    private const val TAP_WINDOW_MS = 600L
    private const val MAX_FAILED_ATTEMPTS = 1

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

    fun isLocked(ctx: Context): Boolean {
        return getPrefs(ctx).getBoolean("unlock_locked_out", false)
    }

    private fun getPrefs(ctx: Context) =
        ctx.getSharedPreferences("decoy_newsreader", Context.MODE_PRIVATE)

    fun generateExpectedCode(ctx: Context, firstTitle: String, firstSource: String): String {
        val timestamp = RssSyncWorker.getLastFetchedTimestamp(ctx)
        if (timestamp == 0L || firstTitle.isBlank()) return ""

        val char1 = firstTitle.first().lowercaseChar()
        val char2 = firstSource.firstOrNull()?.lowercaseChar() ?: 'x'

        val fetchSecs = timestamp / 1000
        val secsPart = (fetchSecs % 60).toInt()
        val minuteRounded = fetchSecs - secsPart
        val currentMinutes = Calendar.getInstance().get(Calendar.MINUTE)
        val codeNum = minuteRounded + currentMinutes

        return "${char1}${char2}${codeNum}"
    }

    fun validate(ctx: Context, input: String, firstTitle: String, firstSource: String): Boolean {
        if (input.isBlank()) return false
        val prefs = getPrefs(ctx)
        val lockedOut = prefs.getBoolean("unlock_locked_out", false)
        val expected = generateExpectedCode(ctx, firstTitle, firstSource)
        if (expected.isEmpty()) return false

        if (lockedOut) {
            return handleLockedInput(ctx, prefs, input, expected)
        }

        if (input.trim().lowercase(Locale.US) == expected) {
            resetFailedAttempts(prefs)
            return true
        }

        incrementFailedAttempts(ctx, prefs)
        return false
    }

    private fun handleLockedInput(ctx: Context, prefs: android.content.SharedPreferences, input: String, expected: String): Boolean {
        val lower = input.trim().lowercase(Locale.US)
        if (!lower.startsWith("reset")) return false

        val rest = lower.removePrefix("reset")
        if (rest.length < 4) return false

        val enteredHour = rest.substring(0, 2)
        val enteredCode = rest.substring(2)

        val timestamp = RssSyncWorker.getLastFetchedTimestamp(ctx)
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        val fetchHour = "%02d".format(cal.get(Calendar.HOUR_OF_DAY))

        if (enteredHour != fetchHour) return false
        if (enteredCode != expected) return false

        resetFailedAttempts(prefs)
        return true
    }

    private fun incrementFailedAttempts(ctx: Context, prefs: android.content.SharedPreferences) {
        val attempts = prefs.getInt("unlock_failed_attempts", 0) + 1
        prefs.edit()
            .putInt("unlock_failed_attempts", attempts)
            .putBoolean("unlock_locked_out", attempts >= MAX_FAILED_ATTEMPTS)
            .apply()
    }

    private fun resetFailedAttempts(prefs: android.content.SharedPreferences) {
        prefs.edit()
            .putInt("unlock_failed_attempts", 0)
            .putBoolean("unlock_locked_out", false)
            .apply()
    }
}
