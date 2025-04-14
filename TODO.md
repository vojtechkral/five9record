# TODO

- DRY out catQuery/catCmd?
- Error handling UI - produce a notification on error while recording
- Start recording dialog
  - Opts persistence
  - QTH options (-> dialog)
- Wake lock? (Needed?)
- Unit test FT-891 CAT parsing
- AAC stream not finalized properly
- Fix NumSatellites.usedInFix?
- Extract strings to resources
- Logcat collection
- Theme / colours
- Bump deps
- Config option for coarse GNSS / gridsquare only / no QTH (privacy)
  - In meta track too
  - Android has the "approximate location" feature, cf. the permission dialog

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
