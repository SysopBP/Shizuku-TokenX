package moe.shizuku.manager.ui.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.ui.theme.TokenXAppearanceKeys
import moe.shizuku.manager.ui.theme.TokenXHapticStrength

enum class TokenXHapticEvent { TAP, CONFIRM, SUCCESS, WARNING, ERROR }

object TokenXHaptics {
    fun perform(context: Context, event: TokenXHapticEvent) {
        val prefs = ShizukuSettings.getPreferences()
        val strength = runCatching {
            TokenXHapticStrength.valueOf(
                prefs.getString(TokenXAppearanceKeys.HAPTIC_STRENGTH, TokenXHapticStrength.STANDARD.name)!!
            )
        }.getOrDefault(TokenXHapticStrength.STANDARD)
        if (strength == TokenXHapticStrength.OFF) return

        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return
        if (!vibrator.hasVibrator()) return

        val amplitudeScale = when (strength) {
            TokenXHapticStrength.OFF -> return
            TokenXHapticStrength.LIGHT -> .55f
            TokenXHapticStrength.STANDARD -> .78f
            TokenXHapticStrength.STRONG -> 1f
        }
        val baseAmplitude = when (event) {
            TokenXHapticEvent.TAP -> 70
            TokenXHapticEvent.CONFIRM -> 105
            TokenXHapticEvent.SUCCESS -> 125
            TokenXHapticEvent.WARNING -> 155
            TokenXHapticEvent.ERROR -> 205
        }
        val amplitude = (baseAmplitude * amplitudeScale).toInt().coerceIn(1, 255)
        val duration = when (event) {
            TokenXHapticEvent.TAP -> 12L
            TokenXHapticEvent.CONFIRM -> 18L
            TokenXHapticEvent.SUCCESS -> 24L
            TokenXHapticEvent.WARNING -> 32L
            TokenXHapticEvent.ERROR -> 42L
        }
        runCatching { vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude)) }
    }
}
