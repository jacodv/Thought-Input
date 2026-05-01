package com.thoughtinput.capture.data.destinations

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.UUID

object UUIDSerializer : KSerializer<UUID> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: UUID) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): UUID = UUID.fromString(decoder.decodeString())
}

@Serializable
data class KeychainRef(val account: String) {
    companion object {
        fun create(): KeychainRef = KeychainRef(UUID.randomUUID().toString())
    }
}

@Serializable
data class Destination(
    @Serializable(with = UUIDSerializer::class) val id: UUID = UUID.randomUUID(),
    val name: String,
    val isActive: Boolean = false,
    val type: DestinationType
)

@Serializable
sealed class DestinationType {
    abstract val displayName: String

    @Serializable
    data class Supabase(
        val projectURL: String,
        val tableName: String,
        val apiKeyRef: KeychainRef
    ) : DestinationType() {
        override val displayName: String get() = "Supabase"
    }

    @Serializable
    data class RestNoAuth(
        val endpointURL: String
    ) : DestinationType() {
        override val displayName: String get() = "REST (No Auth)"
    }

    @Serializable
    data class RestApiKey(
        val endpointURL: String,
        val headerName: String,
        val apiKeyRef: KeychainRef
    ) : DestinationType() {
        override val displayName: String get() = "REST (API Key)"
    }

    @Serializable
    data class RestOAuthPassword(
        val endpointURL: String,
        val tokenURL: String,
        val usernameRef: KeychainRef,
        val passwordRef: KeychainRef
    ) : DestinationType() {
        override val displayName: String get() = "REST (OAuth Password)"
    }

    @Serializable
    data class RestOAuthClientCredentials(
        val endpointURL: String,
        val tokenURL: String,
        val clientIDRef: KeychainRef,
        val clientSecretRef: KeychainRef
    ) : DestinationType() {
        override val displayName: String get() = "REST (OAuth Client Credentials)"
    }

    val keychainRefs: List<KeychainRef>
        get() = when (this) {
            is Supabase -> listOf(apiKeyRef)
            is RestNoAuth -> emptyList()
            is RestApiKey -> listOf(apiKeyRef)
            is RestOAuthPassword -> listOf(usernameRef, passwordRef)
            is RestOAuthClientCredentials -> listOf(clientIDRef, clientSecretRef)
        }
}

data class OAuthToken(
    val accessToken: String,
    val expiresAt: Long?,
    val refreshToken: String?
) {
    val isExpired: Boolean
        get() = expiresAt?.let { System.currentTimeMillis() + 30_000L >= it } ?: false
}

@Serializable
data class PendingCapture(
    val payload: com.thoughtinput.capture.data.CapturePayload,
    @Serializable(with = UUIDSerializer::class) val destinationID: UUID,
    val destinationSnapshot: Destination
)
