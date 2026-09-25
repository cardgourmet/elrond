package dev.cowzy.cardgourmet.elrond.user.config.pcg

import dev.cowzy.cardgourmet.chef.commons.model.image.CardImage
import dev.cowzy.cardgourmet.chef.commons.model.card.pcg.PcgCardTranslation
import dev.cowzy.cardgourmet.commons.user.UserCard
import dev.cowzy.cardgourmet.elrond.ColumnContext
import dev.cowzy.cardgourmet.elrond.config.SearchQueryFilterBuilder
import dev.cowzy.cardgourmet.elrond.config.SearchQueryExecutor
import dev.cowzy.cardgourmet.elrond.query.SearchQuery
import dev.cowzy.cardgourmet.elrond.query.SearchQueryMode
import dev.cowzy.cardgourmet.elrond.user.config.configureCollectionFilters
import dev.cowzy.cardgourmet.elrond.values.ValueProviderPool
import dev.cowzy.cardgourmet.tcg.config.card.TcgCardSearchQueryDistinctMode
import dev.cowzy.cardgourmet.tcg.config.card.pcg.PcgCardSearchQueryFlag
import dev.cowzy.cardgourmet.tcg.config.card.pcg.applyPcgSort
import dev.cowzy.cardgourmet.tcg.config.card.pcg.configureBasicPcgCardFilters
import dev.cowzy.cardgourmet.tcg.config.card.pcg.createPcgCardBaseBuilder
import dev.cowzy.kuery.query.SelectQueryBuilder
import dev.cowzy.kuery.query.whereNotNull
import dev.cowzy.kuery.reflection.columnName

private val queryBuilder: ((SearchQuery<PcgCardSearchQueryFlag, TcgCardSearchQueryDistinctMode>, SearchQueryMode, SelectQueryBuilder, ColumnContext) -> Unit) = queryBuilder@{ query, mode, builder, ctx ->
    if (!query.flags.contains(PcgCardSearchQueryFlag.ANY_LANGUAGE)) {
        builder.whereColumn(ctx.resolve(UserCard::language), ctx.resolve(PcgCardTranslation::language))
    }

    builder.whereNotNull(ctx.resolve(UserCard::id))

    // No need to apply sort for count/random queries.
    if (mode != SearchQueryMode.SEARCH) return@queryBuilder

    // Always prefer cards with images.
    builder.orderByRaw("CASE WHEN(${ctx.resolve(CardImage::imageId)} IS NOT NULL) THEN 1 ELSE 2 END")

    val languageSort = "CASE " +
            "WHEN(${ctx.resolve(PcgCardTranslation::language)} = ${ctx.resolve(UserCard::language)}) THEN 1 " +
            "WHEN(${ctx.resolve(PcgCardTranslation::language)} = ?) THEN 2 " +
            "WHEN(${ctx.resolve(PcgCardTranslation::language)} = 'en') THEN 3 " +
            "ELSE 4 " +
            "END"

    builder.orderByRaw(languageSort) { stmt, index ->
        stmt.setString(index.getAndIncrement(), query.preferredLanguage)
    }

    applyPcgSort(query, builder, ctx)
}

fun createPcgSearchQueryExecutor(providers: ValueProviderPool): SearchQueryExecutor<PcgCardSearchQueryFlag, TcgCardSearchQueryDistinctMode> {
    val builder = SearchQueryFilterBuilder(providers) {
        configureBasicPcgCardFilters()
    }

    val filters = builder.build()
    val defaultFilter = filters.single { it.keywords.contains("name") }

    return createPcgCardBaseBuilder(pcgSearchQueryConfig, fallbackFilter = defaultFilter)
        .filters(filters)
        .build()
}

fun createPcgCollectionSearchQueryExecutor(providers: ValueProviderPool): SearchQueryExecutor<PcgCardSearchQueryFlag, TcgCardSearchQueryDistinctMode> {
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

