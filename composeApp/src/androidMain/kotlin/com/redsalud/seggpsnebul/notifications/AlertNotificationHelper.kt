package com.redsalud.seggpsnebul.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.redsalud.seggpsnebul.MainActivity
import com.redsalud.seggpsnebul.domain.model.AlertType
import java.util.concurrent.atomic.AtomicInteger

object AlertNotificationHelper {

    private const val CHANNEL_ID   = "brigade_alerts"
    private const val CHANNEL_NAME = "Alertas de brigada"

    // Thread-safe incrementing notification ID so each alert shows separately
    private val nextId = AtomicInteger(2000)

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alertas de suministro y mensajes de la brigada"
            enableLights(true)
            enableVibration(true)
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    fun show(context: Context, alertType: String, message: String?) {
        val type  = AlertType.fromValue(alertType)
        val title = "${type?.emoji ?: "❗"} ${type?.label ?: alertType}"
        val body  = message?.takeIf { it.isNotBlank() } ?: "Solicitud de la brigada"

        val tapIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(tapIntent)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(nextId.getAndIncrement(), notification)
    }
}
