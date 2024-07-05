package dev.cowzy.cardgourmet.elrond.query

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class QueryValidationRule {
    @SerialName("no_ignored_values") NO_IGNORED_VALUES,
    @SerialName("no_custom_distinct_mode") NO_CUSTOM_DISTINCT_MODE,
    @SerialName("no_custom_sorting") NO_CUSTOM_SORTING,
    @SerialName("no_custom_flags") NO_CUSTOM_FLAGS,
    @SerialName("not_empty") NOT_EMPTY
}