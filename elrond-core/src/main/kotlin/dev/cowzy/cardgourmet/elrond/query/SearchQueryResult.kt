package dev.cowzy.cardgourmet.elrond.query

import java.util.UUID

data class SearchQueryResult(
    val id: UUID,
    val customFields: Map<String, Any?>,
) {
    @Suppress("UNCHECKED_CAST")
    fun <T> getCustomField(name: String): T? = customFields[name] as? T
}
