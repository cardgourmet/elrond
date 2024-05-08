package dev.cowzy.cardgourmet.elrond.values.pcg

import dev.cowzy.cardgourmet.commons.database.SqlDatabasePool
import dev.cowzy.cardgourmet.commons.database.set.pcg.PcgSet
import dev.cowzy.cardgourmet.elrond.values.CachedValueProvider
import dev.cowzy.kuery.column.transformer.LocalDateColumnTransformer
import dev.cowzy.kuery.query.selectBuilder
import java.time.LocalDate

class PcgSetEndReleaseDateMappingProvider(private val pool: SqlDatabasePool) : CachedValueProvider<Pair<String, LocalDate>>(24 * 60 * 60) {
    override suspend fun fetchValues(): Iterable<Pair<String, LocalDate>> {
        return pool.use {
            PcgSet::class.selectBuilder()
                .distinctOn(PcgSet::setCode)
                .select(PcgSet::setCode)
                .select(PcgSet::releaseEndDate)
                .get(it) { row, index ->
                    row.getString(index.getAndIncrement()) to LocalDateColumnTransformer.fromSql(row, index)
                }
                .mapNotNull { entry -> entry.second?.let { entry.first to it } }
        }
    }
}