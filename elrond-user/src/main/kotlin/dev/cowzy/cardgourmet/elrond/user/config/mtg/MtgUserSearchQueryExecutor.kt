package dev.cowzy.cardgourmet.elrond.user.config.mtg

import dev.cowzy.cardgourmet.commons.database.Schemata
import dev.cowzy.cardgourmet.commons.database.card.mtg.*
import dev.cowzy.cardgourmet.commons.user.UserCard
import dev.cowzy.cardgourmet.elrond.config.SearchQueryConfigBuilder
import dev.cowzy.cardgourmet.elrond.config.SearchQueryExecutor
import dev.cowzy.cardgourmet.elrond.config.mtg.*
import dev.cowzy.cardgourmet.elrond.query.SearchQuery
import dev.cowzy.cardgourmet.elrond.query.SearchQueryMode
import dev.cowzy.cardgourmet.elrond.user.config.configureCollectionFilters
import dev.cowzy.cardgourmet.elrond.values.ValueProviderPool
import dev.cowzy.kuery.query.SelectQueryBuilder
import dev.cowzy.kuery.reflection.columnName

private val queryBuilder: ((SearchQuery<MtgSearchQueryFlag>, SearchQueryMode, SelectQueryBuilder) -> Unit) = queryBuilder@{ query, mode, builder ->
    val preferMode = query.flags.firstOfOrNull(MtgSearchQueryFlag.preferModes)

    if (!query.flags.contains(MtgSearchQueryFlag.INCLUDE_EXTRAS)) {
        builder.whereInRaw(MtgPrint::id, "(SELECT id FROM ${Schemata.MAGIC_THE_GATHERING}.primary_print_ids)")
    }

    if (!query.flags.contains(MtgSearchQueryFlag.ANY_LANGUAGE)) {
        builder.whereColumn(UserCard::language, MtgCardFaceTranslation::language)
    }

    // No need to apply sort for count/random queries.
    if (mode != SearchQueryMode.SEARCH) return@queryBuilder

    applyMtgSortPreLanguage(builder, preferMode)

    val languageSort = "CASE " +
            "WHEN(${MtgCardFaceTranslation::language.columnName()} = ${UserCard::language.columnName()}) THEN 1 " +
            "WHEN(${MtgCardFaceTranslation::language.columnName()} = ?) THEN 2 " +
            "WHEN(${MtgCardFaceTranslation::language.columnName()} = 'en') THEN 3 " +
            "ELSE 4 " +
            "END"

    builder.orderByRaw(languageSort) { stmt, index ->
        stmt.setString(index.getAndIncrement(), query.preferredLanguage)
    }

    applyMtgSortPostLanguage(query, builder, preferMode)
}

fun createMtgSearchQueryExecutor(providers: ValueProviderPool): SearchQueryExecutor<MtgSearchQueryFlag> {
    val builder = SearchQueryConfigBuilder(providers) {
        configureBasicMtgFilters()
    }

    val filters = builder.build()
    val defaultFilter = filters.single { it.keywords.contains("name") }

    return createMtgBaseBuilder(mtgSearchQueryConfig, fallbackFilter = defaultFilter)
        .filters(filters)
        .build()
}

fun createMtgCollectionSearchQueryExecutor(providers: ValueProviderPool): SearchQueryExecutor<MtgSearchQueryFlag> {
    val builder = SearchQueryConfigBuilder(providers) {
        configureBasicMtgFilters()
        configureCollectionFilters()
        configureMtgCollectionFilters()
    }

    val filters = builder.build()
    val defaultFilter = filters.single { it.keywords.contains("name") }

    return createMtgBaseBuilder(mtgSearchQueryConfig, queryBuilder, defaultFilter)
        .filters(filters)
        .build()
}
