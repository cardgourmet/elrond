package dev.cowzy.cardgourmet.elrond

import dev.cowzy.cardgourmet.elrond.property.SearchQueryProperty

class QueryFilter(
    val keywords: Array<String>,
    vararg val properties: SearchQueryProperty<out Any>,
    val ignoreReferenceKeywords: Array<String> = emptyArray(),
    val inverted: Boolean = false
)
