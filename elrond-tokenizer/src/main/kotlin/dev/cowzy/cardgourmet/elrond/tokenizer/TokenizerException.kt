package dev.cowzy.cardgourmet.elrond.tokenizer

class TokenizerException(
    value: String,
    reason: String,
    supportedValues: List<String>? = null,
    cause: Throwable? = null
) : RuntimeException(
    "Tokenizer error for value '$value': $reason. ${supportedValues?.let { " Supported values: [${it.joinToString(",")}]." } ?: ""}".trim(),
    cause
)