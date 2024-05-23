package dev.cowzy.cardgourmet.elrond.property

import dev.cowzy.kuery.query.WhereQueryBuilder
import dev.cowzy.cardgourmet.commons.getSerialName
import dev.cowzy.cardgourmet.commons.i18n.LocalizationService
import dev.cowzy.cardgourmet.commons.i18n.UserLanguage
import dev.cowzy.cardgourmet.elrond.*
import dev.cowzy.cardgourmet.elrond.descriptor.PropertyDescriptor
import dev.cowzy.kuery.reflection.DatabaseReflectionCache.determineTransformer
import dev.cowzy.kuery.reflection.table
import kotlin.reflect.KProperty1
import kotlin.reflect.jvm.javaField

class EnumColumnProperty<ValueType : Enum<ValueType>>(
    private val column: KProperty1<*, *>,
    display: ((ValueType, LocalizationService, UserLanguage) -> String)? = null,
    descriptor: PropertyDescriptor,
    key: String,
) : SearchQueryProperty<ValueType>(
    supportedOperators = stringQueryOperators,
    affectedTables = arrayOf(column.table()),
    descriptor = descriptor,
    key = key
) {

    override val valueDefinition = QueryValueDefinition<ValueType> {
        display { value, i18n, locale -> display?.invoke(value, i18n, locale) ?: "`${value.getSerialName()}`" }
    }

    override suspend fun <T : WhereQueryBuilder<T>> applyCondition(
        builder: T,
        operator: SearchQueryOperator,
        value: ValueType
    ) {
        if (column.javaField!!.type.isEnum) {
            builder.where(column, value)
        } else {
            builder.where(column, value.getSerialName())
        }
    }

}

fun <T : Enum<T>> enumColumnProperty(
    column: KProperty1<*, *>,
    descriptor: PropertyDescriptor,
    key: String,
    display: ((T, LocalizationService, UserLanguage) -> String)? = null,
) = EnumColumnProperty(column, display, descriptor, key)
