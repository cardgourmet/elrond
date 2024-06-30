package dev.cowzy.cardgourmet.tcg.config.card.mtg

import dev.cowzy.cardgourmet.chef.commons.model.image.CardImage
import dev.cowzy.cardgourmet.commons.database.Schemata
import dev.cowzy.cardgourmet.commons.database.card.mtg.*
import dev.cowzy.cardgourmet.elrond.QueryFilter
import dev.cowzy.cardgourmet.elrond.config.*
import dev.cowzy.cardgourmet.elrond.query.BooleanQueryExpression
import dev.cowzy.cardgourmet.elrond.query.SearchQuery
import dev.cowzy.cardgourmet.elrond.query.SearchQueryMode
import dev.cowzy.cardgourmet.elrond.values.ValueProviderPool
import dev.cowzy.cardgourmet.tcg.config.card.TcgCardSearchQueryDistinctMode
import dev.cowzy.kuery.Order
import dev.cowzy.kuery.query.SelectQueryBuilder
import dev.cowzy.kuery.reflection.columnName

private val queryBuilder: ((SearchQuery<MtgCardSearchQueryFlag, TcgCardSearchQueryDistinctMode>, SearchQueryMode, SelectQueryBuilder) -> Unit) = queryBuilder@{ query, mode, builder ->
    val preferMode = query.flags.firstOfOrNull(MtgCardSearchQueryFlag.preferModes)

    if (!query.flags.contains(MtgCardSearchQueryFlag.INCLUDE_EXTRAS)) {
        builder.whereInRaw(MtgPrint::id, "(SELECT id FROM ${Schemata.MAGIC_THE_GATHERING}.primary_print_ids)")
    }

    if (!query.flags.contains(MtgCardSearchQueryFlag.ANY_LANGUAGE)) {
        builder.whereInRaw(MtgCardFaceTranslation::language, "(?, 'en')") { stmt, index ->
            stmt.setString(index.getAndIncrement(), query.preferredLanguage)
        }
    }

    // No need to apply sort for count/random queries.
    if (mode != SearchQueryMode.SEARCH) return@queryBuilder

    applyMtgSortPreLanguage(builder, preferMode)

    val languageSort = "CASE " +
            "WHEN(${MtgCardFaceTranslation::language.columnName()} = ?) THEN 1 " +
            "WHEN(${MtgCardFaceTranslation::language.columnName()} = 'en') THEN 2 " +
            "ELSE 3 " +
            "END"

    builder.orderByRaw(languageSort) { stmt, index ->
        stmt.setString(index.getAndIncrement(), query.preferredLanguage)
    }

    applyMtgSortPostLanguage(query, builder, preferMode)
}

fun applyMtgSortPreLanguage(builder: SelectQueryBuilder, preferMode: MtgCardSearchQueryFlag?) {
    // Always prefer cards with images.
    builder.orderByRaw("CASE WHEN(${CardImage::imageId.columnName()} IS NOT NULL) THEN 1 ELSE 2 END")

    // Next, sort by release date or price (if required).
    when (preferMode) {
        MtgCardSearchQueryFlag.PREFER_OLDEST -> builder.orderBy(MtgPrint::releaseDate)
        MtgCardSearchQueryFlag.PREFER_NEWEST -> builder.orderBy(MtgPrint::releaseDate, Order.DESCENDING)
        MtgCardSearchQueryFlag.PREFER_EUR_LOW -> builder.orderByRaw("COALESCE(${MtgPrintPrice::priceEur.columnName()}, 0)")
        MtgCardSearchQueryFlag.PREFER_EUR_HIGH -> builder.orderBy("COALESCE(${MtgPrintPrice::priceEur.columnName()}, 0)", Order.DESCENDING)
        MtgCardSearchQueryFlag.PREFER_USD_LOW -> builder.orderBy("COALESCE(${MtgPrintPrice::priceUsd.columnName()}, 0)")
        MtgCardSearchQueryFlag.PREFER_USD_HIGH -> builder.orderBy("COALESCE(${MtgPrintPrice::priceUsd.columnName()}, 0)", Order.DESCENDING)
        MtgCardSearchQueryFlag.PREFER_TIX_LOW -> builder.orderBy("COALESCE(${MtgPrintPrice::priceTix.columnName()}, 0)")
        MtgCardSearchQueryFlag.PREFER_TIX_HIGH -> builder.orderBy("COALESCE(${MtgPrintPrice::priceTix.columnName()}, 0)", Order.DESCENDING)
        else -> Unit
    }

    // Next, sort by set (if required).
    if (preferMode == MtgCardSearchQueryFlag.PREFER_PROMO) {
        builder.orderByRaw("CASE WHEN(CARDINALITY(${MtgPrint::promoTypes.columnName()}) > 0) THEN 1 ELSE 2 END")
    } else if (preferMode == MtgCardSearchQueryFlag.PREFER_ARENA) {
        builder.orderByRaw("CASE WHEN(${MtgPrint::mediums.columnName()} = ARRAY['arena']::text[]) THEN 1 ELSE 2 END")
    }
}

