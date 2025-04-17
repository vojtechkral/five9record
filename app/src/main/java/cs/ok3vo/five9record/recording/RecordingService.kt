package cs.ok3vo.five9record.recording

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Parcelable
import androidx.core.app.NotificationCompat
import cs.ok3vo.five9record.location.LocationStatus
import cs.ok3vo.five9record.radio.Radio
import cs.ok3vo.five9record.render.StatusRenderer
import cs.ok3vo.five9record.util.Mutex
import cs.ok3vo.five9record.util.Utc
import cs.ok3vo.five9record.util.logE
import cs.ok3vo.five9record.util.logI
import cs.ok3vo.five9record.util.logW
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean

// TODO: wake lock?, see MyLocation

class RecordingService: Service() {
    private val renderer by lazy { StatusRenderer(this) }
    private val locationManager by lazy { getSystemService(LOCATION_SERVICE) as LocationManager }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Sanitize intent
        if (intent == null || !intent.hasExtra(INTENT_STARTUP_DATA)) {
            // Likely being restarted by system, don't start up.
            logW("Empty Intent, not starting up")
            stopSelf()
            return START_NOT_STICKY
        }

        // Check this is the only instance
        if (!running.compareAndSet(false, true)) {
            logE("Another RecordingService is already running, not starting up")
            return stop()
        }

        // Check Radio
        if (!Radio.isRunning) {
            val msg = "Radio I/O not running while RecordingService starting up"
            logE(msg)
            channel.trySend(Result.failure(IllegalStateException(msg)))
            return stop()
        }

        logI("Starting recording service...")
        startForeground(1, createNotification())
        startLocationUpdates()

        @Suppress("DEPRECATION") // new method only from Tiramisu and up
        val startupData = intent.getParcelableExtra<StartupData>(INTENT_STARTUP_DATA)
        if (startupData == null) {
            logW("Intent doesn't carry StartupData, not starting up")
            return stop()
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                runRecording(startupData)
            } catch (e: Exception) {
                logE(RecordingService::class, "recording stopped with a failure", e)
                channel.trySend(Result.failure(e))
            } finally {
                stop()
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?) = null

    override fun onDestroy() {
        super.onDestroy()
        locationManager.removeUpdates(locationListener)
    }

    private fun createNotification(): Notification {
        val channelId = "five9record_channel"
        val channelName = "Five9 Record Service"

        val notificationManager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT)
        notificationManager.createNotificationChannel(channel)

        val intent = Intent(this, RecordingActivity::class.java)
        val pi = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, channelId)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setContentTitle("Recording in Progress")
            .setContentText("Five9 Record is recording an operation")
            .setSmallIcon(android.R.drawable.stat_notify_voicemail)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pi)
            .setAutoCancel(false)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun startLocationUpdates() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // FIXME: error
            return
        }

        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            GPS_MIN_TIME_MS,
            GPS_MIN_DISTANCE_M,
            locationListener,
        )

        val gnssEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        locationListener.setGnssEnabled(gnssEnabled)
        locationManager.registerGnssStatusCallback(locationListener, null)
    }

    private fun runRecording(startupData: StartupData) {
        val filename = recordingFilename()
        val audioDevice = startupData.audioDevice
        val encoder = RecordingEncoder(
            context = this,
            renderer = renderer,
            filename = filename,
            audioDeviceId = audioDevice,
            radioDelay = 100, // FIXME: configurable
        )

        logI("starting recording with audio dev $audioDevice to file $filename")
        encoder.record {
            // We call into Radio directly from the video thread to read status and render it as closely
            // to encoding it as possible, though there will still be some skew.
            val radioData = Radio.readRadioData()
            if (radioData == null) {
                logI("Recording stopped via Radio I/O")
                return@record null
            }

            val statusData = StatusData(
                radio = radioData,
                location = locationListener.getLastLocation(),
            )

            channel.trySend(Result.success(statusData))
            statusData
        }

        logI("recording stopped normally")
    }

    private fun stop(): Int {
        running.set(false)
        stopSelf()
        return START_NOT_STICKY
    }

    private fun recordingFilename(): String {
        val timestamp = Utc.now.format(filenameFormatter)
        return "${timestamp}.mp4"
    }

    private val locationListener = object : LocationListener, GnssStatus.Callback() {
        private val locationStatus = Mutex(LocationStatus())

        fun getLastLocation(): LocationStatus = locationStatus.lock { copy() }

        fun setGnssEnabled(enabled: Boolean) = locationStatus.lock { gnssEnabled = enabled }

        override fun onLocationChanged(location: android.location.Location) {
            logI(RecordingService::class, "received location: $location")
            locationStatus.lock {
                position = LocationStatus.Position.fromAndroid(location)
            }
        }

        @Deprecated("present for compatibility with older SDK levels")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

        override fun onSatelliteStatusChanged(status: GnssStatus) {
            locationStatus.lock {
                numSatellites = LocationStatus.NumSatellites.fromAndroid(status)
            }
        }

        override fun onProviderEnabled(provider: String) {
            if (provider == LocationManager.GPS_PROVIDER) {
                locationStatus.lock { gnssEnabled = true }
            }
        }

        override fun onProviderDisabled(provider: String) {
            if (provider == LocationManager.GPS_PROVIDER) {
                locationStatus.lock { gnssEnabled = false }
            }
        }
    }

    @Parcelize
    data class StartupData(
        val audioDevice: Int,
    ): Parcelable

    companion object {
        const val INTENT_STARTUP_DATA = "startup_data"
        const val GPS_MIN_TIME_MS = 10_000L
        const val GPS_MIN_DISTANCE_M = 10.0f

        private val running = AtomicBoolean(false)
        private val filenameFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
        private val channel = Channel<Result<StatusData>>(capacity = Channel.UNLIMITED)

        fun isRunning() = running.get()
        suspend fun receiveStatusData(): Result<StatusData> = channel.receive()
    }
}
