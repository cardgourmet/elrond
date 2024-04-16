package elrond.values.dlc

import dev.cowzy.cardgourmet.commons.database.SqlDatabasePool
import dev.cowzy.cardgourmet.commons.database.card.dlc.DlcCard
import dev.cowzy.cardgourmet.elrond.values.CachedValueProvider
import dev.cowzy.kuery.query.selectBuilder

class DlcTypeValueProvider(private val pool: SqlDatabasePool) : CachedValueProvider<String>(24 * 60 * 60) {
    override suspend fun fetchValues(): Iterable<String> {
        return pool.use {
            DlcCard::class.selectBuilder()
                .distinct()
                .select(DlcCard::type)
                .get(it) { row, index -> row.getString(index.getAndIncrement()) }
        }
    }
}