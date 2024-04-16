package elrond.values.dlc

import dev.cowzy.cardgourmet.commons.database.SqlDatabasePool
import dev.cowzy.cardgourmet.commons.database.card.dlc.DlcPrint
import dev.cowzy.cardgourmet.elrond.values.CachedValueProvider
import dev.cowzy.kuery.query.selectBuilder

class DlcSeparatorValueProvider(private val pool: SqlDatabasePool) : CachedValueProvider<String>(24 * 60 * 60) {
    override suspend fun fetchValues(): Iterable<String> {
        return pool.use {
            DlcPrint::class.selectBuilder()
                .distinct()
                .select(DlcPrint::separator)
                .get(it) { row, index -> row.getString(index.getAndIncrement()) }
        }
    }
}