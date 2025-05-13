package cs.ok3vo.five9record.location

import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import cs.ok3vo.five9record.recording.RecordingService
import cs.ok3vo.five9record.util.Mutex
import cs.ok3vo.five9record.util.elapsed
import cs.ok3vo.five9record.util.logI
import java.time.Instant

class LocationListener: LocationListener, GnssStatus.Callback() {
    private val locationStatus = Mutex(LocationStatus())
    private var lastNonZeroNumUsedInFix = Instant.MIN

    fun getLastLocation(): LocationStatus = locationStatus.lock { copy() }

    fun setGnssEnabled(enabled: Boolean) = locationStatus.lock { gnssEnabled = enabled }
    fun setCoarse(coarse: Boolean) = locationStatus.lock { this.coarse = coarse }

    override fun onLocationChanged(location: Location) {
        logI(RecordingService::class, "received location: $location")
        locationStatus.lock {
            position = LocationStatus.Position.fromAndroid(location)
        }
    }

    @Deprecated("present for compatibility with older SDK levels")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    override fun onSatelliteStatusChanged(status: GnssStatus) {
        val total = status.satelliteCount
        val usedInFix = status.satellitesUsedInFix()
        if (usedInFix > 0) {
            lastNonZeroNumUsedInFix = Instant.now()
        }

        locationStatus.lock {
            // Hold on to non-zero numUsedInFix for a bit,
            // otherwise Android tends to reset it back to zero very soon after a fix.
            val prevNumSatellites = numSatellites
            numSatellites = if (prevNumSatellites != null
                && usedInFix == 0
                && lastNonZeroNumUsedInFix.elapsed().seconds < 30
            ) {
                prevNumSatellites.copy(total = total)
            } else {
                LocationStatus.NumSatellites(
                    usedInFix = usedInFix,
                    total = total,
                )
            }
        }
    }

    override fun onProviderEnabled(provider: String) {
        if (provider == LocationManager.GPS_PROVIDER) {
            locationStatus.lock { gnssEnabled = true }
        }
    }

    override fun onProviderDisabled(provider: String) {
        if (provider == LocationManager.GPS_PROVIDER) {
            locationStatus.lock { gnssEnabled = false }
        }
    }
}

fun GnssStatus.satellitesUsedInFix(): Int
    = (0 ..< satelliteCount).count { usedInFix(it) }
