package dev.cowzy.cardgourmet.elrond.values.mtg

import dev.cowzy.cardgourmet.commons.database.SqlDatabasePool
import dev.cowzy.cardgourmet.commons.database.card.mtg.MtgCard
import dev.cowzy.cardgourmet.elrond.values.CachedValueProvider
import dev.cowzy.kuery.query.selectBuilder
import dev.cowzy.kuery.reflection.columnName

class MtgReprintInValueProvider(private val pool: SqlDatabasePool) : CachedValueProvider<String>(24 * 60 * 60) {
    override suspend fun fetchValues(): Iterable<String> {
        return pool.use {
            MtgCard::class.selectBuilder()
                .distinct()
                .selectRaw("unnest(${MtgCard::reprintIn.columnName()})")
                .get(it) { row, index -> row.getString(index.getAndIncrement()) }
        }
    }
}