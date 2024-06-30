package dev.cowzy.cardgourmet.elrond.config

import dev.cowzy.kuery.query.SelectQueryBuilder
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

data class SearchQuerySqlConfig(
    val baseTable: KClass<*>,
    val tableDependencies: Map<KClass<*>, TableDependency>,
    val customFields: Map<String, CustomField<out Any>>
)

data class TableDependency(
    val tables: List<KClass<*>>,
    val join: (SelectQueryBuilder) -> Unit
) {
    constructor(vararg tables: KClass<*>, join: (SelectQueryBuilder) -> Unit) : this(tables = tables.toList(), join = join)
}

data class CustomField<T : Any>(val properties: List<KProperty1<*, T>>) {
    constructor(vararg properties: KProperty1<*, T>) : this(properties.toList())
}
