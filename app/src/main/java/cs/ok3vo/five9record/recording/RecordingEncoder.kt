package cs.ok3vo.five9record.recording

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Process
import android.view.Surface
import cs.ok3vo.five9record.ui.video.VideoView
import cs.ok3vo.five9record.util.Mutex
import cs.ok3vo.five9record.util.logD
import cs.ok3vo.five9record.util.logE
import cs.ok3vo.five9record.util.logI
import cs.ok3vo.five9record.util.logW
import cs.ok3vo.five9record.util.throwError
import kotlinx.serialization.json.Json
import java.io.File
import java.lang.IllegalStateException
import java.nio.ByteBuffer
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

class RecordingEncoder(
    context: Context,
    private val videoView: VideoView,
    private val outputFile: File,
    private val audioDeviceId: Int,
    private val radioPollInterval: Int,
    private val locationInMetatrack: Boolean,
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private lateinit var surface: Surface
    private lateinit var vcodec: MediaCodec
    private lateinit var adevice: AudioDeviceInfo
    private lateinit var acodec: MediaCodec
    private lateinit var muxer: Mutex<MediaMuxer>
    private var stateSync = StateSync(tracks = 2)

    /**
     * Starts media encoding.
     *
     * This is a blocking (non-async) operation, the method may perform some I/O
     * and waits on encoding sub-threads to finalize.
     *
     * The supplied closure will be called periodically to fetch status data.
     * The closure may throw an exception. If this happens, recording is stopped
     * and the exception is rethrown by `record()`.
     */
    fun record(statusDataFn: () -> StatusData?) {
        logI("initializing encoder...")

        // First make sure we have the audio device
        adevice = findAudioDevice()

        // Set up video
        val vformat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, VIDEO_W, VIDEO_H)
            .apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_RATE)
                setInteger(MediaFormat.KEY_FRAME_RATE, VIDEO_FPS)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, VIDEO_I_INTERVAL)
            }

        vcodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        vcodec.configure(vformat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        surface = vcodec.createInputSurface()
        vcodec.start()

        // Set up audio
        val aformat = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(AUDIO_SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()

        val amformat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, aformat.sampleRate, 1)
            .apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_RATE)
            }
        acodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        acodec.configure(amformat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        acodec.start()

        @SuppressLint("MissingPermission")
        val audioRecord = AudioRecord.Builder()
            .setAudioFormat(aformat)
            .build()
        audioRecord.setPreferredDevice(adevice)

        // Setup up metadata track format
        val metaformat = MediaFormat()
        metaformat.setString(MediaFormat.KEY_MIME, META_MIME_TYPE)

        // Set up the muxer
        // We can't add the tracks and start the muxer here, because the MediaFormats aren't
        // fully initialized until the codec starts processing data (at least for video).
        // This is pretty annoying and the StateSync utility is written to address that.
        muxer = Mutex(MediaMuxer(outputFile.absolutePath, MUX_FORMAT))

        val vthread = VideoThread(statusDataFn, metaformat).also { it.start() }
        val athread = AudioThread(audioRecord, aformat).also { it.start() }

        stateSync.threadsLatch.await()
        logI("Track threads done")

        muxer.lock {
            release() // will also call stop() if started
        }

        vthread.exception?.let { throw it }
        athread.exception?.let { throw it }
    }

    private fun findAudioDevice() =
        audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).find {
            it.id == audioDeviceId
        } ?: "Could not find audio device $audioDeviceId".throwError()

    inner class VideoThread(
        private val statusDataFn: () -> StatusData?,
        metaformat: MediaFormat,
    ): TrackThread(stateSync = stateSync, muxer = muxer, codec = vcodec, metaformat = metaformat) {
        private var firstTimestamp: Instant? = null
        private var ptsQueue: ArrayDeque<Long> = ArrayDeque(16)
        private var lastPts = 0L
        private val metaBufferInfo = MediaCodec.BufferInfo()
        private val metaBuffer = ByteBuffer.allocate(META_BUFFER_SIZE)

        override fun trackLoop() = try {
            while (!stateSync.stop.get()) {
                drainBuffers()

                val statusData = try {
                    statusDataFn()
                } catch (e: Exception) {
                    setException(e)
                    stateSync.stop.set(true)
                    break
                }

                val pts = if (statusData == null) {
                    stateSync.stop.set(true)
                    break
                } else {
                    enqueuePts(statusData.timestamp)
                }

                val canvas = surface.lockCanvas(null)
                try {
                    videoView.updateData(statusData)
                    videoView.renderToCanvas(canvas)
                } finally {
                    surface.unlockCanvasAndPost(canvas)
                }

                writeMetadata(statusData, pts, locationInMetatrack)

                sleep(radioPollInterval.toLong())
            }

            finalizeStream()
        } finally {
            logI("Video thread wrapping up")
            codec.stop()
            codec.release()
            surface.release()
        }

        private fun drainBuffers() {
            while (true) {
                val bufferId = codec.dequeueOutputBuffer(bufferInfo, VIDEO_BUFFER_TIMEOUT)

                if (bufferId >= 0) {
                    val encodedData = codec.getOutputBuffer(bufferId)

                    if (encodedData != null) {

                        // Assign PTS to the dequeued frame data
                        val pts = ptsQueue.removeFirstOrNull()
                        if (pts != null) {
                            bufferInfo.presentationTimeUs = pts
                            lastPts = pts
                        } else {
                            logW("PTS queue under-run, falling back to last PTS seen: $lastPts")
                            bufferInfo.presentationTimeUs = lastPts
                        }

                        ensureTrackAdded()
                        ensureMuxerStarted()
                        muxer.lock {
                            writeSampleData(trackId, encodedData, bufferInfo)
                        }
                    }

                    codec.releaseOutputBuffer(bufferId, false)
                }

                when (bufferId) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        // Should happen once before receiving buffers
                        ensureTrackAdded()
                    }

                    MediaCodec.INFO_TRY_AGAIN_LATER -> break

                    else -> { /* ignored */ }
                }
            }
        }

        private fun finalizeStream() {
            try {
                codec.signalEndOfInputStream()
            } catch (_: IllegalStateException) {
                // codec already not executing
                return
            }

            drainBuffers()
        }

        private fun enqueuePts(timestamp: Instant): Long {
            if (firstTimestamp == null) {
                firstTimestamp = timestamp
                ptsQueue.addLast(0L)
                return 0
            } else {
                val diff = Duration.between(firstTimestamp!!, timestamp)

                if (diff.isNegative) {
                    logE("Non-monotonic timestamps passed to encoder")
                    firstTimestamp = timestamp
                    ptsQueue.addLast(0L)
                    return 0
                } else {
                    val pts = diff.toNanos() / 1_000
                    ptsQueue.addLast(pts)
                    return pts
                }
            }
        }

        private fun writeMetadata(statusData: StatusData, pts: Long, includeLocation: Boolean) {
            if (!stateSync.muxerStarted.get()) {
                // Don't attempt to write metadata until the muxer is started
                return
            }

            val data = statusData.copy(location = statusData.location.copy())
            if (!includeLocation) {
                data.location.gnssEnabled = false
                data.location.position = null
            }

            val json = Json.encodeToString(data)
            metaBuffer.clear()
            metaBuffer.put(json.toByteArray(Charsets.UTF_8))
            metaBuffer.put('\n'.code.toByte())

            metaBufferInfo.presentationTimeUs = pts
            metaBufferInfo.offset = 0
            metaBufferInfo.flags = 0
            metaBufferInfo.size = metaBuffer.position()

            muxer.lock {
                writeSampleData(metaTrackId, metaBuffer, metaBufferInfo)
            }
        }
    }

    inner class AudioThread(
        private val audioRecord: AudioRecord,
        format: AudioFormat,
    ): TrackThread(
        stateSync = stateSync, muxer = muxer, codec = acodec,
    ) {
        private val sampleRate = format.sampleRate
        private val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, format.channelMask, format.encoding)
        private val recBuffer = ByteArray(minBufferSize)

        private var samplesRead = 0L
        private val pts get() = 1_000_000L * samplesRead / sampleRate

        override fun trackLoop() = try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)

            audioRecord.startRecording()

            while (!stateSync.stop.get()) {
                readAudio()
                drainBuffers()
            }

            finalizeStream()
        } finally {
            logI("Audio thread wrapping up")
            audioRecord.stop()
            codec.stop()
            codec.release()
        }

        private fun readAudio() {
            // Read audio data from the recorder
            //
            // NB. it's ok to use this the overload even though doc says
            // it's to be used with 8-bit PCM - we're just passing bytes around so we want
            // the 16bit chunks broken down to bytes. We just need to divide size read by 2
            // when calculating number of samples for PTS calc.
            val sizeRead = audioRecord.read(recBuffer, 0, recBuffer.size)

            if (sizeRead == 0) {
                return
            } else if (sizeRead < 0) {
                logE("AudioRecord.read error: $sizeRead")
                "Error reading audio data: $sizeRead".throwError()
            }

            // Write audio data to the codec
            var offset = 0
            while (offset < sizeRead) {
                val inBufferId = codec.dequeueInputBuffer(AUDIO_BUFFER_TIMEOUT)

                if (inBufferId < 0) {
                    // no buffer currently available
                    return
                }

                val inBuffer = codec.getInputBuffer(inBufferId)
                    ?: "Received invalid audio codec input buffer".throwError()
                inBuffer.clear()
                val putSize = min(inBuffer.remaining(), sizeRead - offset)
                inBuffer.put(recBuffer, offset, putSize)
                offset += putSize

                val pts = pts
                codec.queueInputBuffer(inBufferId, 0, putSize, pts, 0)
                samplesRead += putSize / 2  // 16bit PCM => 1 sample = 2 bytes
            }
        }

        private fun drainBuffers() {
            while (true) {
                val bufferId = codec.dequeueOutputBuffer(bufferInfo, AUDIO_BUFFER_TIMEOUT)

                if (bufferId >= 0) {
                    val buffer = codec.getOutputBuffer(bufferId)!!
                    val encodedData = ByteArray(bufferInfo.size)
                    buffer.get(encodedData)
                    buffer.clear()

                    ensureTrackAdded()
                    ensureMuxerStarted()
                    muxer.lock {
                        writeSampleData(trackId, buffer, bufferInfo)
                    }

                    codec.releaseOutputBuffer(bufferId, false)

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM > 0) {
                        logD("BUFFER_FLAG_END_OF_STREAM received, terminating codec loop")
                        break
                    }
                }

                when (bufferId) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        // should happen before receiving buffers, and should only happen once
                        ensureTrackAdded()
                    }

                    MediaCodec.INFO_TRY_AGAIN_LATER -> break

                    else -> { /* ignored */ }
                }
            }
        }

        /**
         * Despite all my efforts, this actually still doesn't properly finalize the AAC stream.
         * Tried everything, no idea why it's still not finalized. Might be a muxing problem.
         */
        private fun finalizeStream() {
            drainBuffers()

            val inBufferId = codec.dequeueInputBuffer(AUDIO_BUFFER_TIMEOUT)
            if (inBufferId >= 0) {
                val inBuffer = codec.getInputBuffer(inBufferId)
                    ?: "Received invalid audio codec input buffer".throwError()
                inBuffer.clear()
                codec.queueInputBuffer(inBufferId, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            } else {
                logE("could not get input buffer to finalize stream: $inBufferId")
            }

            // Drain buffers again to apply the ending sequence:
            drainBuffers()
        }
    }

    companion object {
        const val VIDEO_W = 640
        const val VIDEO_H = 480
        private const val VIDEO_FPS = 10
        private const val VIDEO_RATE = 2_000_000
        private const val VIDEO_I_INTERVAL = 1
        private const val VIDEO_BUFFER_TIMEOUT = 10_000L

        private const val AUDIO_SAMPLE_RATE = 44100
        private const val AUDIO_RATE = 128_000
        private const val AUDIO_BUFFER_TIMEOUT = 10_000L

        private const val META_MIME_TYPE = "application/json"
        private const val META_BUFFER_SIZE = 4096

        private const val MUX_FORMAT = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
    }
}

