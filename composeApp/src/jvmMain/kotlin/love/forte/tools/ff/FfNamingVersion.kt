package love.forte.tools.ff

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 *
 * @author ForteScarlet
 */
@Serializable(FfNamingVersionSerializer::class)
enum class FfNamingVersion(val versionValue: Int) {
    @SerialName("1")
    V1(1),

    @SerialName("2")
    V2(2),

    @SerialName("3")
    V3(3);


}

object FfNamingVersionSerializer : KSerializer<FfNamingVersion> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FfNamingVersion", PrimitiveKind.INT)

    override fun serialize(
        encoder: Encoder,
        value: FfNamingVersion
    ) {
        encoder.encodeInt(value.versionValue)
    }

    override fun deserialize(decoder: Decoder): FfNamingVersion {
        val decodeInt = decoder.decodeInt()
        return FfNamingVersion.entries.find { it.versionValue == decodeInt }
            ?: throw SerializationException("Can not deserialize FfNamingVersion: unknown version value $decodeInt.")
    }
}
