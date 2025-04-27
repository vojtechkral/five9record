package cs.ok3vo.five9record.location

import io.kotest.core.spec.style.StringSpec
import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement

class LocationStatusTest: StringSpec({
    "maidenhead gridsquare" {
        forAll(
            row(50.0755, 14.4378, "JO70FB"),
            row(21.3069, -157.858, "BL11BH"),
            row(-41.2924, 174.779, "RE78JQ"),
        ) {
            lat, lon, expectedLocator ->
            val locator = LocationStatus.Position(lat, lon, null).toMaidenhead(true)
            locator shouldBe expectedLocator

            val noSubsquare = LocationStatus.Position(lat, lon, null).toMaidenhead(false)
            noSubsquare shouldBe expectedLocator.substring(0..3)
        }
    }

    "serialization with GNSS disabled" {
        val data = LocationStatus()

        val json = Json.encodeToJsonElement(data)
        val expected = Json.parseToJsonElement("""{
            "gnssEnabled": false,
            "position": null,
            "numSatellites": {
                "usedInFix": 0,
                "total": 0
            }
        }""".trimIndent())

        json shouldBe expected
    }

    "serialization with GNSS acquiring" {
        val data = LocationStatus(
            gnssEnabled = true,
            position = null,
            numSatellites = LocationStatus.NumSatellites(5, 10),
        )

        val json = Json.encodeToJsonElement(data)
        val expected = Json.parseToJsonElement("""{
            "gnssEnabled": true,
            "position": null,
            "numSatellites": {
                "usedInFix": 5,
                "total": 10
            }
        }""".trimIndent())

        json shouldBe expected
    }

    "serialization with GNSS position" {
        forAll(
            row(null, "null"),
            row(10.0f, "10.0"),
        ) {
            accuracy, accuracyJson ->
            val data = LocationStatus(
                gnssEnabled = true,
                position = LocationStatus.Position(50.0, 0.0, accuracy),
                numSatellites = LocationStatus.NumSatellites(5, 10),
            )

            val json = Json.encodeToJsonElement(data)
            val expected = Json.parseToJsonElement(
                """{
                    "gnssEnabled": true,
                    "position": {
                        "latitude": 50.0,
                        "longitude": 0.0,
                        "accuracyRadius": $accuracyJson
                    },
                    "numSatellites": {
                        "usedInFix": 5,
                        "total": 10
                    }
                }"""
            )

            json shouldBe expected
        }
    }
})
