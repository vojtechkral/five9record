# TODO

- Fix Main screen back btn
- FIXMEs
- Prune deps
- Unit test FT-891 CAT parsing
- Display audio RMS in recording activity (val rms = sqrt(buffer.map { it * it }.average()))
- Logcat collection
- Theme / colours

- Configurable codec bitrates?
- DRY out catQuery/catCmd?
- AOR AR8200 implementation
- AAC stream not finalized properly
- Mux to MKV? https://github.com/Matroska-Org/jebml
  - eg.: https://gitlab.com/axet/android-audio-library/-/blob/master/src/main/java/com/github/axet/audiolibrary/encoders/FormatMKA_AAC.java

# Random Notes

- JSON metadata can be extracted with:
  ```shell
  ffmpeg -i file.mp4 -map 0:d:0 -c copy -f rawvideo metadata.json
  ```

- FT-891 meter CAT reading have been yielding weird / unreliable numbers - not used.

- Icons are from https://www.flaticon.com/

- onClick = null reduces the size of Radio/Checkboxes

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
- Wake lock (fix silent recording)
- Fix NumSatellites.usedInFix?
- Start recording dialog persistence
- Config option for coarse GNSS / gridsquare only / no QTH (privacy)
  - In meta track too
  - Android has the "approximate location" feature, cf. the permission dialog
- Recordings browser
- Recordings location hint
- Convert to Compose
- Extract strings to resources
- Fix coarse location support
- Fix no USB error
- String resources for baud rate hints
