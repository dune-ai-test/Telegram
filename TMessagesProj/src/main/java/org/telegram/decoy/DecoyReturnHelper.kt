package org.telegram.decoy

import android.content.Context
import android.content.Intent

object DecoyReturnHelper {
    private var returnTapCount = 0
    private var lastTapTime = 0L
    private const val RETURN_TAP_COUNT = 5
    private const val TAP_WINDOW_MS = 800L

    @JvmStatic
    fun handleReturnTap(context: Context): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastTapTime > TAP_WINDOW_MS) {
            returnTapCount = 0
        }
        lastTapTime = now
        returnTapCount++

        if (returnTapCount >= RETURN_TAP_COUNT) {
            returnTapCount = 0
            try {
                val intent = Intent()
                intent.setClassName(
                    context.applicationContext.packageName,
                    "org.telegram.decoy.DecoyActivity"
                )
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                context.startActivity(intent)
            } catch (e: Exception) {
                return false
            }
            return true
        }
        return false
    }
}
