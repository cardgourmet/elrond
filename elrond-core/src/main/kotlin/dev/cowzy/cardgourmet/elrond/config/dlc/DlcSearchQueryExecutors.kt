package dev.cowzy.cardgourmet.elrond.config.dlc

import dev.cowzy.cardgourmet.chef.commons.model.image.CardImage
import dev.cowzy.cardgourmet.commons.database.card.dlc.DlcCardTranslation
import dev.cowzy.cardgourmet.commons.database.card.dlc.DlcPrint
import dev.cowzy.cardgourmet.commons.database.card.dlc.DlcPrintTranslation
import dev.cowzy.cardgourmet.commons.database.set.dlc.DlcSet
import dev.cowzy.cardgourmet.elrond.QueryFilter
import dev.cowzy.cardgourmet.elrond.config.SearchQueryConfig
import dev.cowzy.cardgourmet.elrond.config.SearchQueryConfigBuilder
import dev.cowzy.cardgourmet.elrond.config.SearchQueryExecutor
import dev.cowzy.cardgourmet.elrond.config.SearchQueryExecutorBuilder
import dev.cowzy.cardgourmet.elrond.query.SearchQuery
import dev.cowzy.cardgourmet.elrond.query.SearchQueryMode
import dev.cowzy.cardgourmet.elrond.values.ValueProviderPool
import dev.cowzy.kuery.Order
import dev.cowzy.kuery.query.SelectQueryBuilder
import dev.cowzy.kuery.reflection.columnName

private val queryBuilder: ((SearchQuery<DlcSearchQueryFlag>, SelectQueryBuilder) -> Unit) = queryBuilder@{ query, builder ->
    if (!query.flags.contains(DlcSearchQueryFlag.ANY_LANGUAGE)) {
        builder.whereInRaw(DlcCardTranslation::language, "(?, 'en')") { stmt, index ->
            stmt.setString(index.getAndIncrement(), query.preferredLanguage)
        }
    }

    // No need to apply sort for count queries.
    if (query.mode == SearchQueryMode.COUNT) return@queryBuilder

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

fun applyDlcSort(query: SearchQuery<DlcSearchQueryFlag>, builder: SelectQueryBuilder) {
    builder.orderByRaw("array_position(ARRAY[?, 'en'], ${DlcPrintTranslation::language.columnName()})") { stmt, index ->
        stmt.setString(index.getAndIncrement(), query.preferredLanguage)
    }

    // Apply default sort.
    builder.orderBy(DlcSet::releaseDate, Order.DESCENDING)

    // Lastly, sort by collector number.
    builder.orderBy(DlcPrint::collectorNumberValue) // rough sorting
    builder.orderBy(DlcPrint::collectorNumber) // exact sorting for subset
}

fun createDlcBaseBuilder(
    config: SearchQueryConfig,
    builder: (SearchQuery<DlcSearchQueryFlag>, SelectQueryBuilder) -> Unit = queryBuilder,
    fallbackFilter: QueryFilter
): SearchQueryExecutorBuilder<DlcSearchQueryFlag> {
    return SearchQueryExecutorBuilder<DlcSearchQueryFlag>(config)
        .fallbackFilter(fallbackFilter)
        .flags(*DlcSearchQueryFlag.values())
        .customTables {
            when (it.mode) {
                SearchQueryMode.SEARCH -> setOf(CardImage::class, DlcSet::class, DlcCardTranslation::class)
                SearchQueryMode.COUNT -> setOf(DlcCardTranslation::class)
            }
        }
        .customBuilder(builder)
        .transformAttempt {
            val anyLang = it.flags.contains(DlcSearchQueryFlag.ANY_LANGUAGE)
            when {
                anyLang -> null
                else -> it.copy(flags = it.flags + DlcSearchQueryFlag.ANY_LANGUAGE)
            }
        }
}

fun createDlcSearchQueryExecutor(providers: ValueProviderPool): SearchQueryExecutor<DlcSearchQueryFlag> {
    val builder = SearchQueryConfigBuilder(providers) {
        configureBasicDlcFilters()
    }

    val filters = builder.build()
    val defaultFilter = filters.single { it.keywords.contains("name") }

    return createDlcBaseBuilder(dlcBasicSearchQueryConfig, queryBuilder, defaultFilter)
        .filters(filters)
        .build()
}
