package dev.cowzy.cardgourmet.elrond.user.config.pcg

import dev.cowzy.cardgourmet.commons.database.card.pcg.PcgCardTranslation
import dev.cowzy.cardgourmet.commons.user.UserCard
import dev.cowzy.cardgourmet.elrond.config.SearchQueryExecutor
import dev.cowzy.cardgourmet.elrond.config.pcg.*
import dev.cowzy.cardgourmet.elrond.query.SearchQuery
import dev.cowzy.cardgourmet.farbeagle.model.CardImage
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

fun createPcgSearchQueryExecutor(): SearchQueryExecutor<PcgSearchQueryFlag> {
    return createPcgBaseBuilder(pcgSearchQueryConfig, fallbackFilter = pcgNameFilter)
        .filters(createBasicPcgSearchQueryFilters())
        .build()
}

fun createPcgCollectionSearchQueryExecutor(): SearchQueryExecutor<PcgSearchQueryFlag> {
    return createPcgBaseBuilder(pcgSearchQueryConfig, queryBuilder, pcgCollectionNameFilter)
        .filters(createPcgCollectionSearchQueryFilters())
        .filters(createBasicPcgSearchQueryFilters())
        .build()
}