class StateSync(tracks: Int) {
    val threadsLatch = CountDownLatch(tracks)
    val tracksLatch = CountDownLatch(tracks)
    val muxerStarted = AtomicBoolean(false)
    val stop = AtomicBoolean(false)
}

abstract class TrackThread(
    private val stateSync: StateSync,
    private val muxer: Mutex<MediaMuxer>,
    protected val codec: MediaCodec,
    protected val metaformat: MediaFormat? = null,
): Thread() {
    protected val bufferInfo = MediaCodec.BufferInfo()
    protected var trackId = -1
        private set
    protected var metaTrackId = -1
        private set

    var exception: Exception? = null
        private set

    override fun run() = try {
        trackLoop()
    } catch (e: Exception) {
        logE("Exception in track thread", e)
        setException(e)
        stateSync.stop.set(true)
    } finally {
        stateSync.threadsLatch.countDown()
    }

    protected abstract fun trackLoop()

    protected fun ensureTrackAdded() {
        if (trackId == -1) {
            val outputFormat = codec.outputFormat

            muxer.lock {
                trackId = addTrack(outputFormat)
                metaformat?.let {
                    // Additionally also add a metadata track if format has been provided for one
                    // (we do this for video only):
                    metaTrackId = addTrack(it)
                }
            }

            stateSync.tracksLatch.countDown()
        }
    }

    protected fun ensureMuxerStarted() {
        // muxer can only be started once all tracks have been added:
        stateSync.tracksLatch.await()
        if (stateSync.muxerStarted.compareAndSet(false, true)) {
            muxer.lock { start() }
        }
    }

    protected fun setException(e: Exception) {
        // don't overwrite an earlier exception, if any
        if (exception == null) {
            exception = e
        }
    }
}
