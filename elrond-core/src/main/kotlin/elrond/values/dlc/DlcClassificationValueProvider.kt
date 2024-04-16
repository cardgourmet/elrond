package elrond.values.dlc

import dev.cowzy.cardgourmet.commons.database.SqlDatabasePool
import dev.cowzy.cardgourmet.commons.database.card.dlc.DlcCard
import dev.cowzy.cardgourmet.elrond.values.CachedValueProvider
import dev.cowzy.kuery.query.selectBuilder
import dev.cowzy.kuery.reflection.columnName

class DlcClassificationValueProvider(private val pool: SqlDatabasePool) : CachedValueProvider<String>(24 * 60 * 60) {
    override suspend fun fetchValues(): Iterable<String> {
        return pool.use {
            DlcCard::class.selectBuilder()
                .distinct()
                .selectRaw("unnest(${DlcCard::classifications.columnName()})")
                .get(it) { row, index -> row.getString(index.getAndIncrement()) }
        }
    }
}