package dev.cowzy.cardgourmet.elrond.user.config.pcg

import dev.cowzy.cardgourmet.chef.commons.model.image.CardImage
import dev.cowzy.cardgourmet.commons.database.card.pcg.PcgCardTranslation
import dev.cowzy.cardgourmet.commons.user.UserCard
import dev.cowzy.cardgourmet.elrond.config.SearchQueryConfigBuilder
import dev.cowzy.cardgourmet.elrond.config.SearchQueryExecutor
import dev.cowzy.cardgourmet.elrond.config.pcg.PcgSearchQueryFlag
import dev.cowzy.cardgourmet.elrond.config.pcg.applyPcgSort
import dev.cowzy.cardgourmet.elrond.config.pcg.configureBasicPcgFilters
import dev.cowzy.cardgourmet.elrond.config.pcg.createPcgBaseBuilder
import dev.cowzy.cardgourmet.elrond.query.SearchQuery
import dev.cowzy.cardgourmet.elrond.user.config.configureCollectionFilters
import dev.cowzy.cardgourmet.elrond.values.ValueProviderPool
import dev.cowzy.kuery.query.SelectQueryBuilder
import dev.cowzy.kuery.query.whereNotNull
import dev.cowzy.kuery.reflection.columnName

private val queryBuilder: ((SearchQuery<PcgSearchQueryFlag>, SelectQueryBuilder) -> Unit) = { query, builder ->
    if (!query.flags.contains(PcgSearchQueryFlag.ANY_LANGUAGE)) {
        builder.whereColumn(UserCard::language, PcgCardTranslation::language)
    }

    builder.whereNotNull(UserCard::id)

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

fun createPcgSearchQueryExecutor(providers: ValueProviderPool): SearchQueryExecutor<PcgSearchQueryFlag> {
    val builder = SearchQueryConfigBuilder(providers) {
        configureBasicPcgFilters()
    }

    val filters = builder.build()
    val defaultFilter = filters.single { it.keywords.contains("name") }

    return createPcgBaseBuilder(pcgSearchQueryConfig, fallbackFilter = defaultFilter)
        .filters(filters)
        .build()
}

fun createPcgCollectionSearchQueryExecutor(providers: ValueProviderPool): SearchQueryExecutor<PcgSearchQueryFlag> {
    val builder = SearchQueryConfigBuilder(providers) {
        configureBasicPcgFilters()
        configureCollectionFilters()
        configurePcgCollectionFilters()
    }

    val filters = builder.build()
    val defaultFilter = filters.single { it.keywords.contains("name") }

    return createPcgBaseBuilder(pcgSearchQueryConfig, queryBuilder, defaultFilter)
        .filters(filters)
        .build()
}

