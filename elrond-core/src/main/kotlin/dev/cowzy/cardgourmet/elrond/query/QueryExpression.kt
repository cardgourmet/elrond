package dev.cowzy.cardgourmet.elrond.query

import dev.cowzy.cardgourmet.elrond.QueryFilter
import dev.cowzy.cardgourmet.elrond.SearchQueryOperator
import dev.cowzy.cardgourmet.elrond.property.SearchQueryProperty

sealed class QueryExpression(var negate: Boolean, val rawValue: String? = null)

sealed class LeafQueryExpression(
    negate: Boolean,
    rawValue: String? = null
) : QueryExpression(negate, rawValue)

class BooleanQueryExpression(
    value: Boolean,
    rawValue: String? = null
) : LeafQueryExpression(!value, rawValue)

sealed class PropertyQueryExpression(
    val filter: QueryFilter,
    val property: SearchQueryProperty<Any>,
    val operator: SearchQueryOperator,
    negate: Boolean,
    rawValue: String? = null
) : LeafQueryExpression(negate, rawValue)

class ValueLeafQueryExpression(
    filter: QueryFilter,
    property: SearchQueryProperty<Any>,
    operator: SearchQueryOperator,
    val value: Any,
    negate: Boolean,
    rawValue: String? = null
) : PropertyQueryExpression(filter, property, operator, negate, rawValue)

class FilterLeafQueryExpression(
    filter: QueryFilter,
    property: SearchQueryProperty<Any>,
    operator: SearchQueryOperator,
    val otherFilter: QueryFilter,
    val otherProperty: SearchQueryProperty<Any>,
    negate: Boolean,
    rawValue: String? = null
) : PropertyQueryExpression(filter, property, operator, negate, rawValue)

enum class LogicalOperator { AND, OR }

fun LogicalOperator.invert() = when (this) {
    LogicalOperator.AND -> LogicalOperator.OR
    LogicalOperator.OR -> LogicalOperator.AND
}

class QueryExpressionGroup(
    val children: List<QueryExpression>,
    val operator: LogicalOperator,
    negate: Boolean
) : QueryExpression(negate)
