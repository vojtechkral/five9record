package cs.ok3vo.five9record.recording

import androidx.appcompat.app.AppCompatActivity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import androidx.activity.addCallback
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import cs.ok3vo.five9record.databinding.ActivityRecordingBinding
import cs.ok3vo.five9record.radio.Radio
import cs.ok3vo.five9record.render.StatusRenderer
import cs.ok3vo.five9record.util.elapsed
import cs.ok3vo.five9record.util.logE
import cs.ok3vo.five9record.util.logI
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

// TODO: Display meta info about recording - filename, size etc.
// TODO: Refactor preview, make toggle-able?

class RecordingActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRecordingBinding
    private lateinit var startTime: Instant
    private val renderer by lazy { StatusRenderer(this) }
    private val previewBitmap = createPreviewBitmap()
    private var errorDialog: AlertDialog? = null

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
            } catch (e: InterruptedException) {
                // loop stopped normally
            } catch (e: Exception) {
                logE(RecordingActivity::class, "recording stopped with a failure", e)
                errorDialog(e)
            } finally {
                recordingStopped()
            }
        }
    }

    /**
     * The update loop is wrapped in a repeatOnLifecycle in order to automatically
     * not perform updates if the activity is not active, to reduce load.
     */
    private suspend fun uiUpdateLoop() = repeatOnLifecycle(Lifecycle.State.STARTED) {
        while (true) {
            updateDuration()

            when (RecordingService.state) {
                RecordingService.State.StartingUp -> { /* delay */ }
                RecordingService.State.Running -> renderStatus()
                RecordingService.State.Error -> RecordingService.takeError()?.let { throw it }
                RecordingService.State.Stopped -> throw InterruptedException()
            }

            delay(100)
        }
    }

    private fun updateDuration() {
        val duration = startTime.elapsed()
        val hours = duration.toHours()
        val minutes = duration.toMinutes() % 60
        val seconds = duration.seconds % 60
        val durationText = "%02d:%02d:%02d".format(hours, minutes, seconds)
        binding.tvTime.text = durationText
    }

    private fun renderStatus() {
        val statusData = RecordingService.State.Running.statusData.get()
        val canvas = Canvas(previewBitmap)
        renderer.render(statusData, canvas)
        binding.ivPreview.setImageBitmap(previewBitmap)
    }

    private fun stopRequest() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Stop recording?")
            .setMessage("Are you sure you want to stop the current recording?")
            .setPositiveButton("Yes") { _, _ ->
                logI("stopping recording...")
                Radio.stop()
            }
            .setNegativeButton("No") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun recordingStopped() {
        logI("recording stopped")
        if (errorDialog == null) {
            finish()
        }
    }

    private fun errorDialog(e: Exception) {
        errorDialog = MaterialAlertDialogBuilder(this)
            .setTitle("Recording Error")
            .setMessage(
                """The recording was stopped due to an error:
                    |
                    |${e.message}
                """.trimMargin()
            )
            .setPositiveButton("Ok") {
                dialog, _ ->
                dialog.dismiss()
                finish()
            }
            .create()
            .also { it.show() }
    }

    companion object {
        fun createPreviewBitmap() = Bitmap.createBitmap(
            StatusRenderer.WIDTH,
            StatusRenderer.HEIGHT,
            Bitmap.Config.RGB_565,
        )
    }
}
