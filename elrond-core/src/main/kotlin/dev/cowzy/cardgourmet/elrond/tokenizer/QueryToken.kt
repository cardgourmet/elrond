package dev.cowzy.cardgourmet.elrond.tokenizer

import dev.cowzy.cardgourmet.elrond.SearchQueryOperator
import dev.cowzy.cardgourmet.elrond.query.LogicalOperator

sealed class QueryToken {
    abstract var negate: Boolean
}

data class QueryFilterToken(
    val filter: QueryTokenizerFilter,
    val keyword: String?,
    val operator: SearchQueryOperator,
    val value: ValueToken,
    override var negate: Boolean,
    val raw: String
) : QueryToken() {
    override fun toString(): String {
        return when {
            negate -> "-${keyword ?: filter.keywords.first()}${operator.value}${value.raw}"
            else -> "${keyword ?: filter.keywords.first()}${operator.value}${value.raw}"
        }
    }
}

data class QueryTokenGroup(
    val children: List<QueryToken>,
    val operator: LogicalOperator,
    override var negate: Boolean
) : QueryToken()

fun QueryToken.negated(): QueryToken = when (this) {
    is QueryTokenGroup -> this.copy(negate = !this.negate)
    is QueryFilterToken -> this.copy(negate = !this.negate)
}

operator fun QueryToken.plus(other: List<QueryToken>) = listOf(this) + other
