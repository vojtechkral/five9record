    package cs.ok3vo.five9record.recording

import android.Manifest
import android.R
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Parcelable
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import cs.ok3vo.five9record.location.LocationStatus
import cs.ok3vo.five9record.radio.Radio
import cs.ok3vo.five9record.render.StatusRenderer
import cs.ok3vo.five9record.ui.NotificationBuilder
import cs.ok3vo.five9record.ui.acquire
import cs.ok3vo.five9record.ui.release
import cs.ok3vo.five9record.util.Mutex
import cs.ok3vo.five9record.util.Utc
import cs.ok3vo.five9record.util.logE
import cs.ok3vo.five9record.util.logI
import cs.ok3vo.five9record.util.logW
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicReference

class RecordingService: Service() {
    private val renderer by lazy { StatusRenderer(this) }
    private val locationManager by lazy { getSystemService(LOCATION_SERVICE) as LocationManager }
    private val powerManager by lazy { getSystemService(POWER_SERVICE) as PowerManager }
    private var wakeLock = AtomicReference<PowerManager.WakeLock?>(null)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Sanitize intent
        if (intent == null || !intent.hasExtra(INTENT_STARTUP_DATA)) {
            // Likely being restarted by system, don't start up.
            logW("Empty Intent, not starting up")
            stopSelf()
            return START_NOT_STICKY
        }

        // Check this is the only instance
        if (!setStateStartup()) {
            logE("Another RecordingService is already running, not starting up")
            return stop()
        }

        // Check Radio
        if (!Radio.isRunning) {
            val msg = "Radio I/O not running while RecordingService starting up"
            logE(msg)
            setError(IllegalStateException(msg))
            return stop()
        }

        logI("Starting recording service...")
        startForeground(1, serviceNotification())
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
                setError(e)
                errorNotification(e)
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
        wakeLock.release()
    }

    private fun setStateStartup(): Boolean {
        return statePrivate.compareAndSet(State.Stopped, State.StartingUp)
            || statePrivate.compareAndSet(State.Error, State.StartingUp)
    }

    private fun setStatusData(data: StatusData) {
        State.Running.statusData.set(data)
        statePrivate.set(State.Running)
    }

    private fun setError(e: Exception) {
        State.Error.error.set(e)
        statePrivate.set(State.Error)
    }

    private fun serviceNotification()
        = NotificationBuilder(
            context = this,
            title = "Recording in Progress",
            text = "Five9 Record is recording an operation",
            icon = R.drawable.stat_notify_voicemail,
            silent = true,
            ongoing = true,
            priority = NotificationCompat.PRIORITY_DEFAULT,
            targetActivity = RecordingActivity::class,
        ).build()

    private fun errorNotification(e: Exception)
        = NotificationBuilder(
            context = this,
            title = "Recording Error",
            text = "The recording was stopped due to an error:\n${e.message}",
            icon = R.drawable.stat_notify_error,
            silent = false,
            ongoing = false,
            priority = NotificationCompat.PRIORITY_MAX,
            targetActivity = RecordingActivity::class,
        ).notify()

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

        wakeLock.acquire(powerManager, WAKE_LOCK_TAG)

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

            setStatusData(statusData)
            statusData
        }

        logI("recording stopped normally")
    }

    private fun stop(): Int {
        wakeLock.release()

        Radio.stop() // in case recording was interrupted by a non-radio cause (error)

        statePrivate.compareAndSet(State.StartingUp, State.Stopped)
        statePrivate.compareAndSet(State.Running, State.Stopped)

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

        override fun onLocationChanged(location: Location) {
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

    sealed class State {
        /** Ground state. */
        data object Stopped: State()
        /**
         * After service has started but before recording actually starts and
         * status data becomes available.
         */
        data object StartingUp: State()

        /** Recording running, status data are being published. */
        data object Running: State() {
            val statusData: AtomicReference<StatusData> = AtomicReference(StatusData.dummyValue)
        }

        /** Recording ended with an error. */
        data object Error: State() {
            val error: AtomicReference<Exception> = AtomicReference(RuntimeException())
        }
    }

    companion object {
        const val INTENT_STARTUP_DATA = "startup_data"
        const val GPS_MIN_TIME_MS = 10_000L
        const val GPS_MIN_DISTANCE_M = 10.0f

        private const val WAKE_LOCK_TAG = "five9record:recording"
        private val filenameFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")

        private val statePrivate: AtomicReference<State> = AtomicReference(State.Stopped)
        val state: State get() = statePrivate.get()

        fun takeError(): Exception?
            = if (statePrivate.compareAndSet(State.Error, State.Stopped)) {
                State.Error.error.get()
            } else {
                null
            }
    }
}
