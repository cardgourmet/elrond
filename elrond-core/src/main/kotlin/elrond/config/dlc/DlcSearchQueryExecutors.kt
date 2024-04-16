package elrond.config.dlc

import dev.cowzy.cardgourmet.commons.database.card.dlc.DlcCardTranslation
import dev.cowzy.cardgourmet.commons.database.card.dlc.DlcPrint
import dev.cowzy.cardgourmet.commons.database.card.dlc.DlcPrintTranslation
import dev.cowzy.cardgourmet.commons.database.set.dlc.DlcSet
import dev.cowzy.cardgourmet.commons.user.UserCard
import dev.cowzy.cardgourmet.elrond.QueryFilter
import dev.cowzy.cardgourmet.elrond.config.SearchQueryConfig
import dev.cowzy.cardgourmet.elrond.config.SearchQueryExecutor
import dev.cowzy.cardgourmet.elrond.config.SearchQueryExecutorBuilder
import dev.cowzy.cardgourmet.farbeagle.model.CardImage
import dev.cowzy.kuery.Order
import dev.cowzy.kuery.query.whereNotNull
import dev.cowzy.kuery.reflection.columnName
import dev.cowzy.kuery.reflection.table

private fun createDlcBaseBuilder(config: SearchQueryConfig, fallbackFilter: QueryFilter): SearchQueryExecutorBuilder<DlcSearchQueryFlag> {
    return SearchQueryExecutorBuilder<DlcSearchQueryFlag>(config)
        .fallbackFilter(fallbackFilter)
        .flags(*DlcSearchQueryFlag.values())
        .customTables {
            setOf(CardImage::class, DlcSet::class, DlcCardTranslation::class)
        }
        .customBuilder { query, builder ->
            if (!query.flags.contains(DlcSearchQueryFlag.ANY_LANGUAGE)) {
                if (query.distinctBy.table() == UserCard::class) {
                    builder.whereColumn(UserCard::language, DlcCardTranslation::language)
                } else {
                    builder.whereInRaw(DlcCardTranslation::language, "(?, 'en')") { stmt, index ->
                        stmt.setString(index.getAndIncrement(), query.preferredLanguage)
                    }
                }
            }

            if (query.distinctBy.table() == UserCard::class) {
                builder.whereNotNull(UserCard::id)
            }

            // Always prefer cards with images.
            builder.orderByRaw("CASE WHEN(${CardImage::imageId.columnName()} IS NOT NULL) THEN 1 ELSE 2 END")

            val languageSort = when (query.distinctBy.table()) {
                UserCard::class -> {
                    "CASE " +
                            "WHEN(${DlcCardTranslation::language.columnName()} = ${UserCard::language.columnName()}) THEN 1 " +
                            "WHEN(${DlcCardTranslation::language.columnName()} = ?) THEN 2 " +
                            "WHEN(${DlcCardTranslation::language.columnName()} = 'en') THEN 3 " +
                            "ELSE 4 " +
                            "END"
                }

                else -> {
                    "CASE " +
                            "WHEN(${DlcCardTranslation::language.columnName()} = ?) THEN 1 " +
                            "WHEN(${DlcCardTranslation::language.columnName()} = 'en') THEN 2 " +
                            "ELSE 3 " +
                            "END"
                }
            }

            builder.orderByRaw(languageSort) { stmt, index ->
                stmt.setString(index.getAndIncrement(), query.preferredLanguage)
            }

            builder.orderByRaw("array_position(ARRAY[?, 'en'], ${DlcPrintTranslation::language.columnName()})") { stmt, index ->
                stmt.setString(index.getAndIncrement(), query.preferredLanguage)
            }

            // Apply default sort.
            builder.orderBy(DlcSet::releaseDate, Order.DESCENDING)

            // Lastly, sort by collector number.
            builder.orderBy(DlcPrint::collectorNumberValue) // rough sorting
            builder.orderBy(DlcPrint::collectorNumber) // exact sorting for subset
        }
        .transformAttempt {
            val anyLang = it.flags.contains(DlcSearchQueryFlag.ANY_LANGUAGE)
            when {
                anyLang -> null
                else -> it.copy(flags = it.flags + DlcSearchQueryFlag.ANY_LANGUAGE)
            }
        }
}

fun createDlcSearchQueryExecutor(providers: DlcValueProviders): SearchQueryExecutor<DlcSearchQueryFlag> {
    return createDlcBaseBuilder(dlcSearchQueryConfig, dlcNameFilter)
        .filters(createDlcSearchQueryFilters(providers))
        .build()
}

fun createDlcCollectionSearchQueryExecutor(providers: DlcValueProviders): SearchQueryExecutor<DlcSearchQueryFlag> {
    return createDlcBaseBuilder(dlcSearchQueryConfig, dlcCollectionNameFilter)
        .filters(createDlcCollectionSearchQueryFilters())
        .filters(createDlcSearchQueryFilters(providers))
        .build()
}
