package cs.ok3vo.five9record.recording

import android.content.Context

fun Context.recordingsDirectory()
    = getExternalFilesDir(null)!!
