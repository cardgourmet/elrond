package dev.cowzy.cardgourmet.elrond.property

import dev.cowzy.cardgourmet.commons.getSerialName
import dev.cowzy.cardgourmet.commons.i18n.LocalizationService
import dev.cowzy.cardgourmet.commons.i18n.UserLanguage
import dev.cowzy.cardgourmet.elrond.QueryValueDefinition
import dev.cowzy.cardgourmet.elrond.SearchQueryComplexity
import dev.cowzy.cardgourmet.elrond.SearchQueryOperator
import dev.cowzy.cardgourmet.elrond.descriptor.PropertyDescriptor
import dev.cowzy.kuery.query.WhereQueryBuilder
import dev.cowzy.kuery.reflection.placeholder
import dev.cowzy.kuery.reflection.table
import kotlin.reflect.KProperty1

class EnumArrayColumnProperty<ValueType : Enum<ValueType>>(
    val column: KProperty1<*, List<ValueType>>,
    display: ((ValueType, LocalizationService, UserLanguage) -> String)? = null,
    descriptor: PropertyDescriptor,
    key: String? = null,
) : SearchQueryProperty<ValueType>(
    supportedOperators = arrayOf(SearchQueryOperator.CONTAINS),
    affectedTables = arrayOf(column.table()),
    descriptor = descriptor,
    key = key
) {

    override val valueDefinition = QueryValueDefinition<ValueType> {
        complexity { _, _ -> SearchQueryComplexity.MEDIUM }

        display { value, i18n, locale -> display?.invoke(value, i18n, locale) ?: "`${value.getSerialName()}`" }
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

inline fun <reified T : Enum<T>> enumArrayColumnProperty(
    column: KProperty1<*, List<T>>,
    descriptor: PropertyDescriptor,
    key: String,
) = EnumArrayColumnProperty(column, null, descriptor, key)
