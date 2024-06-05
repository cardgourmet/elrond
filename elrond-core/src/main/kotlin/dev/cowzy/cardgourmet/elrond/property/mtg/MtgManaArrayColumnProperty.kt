package dev.cowzy.cardgourmet.elrond.property.mtg

import dev.cowzy.cardgourmet.commons.*
import dev.cowzy.kuery.ColumnIndex
import dev.cowzy.kuery.query.WhereQueryBuilder
import dev.cowzy.kuery.reflection.columnName
import dev.cowzy.kuery.reflection.table
import dev.cowzy.cardgourmet.elrond.*
import dev.cowzy.cardgourmet.elrond.descriptor.PropertyDescriptor
import dev.cowzy.cardgourmet.elrond.property.SearchQueryProperty
import dev.cowzy.kuery.setNumber
import java.sql.PreparedStatement
import kotlin.reflect.KProperty1

class MtgManaArrayColumnProperty(
    private val column: KProperty1<*, *>,
    private val mapContainsToLessThanOrEquals: Boolean = false,
    descriptor: PropertyDescriptor,
) : SearchQueryProperty<List<ManaValue>>(
    supportedOperators = numericQueryOperators,
    affectedTables = arrayOf(column.table()),
    descriptor = descriptor,
) {

    override val valueDefinition = QueryValueDefinition<List<ManaValue>> {
        complexity { _, _ -> SearchQueryComplexity.MEDIUM }

        formatValue { manaTypes -> manaTypes.joinToString("") { "{${it.type.symbol}}" } }

        StringValue::class {
            format = "mtg_mana"
            transform { value -> value.value.toManaDisplays()?.flatten() }
            match { values -> values.all { it is ConcreteManaValue } }
        }
    }

    override suspend fun <T : WhereQueryBuilder<T>> applyCondition(
        builder: T,
        operator: SearchQueryOperator,
        value: List<ManaValue>
    ) {
        val manaValues = value.map { it.type }.distinct().toManaColorIndices()

        val sqlArray = "ARRAY[${manaValues.joinToString { "?" }}]::smallint[]"

        val fillArray: (PreparedStatement, ColumnIndex) -> Unit = { stmt, index ->
            manaValues.forEach { stmt.setNumber(index.getAndIncrement(), it) }
        }

        when (operator) {
            SearchQueryOperator.CONTAINS -> when {
                mapContainsToLessThanOrEquals -> builder.whereRaw(column, "<@", sqlArray, fill = fillArray)
                !mapContainsToLessThanOrEquals -> builder.whereRaw(column, "@>", sqlArray, fill = fillArray)
            }

            SearchQueryOperator.GREATER_THAN_OR_EQUALS -> builder
                .whereRaw(column, "@>", sqlArray, fill = fillArray)

            SearchQueryOperator.GREATER_THAN -> builder
                .whereRaw(column, "@>", sqlArray, fill = fillArray)
                .where("cardinality(${column.columnName()})", ">", manaValues.size)

            SearchQueryOperator.LESS_THAN_OR_EQUALS -> builder
                .whereRaw(column, "<@", sqlArray, fill = fillArray)

            SearchQueryOperator.LESS_THAN -> builder
                .whereRaw(column, "<@", sqlArray, fill = fillArray)
                .where("cardinality(${column.columnName()})", "<", manaValues.size)

            SearchQueryOperator.EQUALS -> builder
                .whereRaw(column, "=", sqlArray, fill = fillArray)
        }
    }

}