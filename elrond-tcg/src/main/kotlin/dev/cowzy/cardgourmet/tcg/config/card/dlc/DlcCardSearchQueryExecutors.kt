package dev.cowzy.cardgourmet.tcg.config.card.dlc

import dev.cowzy.cardgourmet.chef.commons.model.image.CardImage
import dev.cowzy.cardgourmet.commons.database.card.dlc.DlcCard
import dev.cowzy.cardgourmet.commons.database.card.dlc.DlcCardTranslation
import dev.cowzy.cardgourmet.commons.database.card.dlc.DlcPrint
import dev.cowzy.cardgourmet.commons.database.card.dlc.DlcPrintTranslation
import dev.cowzy.cardgourmet.commons.database.set.dlc.DlcSet
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

private val queryBuilder: ((SearchQuery<DlcCardSearchQueryFlag, TcgCardSearchQueryDistinctMode>, SearchQueryMode, SelectQueryBuilder) -> Unit) = queryBuilder@{ query, mode, builder ->
    if (!query.flags.contains(DlcCardSearchQueryFlag.ANY_LANGUAGE)) {
        builder.whereInRaw(DlcCardTranslation::language, "(?, 'en')") { stmt, index ->
            stmt.setString(index.getAndIncrement(), query.preferredLanguage)
        }
    }

    if (query.flags.contains(DlcCardSearchQueryFlag.REQUIRE_IMAGE)) {
        builder.whereNotNull(CardImage::imageId)
    }

    // No need to apply sort for count/random queries.
    if (mode != SearchQueryMode.SEARCH) return@queryBuilder

    // Always prefer cards with images.
    builder.orderByRaw("CASE WHEN(${CardImage::imageId.columnName()} IS NOT NULL) THEN 1 ELSE 2 END")

    val languageSort = "CASE " +
            "WHEN(${DlcCardTranslation::language.columnName()} = ?) THEN 1 " +
            "WHEN(${DlcCardTranslation::language.columnName()} = 'en') THEN 2 " +
            "ELSE 3 " +
            "END"

    builder.orderByRaw(languageSort) { stmt, index ->
        stmt.setString(index.getAndIncrement(), query.preferredLanguage)
    }

    applyDlcSort(query, builder)
}

fun applyDlcSort(query: SearchQuery<DlcCardSearchQueryFlag, TcgCardSearchQueryDistinctMode>, builder: SelectQueryBuilder) {
    builder.orderByRaw("array_position(ARRAY[?, 'en'], ${DlcPrintTranslation::language.columnName()})") { stmt, index ->
        stmt.setString(index.getAndIncrement(), query.preferredLanguage)
    }

    // Apply default sort.
    builder.orderBy(DlcSet::releaseDate, Order.DESCENDING)

    // Lastly, sort by collector number.
    builder.orderBy(DlcPrint::collectorNumberValue) // rough sorting
    builder.orderBy(DlcPrint::collectorNumber) // exact sorting for subset
}

fun createDlcCardBaseBuilder(
    config: SearchQuerySqlConfig,
    builder: (SearchQuery<DlcCardSearchQueryFlag, TcgCardSearchQueryDistinctMode>, SearchQueryMode, SelectQueryBuilder) -> Unit = queryBuilder,
    fallbackFilter: QueryFilter
): SearchQueryExecutorBuilder<DlcCardSearchQueryFlag, TcgCardSearchQueryDistinctMode> {
    return SearchQueryExecutorBuilder<DlcCardSearchQueryFlag, TcgCardSearchQueryDistinctMode>(config)
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
        .customTables { _, mode ->
            when (mode) {
                SearchQueryMode.SEARCH -> setOf(CardImage::class, DlcSet::class, DlcCardTranslation::class)
                else -> setOf(DlcCardTranslation::class)
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

fun createDlcCardSearchQueryExecutor(providers: ValueProviderPool): SearchQueryExecutor<DlcCardSearchQueryFlag, TcgCardSearchQueryDistinctMode> {
    val builder = SearchQueryFilterBuilder(providers) {
        configureBasicDlcCardFilters()
    }

    val filters = builder.build()
    val defaultFilter = filters.single { it.keywords.contains("name") }

    return createDlcCardBaseBuilder(dlcBasicCardSearchQueryConfig, queryBuilder, defaultFilter)
        .filters(filters)
        .build()
}
