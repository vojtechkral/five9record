package cs.ok3vo.five9record.render

import cs.ok3vo.five9record.location.LocationPrecision
import cs.ok3vo.five9record.location.LocationStatus
import cs.ok3vo.five9record.radio.rig.MockedRadio
import cs.ok3vo.five9record.radio.Mode
import cs.ok3vo.five9record.radio.RadioData
import cs.ok3vo.five9record.radio.rig.MockedRadio.Companion.name
import cs.ok3vo.five9record.ui.video.format
import cs.ok3vo.five9record.ui.video.formatFreq
import cs.ok3vo.five9record.ui.video.formatGnssCoords
import cs.ok3vo.five9record.ui.video.formatLat
import cs.ok3vo.five9record.ui.video.formatLon
import cs.ok3vo.five9record.ui.video.formatMode
import cs.ok3vo.five9record.ui.video.formatPower
import cs.ok3vo.five9record.ui.video.formatQth
import io.kotest.core.spec.style.StringSpec
import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.matchers.shouldBe
import kotlin.random.Random

private fun position(lon: Double, lat: Double, accuracyRadius: Float? = null)
    = LocationStatus.Position(lon, lat, accuracyRadius)

fun RadioData.Companion.random() = RadioData(
    rig = name,
    freq = (14200000L..14350000L).random(),
    mode = Mode.USB,
    tx = Random.nextBoolean(),
    power = 40,
)

class VideoViewTest: StringSpec ({
    "formatFreq" {
        forAll(
            row(12_345_678L, "12.345.678 Hz"),
            row(1_234L, "1.234 Hz"),
            row(1L, "1 Hz"),
        ) {
            freq, expected ->
            RadioData.random()
                .copy(freq = freq)
                .formatFreq() shouldBe expected
        }
    }

    "formatMode" {
        forAll(
            row(Mode.USB, "USB"),
            row(Mode.CW, "CW"),
            row(Mode.OTHER, "Unknown"),
        ) {
            mode, expected ->
            RadioData.random()
                .copy(mode = mode)
                .formatMode() shouldBe expected
        }
    }

    "formatPower" {
        RadioData
            .random()
            .copy(power = 123)
            .formatPower() shouldBe "123W"
    }

    "formatLon and formatLat" {
        forAll(
            row(position(50.0755, 14.4378), "50.07550N", "14.43780E"),
            row(position(-41.2924, 174.779), "41.29240S", "174.77900E"),
            row(position(21.3069, -157.858), "21.30690N", "157.85800W"),
        ) {
            pos, expectedLat, expectedLon ->
            pos.formatLat() shouldBe expectedLat
            pos.formatLon() shouldBe expectedLon
        }
    }

    "formatQth and formatGnssDetail" {
        forAll(
            row(LocationStatus(), "N/A", null, ""),
            row(
                LocationStatus(
                    gnssEnabled = true,
                    coarse = false,
                    position = null,
                    numSatellites = LocationStatus.NumSatellites(1, 10),
                ),
                "Acquiring…",
                "1/10",
                "",
            ),
            row(
                LocationStatus(
                    gnssEnabled = true,
                    coarse = false,
                    position = position(0.123456789, 50.123456789),
                    numSatellites = LocationStatus.NumSatellites(15, 30),
                ),
                "LJ50BC",
                "15/30",
                "0.12346N 50.12346E",
            ),
            row(
                LocationStatus(
                    gnssEnabled = true,
                    coarse = false,
                    position = position(0.123456789, 50.123456789, 16.555f),
                    numSatellites = LocationStatus.NumSatellites(15, 30),
                ),
                "LJ50BC",
                "15/30",
                "0.12346N 50.12346E ±16.6m",
            ),
            row(
                LocationStatus(
                    gnssEnabled = true,
                    coarse = true,
                    position = position(0.123456789, 50.123456789, 2000.0f),
                    numSatellites = null,
                ),
                "LJ50BC",
                null,
                "0.12346N 50.12346E ±2000.0m", // will not be actually shown
            ),
        ) {
            location, expectedQth, expectedNumSats, expectedCoords ->

            val precision = LocationPrecision.FULL_LOCATION
            location.formatQth(precision) shouldBe expectedQth
            location.numSatellites?.format() shouldBe expectedNumSats
            location.formatGnssCoords(precision) shouldBe expectedCoords
        }
    }

    "formatQth and formatGnssDetail precision config" {
        val location = LocationStatus(
            gnssEnabled = true,
            coarse = false,
            position = position(0.123456789, 50.123456789),
            numSatellites = LocationStatus.NumSatellites(15, 30),
        )
        forAll(
            row(LocationPrecision.FULL_LOCATION, "LJ50BC", "0.12346N 50.12346E"),
            row(LocationPrecision.LOCATOR_SUBSQUARE, "LJ50BC", ""),
            row(LocationPrecision.LOCATOR_SQUARE, "LJ50", ""),
        ) {
            precision, expectedQth, expectedCoords ->
            location.formatQth(precision) shouldBe expectedQth
            location.formatGnssCoords(precision) shouldBe expectedCoords
        }
    }
})
