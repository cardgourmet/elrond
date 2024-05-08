package dev.cowzy.cardgourmet.elrond.values

import dev.cowzy.cardgourmet.commons.database.SqlDatabasePool
import dev.cowzy.kuery.reflection.columnName
import kotlin.reflect.KProperty1

class ValueProviderPool(private val dbPool: SqlDatabasePool) {

    private val providers = mutableMapOf<String, ValueProvider<out Any>>()

    fun <Value : Any, Provider : ValueProvider<Value>> getOrPut(
        column: KProperty1<*, *>,
        compute: (SqlDatabasePool) -> Provider
    ): Provider {
        return getOrPut(column.columnName(), compute)
    }

    @Suppress("UNCHECKED_CAST")
    fun <Value : Any, Provider : ValueProvider<Value>> getOrPut(key: String, compute: (SqlDatabasePool) -> Provider): Provider {
        return providers.getOrPut(key) { compute(dbPool) } as Provider
    }

    fun getAutoStringProvider(vararg columns: KProperty1<*, String?>, ttl: Long = 3600): AutoStringValueProvider {
        return getOrPut(columns.joinToString("-") { it.columnName() }) { AutoStringValueProvider(dbPool, columns = columns, ttl) }
    }

    fun getAutoStringArrayProvider(vararg columns: KProperty1<*, List<String>?>, ttl: Long = 3600): AutoStringArrayValueProperty {
        return getOrPut(columns.joinToString("-") { it.columnName() }) { AutoStringArrayValueProperty(dbPool, columns = columns, ttl) }
    }

}