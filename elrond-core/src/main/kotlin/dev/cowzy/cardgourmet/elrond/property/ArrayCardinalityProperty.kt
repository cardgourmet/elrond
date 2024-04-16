package dev.cowzy.cardgourmet.elrond.property

import dev.cowzy.kuery.reflection.columnName
import dev.cowzy.kuery.reflection.table
import dev.cowzy.cardgourmet.elrond.*
import kotlin.reflect.KProperty1

class ArrayCardinalityProperty(
    private val column: KProperty1<*, *>,
    private val mappings: Map<String, Pair<Number, SearchQueryOperator>> = emptyMap(),
    propertyKey: String
) : NumericSearchQueryProperty(
    affectedTables = arrayOf(column.table()),
    descriptorSubjectKey = propertyKey
) {

    override val valueDefinition = QueryValueDefinition {
        NumberValue::class {
            transform { it.value }
        }

        StringValue::class {
            transformWithOperator { it, operator ->
                val mapping = mappings[it.value] ?: return@transformWithOperator null
                val mappedOperator = if (operator == SearchQueryOperator.CONTAINS) mapping.second else operator
                mapping.first to mappedOperator
            }
        }
    }

    override fun getRawSql() = "cardinality(${column.columnName()})"

}