package cs.ok3vo.five9record.recording

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.LocationManager
import android.os.Build
import android.os.Parcelable
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import cs.ok3vo.five9record.MainActivity
import cs.ok3vo.five9record.R
import cs.ok3vo.five9record.location.LocationListener
import cs.ok3vo.five9record.location.LocationPrecision
import cs.ok3vo.five9record.radio.Radio
import cs.ok3vo.five9record.ui.video.VideoView
import cs.ok3vo.five9record.ui.NotificationBuilder
import cs.ok3vo.five9record.util.acquire
import cs.ok3vo.five9record.util.release
import cs.ok3vo.five9record.util.logE
import cs.ok3vo.five9record.util.logI
import cs.ok3vo.five9record.util.logW
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import java.io.File
import java.util.concurrent.atomic.AtomicReference

class RecordingService: Service() {
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(1, serviceNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(1, serviceNotification())
        }

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
            icon = R.drawable.voicemail,
            silent = true,
            ongoing = true,
            priority = NotificationCompat.PRIORITY_DEFAULT,
            targetActivity = MainActivity::class,
        ).build()

    private fun errorNotification(e: Exception)
        = NotificationBuilder(
            context = this,
            title = "Recording Error",
            text = "The recording was stopped due to an error:\n${e.message}",
            icon = R.drawable.warning,
            silent = false,
            ongoing = false,
            priority = NotificationCompat.PRIORITY_MAX,
            targetActivity = MainActivity::class,
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
        val file = startupData.outputFile
        State.Running.outputFile = file
        State.Running.locationPrecision = startupData.locationPrecision

        val audioDevice = startupData.audioDevice
        val videoView = VideoView(this, startupData.locationPrecision)
        val encoder = RecordingEncoder(
            context = this,
            videoView = videoView,
            outputFile = file,
            audioDeviceId = audioDevice,
            radioDelay = 100, // FIXME: configurable
            locationInMetatrack = startupData.locationInMetatrack,
        )

        wakeLock.acquire(powerManager, WAKE_LOCK_TAG)

        logI("starting recording with audio dev $audioDevice to file $file")
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

    private val locationListener = LocationListener()

    @Parcelize
    data class StartupData(
        val audioDevice: Int,
        val outputFile: File,
        val locationPrecision: LocationPrecision,
        val locationInMetatrack: Boolean,
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
            var locationPrecision = LocationPrecision.FULL_LOCATION
            var outputFile: File = File("/dev/null")
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
