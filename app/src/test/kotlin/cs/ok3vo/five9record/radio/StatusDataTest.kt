package cs.ok3vo.five9record.radio

import cs.ok3vo.five9record.location.LocationStatus
import cs.ok3vo.five9record.recording.StatusData
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import java.time.Instant

class StatusDataTest: StringSpec({
    "serialization" {
        val data = StatusData(
            radio = RadioData(
                rig = "test",
                freq = 14_000_000,
                mode = Mode.USB,
                tx = false,
                power = 10,
            ),
            location = LocationStatus(
                gnssEnabled = true,
                coarse = false,
                position = LocationStatus.Position(50.0, 0.0, 5.0f),
                numSatellites = LocationStatus.NumSatellites(7, 10),
            ),
            timestamp = Instant.EPOCH,
        )

        val json = Json.encodeToJsonElement(data)
        val expected = Json.parseToJsonElement("""{
            "radio": {
                "rig": "test",
                "freq": 14000000,
                "mode": "USB",
                "tx": false,
                "power": 10
            },
            "location": {
                "gnssEnabled": true,
                "coarse": false,
                "position": {
                    "latitude": 50.0,
                    "longitude": 0.0,
                    "accuracyRadius": 5.0
                },
                "numSatellites": {
                    "usedInFix": 7,
                    "total": 10
                } 
            },
            "timestamp": "1970-01-01T00:00:00Z"
        }""".trimIndent())

        json shouldBe expected
    }
})
