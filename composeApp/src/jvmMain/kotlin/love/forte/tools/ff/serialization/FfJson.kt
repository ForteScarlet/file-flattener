package love.forte.tools.ff.serialization

import kotlinx.serialization.json.Json

object FfJson {
    val instance: Json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }
}

