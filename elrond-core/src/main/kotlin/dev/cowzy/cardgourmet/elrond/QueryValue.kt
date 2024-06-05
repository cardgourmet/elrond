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