fun applyMtgSortPostLanguage(query: SearchQuery<MtgCardSearchQueryFlag, TcgCardSearchQueryDistinctMode>, builder: SelectQueryBuilder, preferMode: MtgCardSearchQueryFlag?) {
    builder.orderByRaw("array_position(ARRAY[?, 'en'], ${MtgPrintFaceTranslation::language.columnName()})") { stmt, index ->
        stmt.setString(index.getAndIncrement(), query.preferredLanguage)
    }

    // Apply default sort.
    if (preferMode == null) {
        builder.orderByRaw("CASE WHEN(${MtgPrint::mediums.columnName()} = ARRAY['arena']::text[]) THEN 3 WHEN(CARDINALITY(${MtgPrint::promoTypes.columnName()}) > 0) THEN 2 ELSE 1 END")
        builder.orderBy(MtgPrint::releaseDate, Order.DESCENDING)
    }

    // Lastly, sort by collector number.
    builder.orderBy(MtgPrint::collectorNumberValue) // rough sorting
    builder.orderBy(MtgPrint::collectorNumber) // exact sorting for subset

    // Make sure to always return the same face.
    builder.orderBy(MtgCardFace::index)
}

fun createMtgCardBaseBuilder(
    config: SearchQuerySqlConfig,
    builder: (SearchQuery<MtgCardSearchQueryFlag, TcgCardSearchQueryDistinctMode>, SearchQueryMode, SelectQueryBuilder) -> Unit = queryBuilder,
    fallbackFilter: QueryFilter
): SearchQueryExecutorBuilder<MtgCardSearchQueryFlag, TcgCardSearchQueryDistinctMode> {
    return SearchQueryExecutorBuilder<MtgCardSearchQueryFlag, TcgCardSearchQueryDistinctMode>(config)
        .fallbackFilter(fallbackFilter)
        .flags(*MtgCardSearchQueryFlag.values())
        // TODO: distinct mode unique:art
        .distinctMode(TcgCardSearchQueryDistinctMode.UNIQUE_CARDS, MtgCard::id)
        .distinctMode(TcgCardSearchQueryDistinctMode.UNIQUE_FACES, MtgCardFace::id)
        .distinctMode(TcgCardSearchQueryDistinctMode.UNIQUE_PRINTS, MtgPrint::id)
        .distinctMode(TcgCardSearchQueryDistinctMode.UNIQUE_PRINT_FACES, MtgPrintFace::id)
        .sortModes(*MtgCardSortMode.values()) { expression ->
            when (expression) {
                is BooleanQueryExpression -> MtgCardSortMode.RELEASE_DATE
                else -> MtgCardSortMode.NAME
            }
        }
        .customTables { query, mode ->
            when (mode) {
                SearchQueryMode.SEARCH -> {
                    val preferMode = query.flags.firstOfOrNull(MtgCardSearchQueryFlag.preferModes)

                    setOf(CardImage::class, MtgCardFaceTranslation::class) + when {
                        MtgCardSearchQueryFlag.costPreferModes.contains(preferMode) -> setOf(MtgPrintPrice::class)
                        else -> emptySet()
                    }
                }

                else -> setOf(MtgCardFaceTranslation::class)
            }
        }
        .customBuilder(builder)
        .transformAttempt {
            val anyLang = it.flags.contains(MtgCardSearchQueryFlag.ANY_LANGUAGE)
            val extras = it.flags.contains(MtgCardSearchQueryFlag.INCLUDE_EXTRAS)
            when {
                anyLang && extras -> null
                !extras -> it.copy(flags = it.flags + MtgCardSearchQueryFlag.INCLUDE_EXTRAS)
                else -> it.copy(flags = it.flags + MtgCardSearchQueryFlag.ANY_LANGUAGE)
            }
        }
        .transformAttempt {
            val anyLang = it.flags.contains(MtgCardSearchQueryFlag.ANY_LANGUAGE)
            val extras = it.flags.contains(MtgCardSearchQueryFlag.INCLUDE_EXTRAS)
            when {
                anyLang && extras -> null
                !anyLang -> it.copy(flags = it.flags + MtgCardSearchQueryFlag.ANY_LANGUAGE)
                else -> it.copy(flags = it.flags + MtgCardSearchQueryFlag.ANY_LANGUAGE + MtgCardSearchQueryFlag.INCLUDE_EXTRAS)
            }
        }
        .transformAttempt {
            val anyLang = it.flags.contains(MtgCardSearchQueryFlag.ANY_LANGUAGE)
            val extras = it.flags.contains(MtgCardSearchQueryFlag.INCLUDE_EXTRAS)
            when {
                anyLang && extras -> null
                else -> it.copy(flags = it.flags + MtgCardSearchQueryFlag.ANY_LANGUAGE + MtgCardSearchQueryFlag.INCLUDE_EXTRAS)
            }
        }
}

fun createMtgCardSearchQueryExecutor(providers: ValueProviderPool): SearchQueryExecutor<MtgCardSearchQueryFlag, TcgCardSearchQueryDistinctMode> {
    val builder = SearchQueryFilterBuilder(providers) {
        configureBasicMtgCardFilters()
    }

    val filters = builder.build()
    val defaultFilter = filters.single { it.keywords.contains("name") }

    return createMtgCardBaseBuilder(mtgBasicSearchQueryConfig, queryBuilder, defaultFilter)
        .filters(filters)
        .build()
}

fun <T : Enum<T>> Iterable<T>.firstOfOrNull(values: Collection<T>) = this.firstOrNull { values.contains(it) }
