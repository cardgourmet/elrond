package dev.cowzy.cardgourmet.elrond.values

import dev.cowzy.cardgourmet.commons.database.SqlDatabasePool
import dev.cowzy.kuery.query.SelectQueryBuilder
import dev.cowzy.kuery.query.selectBuilder
import dev.cowzy.kuery.reflection.columnName
import dev.cowzy.kuery.reflection.table
import kotlin.reflect.KProperty1

class AutoStringValueProvider(
    private val dbPool: SqlDatabasePool,
    private vararg val columns: KProperty1<*, *>,
    ttl: Long = 3600
) : CachedValueProvider<String>(ttl) {

    override suspend fun fetchValues(): Iterable<String> {
        return dbPool.use { connection ->
            select(columns.first())
                .apply { columns.drop(1).forEach { union(select(it)) } }
                .get(connection) { row, index -> row.getString(index.getAndIncrement()) }
                .filterNotNull()
                .distinct()
        }
    }

    private fun select(column: KProperty1<*, *>): SelectQueryBuilder {
        return column.table().selectBuilder().select(column)
    }

}

class AutoStringArrayValueProvider(
    private val dbPool: SqlDatabasePool,
    private vararg val columns: KProperty1<*, *>,
    ttl: Long = 3600
) : CachedValueProvider<String>(ttl) {

    override suspend fun fetchValues(): Iterable<String> {
        return dbPool.use { connection ->
            select(columns.first())
                .apply { columns.drop(1).forEach { union(select(it)) } }
                .get(connection) { row, index -> row.getString(index.getAndIncrement()) }
                .filterNotNull()
                .distinct()
        }
    }

    private fun select(column: KProperty1<*, *>): SelectQueryBuilder {
        return column.table().selectBuilder().selectRaw("unnest(${column.columnName()})")
    }

}
