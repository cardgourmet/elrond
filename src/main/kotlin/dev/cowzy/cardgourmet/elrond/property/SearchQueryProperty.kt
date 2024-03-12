package dev.cowzy.cardgourmet.elrond.property

import dev.cowzy.kuery.query.SelectQueryBuilder
import dev.cowzy.kuery.query.WhereQueryBuilder
import dev.cowzy.cardgourmet.elrond.QueryValueDefinition
import dev.cowzy.cardgourmet.elrond.SearchQueryOperator
import dev.cowzy.cardgourmet.elrond.descriptor.PropertyDescriptor
import kotlin.reflect.KClass

abstract class SearchQueryProperty<OutputType : Any>(
    val supportedOperators: Array<SearchQueryOperator>,
    val comparableTo: Array<KClass<out SearchQueryProperty<*>>> = emptyArray(),
    val affectedTables: Array<KClass<*>>,
    val descriptor: PropertyDescriptor
) {

    abstract val valueDefinition: QueryValueDefinition<OutputType>

    /**
     * Apply the property to the query builder.
     * Executed only once per query, even if the property is used multiple times.
     */
    open fun applyProperty(builder: SelectQueryBuilder) = Unit

    /**
     * Apply the condition to the query builder for the given operator and value.
     */
    abstract suspend fun <T : WhereQueryBuilder<T>> applyCondition(
        builder: T,
        operator: SearchQueryOperator,
        value: OutputType
    )

    open suspend fun <T : WhereQueryBuilder<T>> applyCondition(
        builder: T,
        operator: SearchQueryOperator,
        other: SearchQueryProperty<*>
    ) = Unit

    open suspend fun <T : WhereQueryBuilder<T>> applyComplexCondition(
        baseBuilder: SelectQueryBuilder,
        builder: T,
        operator: SearchQueryOperator,
        value: OutputType
    ) = applyCondition(builder, operator, value)
}
