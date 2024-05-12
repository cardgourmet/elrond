package dev.cowzy.cardgourmet.elrond.values

import dev.cowzy.cardgourmet.commons.database.SqlDatabasePool
import dev.cowzy.kuery.reflection.columnName
import kotlin.reflect.KProperty1

class PropertyProviderPool(private val dbPool: SqlDatabasePool) {

    private val providers = mutableMapOf<String, PropertyProvider<*>>()

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getOrPut(key: String, compute: (SqlDatabasePool) -> PropertyProvider<T>): PropertyProvider<T> {
        val existingProvider = this.providers[key] as PropertyProvider<T>?
        if (existingProvider != null) return existingProvider

        val provider = compute(dbPool)
        this.providers[key] = provider
        return provider
    }

    fun <T : Any> getOrPut(column: KProperty1<*, *>, compute: (SqlDatabasePool) -> PropertyProvider<T>): PropertyProvider<T> {
        val key = column.columnName()
        return getOrPut(key, compute)
    }

    fun <T : Any> getOrPut(vararg columns: KProperty1<*, *>, compute: (SqlDatabasePool) -> PropertyProvider<T>): PropertyProvider<T> {
        val key = columns.joinToString("-") { it.columnName() }
        return getOrPut(key, compute)
    }

}