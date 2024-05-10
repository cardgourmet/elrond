package dev.cowzy.cardgourmet.elrond.property

import dev.cowzy.cardgourmet.elrond.*
import dev.cowzy.cardgourmet.elrond.descriptor.StringDescriptor
import dev.cowzy.kuery.query.WhereQueryBuilder
import dev.cowzy.kuery.reflection.table
import kotlin.reflect.KProperty1

class StringRegexProperty(
    private val column: KProperty1<*, *>,
    private val mapPattern: (String, SearchQueryOperator) -> String,
    enableNumericOperators: Boolean = false,
    propertyKey: String,
) : SearchQueryProperty<String>(
    supportedOperators = if (enableNumericOperators) numericQueryOperators else stringQueryOperators,
    affectedTables = arrayOf(column.table()),
    descriptor = StringDescriptor(propertyKey)
) {

    override val valueDefinition = QueryValueDefinition {
        StringValue::class {
            transform { it.value }
        }
    }

    override suspend fun <T : WhereQueryBuilder<T>> applyCondition(
        builder: T,
        operator: SearchQueryOperator,
        value: String
    ) {
        val escapedValue = value.replace(Regex("[^\\p{L}\\p{N}]"), ".")
        val pattern = this.mapPattern(escapedValue, operator)
        builder.where(column, "~*", value = pattern)
    }

}