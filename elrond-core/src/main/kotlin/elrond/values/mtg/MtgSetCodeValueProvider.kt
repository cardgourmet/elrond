package elrond.values.mtg

import dev.cowzy.cardgourmet.commons.database.SqlDatabasePool
import dev.cowzy.cardgourmet.commons.database.set.mtg.MtgSet
import dev.cowzy.cardgourmet.elrond.values.CachedValueProvider
import dev.cowzy.kuery.query.selectBuilder

class MtgSetCodeValueProvider(private val pool: SqlDatabasePool) : CachedValueProvider<String>(24 * 60 * 60) {
    override suspend fun fetchValues(): Iterable<String> {
        return pool.use {
            MtgSet::class.selectBuilder()
                .distinct()
                .select(MtgSet::code)
                .get(it) { row, index -> row.getString(index.getAndIncrement()) }
        }
    }
}