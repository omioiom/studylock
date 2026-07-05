package com.studylock

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * 빠른 설정용 시스템 제어 (밝기 · 밝기 최적화(자동밝기) · 자동회전).
 * 전부 Settings.System 쓰기 → '설정 수정(WRITE_SETTINGS)' 권한 필요.
 */
object QuickActions {

    fun canWriteSettings(ctx: Context): Boolean = Settings.System.canWrite(ctx)

    fun openWriteSettings(ctx: Context) = runCatching {
        ctx.startActivity(
            Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    // ---- 밝기 (0..255) ----
    fun getBrightness(ctx: Context): Int =
        runCatching { Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS) }
            .getOrDefault(128)

    /** 수동 밝기 설정. 자동밝기가 켜져 있으면 끈다(수동으로 전환). */
    fun setBrightness(ctx: Context, value: Int) {
        if (!Settings.System.canWrite(ctx)) { openWriteSettings(ctx); return }
        val v = value.coerceIn(4, 255)
        runCatching {
            Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, v)
        }
    }

    // ---- 밝기 최적화 (자동 밝기) ----
    fun isAutoBrightness(ctx: Context): Boolean =
        runCatching {
            Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE) ==
                Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        }.getOrDefault(false)

    fun setAutoBrightness(ctx: Context, on: Boolean) {
        if (!Settings.System.canWrite(ctx)) { openWriteSettings(ctx); return }
        runCatching {
            Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                if (on) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        }
    }

    // ---- 자동 회전 ----
    fun isAutoRotate(ctx: Context): Boolean =
        runCatching { Settings.System.getInt(ctx.contentResolver, Settings.System.ACCELEROMETER_ROTATION) == 1 }
            .getOrDefault(false)

    fun setAutoRotate(ctx: Context, on: Boolean) {
        if (!Settings.System.canWrite(ctx)) { openWriteSettings(ctx); return }
        runCatching {
            Settings.System.putInt(ctx.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (on) 1 else 0)
        }
    }
}
