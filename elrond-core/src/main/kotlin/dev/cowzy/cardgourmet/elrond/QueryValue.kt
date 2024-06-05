package dev.cowzy.cardgourmet.elrond

import dev.cowzy.cardgourmet.elrond.property.SearchQueryProperty

sealed class QueryValue<T : Any>(val value: T) {
    companion object {
        val EMPTY = EmptyQueryValue()
    }

    override fun equals(other: Any?): Boolean {
        val sameClass = other != null && other::class == this::class
        val sameValue = sameClass && (other as QueryValue<*>).value == value
        return sameValue
    }
}

class RegexValue(value: Regex) : QueryValue<Regex>(value) {
    override fun toString() = "/${value.pattern}/"
}

class StringValue(value: String, val exact: Boolean = false) : QueryValue<String>(value) {
    override fun toString() = if (exact) "\"${value.lowercase()}\"" else value.lowercase()
}

class NumberValue(value: Number) : QueryValue<Number>(value) {
    override fun toString() = value.toString()
}

class FilterValue(
    value: QueryFilter,
    val properties: Pair<SearchQueryProperty<Any>, SearchQueryProperty<Any>>
) : QueryValue<QueryFilter>(value)

class EmptyQueryValue : QueryValue<Unit>(Unit)

fun QueryValue<*>.measureComplexity(operator: SearchQueryOperator): Double {
    return when {
        this is RegexValue && operator == dev.cowzy.cardgourmet.elrond.SearchQueryOperator.EQUALS -> SearchQueryComplexity.HIGH + this.measureRegexComplexity()
        this is RegexValue && operator == dev.cowzy.cardgourmet.elrond.SearchQueryOperator.CONTAINS -> SearchQueryComplexity.VERY_HIGH + this.measureRegexComplexity()

        this is StringValue && operator == dev.cowzy.cardgourmet.elrond.SearchQueryOperator.EQUALS -> SearchQueryComplexity.LOW
        this is StringValue && operator == dev.cowzy.cardgourmet.elrond.SearchQueryOperator.CONTAINS -> {
            when (this.value.length) {
                in 0..2 -> SearchQueryComplexity.VERY_HIGH
                in 3..5 -> SearchQueryComplexity.HIGH
                else -> SearchQueryComplexity.MEDIUM
            }
        }

        else -> SearchQueryComplexity.MEDIUM
    }
}

fun RegexValue.measureRegexComplexity(): Double {
    val regex = Regex(value.pattern + "|")
    val captureGroupCount = regex.find("")?.groups?.size ?: 0

    val lengthComplexity = when {
        value.pattern.length < 5 -> SearchQueryComplexity.LOW
        value.pattern.length < 10 -> SearchQueryComplexity.MEDIUM
        value.pattern.length < 20 -> SearchQueryComplexity.HIGH
        else -> SearchQueryComplexity.VERY_HIGH
    }

    return lengthComplexity + captureGroupCount * SearchQueryComplexity.MEDIUM
}
