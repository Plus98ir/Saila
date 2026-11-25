package com.v2ray.ang.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.handler.NotificationManager
import com.v2ray.ang.util.LogUtil

/**
 * Saila: the Pause / Resume / Close buttons of the notifications. Pause stops the service like
 * Stop does, but leaves a notification whose Resume button connects again from the bar.
 * Not exported: only our own explicit PendingIntents reach it.
 */
class PauseReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_PAUSE -> {
                LogUtil.i(AppConfig.TAG, "PauseReceiver: pause from notification")
                LauncherManager.stopService(context)
                NotificationManager.showPausedNotification(context)
            }

            ACTION_RESUME -> {
                LogUtil.i(AppConfig.TAG, "PauseReceiver: resume from notification")
                NotificationManager.cancelPausedNotification(context)
                // A notification action click lets the app start a foreground service from the background.
                if (!LauncherManager.startServiceFromToggle(context)) {
                    NotificationManager.showPausedNotification(context)
                }
            }

            ACTION_CLOSE -> NotificationManager.cancelPausedNotification(context)
        }
    }

    companion object {
        const val ACTION_PAUSE = "${AppConfig.ANG_PACKAGE}.action.notification.pause"
        const val ACTION_RESUME = "${AppConfig.ANG_PACKAGE}.action.notification.resume"
        const val ACTION_CLOSE = "${AppConfig.ANG_PACKAGE}.action.notification.close"
    }
}
