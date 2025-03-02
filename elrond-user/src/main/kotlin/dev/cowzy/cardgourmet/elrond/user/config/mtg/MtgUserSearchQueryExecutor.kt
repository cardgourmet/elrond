package dev.cowzy.cardgourmet.elrond.user.config.mtg

import dev.cowzy.cardgourmet.commons.database.Schemata
import dev.cowzy.cardgourmet.chef.commons.model.card.mtg.*
import dev.cowzy.cardgourmet.commons.user.UserCard
import dev.cowzy.cardgourmet.elrond.config.SearchQueryFilterBuilder
import dev.cowzy.cardgourmet.elrond.config.SearchQueryExecutor
import dev.cowzy.cardgourmet.elrond.query.SearchQuery
import dev.cowzy.cardgourmet.elrond.query.SearchQueryMode
import dev.cowzy.cardgourmet.elrond.user.config.configureCollectionFilters
import dev.cowzy.cardgourmet.elrond.values.ValueProviderPool
import dev.cowzy.cardgourmet.tcg.config.card.TcgCardSearchQueryDistinctMode
import dev.cowzy.cardgourmet.tcg.config.card.mtg.*
import dev.cowzy.kuery.query.SelectQueryBuilder
import dev.cowzy.kuery.reflection.columnName

private val queryBuilder: ((SearchQuery<MtgCardSearchQueryFlag, TcgCardSearchQueryDistinctMode>, SearchQueryMode, SelectQueryBuilder) -> Unit) = queryBuilder@{ query, mode, builder ->
    val preferMode = query.flags.firstOfOrNull(MtgCardSearchQueryFlag.preferModes)

    if (!query.flags.contains(MtgCardSearchQueryFlag.INCLUDE_EXTRAS)) {
        builder.whereInRaw(MtgPrint::id, "(SELECT id FROM ${Schemata.MAGIC_THE_GATHERING}.primary_print_ids)")
    }

    if (!query.flags.contains(MtgCardSearchQueryFlag.ANY_LANGUAGE)) {
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

fun createMtgSearchQueryExecutor(providers: ValueProviderPool): SearchQueryExecutor<MtgCardSearchQueryFlag, TcgCardSearchQueryDistinctMode> {
    val builder = SearchQueryFilterBuilder(providers) {
        configureBasicMtgCardFilters()
    }

    val filters = builder.build()
    val defaultFilter = filters.single { it.keywords.contains("name") }

    return createMtgCardBaseBuilder(mtgSearchQueryConfig, fallbackFilter = defaultFilter)
        .filters(filters)
        .build()
}

fun createMtgCollectionSearchQueryExecutor(providers: ValueProviderPool): SearchQueryExecutor<MtgCardSearchQueryFlag, TcgCardSearchQueryDistinctMode> {
    val builder = SearchQueryFilterBuilder(providers) {
        configureBasicMtgCardFilters()
        configureCollectionFilters()
        configureMtgCollectionFilters()
    }

    val filters = builder.build()
    val defaultFilter = filters.single { it.keywords.contains("name") }

    return createMtgCardBaseBuilder(mtgSearchQueryConfig, queryBuilder, defaultFilter)
        .filters(filters)
        .build()
}
