package dev.cowzy.cardgourmet.elrond

import kotlinx.serialization.SerialName

enum class SearchQueryOperator(val value: String, vararg val aliases: String) {
    @SerialName(":") CONTAINS(":"),
    @SerialName(">=") GREATER_THAN_OR_EQUALS(">=", "≥"),
    @SerialName(">") GREATER_THAN(">"),
    @SerialName("<=") LESS_THAN_OR_EQUALS("<=", "≤"),
    @SerialName("<") LESS_THAN("<"),
    @SerialName("=") EQUALS("=");

    companion object {
        fun tryParse(value: String) = SearchQueryOperator.values().find {
            it.value == value || it.aliases.contains(value)
        }
    }
}

val numericQueryOperators = SearchQueryOperator.values()

val stringQueryOperators = arrayOf(
    SearchQueryOperator.CONTAINS,
    SearchQueryOperator.EQUALS,
)

fun SearchQueryOperator.toNumericSqlOperator() = when (this) {
    SearchQueryOperator.CONTAINS, SearchQueryOperator.EQUALS -> "="
    SearchQueryOperator.GREATER_THAN_OR_EQUALS -> ">="
    SearchQueryOperator.GREATER_THAN -> ">"
    SearchQueryOperator.LESS_THAN_OR_EQUALS -> "<="
    SearchQueryOperator.LESS_THAN -> "<"
}

fun SearchQueryOperator.negated() = when (this) {
    SearchQueryOperator.GREATER_THAN_OR_EQUALS -> SearchQueryOperator.LESS_THAN
    SearchQueryOperator.GREATER_THAN -> SearchQueryOperator.LESS_THAN_OR_EQUALS
    SearchQueryOperator.LESS_THAN_OR_EQUALS -> SearchQueryOperator.GREATER_THAN
    SearchQueryOperator.LESS_THAN -> SearchQueryOperator.GREATER_THAN_OR_EQUALS
    else -> null
}
