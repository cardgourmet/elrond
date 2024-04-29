package dev.cowzy.cardgourmet.elrond.property

import dev.cowzy.cardgourmet.commons.getSerialName
import dev.cowzy.cardgourmet.elrond.QueryValueDefinition
import dev.cowzy.cardgourmet.elrond.SearchQueryOperator
import dev.cowzy.cardgourmet.elrond.StringValue
import dev.cowzy.cardgourmet.elrond.descriptor.IsPresentDescriptor
import dev.cowzy.cardgourmet.elrond.enumToMappings
import dev.cowzy.kuery.query.WhereQueryBuilder
import dev.cowzy.kuery.reflection.placeholder
import dev.cowzy.kuery.reflection.table
import kotlin.reflect.KProperty1

class EnumArrayColumnProperty<ValueType : Enum<ValueType>>(
    val column: KProperty1<*, List<ValueType>>,
    enumValues: Array<ValueType>,
    propertyKey: String
) : SearchQueryProperty<ValueType>(
    supportedOperators = arrayOf(SearchQueryOperator.CONTAINS),
    affectedTables = arrayOf(column.table()),
    descriptor = IsPresentDescriptor(propertyKey),
    key = propertyKey
) {

    override val valueDefinition = QueryValueDefinition {
        StringValue::class {
            mappings(enumToMappings(enumValues) { emptyList() })
            values(enumValues.map { it.getSerialName() })
        }
    }

    override suspend fun <T : WhereQueryBuilder<T>> applyCondition(
        builder: T,
        operator: SearchQueryOperator,
        value: ValueType
    ) {
        builder.whereRaw(column, "@>", "ARRAY[${column.placeholder()}]::text[]") { stmt, index ->
            stmt.setString(index.getAndIncrement(), value.getSerialName())
        }
    }

}

inline fun <reified T : Enum<T>> enumArrayColumnProperty(column: KProperty1<*, List<T>>, propertyKey: String) = EnumArrayColumnProperty(column, enumValues(), propertyKey)
