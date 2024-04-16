package elrond.values.mtg

import dev.cowzy.cardgourmet.commons.database.SqlDatabasePool
import dev.cowzy.cardgourmet.commons.database.card.mtg.MtgCard
import dev.cowzy.cardgourmet.elrond.values.CachedValueProvider
import dev.cowzy.kuery.query.selectBuilder

class MtgLayoutValueProvider(private val pool: SqlDatabasePool) : CachedValueProvider<String>(24 * 60 * 60) {
    override suspend fun fetchValues(): Iterable<String> {
        return pool.use {
            MtgCard::class.selectBuilder()
                .distinct()
                .select(MtgCard::layout)
                .get(it) { row, index -> row.getString(index.getAndIncrement()) }
        }
    }
}