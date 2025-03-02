package dev.cowzy.cardgourmet.elrond.user.config.dlc

import dev.cowzy.cardgourmet.chef.commons.model.image.CardImage
import dev.cowzy.cardgourmet.chef.commons.model.card.dlc.DlcCardTranslation
import dev.cowzy.cardgourmet.commons.user.UserCard
import dev.cowzy.cardgourmet.elrond.config.SearchQueryFilterBuilder
import dev.cowzy.cardgourmet.elrond.config.SearchQueryExecutor
import dev.cowzy.cardgourmet.elrond.query.SearchQuery
import dev.cowzy.cardgourmet.elrond.query.SearchQueryMode
import dev.cowzy.cardgourmet.elrond.user.config.configureCollectionFilters
import dev.cowzy.cardgourmet.elrond.values.ValueProviderPool
import dev.cowzy.cardgourmet.tcg.config.card.TcgCardSearchQueryDistinctMode
import dev.cowzy.cardgourmet.tcg.config.card.dlc.DlcCardSearchQueryFlag
import dev.cowzy.cardgourmet.tcg.config.card.dlc.applyDlcSort
import dev.cowzy.cardgourmet.tcg.config.card.dlc.configureBasicDlcCardFilters
import dev.cowzy.cardgourmet.tcg.config.card.dlc.createDlcCardBaseBuilder
import dev.cowzy.kuery.query.SelectQueryBuilder
import dev.cowzy.kuery.query.whereNotNull
import dev.cowzy.kuery.reflection.columnName

private val queryBuilder: ((SearchQuery<DlcCardSearchQueryFlag, TcgCardSearchQueryDistinctMode>, SearchQueryMode, SelectQueryBuilder) -> Unit) = queryBuilder@{ query, mode, builder ->
    if (!query.flags.contains(DlcCardSearchQueryFlag.ANY_LANGUAGE)) {
        builder.whereColumn(UserCard::language, DlcCardTranslation::language)
    }

    builder.whereNotNull(UserCard::id)

    // No need to apply sort for count/random queries.
    if (mode != SearchQueryMode.SEARCH) return@queryBuilder

    // Always prefer cards with images.
    builder.orderByRaw("CASE WHEN(${CardImage::imageId.columnName()} IS NOT NULL) THEN 1 ELSE 2 END")

    val languageSort = "CASE " +
            "WHEN(${DlcCardTranslation::language.columnName()} = ${UserCard::language.columnName()}) THEN 1 " +
            "WHEN(${DlcCardTranslation::language.columnName()} = ?) THEN 2 " +
            "WHEN(${DlcCardTranslation::language.columnName()} = 'en') THEN 3 " +
            "ELSE 4 " +
            "END"

    builder.orderByRaw(languageSort) { stmt, index ->
        stmt.setString(index.getAndIncrement(), query.preferredLanguage)
    }

    applyDlcSort(query, builder)
}


fun createDlcSearchQueryExecutor(providers: ValueProviderPool): SearchQueryExecutor<DlcCardSearchQueryFlag, TcgCardSearchQueryDistinctMode> {
    val builder = SearchQueryFilterBuilder(providers) {
        configureBasicDlcCardFilters()
    }

    val filters = builder.build()
    val defaultFilter = filters.single { it.keywords.contains("name") }

    return createDlcCardBaseBuilder(dlcSearchQueryConfig, fallbackFilter = defaultFilter)
        .filters(filters)
        .build()
}

fun createDlcCollectionSearchQueryExecutor(providers: ValueProviderPool): SearchQueryExecutor<DlcCardSearchQueryFlag, TcgCardSearchQueryDistinctMode> {
    val builder = SearchQueryFilterBuilder(providers) {
        configureBasicDlcCardFilters()
        configureCollectionFilters()
        configureDlcCollectionFilters()
    }

    val filters = builder.build()
    val defaultFilter = filters.single { it.keywords.contains("name") }

    return createDlcCardBaseBuilder(dlcSearchQueryConfig, queryBuilder, defaultFilter)
        .filters(filters)
        .build()
}
