package dev.cowzy.cardgourmet.elrond.values.mtg

import dev.cowzy.cardgourmet.commons.database.SqlDatabasePool
import dev.cowzy.cardgourmet.commons.database.set.mtg.MtgSet
import dev.cowzy.cardgourmet.elrond.values.CachedValueProvider
import dev.cowzy.kuery.column.transformer.LocalDateColumnTransformer
import dev.cowzy.kuery.query.selectBuilder
import java.time.LocalDate

class MtgSetReleaseDateMappingProvider(
    private val pool: SqlDatabasePool,
    private val mappings: Map<String, String>
) : CachedValueProvider<Pair<String, LocalDate>>(24 * 60 * 60) {
    override suspend fun fetchValues(): Iterable<Pair<String, LocalDate>> {
        val values = pool.use {
            MtgSet::class.selectBuilder()
                .distinctOn(MtgSet::code)
                .select(MtgSet::code)
                .select(MtgSet::releaseDate)
                .get(it) { row, index ->
                    row.getString(index.getAndIncrement()) to LocalDateColumnTransformer.fromSql(row, index)!!
                }
        }.toMap()

        return mappings.mapNotNull { (key, value) ->
            values[value]?.let { key to it }
        } + values.entries.map { it.toPair() }
    }
}