package dev.cowzy.cardgourmet.elrond.values.dlc

import dev.cowzy.cardgourmet.commons.database.SqlDatabasePool
import dev.cowzy.cardgourmet.commons.database.set.dlc.DlcSet
import dev.cowzy.cardgourmet.elrond.values.CachedValueProvider
import dev.cowzy.kuery.column.transformer.LocalDateColumnTransformer
import dev.cowzy.kuery.query.selectBuilder
import java.time.LocalDate

class DlcSetReleaseDateValueProvider(private val pool: SqlDatabasePool) : CachedValueProvider<Pair<String, LocalDate>>(24 * 60 * 60) {
    override suspend fun fetchValues(): Iterable<Pair<String, LocalDate>> {
        return pool.use {
            DlcSet::class.selectBuilder()
                .distinctOn(DlcSet::code)
                .select(DlcSet::code)
                .select(DlcSet::releaseDate)
                .get(it) { row, index ->
                    row.getString(index.getAndIncrement()) to LocalDateColumnTransformer.fromSql(row, index)!!
                }
        }
    }
}