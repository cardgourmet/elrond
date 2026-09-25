package dev.cowzy.cardgourmet.elrond.config

import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

data class MaterializedView(
    val table: String,
    val coveredTables: Set<KClass<*>>,
    val columnMappings: Map<KProperty1<*, *>, String>
)