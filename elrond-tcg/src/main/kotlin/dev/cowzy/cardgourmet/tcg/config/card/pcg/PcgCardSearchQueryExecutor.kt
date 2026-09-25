package dev.cowzy.cardgourmet.tcg.config.card.pcg

import dev.cowzy.cardgourmet.chef.commons.model.image.CardImage
import dev.cowzy.cardgourmet.chef.commons.model.card.pcg.PcgCard
import dev.cowzy.cardgourmet.chef.commons.model.card.pcg.PcgCardTranslation
import dev.cowzy.cardgourmet.chef.commons.model.card.pcg.PcgPrint
import dev.cowzy.cardgourmet.chef.commons.model.set.pcg.PcgSet
import dev.cowzy.cardgourmet.elrond.ColumnContext
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
import dev.cowzy.kuery.reflection.columnName

private val queryBuilder: ((SearchQuery<PcgCardSearchQueryFlag, TcgCardSearchQueryDistinctMode>, SearchQueryMode, SelectQueryBuilder, ColumnContext) -> Unit) = queryBuilder@{ query, mode, builder, ctx ->
    if (!query.flags.contains(PcgCardSearchQueryFlag.ANY_LANGUAGE)) {
        builder.whereInRaw(ctx.resolve(PcgCardTranslation::language), "(?, 'en')") { stmt, index ->
            stmt.setString(index.getAndIncrement(), query.preferredLanguage)
        }
    }

    if (query.flags.contains(PcgCardSearchQueryFlag.REQUIRE_IMAGE)) {
        builder.whereNotNull(ctx.resolve(CardImage::imageId))
    }

    // No need to apply sort for count/random queries.
    if (mode != SearchQueryMode.SEARCH) return@queryBuilder

    // Always prefer cards with images.
    builder.orderByRaw("CASE WHEN(${ctx.resolve(CardImage::imageId)} IS NOT NULL) THEN 1 ELSE 2 END")

    val languageSort = "CASE " +
            "WHEN(${ctx.resolve(PcgCardTranslation::language)} = ?) THEN 1 " +
            "WHEN(${ctx.resolve(PcgCardTranslation::language)} = 'en') THEN 2 " +
            "ELSE 3 " +
            "END"

    builder.orderByRaw(languageSort) { stmt, index ->
        stmt.setString(index.getAndIncrement(), query.preferredLanguage)
    }

    applyPcgSort(query, builder, ctx)
}

fun applyPcgSort(query: SearchQuery<PcgCardSearchQueryFlag, TcgCardSearchQueryDistinctMode>, builder: SelectQueryBuilder, ctx: ColumnContext) {
    builder.orderByRaw("array_position(ARRAY[?, 'en'], ${ctx.resolve(PcgCardTranslation::language)})") { stmt, index ->
        stmt.setString(index.getAndIncrement(), query.preferredLanguage)
    }

    // Apply default sort.
    builder.orderBy(ctx.resolve(PcgSet::releaseStartDate), Order.DESCENDING)
    builder.orderBy(ctx.resolve(PcgSet::releaseEndDate), Order.DESCENDING)

    // Lastly, sort by collector number.
    builder.orderBy(ctx.resolve(PcgPrint::collectorNumberValue)) // rough sorting
    builder.orderBy(ctx.resolve(PcgPrint::collectorNumber)) // exact sorting for subset
}

fun createPcgCardBaseBuilder(
    config: SearchQuerySqlConfig,
    builder: (SearchQuery<PcgCardSearchQueryFlag, TcgCardSearchQueryDistinctMode>, SearchQueryMode, SelectQueryBuilder, ColumnContext) -> Unit = queryBuilder,
    fallbackFilter: QueryFilter
): SearchQueryExecutorBuilder<PcgCardSearchQueryFlag, TcgCardSearchQueryDistinctMode> {
    return SearchQueryExecutorBuilder<PcgCardSearchQueryFlag, TcgCardSearchQueryDistinctMode>(config)
        .fallbackFilter(fallbackFilter)
        .flags(*PcgCardSearchQueryFlag.values())
        // TODO: distinct mode unique:art
        .distinctMode(TcgCardSearchQueryDistinctMode.UNIQUE_CARDS, PcgCard::id)
        .distinctMode(TcgCardSearchQueryDistinctMode.UNIQUE_FACES, PcgCard::id)
        .distinctMode(TcgCardSearchQueryDistinctMode.UNIQUE_PRINTS, PcgPrint::id)
        .distinctMode(TcgCardSearchQueryDistinctMode.UNIQUE_PRINT_FACES, PcgPrint::id)
        .sortModes(*PcgCardSortMode.values()) { expression ->
            when (expression) {
                is BooleanQueryExpression -> PcgCardSortMode.RELEASE_DATE
                else -> PcgCardSortMode.NAME
            }
        }
        .customTables { query, mode ->
            when (mode) {
                SearchQueryMode.SEARCH -> setOf(CardImage::class, PcgSet::class, PcgCardTranslation::class)
                else -> when {
                    query.flags.contains(PcgCardSearchQueryFlag.REQUIRE_IMAGE) -> setOf(PcgCardTranslation::class, CardImage::class)
                    else -> setOf(PcgCardTranslation::class)
                }
            }
        }
        .customBuilder(builder,)
        .transformAttempt {
            val anyLang = it.flags.contains(PcgCardSearchQueryFlag.ANY_LANGUAGE)
            when {
                anyLang -> null
                else -> it.copy(flags = it.flags + PcgCardSearchQueryFlag.ANY_LANGUAGE)
            }
        }
}

fun createPcgCardSearchQueryExecutor(providers: ValueProviderPool): SearchQueryExecutor<PcgCardSearchQueryFlag, TcgCardSearchQueryDistinctMode> {
    val builder = SearchQueryFilterBuilder(providers) {
        configureBasicPcgCardFilters()
    }

    val filters = builder.build()
    val defaultFilter = filters.single { it.keywords.contains("name") }

    return createPcgCardBaseBuilder(pcgBasicCardSearchQueryConfig, queryBuilder, defaultFilter)
        .filters(filters)
        .build()
}
