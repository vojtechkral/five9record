package cs.ok3vo.five9record.recording

import androidx.appcompat.app.AppCompatActivity
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ComponentName
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import cs.ok3vo.five9record.databinding.ActivityRecordingBinding
import cs.ok3vo.five9record.radio.Radio
import cs.ok3vo.five9record.render.StatusRenderer
import cs.ok3vo.five9record.util.Utc
import cs.ok3vo.five9record.util.logE
import cs.ok3vo.five9record.util.logI
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime

class RecordingActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRecordingBinding
    private lateinit var startTime: Instant
    private val renderer by lazy { StatusRenderer(this) }
    private val previewBitmap = Bitmap.createBitmap(StatusRenderer.WIDTH, StatusRenderer.HEIGHT, Bitmap.Config.RGB_565)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // FIXME: take from first status data received instead
        startTime = Instant.now()

        onBackPressedDispatcher.addCallback { stopRequest() }

        binding = ActivityRecordingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnStop.setOnClickListener { stopRequest() }

        lifecycleScope.launch {
            try {
                uiUpdateLoop()
            } catch (e: Exception) {
                logE(RecordingActivity::class, "recording stopped with a failure", e)
                // FIXME: display error info / create notification to alert the user recording
                // stopped with an error.
            } finally {
                stopRecording() // FIXME: this finishes, we might want error info displayed
            }
        }
    }

    private suspend fun uiUpdateLoop() = repeatOnLifecycle(Lifecycle.State.STARTED) {
        while (RecordingService.isRunning()) {
            updateDuration()

            RecordingService
                .receiveStatusData()
                .getOrThrow()
                .also { renderStatus(it) }
            // TODO: maybe don't render status?, display meta info about recording
            //      - filename, size etc.
            // TODO: make status toggle-able?
        }
    }

    private fun updateDuration() {
        val duration = Duration.between(startTime, Instant.now())
        val hours = duration.toHours()
        val minutes = duration.toMinutes() % 60
        val seconds = duration.seconds % 60
        val durationText = "%02d:%02d:%02d".format(hours, minutes, seconds)
        binding.tvTime.text = durationText
    }

    private fun renderStatus(statusData: StatusData) {
        val canvas = Canvas(previewBitmap)
        renderer.render(statusData, canvas)
        binding.ivPreview.setImageBitmap(previewBitmap)
    }

    private fun stopRequest() {
        AlertDialog.Builder(this)
            .setTitle("Stop recording?")
            .setMessage("Are you sure you want to stop the current recording?")
            .setPositiveButton("Yes") { _, _ ->
                stopRecording()
            }
            .setNegativeButton("No") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun stopRecording() {
        logI("stopping recording...")
        Radio.stop()
        logI("recording stopped")
        finish() // FIXME: is there an error alert active?
    }
}
