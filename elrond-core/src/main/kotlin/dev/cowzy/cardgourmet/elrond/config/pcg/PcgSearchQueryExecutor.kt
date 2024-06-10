package dev.cowzy.cardgourmet.elrond.config.pcg

import dev.cowzy.cardgourmet.chef.commons.model.image.CardImage
import dev.cowzy.cardgourmet.commons.database.card.pcg.PcgCard
import dev.cowzy.cardgourmet.commons.database.card.pcg.PcgCardTranslation
import dev.cowzy.cardgourmet.commons.database.card.pcg.PcgPrint
import dev.cowzy.cardgourmet.commons.database.set.pcg.PcgSet
import dev.cowzy.cardgourmet.elrond.QueryFilter
import dev.cowzy.cardgourmet.elrond.config.*
import dev.cowzy.cardgourmet.elrond.query.BooleanQueryExpression
import dev.cowzy.cardgourmet.elrond.query.SearchQuery
import dev.cowzy.cardgourmet.elrond.query.SearchQueryMode
import dev.cowzy.cardgourmet.elrond.values.ValueProviderPool
import dev.cowzy.kuery.Order
import dev.cowzy.kuery.query.SelectQueryBuilder
import dev.cowzy.kuery.reflection.columnName

private val queryBuilder: ((SearchQuery<PcgSearchQueryFlag>, SearchQueryMode, SelectQueryBuilder) -> Unit) = queryBuilder@{ query, mode, builder ->
    if (!query.flags.contains(PcgSearchQueryFlag.ANY_LANGUAGE)) {
        builder.whereInRaw(PcgCardTranslation::language, "(?, 'en')") { stmt, index ->
            stmt.setString(index.getAndIncrement(), query.preferredLanguage)
        }
    }

    // No need to apply sort for count/random queries.
    if (mode != SearchQueryMode.SEARCH) return@queryBuilder

    // Always prefer cards with images.
    builder.orderByRaw("CASE WHEN(${CardImage::imageId.columnName()} IS NOT NULL) THEN 1 ELSE 2 END")

    val languageSort = "CASE " +
            "WHEN(${PcgCardTranslation::language.columnName()} = ?) THEN 1 " +
            "WHEN(${PcgCardTranslation::language.columnName()} = 'en') THEN 2 " +
            "ELSE 3 " +
            "END"

    builder.orderByRaw(languageSort) { stmt, index ->
        stmt.setString(index.getAndIncrement(), query.preferredLanguage)
    }

    applyPcgSort(query, builder)
}

fun applyPcgSort(query: SearchQuery<PcgSearchQueryFlag>, builder: SelectQueryBuilder) {
    builder.orderByRaw("array_position(ARRAY[?, 'en'], ${PcgCardTranslation::language.columnName()})") { stmt, index ->
        stmt.setString(index.getAndIncrement(), query.preferredLanguage)
    }

    // Apply default sort.
    builder.orderBy(PcgSet::releaseStartDate, Order.DESCENDING)
    builder.orderBy(PcgSet::releaseEndDate, Order.DESCENDING)

    // Lastly, sort by collector number.
    builder.orderBy(PcgPrint::collectorNumberValue) // rough sorting
    builder.orderBy(PcgPrint::collectorNumber) // exact sorting for subset
}

fun createPcgBaseBuilder(
    config: SearchQueryConfig,
    builder: (SearchQuery<PcgSearchQueryFlag>, SearchQueryMode, SelectQueryBuilder) -> Unit = queryBuilder,
    fallbackFilter: QueryFilter
): SearchQueryExecutorBuilder<PcgSearchQueryFlag> {
    return SearchQueryExecutorBuilder<PcgSearchQueryFlag>(config)
        .fallbackFilter(fallbackFilter)
        .flags(*PcgSearchQueryFlag.values())
        // TODO: distinct mode unique:art
        .distinctMode(SearchQueryDistinctMode.UNIQUE_CARDS, PcgCard::id)
        .distinctMode(SearchQueryDistinctMode.UNIQUE_FACES, PcgCard::id)
        .distinctMode(SearchQueryDistinctMode.UNIQUE_PRINTS, PcgPrint::id)
        .distinctMode(SearchQueryDistinctMode.UNIQUE_PRINT_FACES, PcgPrint::id)
        .sortModes(*PcgSortMode.values()) { expression ->
            when (expression) {
                is BooleanQueryExpression -> PcgSortMode.RELEASE_DATE
                else -> PcgSortMode.NAME
            }
        }
        .customTables { _, mode ->
            when (mode) {
                SearchQueryMode.SEARCH -> setOf(CardImage::class, PcgSet::class, PcgCardTranslation::class)
                else -> setOf(PcgCardTranslation::class)
            }
        }
        .customBuilder(builder)
        .transformAttempt {
            val anyLang = it.flags.contains(PcgSearchQueryFlag.ANY_LANGUAGE)
            when {
                anyLang -> null
                else -> it.copy(flags = it.flags + PcgSearchQueryFlag.ANY_LANGUAGE)
            }
        }
}

fun createPcgSearchQueryExecutor(providers: ValueProviderPool): SearchQueryExecutor<PcgSearchQueryFlag> {
    val builder = SearchQueryConfigBuilder(providers) {
        configureBasicPcgFilters()
    }

    val filters = builder.build()
    val defaultFilter = filters.single { it.keywords.contains("name") }

    return createPcgBaseBuilder(pcgBasicSearchQueryConfig, queryBuilder, defaultFilter)
        .filters(filters)
        .build()
}
