package cs.ok3vo.five9record.util

val Json = kotlinx.serialization.json.Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    prettyPrint = true
}
