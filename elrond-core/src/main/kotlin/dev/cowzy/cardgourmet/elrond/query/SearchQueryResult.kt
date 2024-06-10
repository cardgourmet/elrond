package dev.cowzy.cardgourmet.elrond.query

import java.util.UUID

data class SearchQueryResult(
    val id: UUID,
    val matchedPrintId: UUID? = null,
    val matchedFaceIndex: Int? = null,
    val matchedLanguage: String? = null,
)
