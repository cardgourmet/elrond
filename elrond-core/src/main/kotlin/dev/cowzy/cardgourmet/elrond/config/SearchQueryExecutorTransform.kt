package dev.cowzy.cardgourmet.elrond.config

data class SearchQueryExecutorTransform(
    val applyFilters: (SearchQueryFilterBuilder.() -> Unit)?,
    val transformConfig: ((SearchQuerySqlConfig) -> SearchQuerySqlConfig)?
)