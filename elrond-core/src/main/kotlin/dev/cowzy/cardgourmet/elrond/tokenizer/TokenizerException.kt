package dev.cowzy.cardgourmet.elrond.tokenizer

import dev.cowzy.cardgourmet.elrond.query.IgnoredQueryValue

class TokenizerException(
    val value: String,
    private val reason: String,
    private val supportedValues: List<String>? = null,
    cause: Throwable? = null
) : RuntimeException(
    "Tokenizer error for value '$value': $reason. ${supportedValues?.let { " Supported values: [${it.joinToString(",")}]." } ?: ""}".trim(),
    cause
) {
    fun toIgnoredValue() = IgnoredQueryValue(value, reason, supportedValues)
}
