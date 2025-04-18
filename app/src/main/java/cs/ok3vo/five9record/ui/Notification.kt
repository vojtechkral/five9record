package cs.ok3vo.five9record.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KClass

class NotificationBuilder(
    context: Context,
    title: String,
    text: String,
    icon: Int,
    silent: Boolean,
    ongoing: Boolean,
    priority: Int,
    targetActivity: KClass<*>,
): NotificationCompat.Builder(context, CHANNEL_ID) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            priorityToImportance(priority),
        )

        manager.createNotificationChannel(channel)

        val intent = Intent(context, targetActivity.java)
        val pi = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        setSilent(silent)
        setOnlyAlertOnce(true)
        setContentTitle(title)
        setContentText(text)
        setSmallIcon(icon)
        setPriority(priority)
        setContentIntent(pi)
        setAutoCancel(false)
        setOngoing(ongoing)
        setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
    }

    /**
     * Sends the notification to the system.
     * This requires the notification permission to have been gained.
     * Returns an ID of this particular notification.
     */
    fun notify(): Int {
        val id = idCounter.getAndAdd(1)
        manager.notify(id, this.build())
        return id
    }

    companion object {
        private const val CHANNEL_ID = "five9record_channel"
        private const val CHANNEL_NAME = "Five9 Record Service"

        private val idCounter = AtomicInteger(9001)

        private fun priorityToImportance(priority: Int) = when (priority) {
            NotificationCompat.PRIORITY_MIN -> NotificationManager.IMPORTANCE_MIN
            NotificationCompat.PRIORITY_LOW -> NotificationManager.IMPORTANCE_LOW
            NotificationCompat.PRIORITY_HIGH -> NotificationManager.IMPORTANCE_HIGH
            NotificationCompat.PRIORITY_MAX -> NotificationManager.IMPORTANCE_MAX
            else -> NotificationManager.IMPORTANCE_DEFAULT
        }
    }
}
