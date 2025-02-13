package cs.ok3vo.five9record.radio

import kotlinx.serialization.Serializable

enum class Mode {
    CW, LSB, USB, AM, FM, DATA, RTTY, OTHER,
    ;
    companion object {}
}

@Serializable
data class RadioData(
    /** Name of the device */
    val rig: String,

    /** frequency in Hz */
    val freq: Long,
    val mode: Mode,
    val tx: Boolean,
    /** configured PEP in Watts */
    val power: Int,
)
