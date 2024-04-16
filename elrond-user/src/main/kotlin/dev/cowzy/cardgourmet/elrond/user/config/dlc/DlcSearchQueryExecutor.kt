package dev.cowzy.cardgourmet.elrond.user.config.dlc

import dev.cowzy.cardgourmet.commons.database.card.dlc.DlcCardTranslation
import dev.cowzy.cardgourmet.commons.user.UserCard
import dev.cowzy.cardgourmet.elrond.config.SearchQueryExecutor
import dev.cowzy.cardgourmet.elrond.config.dlc.*
import dev.cowzy.cardgourmet.elrond.query.SearchQuery
import dev.cowzy.cardgourmet.farbeagle.model.CardImage
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

fun createDlcSearchQueryExecutor(providers: DlcValueProviders): SearchQueryExecutor<DlcSearchQueryFlag> {
    return createDlcBaseBuilder(dlcSearchQueryConfig, fallbackFilter = dlcNameFilter)
        .filters(createBasicDlcSearchQueryFilters(providers))
        .build()
}

fun createDlcCollectionSearchQueryExecutor(providers: DlcValueProviders): SearchQueryExecutor<DlcSearchQueryFlag> {
    return createDlcBaseBuilder(dlcSearchQueryConfig, queryBuilder, dlcCollectionNameFilter)
        .filters(createDlcCollectionSearchQueryFilters())
        .filters(createBasicDlcSearchQueryFilters(providers))
        .build()
}
