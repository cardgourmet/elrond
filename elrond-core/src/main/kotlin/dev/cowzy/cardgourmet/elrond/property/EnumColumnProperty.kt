package dev.cowzy.cardgourmet.elrond.property

import dev.cowzy.kuery.query.WhereQueryBuilder
import dev.cowzy.cardgourmet.commons.getSerialName
import dev.cowzy.cardgourmet.elrond.*
import dev.cowzy.cardgourmet.elrond.descriptor.EqualsDescriptor
import dev.cowzy.kuery.reflection.table
import kotlin.reflect.KProperty1

class EnumColumnProperty<ValueType : Enum<ValueType>>(
    private val column: KProperty1<*, ValueType?>,
    enumValues: Array<ValueType>,
    aliasResolver: (ValueType) -> List<String> = { emptyList() },
    propertyKey: String
) : SearchQueryProperty<ValueType>(
    supportedOperators = stringQueryOperators,
    affectedTables = arrayOf(column.table()),
    descriptor = EqualsDescriptor(propertyKey)
) {

    override val valueDefinition = QueryValueDefinition<ValueType> {
        StringValue::class {
            mappings(enumToMappings(enumValues, aliasResolver))
            values(enumValues.map { it.getSerialName() })

            transform { value ->
                enumValues.find { it.getSerialName().equals(value.value, ignoreCase = true) }
            }

            display { value, _, _ -> "`${value.getSerialName()}`" }
        }
    }

    override suspend fun <T : WhereQueryBuilder<T>> applyCondition(
        builder: T,
        operator: SearchQueryOperator,
        value: ValueType
    ) {
        builder.where(column, value)
    }

}

inline fun <reified T : Enum<T>> enumColumnProperty(
    column: KProperty1<*, T?>,
    propertyKey: String,
    noinline aliasResolver: (T) -> List<String> = { emptyList() }
) = EnumColumnProperty(column, enumValues(), aliasResolver, propertyKey)
