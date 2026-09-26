package dev.cowzy.cardgourmet.elrond.user.config.dlc

import dev.cowzy.cardgourmet.chef.commons.model.image.CardImage
import dev.cowzy.cardgourmet.chef.commons.model.card.dlc.DlcCardTranslation
import dev.cowzy.cardgourmet.commons.user.UserCard
import dev.cowzy.cardgourmet.elrond.ExecutionContext
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

private val queryBuilder: ((SearchQuery<DlcCardSearchQueryFlag, TcgCardSearchQueryDistinctMode>, SearchQueryMode, SelectQueryBuilder, ExecutionContext) -> Unit) = queryBuilder@{ query, mode, builder, ctx ->
    if (!query.flags.contains(DlcCardSearchQueryFlag.ANY_LANGUAGE)) {
        builder.whereColumn(ctx.resolve(UserCard::language), ctx.resolve(DlcCardTranslation::language))
    }

    builder.whereNotNull(ctx.resolve(UserCard::id))

    // No need to apply sort for count/random queries.
    if (mode != SearchQueryMode.SEARCH) return@queryBuilder

    // Always prefer cards with images.
    builder.orderByRaw("CASE WHEN(${ctx.resolve(CardImage::imageId)} IS NOT NULL) THEN 1 ELSE 2 END")

    val languageSort = "CASE " +
            "WHEN(${ctx.resolve(DlcCardTranslation::language)} = ${ctx.resolve(UserCard::language)}) THEN 1 " +
            "WHEN(${ctx.resolve(DlcCardTranslation::language)} = ?) THEN 2 " +
            "WHEN(${ctx.resolve(DlcCardTranslation::language)} = 'en') THEN 3 " +
            "ELSE 4 " +
            "END"

    builder.orderByRaw(languageSort) { stmt, index ->
        stmt.setString(index.getAndIncrement(), query.preferredLanguage)
    }

    applyDlcSort(query, builder, ctx)
}


fun <PrincipalType : Any> createDlcSearchQueryExecutor(providers: ValueProviderPool): SearchQueryExecutor<DlcCardSearchQueryFlag, TcgCardSearchQueryDistinctMode, PrincipalType> {
    val builder = SearchQueryFilterBuilder(providers) {
        configureBasicDlcCardFilters()
    }

    val filters = builder.build()
    val defaultFilter = filters.single { it.keywords.contains("name") }

    return createDlcCardBaseBuilder<PrincipalType>(dlcSearchQueryConfig, fallbackFilter = defaultFilter)
        .filters(filters)
        .build()
}

fun <PrincipalType : Any> createDlcCollectionSearchQueryExecutor(providers: ValueProviderPool): SearchQueryExecutor<DlcCardSearchQueryFlag, TcgCardSearchQueryDistinctMode, PrincipalType> {
    val builder = SearchQueryFilterBuilder(providers) {
        configureBasicDlcCardFilters()
        configureCollectionFilters()
        configureDlcCollectionFilters()
    }

    val filters = builder.build()
    val defaultFilter = filters.single { it.keywords.contains("name") }

    return createDlcCardBaseBuilder<PrincipalType>(dlcSearchQueryConfig, queryBuilder, defaultFilter)
        .filters(filters)
        .build()
}
