package dev.cowzy.cardgourmet.elrond.config.pcg

import dev.cowzy.cardgourmet.chef.commons.model.image.CardImage
import dev.cowzy.cardgourmet.commons.database.card.pcg.PcgCardTranslation
import dev.cowzy.cardgourmet.commons.database.card.pcg.PcgPrint
import dev.cowzy.cardgourmet.commons.database.set.pcg.PcgSet
import dev.cowzy.cardgourmet.elrond.QueryFilter
import dev.cowzy.cardgourmet.elrond.config.SearchQueryConfig
import dev.cowzy.cardgourmet.elrond.config.SearchQueryConfigBuilder
import dev.cowzy.cardgourmet.elrond.config.SearchQueryExecutor
import dev.cowzy.cardgourmet.elrond.config.SearchQueryExecutorBuilder
import dev.cowzy.cardgourmet.elrond.query.SearchQuery
import dev.cowzy.cardgourmet.elrond.values.PropertyProviderPool
import dev.cowzy.cardgourmet.elrond.values.ValueProviderPool
import dev.cowzy.kuery.Order
import dev.cowzy.kuery.query.SelectQueryBuilder
import dev.cowzy.kuery.reflection.columnName

private val queryBuilder: ((SearchQuery<PcgSearchQueryFlag>, SelectQueryBuilder) -> Unit) = { query, builder ->
    if (!query.flags.contains(PcgSearchQueryFlag.ANY_LANGUAGE)) {
        builder.whereInRaw(PcgCardTranslation::language, "(?, 'en')") { stmt, index ->
            stmt.setString(index.getAndIncrement(), query.preferredLanguage)
        }
    }

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
    builder: (SearchQuery<PcgSearchQueryFlag>, SelectQueryBuilder) -> Unit = queryBuilder,
    fallbackFilter: QueryFilter
): SearchQueryExecutorBuilder<PcgSearchQueryFlag> {
    return SearchQueryExecutorBuilder<PcgSearchQueryFlag>(config)
        .fallbackFilter(fallbackFilter)
        .flags(*PcgSearchQueryFlag.values())
        .customTables {
            setOf(CardImage::class, PcgSet::class, PcgCardTranslation::class)
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

fun createPcgSearchQueryExecutor(providers: PropertyProviderPool): SearchQueryExecutor<PcgSearchQueryFlag> {
    val builder = SearchQueryConfigBuilder(providers) {
        configureBasicPcgFilters()
    }

    val filters = builder.build()
    val defaultFilter = filters.single { it.keywords.contains("name") }

    return createPcgBaseBuilder(pcgBasicSearchQueryConfig, queryBuilder, defaultFilter)
        .filters(filters)
        .build()
}
