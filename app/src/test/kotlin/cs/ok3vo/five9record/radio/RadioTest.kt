package cs.ok3vo.five9record.radio

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

class RadioTest: StringSpec({
    "RadioType serde" {
        for (value in RadioType.entries) {
            val json = Json.encodeToString(value)
            json shouldBe "\"${value.name}\""
        }
    }
})
