package cs.ok3vo.five9record.location

import kotlinx.serialization.Serializable

@Serializable
data class LocationStatus(
    var gnssEnabled: Boolean,
    /** Current position coordinates, only non-null when position has been obtained. */
    var position: Position?,
    /** Current number of GNSS satellites observed. */
    var numSatellites: NumSatellites,
) {
    // Not using default values because Json skips over them by default :/
    constructor(): this(
        gnssEnabled = false,
        position = null,
        numSatellites = NumSatellites(0, 0)
    )

    @Serializable
    data class Position(
        val latitude: Double,
        val longitude: Double,
        val accuracyRadius: Float?
    ) {
        fun toMaidenhead(): String {
            // Shift longitude to 0–360 scale, latitude to 0-180
            var lat = latitude + 90.0
            var lon = longitude + 180.0

            // Field: AA-RR
            val fieldLat = (lat / 10).toInt()
            val fieldLon = (lon / 20).toInt()
            lat %= 10
            lon %= 20

            // Square: 00-99
            val squareLat = lat.toInt()
            val squareLon = (lon / 2).toInt()
            lat %= 1
            lon %= 2

            // Subsquare: AA-XX
            val subLat = (lat * 24).toInt()
            val subLon = (lon * 12).toInt()

            // Maidenhead uses the 'odd' lon, lat order:
            return buildString {
                append('A' + fieldLon)
                append('A' + fieldLat)
                append(squareLon)
                append(squareLat)
                append('A' + subLon)
                append('A' + subLat)
            }
        }

        companion object {
            fun fromAndroid(location: android.location.Location)
                = Position(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracyRadius = if (location.hasAccuracy()) {
                        location.accuracy
                    } else {
                        null
                    },
                )
        }
    }

    @Serializable
    data class NumSatellites(
        val usedInFix: Int,
        val total: Int,
    )
}
