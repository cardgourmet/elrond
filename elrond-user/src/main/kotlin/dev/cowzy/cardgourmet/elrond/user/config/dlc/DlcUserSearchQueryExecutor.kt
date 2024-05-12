package dev.cowzy.cardgourmet.elrond.user.config.dlc

import dev.cowzy.cardgourmet.chef.commons.model.image.CardImage
import dev.cowzy.cardgourmet.commons.database.card.dlc.DlcCardTranslation
import dev.cowzy.cardgourmet.commons.user.UserCard
import dev.cowzy.cardgourmet.elrond.config.SearchQueryConfigBuilder
import dev.cowzy.cardgourmet.elrond.config.SearchQueryExecutor
import dev.cowzy.cardgourmet.elrond.config.dlc.*
import dev.cowzy.cardgourmet.elrond.query.SearchQuery
import dev.cowzy.cardgourmet.elrond.user.config.configureCollectionFilters
import dev.cowzy.cardgourmet.elrond.values.PropertyProviderPool
import dev.cowzy.kuery.query.SelectQueryBuilder
import dev.cowzy.kuery.query.whereNotNull
import dev.cowzy.kuery.reflection.columnName

private val queryBuilder: ((SearchQuery<DlcSearchQueryFlag>, SelectQueryBuilder) -> Unit) = { query, builder ->
    if (!query.flags.contains(DlcSearchQueryFlag.ANY_LANGUAGE)) {
        builder.whereColumn(UserCard::language, DlcCardTranslation::language)
    }

    builder.whereNotNull(UserCard::id)

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


fun createDlcSearchQueryExecutor(providers: PropertyProviderPool): SearchQueryExecutor<DlcSearchQueryFlag> {
    val builder = SearchQueryConfigBuilder(providers) {
        configureBasicDlcFilters()
    }

    val filters = builder.build()
    val defaultFilter = filters.single { it.keywords.contains("name") }

    return createDlcBaseBuilder(dlcSearchQueryConfig, fallbackFilter = defaultFilter)
        .filters(filters)
        .build()
}

fun createDlcCollectionSearchQueryExecutor(providers: PropertyProviderPool): SearchQueryExecutor<DlcSearchQueryFlag> {
    val builder = SearchQueryConfigBuilder(providers) {
        configureBasicDlcFilters()
        configureCollectionFilters()
        configureDlcCollectionFilters()
    }

    val filters = builder.build()
    val defaultFilter = filters.single { it.keywords.contains("name") }

    return createDlcBaseBuilder(dlcSearchQueryConfig, queryBuilder, defaultFilter)
        .filters(filters)
        .build()
}