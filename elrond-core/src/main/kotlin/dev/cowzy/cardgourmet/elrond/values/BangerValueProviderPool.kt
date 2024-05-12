package dev.cowzy.cardgourmet.elrond.values

import dev.cowzy.kuery.query.SelectQueryBuilder
import dev.cowzy.kuery.query.selectBuilder
import dev.cowzy.kuery.reflection.columnName
import dev.cowzy.kuery.reflection.table
import java.sql.Connection
import kotlin.reflect.KProperty1

data class ProviderPoolEntry<T>(
    val provider: (Connection) -> Map<String, T>,
    val type: String
)

class BangerValueProviderPool {

    private val providers = mutableMapOf<String, ProviderPoolEntry<*>>()

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getOrPut(key: String, type: String, mappingProvider: (Connection) -> Map<String, T>): ProviderPoolEntry<T> {
        val existingEntry = this.providers[key] as ProviderPoolEntry<T>?
        if (existingEntry != null) return existingEntry

        val entry = ProviderPoolEntry(mappingProvider, type)
        this.providers[key] = entry

        return entry
    }

    fun <T : Any> getOrPut(
        key: String,
        type: String,
        valueProvider: (Connection) -> Iterable<T>,
        displayTransform: (T) -> String = { it.toString() }
    ) = this.getOrPut(key, type) { connection -> valueProvider(connection).associateBy(displayTransform) }

    fun getAutoStringProvider(vararg columns: KProperty1<*, *>, type: String): ProviderPoolEntry<String> {
        val key = columns.joinToString("-") { it.columnName() }
        return this.getOrPut(key, type, valueProvider = { connection ->
            selectStrings(columns, connection) {
                it.table().selectBuilder().select(it)
            }
        })
    }

    fun getAutoStringArrayProvider(vararg columns: KProperty1<*, List<*>?>, type: String): ProviderPoolEntry<String> {
        val key = columns.joinToString("-") { it.columnName() }
        return this.getOrPut(key, type, valueProvider = { connection ->
            selectStrings(columns, connection) {
                it.table().selectBuilder().selectRaw("unnest(${it.columnName()})")
            }
        })
    }

    private fun selectStrings(columns: Array<out KProperty1<*, *>>, connection: Connection, select: (KProperty1<*, *>) -> SelectQueryBuilder): Iterable<String> {
        return select(columns.first())
            .apply { columns.drop(1).forEach { union(select(it)) } }
            .get(connection) { row, index -> row.getString(index.getAndIncrement()) }
            .filterNotNull()
            .distinct()
    }

}
