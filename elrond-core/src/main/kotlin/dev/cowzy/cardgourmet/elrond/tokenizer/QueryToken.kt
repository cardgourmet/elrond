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
        val mappedOperator = operator ?: SearchQueryOperator.CONTAINS

        return when {
            negate -> "-${keyword ?: ""}${mappedOperator.value}${value?.raw ?: ""}"
            else -> "${keyword ?: ""}${mappedOperator.value}${value?.raw ?: ""}"
        }
    }
}

data class QueryTokenGroup(
    val children: List<QueryToken>,
    val operator: LogicalOperator,
    override val negate: Boolean
) : QueryToken()

fun QueryToken.negate(): QueryToken = when (this) {
    is QueryTokenGroup -> this.copy(negate = !this.negate)
    is QueryFilterToken -> this.copy(negate = !this.negate)
}
