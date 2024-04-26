package dev.cowzy.cardgourmet.elrond.config.mtg

import dev.cowzy.cardgourmet.commons.database.Schemata
import dev.cowzy.cardgourmet.commons.database.card.mtg.*
import dev.cowzy.cardgourmet.elrond.QueryFilter
import dev.cowzy.cardgourmet.elrond.config.SearchQueryConfig
import dev.cowzy.cardgourmet.elrond.config.SearchQueryExecutor
import dev.cowzy.cardgourmet.elrond.config.SearchQueryExecutorBuilder
import dev.cowzy.cardgourmet.elrond.query.SearchQuery
import dev.cowzy.cardgourmet.farbeagle.model.CardImage
import dev.cowzy.kuery.Order
import dev.cowzy.kuery.query.SelectQueryBuilder
import dev.cowzy.kuery.query.orWhereRaw
import dev.cowzy.kuery.reflection.columnName

private val queryBuilder: ((SearchQuery<MtgSearchQueryFlag>, SelectQueryBuilder) -> Unit) = { query, builder ->
    val preferMode = query.flags.firstOfOrNull(MtgSearchQueryFlag.preferModes)

    if (!query.flags.contains(MtgSearchQueryFlag.INCLUDE_EXTRAS)) {
        builder.whereInRaw(MtgPrint::id, "(SELECT id FROM ${Schemata.MAGIC_THE_GATHERING}.primary_print_ids)")
    }

    if (!query.flags.contains(MtgSearchQueryFlag.ANY_LANGUAGE)) {
        builder
            .where { inner ->
                if (query.preferredLanguage != "en") {
                    inner.whereRaw(MtgPrint::languages, "@>", "ARRAY[?]::text[]") { stmt, index ->
                        stmt.setString(index.getAndIncrement(), query.preferredLanguage)
                    }
                }

                inner.orWhereRaw(MtgPrint::languages, "@>", "ARRAY['en']::text[]")
            }
            .whereInRaw(MtgCardFaceTranslation::language, "(?, 'en')") { stmt, index ->
                stmt.setString(index.getAndIncrement(), query.preferredLanguage)
            }
    }

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

fun applyMtgSortPreLanguage(builder: SelectQueryBuilder, preferMode: MtgSearchQueryFlag?) {
    // Always prefer cards with images.
    builder.orderByRaw("CASE WHEN(${CardImage::imageId.columnName()} IS NOT NULL) THEN 1 ELSE 2 END")

    // Next, sort by release date or price (if required).
    when (preferMode) {
        MtgSearchQueryFlag.PREFER_OLDEST -> builder.orderBy(MtgPrint::releaseDate)
        MtgSearchQueryFlag.PREFER_NEWEST -> builder.orderBy(MtgPrint::releaseDate, Order.DESCENDING)
        MtgSearchQueryFlag.PREFER_EUR_LOW -> builder.orderByRaw("COALESCE(${MtgPrintPrice::priceEur.columnName()}, 0)")
        MtgSearchQueryFlag.PREFER_EUR_HIGH -> builder.orderBy("COALESCE(${MtgPrintPrice::priceEur.columnName()}, 0)", Order.DESCENDING)
        MtgSearchQueryFlag.PREFER_USD_LOW -> builder.orderBy("COALESCE(${MtgPrintPrice::priceUsd.columnName()}, 0)")
        MtgSearchQueryFlag.PREFER_USD_HIGH -> builder.orderBy("COALESCE(${MtgPrintPrice::priceUsd.columnName()}, 0)", Order.DESCENDING)
        MtgSearchQueryFlag.PREFER_TIX_LOW -> builder.orderBy("COALESCE(${MtgPrintPrice::priceTix.columnName()}, 0)")
        MtgSearchQueryFlag.PREFER_TIX_HIGH -> builder.orderBy("COALESCE(${MtgPrintPrice::priceTix.columnName()}, 0)", Order.DESCENDING)
        else -> Unit
    }

    // Next, sort by set (if required).
    if (preferMode == MtgSearchQueryFlag.PREFER_PROMO) {
        builder.orderByRaw("CASE WHEN(CARDINALITY(${MtgPrint::promoTypes.columnName()}) > 0) THEN 1 ELSE 2 END")
    } else if (preferMode == MtgSearchQueryFlag.PREFER_ARENA) {
        builder.orderByRaw("CASE WHEN(${MtgPrint::mediums.columnName()} = ARRAY['arena']::text[]) THEN 1 ELSE 2 END")
    }
}

fun applyMtgSortPostLanguage(query: SearchQuery<MtgSearchQueryFlag>, builder: SelectQueryBuilder, preferMode: MtgSearchQueryFlag?) {
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

fun createMtgBaseBuilder(
    config: SearchQueryConfig,
    builder: (SearchQuery<MtgSearchQueryFlag>, SelectQueryBuilder) -> Unit = queryBuilder,
    fallbackFilter: QueryFilter
): SearchQueryExecutorBuilder<MtgSearchQueryFlag> {
    return SearchQueryExecutorBuilder<MtgSearchQueryFlag>(config)
        .fallbackFilter(fallbackFilter)
        .flags(*MtgSearchQueryFlag.values())
        .customTables {
            val preferMode = it.flags.firstOfOrNull(MtgSearchQueryFlag.preferModes)

            setOf(CardImage::class, MtgCardFaceTranslation::class) + when {
                MtgSearchQueryFlag.costPreferModes.contains(preferMode) -> setOf(MtgPrintPrice::class)
                else -> emptySet()
            }
        }
        .customBuilder(builder)
        .transformAttempt {
            val anyLang = it.flags.contains(MtgSearchQueryFlag.ANY_LANGUAGE)
            val extras = it.flags.contains(MtgSearchQueryFlag.INCLUDE_EXTRAS)
            when {
                anyLang && extras -> null
                !extras -> it.copy(flags = it.flags + MtgSearchQueryFlag.INCLUDE_EXTRAS)
                else -> it.copy(flags = it.flags + MtgSearchQueryFlag.ANY_LANGUAGE)
            }
        }
        .transformAttempt {
            val anyLang = it.flags.contains(MtgSearchQueryFlag.ANY_LANGUAGE)
            val extras = it.flags.contains(MtgSearchQueryFlag.INCLUDE_EXTRAS)
            when {
                anyLang && extras -> null
                !anyLang -> it.copy(flags = it.flags + MtgSearchQueryFlag.ANY_LANGUAGE)
                else -> it.copy(flags = it.flags + MtgSearchQueryFlag.ANY_LANGUAGE + MtgSearchQueryFlag.INCLUDE_EXTRAS)
            }
        }
        .transformAttempt {
            it.copy(flags = it.flags + MtgSearchQueryFlag.ANY_LANGUAGE + MtgSearchQueryFlag.INCLUDE_EXTRAS)
        }
}

fun createMtgSearchQueryExecutor(providers: MtgValueProviders): SearchQueryExecutor<MtgSearchQueryFlag> {
    val defaultFilter = createMtgDefaultFilter(providers)

    return createMtgBaseBuilder(mtgBasicSearchQueryConfig, queryBuilder, defaultFilter)
        .filters(createBasicMtgSearchQueryFilters(providers) + defaultFilter)
        .build()
}

fun <T : Enum<T>> Iterable<T>.firstOfOrNull(values: Collection<T>) = this.firstOrNull { values.contains(it) }
