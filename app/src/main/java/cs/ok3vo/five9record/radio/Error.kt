package cs.ok3vo.five9record.radio

class CatSerialError(val problem: String = "I/O error"): RuntimeException("CAT serial communication: $problem")

class RadioIdMismatch(val name: String): RuntimeException("Radio does not appear to be $name")
