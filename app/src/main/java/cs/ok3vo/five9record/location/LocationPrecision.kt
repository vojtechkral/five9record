package cs.ok3vo.five9record.location

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
enum class LocationPrecision: Parcelable {
    FULL_LOCATION { override fun toString() = "Full location" },
    LOCATOR_SUBSQUARE  { override fun toString() = "Locator only, sub-square precision" },
    LOCATOR_SQUARE  { override fun toString() = "Locator only, square precision" },

    ;
    val subsquareEnabled get() = this == FULL_LOCATION || this == LOCATOR_SUBSQUARE
}
