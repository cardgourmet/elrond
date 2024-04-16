package elrond.query

import kotlinx.serialization.Serializable

@Serializable
data class IgnoredQueryValue(
    val value: String,
    val reason: String,
    val supportedValues: List<String>? = null
)
