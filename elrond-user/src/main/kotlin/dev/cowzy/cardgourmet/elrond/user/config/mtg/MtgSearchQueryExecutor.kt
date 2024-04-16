package dev.cowzy.cardgourmet.elrond.user.config.mtg

import dev.cowzy.cardgourmet.commons.database.Schemata
import dev.cowzy.cardgourmet.commons.database.card.mtg.*
import dev.cowzy.cardgourmet.commons.user.UserCard
import dev.cowzy.cardgourmet.elrond.config.SearchQueryExecutor
import dev.cowzy.cardgourmet.elrond.config.mtg.*
import dev.cowzy.cardgourmet.elrond.query.SearchQuery
import dev.cowzy.kuery.query.SelectQueryBuilder
import dev.cowzy.kuery.reflection.columnName

private val queryBuilder: ((SearchQuery<MtgSearchQueryFlag>, SelectQueryBuilder) -> Unit) = { query, builder ->
    val preferMode = query.flags.firstOfOrNull(MtgSearchQueryFlag.preferModes)

    if (!query.flags.contains(MtgSearchQueryFlag.INCLUDE_EXTRAS)) {
        builder.whereInRaw(MtgPrint::id, "(SELECT id FROM ${Schemata.MAGIC_THE_GATHERING}.primary_print_ids)")
    }

    if (!query.flags.contains(MtgSearchQueryFlag.ANY_LANGUAGE)) {
        builder.whereColumn(UserCard::language, MtgCardFaceTranslation::language)
    }

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

fun createDlcSearchQueryExecutor(providers: MtgValueProviders): SearchQueryExecutor<MtgSearchQueryFlag> {
    return createMtgBaseBuilder(mtgSearchQueryConfig, queryBuilder, mtgNameFilter)
        .filters(createBasicMtgSearchQueryFilters(providers))
        .build()
}

fun createMtgCollectionSearchQueryExecutor(providers: MtgValueProviders): SearchQueryExecutor<MtgSearchQueryFlag> {
    return createMtgBaseBuilder(mtgSearchQueryConfig, queryBuilder, mtgCollectionNameFilter)
        .filters(createMtgCollectionSearchQueryFilters())
        .filters(createBasicMtgSearchQueryFilters(providers))
        .build()
}
