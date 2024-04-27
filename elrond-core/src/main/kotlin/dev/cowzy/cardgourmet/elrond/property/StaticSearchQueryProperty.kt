package dev.cowzy.cardgourmet.elrond.property

import dev.cowzy.kuery.query.WhereQueryBuilder
import dev.cowzy.cardgourmet.elrond.QueryValueDefinition
import dev.cowzy.cardgourmet.elrond.SearchQueryOperator
import dev.cowzy.cardgourmet.elrond.descriptor.PropertyDescriptor
import kotlin.reflect.KClass

abstract class StaticSearchQueryProperty(
    affectedTables: Array<KClass<*>>,
    descriptor: PropertyDescriptor,
    key: String? = null
) : SearchQueryProperty<Any>(emptyArray(), emptyArray(), affectedTables, descriptor, key) {

    override val valueDefinition = QueryValueDefinition<Any>()

    override suspend fun <T : WhereQueryBuilder<T>> applyCondition(
        builder: T,
        operator: SearchQueryOperator,
        value: Any
    ) = applyCondition(builder)

    protected abstract suspend fun <T : WhereQueryBuilder<T>> applyCondition(builder: T)

}