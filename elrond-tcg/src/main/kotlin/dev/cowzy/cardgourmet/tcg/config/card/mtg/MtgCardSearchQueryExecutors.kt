package dev.cowzy.cardgourmet.tcg.config.card.mtg

import dev.cowzy.cardgourmet.chef.commons.model.image.CardImage
import dev.cowzy.cardgourmet.commons.database.Schemata
import dev.cowzy.cardgourmet.chef.commons.model.card.mtg.*
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

private val queryBuilder: ((
    SearchQuery<MtgCardSearchQueryFlag, TcgCardSearchQueryDistinctMode>,
    SearchQueryMode, SelectQueryBuilder, ExecutionContext) -> Unit
) = queryBuilder@{ query, mode, builder, ctx ->
    val preferMode = query.flags.firstOfOrNull(MtgCardSearchQueryFlag.preferModes)

    if (!query.flags.contains(MtgCardSearchQueryFlag.INCLUDE_EXTRAS)) {
        builder.whereInRaw(ctx.resolve(MtgPrint::id), "(SELECT id FROM ${Schemata.MAGIC_THE_GATHERING}.primary_print_ids)")
    }

    if (!query.flags.contains(MtgCardSearchQueryFlag.ANY_LANGUAGE)) {
        builder.whereInRaw(ctx.resolve(MtgCardFaceTranslation::language), "(?, 'en')") { stmt, index ->
            stmt.setString(index.getAndIncrement(), query.preferredLanguage)
        }
    }

    if (query.flags.contains(MtgCardSearchQueryFlag.REQUIRE_IMAGE)) {
        builder.whereNotNull(ctx.resolve(CardImage::imageId))
    }

    // No need to apply sort for count/random queries.
    if (mode != SearchQueryMode.SEARCH) return@queryBuilder

    applyMtgSortPreLanguage(builder, preferMode, ctx)

    val languageSort = "CASE " +
            "WHEN(${ctx.resolve(MtgCardFaceTranslation::language)} = ?) THEN 1 " +
            "WHEN(${ctx.resolve(MtgCardFaceTranslation::language)} = 'en') THEN 2 " +
            "ELSE 3 " +
            "END"

    builder.orderByRaw(languageSort) { stmt, index ->
        stmt.setString(index.getAndIncrement(), query.preferredLanguage)
    }

    applyMtgSortPostLanguage(query, builder, preferMode, ctx)
}

fun applyMtgSortPreLanguage(builder: SelectQueryBuilder, preferMode: MtgCardSearchQueryFlag?, ctx: ExecutionContext) {
    // Always prefer cards with images.
    builder.orderByRaw("CASE WHEN(${ctx.resolve(CardImage::imageId)} IS NOT NULL) THEN 1 ELSE 2 END")

    // Next, sort by release date or price (if required).
    when (preferMode) {
        MtgCardSearchQueryFlag.PREFER_OLDEST -> builder.orderBy(ctx.resolve(MtgPrint::releaseDate))
        MtgCardSearchQueryFlag.PREFER_NEWEST -> builder.orderBy(ctx.resolve(MtgPrint::releaseDate), Order.DESCENDING)
        MtgCardSearchQueryFlag.PREFER_EUR_LOW -> builder.orderByRaw("COALESCE(${ctx.resolve(MtgPrintPrice::priceEur)}, 0)")
        MtgCardSearchQueryFlag.PREFER_EUR_HIGH -> builder.orderBy("COALESCE(${ctx.resolve(MtgPrintPrice::priceEur)}, 0)", Order.DESCENDING)
        MtgCardSearchQueryFlag.PREFER_USD_LOW -> builder.orderBy("COALESCE(${ctx.resolve(MtgPrintPrice::priceUsd)}, 0)")
        MtgCardSearchQueryFlag.PREFER_USD_HIGH -> builder.orderBy("COALESCE(${ctx.resolve(MtgPrintPrice::priceUsd)}, 0)", Order.DESCENDING)
        MtgCardSearchQueryFlag.PREFER_TIX_LOW -> builder.orderBy("COALESCE(${ctx.resolve(MtgPrintPrice::priceTix)}, 0)")
        MtgCardSearchQueryFlag.PREFER_TIX_HIGH -> builder.orderBy("COALESCE(${ctx.resolve(MtgPrintPrice::priceTix)}, 0)", Order.DESCENDING)
        else -> Unit
    }

    // Next, sort by custom properties.
    if (preferMode == MtgCardSearchQueryFlag.PREFER_PROMO) {
        builder.orderByRaw("CASE WHEN(CARDINALITY(${ctx.resolve(MtgPrint::promoTypes)}) > 0) THEN 1 ELSE 2 END")
    } else if (preferMode == MtgCardSearchQueryFlag.PREFER_ARENA) {
        builder.orderByRaw("CASE WHEN(${ctx.resolve(MtgPrint::mediums)} = ARRAY['arena']::text[]) THEN 1 ELSE 2 END")
    } else if (preferMode == MtgCardSearchQueryFlag.PREFER_SPECIAL) {
        builder.orderByRaw("CASE " +
                "WHEN(CARDINALITY(${ctx.resolve(MtgPrint::promoTypes)}) > 0) THEN 1" +
                "WHEN(${ctx.resolve(MtgPrint::setCode)} = 'SLD') THEN 2 " +
                "WHEN(${ctx.resolve(MtgPrintFaceTranslation::flavorName)} IS NOT NULL) THEN 3 " +
                "ELSE 4 END")
    } else if (preferMode == MtgCardSearchQueryFlag.PREFER_BASIC) {
        builder.orderByRaw("CASE " +
                "WHEN(CARDINALITY(${ctx.resolve(MtgPrint::promoTypes)}) > 0) THEN 4" +
                "WHEN(${ctx.resolve(MtgPrint::setCode)} = 'SLD') THEN 3 " +
                "WHEN(${ctx.resolve(MtgPrintFaceTranslation::flavorName)} IS NOT NULL) THEN 2 " +
                "ELSE 1 END")
    }
}

