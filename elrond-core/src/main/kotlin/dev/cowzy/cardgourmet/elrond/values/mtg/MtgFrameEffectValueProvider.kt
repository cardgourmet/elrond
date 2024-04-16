package dev.cowzy.cardgourmet.elrond.values.mtg

import dev.cowzy.cardgourmet.commons.database.SqlDatabasePool
import dev.cowzy.cardgourmet.commons.database.card.mtg.MtgPrint
import dev.cowzy.cardgourmet.elrond.values.CachedValueProvider
import dev.cowzy.kuery.query.selectBuilder
import dev.cowzy.kuery.reflection.columnName

class MtgFrameEffectValueProvider(private val pool: SqlDatabasePool) : CachedValueProvider<String>(24 * 60 * 60) {
    override suspend fun fetchValues(): Iterable<String> {
        return pool.use {
            MtgPrint::class.selectBuilder()
                .distinct()
                .selectRaw("unnest(${MtgPrint::frameEffects.columnName()})")
                .get(it) { row, index -> row.getString(index.getAndIncrement()) }
        }
    }
}