package dev.cowzy.cardgourmet.tcg.config.card.dlc

import dev.cowzy.cardgourmet.chef.commons.model.image.CardImage
import dev.cowzy.cardgourmet.chef.commons.model.card.dlc.DlcCard
import dev.cowzy.cardgourmet.chef.commons.model.card.dlc.DlcCardTranslation
import dev.cowzy.cardgourmet.chef.commons.model.card.dlc.DlcPrint
import dev.cowzy.cardgourmet.chef.commons.model.card.dlc.DlcPrintTranslation
import dev.cowzy.cardgourmet.chef.commons.model.set.dlc.DlcSet
import dev.cowzy.cardgourmet.elrond.ExecutionContext
import dev.cowzy.cardgourmet.elrond.QueryFilter
import dev.cowzy.cardgourmet.elrond.config.*
import dev.cowzy.cardgourmet.elrond.query.BooleanQueryExpression
import dev.cowzy.cardgourmet.elrond.query.SearchQuery
import dev.cowzy.cardgourmet.elrond.query.SearchQueryMode
import dev.cowzy.cardgourmet.elrond.values.ValueProviderPool
import dev.cowzy.cardgourmet.tcg.config.card.TcgCardSearchQueryDistinctMode
import dev.cowzy.kuery.Order
import dev.cowzy.kuery.query.SelectQueryBuilder
import dev.cowzy.kuery.query.whereNotNull

private val queryBuilder: ((SearchQuery<DlcCardSearchQueryFlag, TcgCardSearchQueryDistinctMode>, SearchQueryMode, SelectQueryBuilder, ExecutionContext) -> Unit) = queryBuilder@{ query, mode, builder, ctx ->
    if (!query.flags.contains(DlcCardSearchQueryFlag.ANY_LANGUAGE)) {
        builder.whereInRaw(ctx.resolve(DlcCardTranslation::language), "(?, 'en')") { stmt, index ->
            stmt.setString(index.getAndIncrement(), query.preferredLanguage)
        }
    }

    if (query.flags.contains(DlcCardSearchQueryFlag.REQUIRE_IMAGE)) {
        builder.whereNotNull(ctx.resolve(CardImage::imageId))
    }

    // No need to apply sort for count/random queries.
    if (mode != SearchQueryMode.SEARCH) return@queryBuilder

    // Always prefer cards with images.
    builder.orderByRaw("CASE WHEN(${ctx.resolve(CardImage::imageId)} IS NOT NULL) THEN 1 ELSE 2 END")

    val languageSort = "CASE " +
            "WHEN(${ctx.resolve(DlcCardTranslation::language)} = ?) THEN 1 " +
            "WHEN(${ctx.resolve(DlcCardTranslation::language)} = 'en') THEN 2 " +
            "ELSE 3 " +
            "END"

    builder.orderByRaw(languageSort) { stmt, index ->
        stmt.setString(index.getAndIncrement(), query.preferredLanguage)
    }

    applyDlcSort(query, builder, ctx)
}

fun applyDlcSort(query: SearchQuery<DlcCardSearchQueryFlag, TcgCardSearchQueryDistinctMode>, builder: SelectQueryBuilder, ctx: ExecutionContext) {
    builder.orderByRaw("array_position(ARRAY[?, 'en'], ${ctx.resolve(DlcPrintTranslation::language)})") { stmt, index ->
        stmt.setString(index.getAndIncrement(), query.preferredLanguage)
    }

    // Apply default sort.
    builder.orderBy(ctx.resolve(DlcSet::releaseDate), Order.DESCENDING)

    // Lastly, sort by collector number.
    builder.orderBy(ctx.resolve(DlcPrint::collectorNumberValue)) // rough sorting
    builder.orderBy(ctx.resolve(DlcPrint::collectorNumber)) // exact sorting for subset
}

fun <PrincipalType : Any> createDlcCardBaseBuilder(
    config: SearchQuerySqlConfig,
    builder: (SearchQuery<DlcCardSearchQueryFlag, TcgCardSearchQueryDistinctMode>, SearchQueryMode, SelectQueryBuilder, ExecutionContext) -> Unit = queryBuilder,
    fallbackFilter: QueryFilter
): SearchQueryExecutorBuilder<DlcCardSearchQueryFlag, TcgCardSearchQueryDistinctMode, PrincipalType> {
    return SearchQueryExecutorBuilder<DlcCardSearchQueryFlag, TcgCardSearchQueryDistinctMode, PrincipalType>(config)
        .fallbackFilter(fallbackFilter)
        .flags(*DlcCardSearchQueryFlag.values())
        // TODO: distinct mode unique:art
        .distinctMode(TcgCardSearchQueryDistinctMode.UNIQUE_CARDS, DlcCard::id)
        .distinctMode(TcgCardSearchQueryDistinctMode.UNIQUE_FACES, DlcCard::id)
        .distinctMode(TcgCardSearchQueryDistinctMode.UNIQUE_PRINTS, DlcPrint::id)
        .distinctMode(TcgCardSearchQueryDistinctMode.UNIQUE_PRINT_FACES, DlcPrint::id)
        .sortModes(*DlcCardSortMode.values()) { expression ->
            when (expression) {
                is BooleanQueryExpression -> DlcCardSortMode.RELEASE_DATE
                else -> DlcCardSortMode.NAME
            }
        }
        .customTables { query, mode ->
            when (mode) {
                SearchQueryMode.SEARCH -> setOf(CardImage::class, DlcSet::class, DlcCardTranslation::class)
                else -> when {
                    query.flags.contains(DlcCardSearchQueryFlag.REQUIRE_IMAGE) -> setOf(DlcCardTranslation::class, CardImage::class)
                    else -> setOf(DlcCardTranslation::class)
                }
            }
        }
        .customBuilder(builder)
        .transformAttempt {
            val anyLang = it.flags.contains(DlcCardSearchQueryFlag.ANY_LANGUAGE)
            when {
                anyLang -> null
                else -> it.copy(flags = it.flags + DlcCardSearchQueryFlag.ANY_LANGUAGE)
            }
        }
}

fun <PrincipalType : Any> createDlcCardSearchQueryExecutor(
    providers: ValueProviderPool,
    transform: SearchQueryExecutorTransform? = null
): SearchQueryExecutor<DlcCardSearchQueryFlag, TcgCardSearchQueryDistinctMode, PrincipalType> {
    val builder = SearchQueryFilterBuilder(providers) {
        configureBasicDlcCardFilters()
        transform?.applyFilters?.invoke(this)
    }

    val filters = builder.build()
    val defaultFilter = filters.single { it.keywords.contains("name") }

    return createDlcCardBaseBuilder<PrincipalType>(
        transform?.transformConfig?.invoke(dlcBasicCardSearchQueryConfig) ?: dlcBasicCardSearchQueryConfig,
        queryBuilder,
        defaultFilter
    )
        .filters(filters)
        .build()
}
