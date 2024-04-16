package dev.cowzy.cardgourmet.elrond.values.mtg

import dev.cowzy.cardgourmet.commons.database.SqlDatabasePool
import dev.cowzy.cardgourmet.commons.database.card.mtg.MtgPrint
import dev.cowzy.cardgourmet.elrond.values.CachedValueProvider
import dev.cowzy.kuery.query.SelectQueryBuilder
import dev.cowzy.kuery.query.selectBuilder
import dev.cowzy.kuery.reflection.columnName
import kotlin.reflect.KProperty1

class MtgFormatValueProvider(private val pool: SqlDatabasePool) : CachedValueProvider<String>(24 * 60 * 60) {
    override suspend fun fetchValues(): Iterable<String> {
        return pool.use {
            selectFormats(MtgPrint::formatsLegal)
                .union(selectFormats(MtgPrint::formatsBanned))
                .union(selectFormats(MtgPrint::formatsRestricted))
                .get(it) { row, index -> row.getString(index.getAndIncrement()) }
        }
    }

    private fun selectFormats(column: KProperty1<MtgPrint, *>): SelectQueryBuilder {
        return MtgPrint::class.selectBuilder()
            .distinct()
            .selectRaw("unnest(${column.columnName()})")
    }
}