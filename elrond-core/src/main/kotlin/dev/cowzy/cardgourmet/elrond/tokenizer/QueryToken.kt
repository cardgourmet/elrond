package dev.cowzy.cardgourmet.elrond.tokenizer

import dev.cowzy.cardgourmet.elrond.SearchQueryOperator
import dev.cowzy.cardgourmet.elrond.query.LogicalOperator

sealed class QueryToken {
    abstract val negate: Boolean
}

data class QueryFilterToken(
    val keyword: String?,
    val operator: SearchQueryOperator?,
    val value: ValueToken?,
    val exactValue: Boolean,
    override val negate: Boolean
) : QueryToken() {
    override fun toString(): String {
        val mappedOperator = if (exactValue) SearchQueryOperator.EQUALS else operator ?: SearchQueryOperator.CONTAINS

        return when {
            negate -> "-$keyword${mappedOperator.value}$value"
            else -> "$keyword${mappedOperator.value}$value"
        }
    }
}

data class QueryTokenGroup(
    val children: List<QueryToken>,
    val operator: LogicalOperator,
    override val negate: Boolean
) : QueryToken()
