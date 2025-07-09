package dev.cowzy.cardgourmet.elrond.tokenizer

import dev.cowzy.cardgourmet.elrond.SearchQueryOperator

sealed class QueryToken {
    abstract var negate: Boolean
    abstract val raw: String
}

data class QueryFilterToken(
    val filter: QueryTokenizerFilter,
    val keyword: String?,
    val operator: SearchQueryOperator,
    val value: ValueToken,
    override var negate: Boolean,
    override val raw: String
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
) : QueryToken() {
    override val raw: String
        get() = ("-".takeIf { negate } ?: "") +
                "(${children.joinToString(" or ".takeIf { operator == LogicalOperator.OR } ?: " ") { it.raw }})"
}

fun QueryToken.negated(): QueryToken = when (this) {
    is QueryTokenGroup -> this.copy(negate = !this.negate)
    is QueryFilterToken -> this.copy(negate = !this.negate)
}

operator fun QueryToken.plus(other: List<QueryToken>) = listOf(this) + other
