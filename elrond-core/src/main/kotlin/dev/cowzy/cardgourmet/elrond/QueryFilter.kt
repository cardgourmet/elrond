package dev.cowzy.cardgourmet.elrond

import dev.cowzy.cardgourmet.elrond.property.SearchQueryProperty

class QueryFilter(
    val key: String,
    val keywords: List<String>,
    val properties: List<SearchQueryProperty<out Any>>,
    val ignoreReferenceKeywords: Set<String>,
    val inverted: Boolean
)
