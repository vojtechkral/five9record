package cs.ok3vo.five9record.recording

import android.content.Context

fun Context.recordingsDirectory()
    = getExternalFilesDir(null)!!

fun Context.listRecordings()
    = recordingsDirectory().listFiles { f -> f.extension == "mp4" }
    ?.sortedByDescending { it.lastModified() }
    ?: emptyList()
