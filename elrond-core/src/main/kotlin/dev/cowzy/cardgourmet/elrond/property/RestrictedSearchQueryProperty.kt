package dev.cowzy.cardgourmet.elrond.property

import dev.cowzy.cardgourmet.elrond.SearchQueryOperator
import dev.cowzy.cardgourmet.elrond.descriptor.PropertyDescriptor
import kotlin.reflect.KClass

abstract class RestrictedSearchQueryProperty<OutputType : Any, PrincipalType : Any>(
    supportedOperators: Array<SearchQueryOperator>,
    comparableTo: Array<KClass<out SearchQueryProperty<*>>> = emptyArray(),
    affectedTables: Array<KClass<*>>,
    descriptor: PropertyDescriptor,
    key: String? = null
) : SearchQueryProperty<OutputType>(supportedOperators, comparableTo, affectedTables, descriptor, key) {

    abstract suspend fun isPermitted(principal: PrincipalType?, value: OutputType): Boolean

}