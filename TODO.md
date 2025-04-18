# TODO

- Recordings browser
- Start recording dialog
  - QTH options (-> dialog)
- FIXMEs
- Wake lock? (Needed? -> likely not due to foreground service)
- Unit test FT-891 CAT parsing
- AOR AR8200 implementation
- Fix NumSatellites.usedInFix?
- Extract strings to resources
- Configurable codec bitrates?
- Logcat collection
- Theme / colours
- Bump deps
- DRY out catQuery/catCmd?
- Config option for coarse GNSS / gridsquare only / no QTH (privacy)
  - In meta track too
  - Android has the "approximate location" feature, cf. the permission dialog
- AAC stream not finalized properly
- Mux to MKV? https://github.com/Matroska-Org/jebml
  - eg.: https://gitlab.com/axet/android-audio-library/-/blob/master/src/main/java/com/github/axet/audiolibrary/encoders/FormatMKA_AAC.java

# Notes

JSON metadata can be extracted with:
```shell
ffmpeg -i file.mp4 -map 0:d:0 -c copy -f rawvideo metadata.json
```

FT-891 meter CAT reading have been yielding weird / unreliable numbers - not used.

# Done

- Encode metadata: https://developer.android.com/reference/android/media/MediaMuxer?hl=en#metadata-track
- Radio and recording error handling
  - Radio I/O can fail on construction and in encoding thread
  - Rec service can fail to start
  - Encoder can fail
- Display baud rate hint on connection error
- Fix lat, lon order
- Decouple num satellites from Location (display num before fix)
- Disable display rotation
- Error handling in StartRecAct serial opening
- Spinners -> choice dialog (MaterialAlertDialogBuilder.setSingleChoiceItems())
- Handle no audio device found
- Start recording dialog Opts persistence
- Error handling UI - produce a notification on error while recording
