package elrond.values.dlc

import dev.cowzy.cardgourmet.commons.database.SqlDatabasePool
import dev.cowzy.cardgourmet.commons.database.set.dlc.DlcSet
import dev.cowzy.cardgourmet.elrond.values.CachedValueProvider
import dev.cowzy.kuery.query.selectBuilder

class DlcSetCodeValueProvider(private val pool: SqlDatabasePool) : CachedValueProvider<String>(24 * 60 * 60) {
    override suspend fun fetchValues(): Iterable<String> {
        return pool.use {
            DlcSet::class.selectBuilder()
                .distinct()
                .select(DlcSet::code)
                .get(it) { row, index -> row.getString(index.getAndIncrement()) }
        }
    }
}