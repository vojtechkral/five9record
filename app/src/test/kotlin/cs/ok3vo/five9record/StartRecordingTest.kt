package cs.ok3vo.five9record

import cs.ok3vo.five9record.location.LocationPrecision
import cs.ok3vo.five9record.radio.RadioType
import cs.ok3vo.five9record.util.Json
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class StartRecordingTest: StringSpec({
    "StartRecordingData serde default" {
        val data = StartRecordingData()
        val json = Json.encodeToString(data)

        json shouldEqualJson """{
            "radioType": "YAESU_FT_891",
            "baudRate": 4800,
            "audioDevice": -1,
            "locationPrecision": "FULL_LOCATION",
            "locationInMetatrack": true
        }"""
    }

    "StartRecordingData serde non-default" {
        val data = StartRecordingData(
            radioType = RadioType.AOR_AR8200,
            baudRate = 9600,
            audioDevice = 9001,
            locationPrecision = LocationPrecision.LOCATOR_SQUARE,
            locationInMetatrack = false,
        )
        val json = Json.encodeToString(data)

        json shouldEqualJson """{
            "radioType": "AOR_AR8200",
            "baudRate": 9600,
            "audioDevice": 9001,
            "locationPrecision": "LOCATOR_SQUARE",
            "locationInMetatrack": false
        }"""
    }

    "StartRecordingData serde unknown fields" {
        val json = """{
            "radioType": "YAESU_FT_891",
            "xxxxxxxxxxxxxxxxxxx": 9001
        }"""

        val data = Json.decodeFromString<StartRecordingData>(json)
        data shouldBe StartRecordingData()
    }
})