fun applyMtgSortPostLanguage(query: SearchQuery<MtgCardSearchQueryFlag, TcgCardSearchQueryDistinctMode>, builder: SelectQueryBuilder, preferMode: MtgCardSearchQueryFlag?, ctx: ExecutionContext) {
    builder.orderByRaw("array_position(ARRAY[?, 'en'], ${ctx.resolve(MtgPrintFaceTranslation::language)})") { stmt, index ->
        stmt.setString(index.getAndIncrement(), query.preferredLanguage)
    }

    // Apply default sort.
    if (preferMode == null) {
        builder.orderByRaw("CASE WHEN(${ctx.resolve(MtgPrint::mediums)} = ARRAY['arena']::text[]) THEN 3 WHEN(CARDINALITY(${ctx.resolve(MtgPrint::promoTypes)}) > 0) THEN 2 ELSE 1 END")
        builder.orderBy(ctx.resolve(MtgPrint::releaseDate), Order.DESCENDING)
    }

    // Lastly, sort by collector number.
    builder.orderBy(ctx.resolve(MtgPrint::collectorNumberValue)) // rough sorting
    builder.orderBy(ctx.resolve(MtgPrint::collectorNumber)) // exact sorting for subset

    // Make sure to always return the same face.
    builder.orderBy(ctx.resolve(MtgCardFace::index))
}

fun <PrincipalType : Any> createMtgCardBaseBuilder(
    config: SearchQuerySqlConfig,
    builder: (SearchQuery<MtgCardSearchQueryFlag, TcgCardSearchQueryDistinctMode>, SearchQueryMode, SelectQueryBuilder, ExecutionContext) -> Unit = queryBuilder,
    fallbackFilter: QueryFilter
): SearchQueryExecutorBuilder<MtgCardSearchQueryFlag, TcgCardSearchQueryDistinctMode, PrincipalType> {
    return SearchQueryExecutorBuilder<MtgCardSearchQueryFlag, TcgCardSearchQueryDistinctMode, PrincipalType>(config)
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

                else -> when {
                    query.flags.contains(MtgCardSearchQueryFlag.REQUIRE_IMAGE) -> setOf(MtgCardFaceTranslation::class, CardImage::class)
                    else -> setOf(MtgCardFaceTranslation::class)
                }
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

fun <PrincipalType : Any> createMtgCardSearchQueryExecutor(
    providers: ValueProviderPool,
    transform: SearchQueryExecutorTransform? = null
): SearchQueryExecutor<MtgCardSearchQueryFlag, TcgCardSearchQueryDistinctMode, PrincipalType> {
    val builder = SearchQueryFilterBuilder(providers) {
        configureBasicMtgCardFilters()
        transform?.applyFilters?.invoke(this)
    }

    val filters = builder.build()
    val defaultFilter = filters.single { it.keywords.contains("name") }

    return createMtgCardBaseBuilder<PrincipalType>(
        transform?.transformConfig?.invoke(mtgBasicSearchQueryConfig) ?: mtgBasicSearchQueryConfig,
        queryBuilder,
        defaultFilter
    )
        .filters(filters)
        .build()
}

fun <T : Enum<T>> Iterable<T>.firstOfOrNull(values: Collection<T>) = this.firstOrNull { values.contains(it) }
