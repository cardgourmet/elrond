package dev.cowzy.cardgourmet.elrond.values.mtg

import dev.cowzy.cardgourmet.commons.database.SqlDatabasePool
import dev.cowzy.cardgourmet.commons.database.card.mtg.MtgCardFace
import dev.cowzy.cardgourmet.elrond.values.CachedValueProvider
import dev.cowzy.kuery.query.selectBuilder
import dev.cowzy.kuery.reflection.columnName
import kotlin.reflect.KProperty1

class MtgTypeValueProvider(private val column: KProperty1<MtgCardFace, *>, private val pool: SqlDatabasePool) : CachedValueProvider<String>() {
    override suspend fun fetchValues(): Iterable<String> {
        return pool.use {
            MtgCardFace::class.selectBuilder()
                .distinct()
                .selectRaw("unnest(${column.columnName()})")
                .get(it) { row, index -> row.getString(index.getAndIncrement()) }
        }
    }
}