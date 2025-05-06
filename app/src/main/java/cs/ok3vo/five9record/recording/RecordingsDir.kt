package cs.ok3vo.five9record.recording

import android.content.Context
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

fun Context.recordingsDirectory(): File
    = getExternalFilesDir(null)!!

fun Context.listRecordings()
    = recordingsDirectory().listFiles { f -> f.extension == "mp4" }
    ?.sortedByDescending { it.lastModified() }
    ?: emptyList()

private val filenameFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")

fun Context.recordingFile(time: Instant): File {
    val timestamp = filenameFormatter.format(time.atOffset(ZoneOffset.UTC))
    val filename = "${timestamp}.mp4"
    return File(recordingsDirectory(), filename).absoluteFile
}
