package cs.ok3vo.five9record.render

import cs.ok3vo.five9record.location.LocationStatus
import cs.ok3vo.five9record.radio.rig.MockedRadio
import cs.ok3vo.five9record.radio.Mode
import io.kotest.core.spec.style.StringSpec
import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.matchers.shouldBe

private fun position(lon: Double, lat: Double, accuracyRadius: Float? = null)
    = LocationStatus.Position(lon, lat, accuracyRadius)

class StatusRendererTest: StringSpec ({
    "formatFreq" {
        forAll(
            row(12_345_678L, "12.345.678 Hz"),
            row(1_234L, "1.234 Hz"),
            row(1L, "1 Hz"),
        ) {
            freq, expected ->
            MockedRadio
                .mockRadioData()
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
            MockedRadio
                .mockRadioData()
                .copy(mode = mode)
                .formatMode() shouldBe expected
        }
    }

    "formatPower" {
        MockedRadio
            .mockRadioData()
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
            row(LocationStatus(), "N/A", "GNSS location disabled"),
            row(
                LocationStatus(
                    gnssEnabled = true,
                    position = null,
                    numSatellites = LocationStatus.NumSatellites(1, 10),
                ),
                "Acquiring…",
                "1/10",
            ),
            row(
                LocationStatus(
                    gnssEnabled = true,
                    position = position(0.123456789, 50.123456789),
                    numSatellites = LocationStatus.NumSatellites(15, 30),
                ),
                "LJ50BC",
                "15/30 0.12346N 50.12346E",
            ),
            row(
                LocationStatus(
                    gnssEnabled = true,
                    position = position(0.123456789, 50.123456789, 16.555f),
                    numSatellites = LocationStatus.NumSatellites(15, 30),
                ),
                "LJ50BC",
                "15/30 0.12346N 50.12346E ±16.6m",
            ),
        ) {
            location, expectedQth, expectedDetail ->

            location.formatQth() shouldBe expectedQth
            location.formatGnssDetail() shouldBe expectedDetail
        }
    }
})
