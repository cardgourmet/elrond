package dev.cowzy.cardgourmet.elrond.values

import dev.cowzy.cardgourmet.elrond.SearchQueryOperator

data class ProvidedValue<T : Any>(
    val input: String,
    val resolvesTo: ResolvedValue<T>,
    val aliases: MutableSet<String>,
    val type: String,
)

data class ResolvedValue<T : Any>(
    val display: String,
    val value: T,
    val operator: SearchQueryOperator?
)