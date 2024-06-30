package dev.cowzy.cardgourmet.elrond.user.config.pcg

import dev.cowzy.cardgourmet.chef.commons.model.image.CardImage
import dev.cowzy.cardgourmet.commons.database.card.pcg.PcgCardTranslation
import dev.cowzy.cardgourmet.commons.user.UserCard
import dev.cowzy.cardgourmet.elrond.config.SearchQueryFilterBuilder
import dev.cowzy.cardgourmet.elrond.config.SearchQueryExecutor
import dev.cowzy.cardgourmet.elrond.query.SearchQuery
import dev.cowzy.cardgourmet.elrond.query.SearchQueryMode
import dev.cowzy.cardgourmet.elrond.user.config.configureCollectionFilters
import dev.cowzy.cardgourmet.elrond.values.ValueProviderPool
import dev.cowzy.cardgourmet.tcg.config.card.TcgSearchQueryDistinctMode
import dev.cowzy.cardgourmet.tcg.config.card.pcg.PcgCardSearchQueryFlag
import dev.cowzy.cardgourmet.tcg.config.card.pcg.applyPcgSort
import dev.cowzy.cardgourmet.tcg.config.card.pcg.configureBasicPcgCardFilters
import dev.cowzy.cardgourmet.tcg.config.card.pcg.createPcgCardBaseBuilder
import dev.cowzy.kuery.query.SelectQueryBuilder
import dev.cowzy.kuery.query.whereNotNull
import dev.cowzy.kuery.reflection.columnName

private val queryBuilder: ((SearchQuery<PcgCardSearchQueryFlag, TcgSearchQueryDistinctMode>, SearchQueryMode, SelectQueryBuilder) -> Unit) = queryBuilder@{ query, mode, builder ->
    if (!query.flags.contains(PcgCardSearchQueryFlag.ANY_LANGUAGE)) {
        builder.whereColumn(UserCard::language, PcgCardTranslation::language)
    }

    builder.whereNotNull(UserCard::id)

    // No need to apply sort for count/random queries.
    if (mode != SearchQueryMode.SEARCH) return@queryBuilder

    // Always prefer cards with images.
    builder.orderByRaw("CASE WHEN(${CardImage::imageId.columnName()} IS NOT NULL) THEN 1 ELSE 2 END")

    val languageSort = "CASE " +
            "WHEN(${PcgCardTranslation::language.columnName()} = ${UserCard::language.columnName()}) THEN 1 " +
            "WHEN(${PcgCardTranslation::language.columnName()} = ?) THEN 2 " +
            "WHEN(${PcgCardTranslation::language.columnName()} = 'en') THEN 3 " +
            "ELSE 4 " +
            "END"

    builder.orderByRaw(languageSort) { stmt, index ->
        stmt.setString(index.getAndIncrement(), query.preferredLanguage)
    }

    applyPcgSort(query, builder)
}

fun createPcgSearchQueryExecutor(providers: ValueProviderPool): SearchQueryExecutor<PcgCardSearchQueryFlag, TcgSearchQueryDistinctMode> {
    val builder = SearchQueryFilterBuilder(providers) {
        configureBasicPcgCardFilters()
    }

    val filters = builder.build()
    val defaultFilter = filters.single { it.keywords.contains("name") }

    return createPcgCardBaseBuilder(pcgSearchQueryConfig, fallbackFilter = defaultFilter)
        .filters(filters)
        .build()
}

fun createPcgCollectionSearchQueryExecutor(providers: ValueProviderPool): SearchQueryExecutor<PcgCardSearchQueryFlag, TcgSearchQueryDistinctMode> {
    val builder = SearchQueryFilterBuilder(providers) {
        configureBasicPcgCardFilters()
        configureCollectionFilters()
        configurePcgCollectionFilters()
    }

    val filters = builder.build()
    val defaultFilter = filters.single { it.keywords.contains("name") }

    return createPcgCardBaseBuilder(pcgSearchQueryConfig, queryBuilder, defaultFilter)
        .filters(filters)
        .build()
}

