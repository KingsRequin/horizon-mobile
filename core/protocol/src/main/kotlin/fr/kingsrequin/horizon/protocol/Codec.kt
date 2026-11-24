package fr.kingsrequin.horizon.protocol

import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Messages ↔ texte : un objet JSON par trame, dont le champ `type` dit lequel. */
object Codec {
    private const val TYPE = "type"

    private val json = Json {
        // Le PC peut ajouter des champs sans casser une appli plus ancienne.
        ignoreUnknownKeys = true
        classDiscriminator = TYPE
        encodeDefaults = true
    }

    /** Types que cette version sait lire (noms de série des messages de [FromPc]). */
    private val known: Set<String> = FromPc.serializer().descriptor.getElementDescriptor(1).elementNames.toSet()

    fun encode(msg: ToPc): String = json.encodeToString(ToPc.serializer(), msg)

    /**
     * Le message reçu ; [FromPc.Unknown] pour un type que cette version ne connaît pas ; `null`
     * si le texte n'est pas un objet JSON avec un `type`, ou s'il manque un champ à un message
     * connu.
     */
    fun decode(text: String): FromPc? {
        val element = try {
            json.parseToJsonElement(text)
        } catch (_: SerializationException) {
            return null
        }
        val type = ((element as? JsonObject)?.get(TYPE) as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
        if (type !in known) return FromPc.Unknown
        return try {
            json.decodeFromJsonElement(FromPc.serializer(), element)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
