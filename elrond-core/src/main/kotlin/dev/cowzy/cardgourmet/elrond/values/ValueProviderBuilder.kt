package dev.cowzy.cardgourmet.elrond.values

import dev.cowzy.cardgourmet.commons.database.SqlDatabasePool
import dev.cowzy.cardgourmet.commons.getSerialName
import dev.cowzy.cardgourmet.elrond.QueryValue
import dev.cowzy.cardgourmet.elrond.SearchQueryOperator
import dev.cowzy.cardgourmet.elrond.enumToMappings
import dev.cowzy.kuery.query.SelectQueryBuilder
import dev.cowzy.kuery.query.selectBuilder
import dev.cowzy.kuery.reflection.columnName
import dev.cowzy.kuery.reflection.simpleColumnName
import dev.cowzy.kuery.reflection.table
import java.sql.Connection
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.typeOf

data class ProviderEntry<T>(
    val provider: (Connection) -> Map<String, T>,
    val type: String
)

class ValueProviderBuilder<T : Any>(private val dbPool: SqlDatabasePool) {

    private var cacheTimeToLife = 3600L

    private var applyValues = mutableListOf<(Connection, ValueGroup<T>, (T) -> String) -> Unit>()

    private var displayTransform: (T) -> String = {
        when (it) {
            is QueryValue<*> -> it.value.toString()
            else -> it.toString()
        }
    }

    private var strictValues = false

    fun cache(ttl: Long = 3600) {
        this.cacheTimeToLife = ttl
    }

    fun strict(strict: Boolean) {
        this.strictValues = strict
    }

    fun transform(transform: (T) -> String) {
        this.displayTransform = transform
    }

    fun values(vararg values: T, type: String, autoAlias: Boolean = false) {
        values(values.toSet(), type, autoAlias)
    }

    fun values(values: Iterable<T>, type: String, autoAlias: Boolean = false) {
        applyValues.add { _, valueGroup, displayTransform ->
            values.toSet().forEach {
                val input = displayTransform(it)
                valueGroup.addOrUpdate(input, it, input, null, type, autoAlias)
            }
        }
    }

    fun values(mappings: Map<String, T>, type: String, autoAlias: Boolean = false) {
        applyValues.add { _, values, displayTransform ->
            mappings.forEach { (input, value) ->
                val display = displayTransform(value)
                values.addOrUpdate(input, value, display, null, type, autoAlias)
            }
        }
    }

    fun valuesWithOperator(mappings: Map<String, Pair<T, SearchQueryOperator?>>, type: String, autoAlias: Boolean = false) {
        applyValues.add { _, values, displayTransform ->
            mappings.forEach { (input, value) ->
                val display = displayTransform(value.first)
                values.addOrUpdate(input, value.first, display, value.second, type, autoAlias)
            }
        }
    }

    fun values(provider: (Connection) -> Map<String, T>, type: String, autoAlias: Boolean = false) {
        applyValues.add { connection, values, displayTransform ->
            val mappings = provider(connection)
            mappings.forEach { (input, value) ->
                val display = displayTransform(value)
                values.addOrUpdate(input, value, display, null, type, autoAlias)
            }
        }
    }

    fun <V> values(provider: (Connection) -> Map<String, V>, transform: (V) -> T, type: String, autoAlias: Boolean = false) {
        applyValues.add { connection, values, displayTransform ->
            val mappings = provider(connection)
            mappings.forEach { (input, value) ->
                val transformedValue = transform(value)
                val display = displayTransform(transformedValue)
                values.addOrUpdate(input, transformedValue, display, null, type, autoAlias)
            }
        }
    }

    fun values(entry: ProviderEntry<T>, autoAlias: Boolean = false) {
        applyValues.add { connection, values, displayTransform ->
            val mappings = entry.provider(connection)
            mappings.forEach { (input, value) ->
                val display = displayTransform(value)
                values.addOrUpdate(input, value, display, null, entry.type, autoAlias)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <E : Enum<E>> enumValues(
        type: String,
        values: Array<E>,
        findKeywords: (E) -> List<String> = { emptyList() },
        transform: (E) -> T = { it as T }
    ) {
        val enumMappings = enumToMappings(values, findKeywords).mapValues { transform(it.value) }
        values(values.associate { it.getSerialName() to transform(it) }, type, true)
        values(enumMappings, type, true)
    }

    @Suppress("UNCHECKED_CAST")
    inline fun <reified E : Enum<E>> enumValues(
        type: String,
        noinline findKeywords: (E) -> List<String> = { emptyList() },
        noinline transform: (E) -> T = { it as T }
    ) = enumValues(type, kotlin.enumValues<E>(), findKeywords, transform)

    @Suppress("UNCHECKED_CAST")
    inline fun <reified E : Enum<E>> enumValues(
        noinline findKeywords: (E) -> List<String> = { emptyList() },
        noinline transform: (E) -> T = { it as T }
    ) {
        val type = typeOf<E>().classifier as KClass<E>
        enumValues(type.simpleName!!, findKeywords, transform)
    }

    fun build(): ValueProvider<T> {
        return ValueProvider(
            dbPool,
            applyValues,
            displayTransform,
            strictValues && applyValues.any(),
            this.cacheTimeToLife
        )
    }

}

fun ValueProviderBuilder<String>.autoValues(
    vararg columns: KProperty1<*, *>,
    type: String,
    autoAlias: Boolean = false
) {
    values(getAutoStringProvider(columns = columns, type), autoAlias)
}

fun ValueProviderBuilder<String>.autoValues(
    column: KProperty1<*, *>,
    type: String = column.simpleColumnName(),
    autoAlias: Boolean = false
) {
    values(getAutoStringProvider(column, type = type), autoAlias)
}

fun ValueProviderBuilder<String>.autoArrayValues(
    vararg columns: KProperty1<*, List<*>?>,
    type: String,
    autoAlias: Boolean = false
) {
    values(getAutoStringArrayProvider(columns = columns, type), autoAlias)
}

fun ValueProviderBuilder<String>.autoArrayValues(
    column: KProperty1<*, List<*>?>,
    type: String = column.simpleColumnName(),
    autoAlias: Boolean = false
) {
    values(getAutoStringArrayProvider(column, type = type), autoAlias)
}

fun getAutoStringProvider(vararg columns: KProperty1<*, *>, type: String): ProviderEntry<String> {
    return ProviderEntry(provider = { connection ->
        selectStrings(columns, connection) {
            it.table().selectBuilder().select(it)
        }.associateBy { it }
    }, type)
}

fun getAutoStringArrayProvider(vararg columns: KProperty1<*, List<*>?>, type: String): ProviderEntry<String> {
    return ProviderEntry(provider = { connection ->
        selectStrings(columns, connection) {
            it.table().selectBuilder().selectRaw("unnest(${it.columnName()})")
        }.associateBy { it }
    }, type)
}

private fun selectStrings(columns: Array<out KProperty1<*, *>>, connection: Connection, select: (KProperty1<*, *>) -> SelectQueryBuilder): Iterable<String> {
    return select(columns.first())
        .apply { columns.drop(1).forEach { union(select(it)) } }
        .get(connection) { row, index -> row.getString(index.getAndIncrement()) }
        .filterNotNull()
        .distinct()
}
