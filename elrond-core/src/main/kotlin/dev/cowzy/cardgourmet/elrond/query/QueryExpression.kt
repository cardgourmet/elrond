package dev.cowzy.cardgourmet.elrond.query

import dev.cowzy.cardgourmet.commons.getSerialName
import dev.cowzy.cardgourmet.elrond.*
import dev.cowzy.cardgourmet.elrond.property.SearchQueryProperty
import dev.cowzy.cardgourmet.elrond.tokenizer.*

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
    val valueToken: ValueToken,
    rawValue: String? = null
) : PropertyQueryExpression(filter, property, operator, negate, rawValue)

class MultiValueLeafQueryExpression(
    val filter: QueryFilter,
    val properties: List<MultiValueLeafProperty>,
    val initialOperator: SearchQueryOperator,
    negate: Boolean,
    val valueToken: ValueToken,
    rawValue: String? = null
) : LeafQueryExpression(negate, rawValue)

class MultiValueLeafProperty(
    val property: SearchQueryProperty<Any>,
    val operator: SearchQueryOperator,
    val value: Any
)

class FilterLeafQueryExpression(
    filter: QueryFilter,
    property: SearchQueryProperty<Any>,
    operator: SearchQueryOperator,
    val otherFilter: QueryFilter,
    val otherProperty: SearchQueryProperty<Any>,
    negate: Boolean,
    rawValue: String? = null
) : PropertyQueryExpression(filter, property, operator, negate, rawValue)

class QueryExpressionGroup(
    val children: List<QueryExpression>,
    val operator: LogicalOperator,
    negate: Boolean
) : QueryExpression(negate)

@Suppress("UNCHECKED_CAST")
private fun <T : QueryExpression> T.invert(): T {
    return when (this) {
        is BooleanQueryExpression -> BooleanQueryExpression(!this.negate)
        is QueryExpressionGroup -> QueryExpressionGroup(this.children.map { it.invert() }, this.operator.invert(), !this.negate)
        is ValueLeafQueryExpression -> ValueLeafQueryExpression(this.filter, this.property, this.operator, this.value, !this.negate, this.valueToken, this.rawValue)
        is FilterLeafQueryExpression -> FilterLeafQueryExpression(this.filter, this.property, this.operator, this.otherFilter, this.otherProperty, !this.negate, this.rawValue)
        else -> throw IllegalArgumentException("Unsupported expression type: ${this::class.simpleName}")
    } as T
}

fun QueryExpression.normalize(): QueryExpression {
    if (this !is QueryExpressionGroup) return this

    // Normalize the group first by making sure the group is not negated.
    val group = when {
        this.negate -> this.invert()
        else -> this
    }

    // Normalize the children.
    val expressions = group.children.map { it.normalize() }

    // Unpack nested groups if they have the same operator.
    val unpackedChildren = expressions.filterIsInstance<QueryExpressionGroup>().filter { it.operator == group.operator }.flatMap { it.children }

    val groups = expressions.filterIsInstance<QueryExpressionGroup>().filter { it.operator != group.operator } + unpackedChildren.filterIsInstance<QueryExpressionGroup>()
    val leafs = expressions.filterIsInstance<LeafQueryExpression>() + unpackedChildren.filterIsInstance<LeafQueryExpression>()

    val valueLeafs = leafs.filterIsInstance<ValueLeafQueryExpression>()
        .union(leafs.filterIsInstance<MultiValueLeafQueryExpression>())

    val filterLeafs = leafs.filterIsInstance<FilterLeafQueryExpression>()

    // Sort hierarchy:
    // 1. Value leafs first, then filter leafs, then groups.
    // 2. Value leafs are sorted by the property name, then by the operator, then by the value.
    // 3. Filter leafs are sorted by the property name, then by the operator, then by the other property name.
    // 4. Groups are sorted by the number of children, then by the operator.
    val sortedValueLeafs = valueLeafs.sortedWith(compareBy({ it.filter.key }, { it.operator.ordinal }, { it.value.toString() }))
    val sortedFilterLeafs = filterLeafs.sortedWith(compareBy({ it.filter.key }, { it.operator.ordinal }, { it.otherFilter.key }))
    val sortedGroups = groups.sortedWith(compareBy({ it.children.size }, { it.operator.ordinal }))

    return QueryExpressionGroup(sortedValueLeafs + sortedFilterLeafs + sortedGroups, group.operator, false)
}

fun QueryExpression.toStructuredExplanation(): QueryExplanationPart? {
    return when (this) {
        is QueryExpressionGroup -> QueryExplanationPart(
            type = QueryExplanationType.GROUP,
            negate = this.negate,
            groupOperator = this.operator,
            children = this.children.mapNotNull { it.toStructuredExplanation() },
        )
        is ValueLeafQueryExpression -> QueryExplanationPart(
            type = QueryExplanationType.FILTER,
            negate = this.negate,
            filter = this.filter.keywords.minBy { it.length },
            filterOperator = this.operator,
            property = this.property.key,
            value = property.valueDefinition.formatValue(value),
            valueType = when (this.valueToken) {
                is RegexToken -> QueryExplanationValueType.REGEX
                is NumberToken -> QueryExplanationValueType.NUMBER
                else -> QueryExplanationValueType.STRING
            },
        )
        is MultiValueLeafQueryExpression -> QueryExplanationPart(
            type = QueryExplanationType.GROUP,
            negate = this.negate,
            groupOperator = LogicalOperator.OR,
            children = this.properties.map {
                QueryExplanationPart(
                    type = QueryExplanationType.FILTER,
                    negate = false,
                    filter = this.filter.keywords.minBy { it.length },
                    filterOperator = it.operator,
                    property = it.property.key,
                    value = it.property.valueDefinition.formatValue(it.value),
                    valueType = when (this.valueToken) {
                        is RegexToken -> QueryExplanationValueType.REGEX
                        is NumberToken -> QueryExplanationValueType.NUMBER
                        else -> QueryExplanationValueType.STRING
                    },
                )
            },
        )
        is FilterLeafQueryExpression -> QueryExplanationPart(
            type = QueryExplanationType.FILTER,
            negate = this.negate,
            filter = this.filter.keywords.minBy { it.length },
            filterOperator = this.operator,
            property = this.property.key,
            value = this.otherFilter.keywords.minBy { it.length },
            valueType = QueryExplanationValueType.FILTER,
            valueProperty = this.otherProperty.key,
        )
        is BooleanQueryExpression -> null
    }
}

fun <SearchFlag : Enum<SearchFlag>, DistinctMode : Enum<DistinctMode>> SearchQuery<SearchFlag, DistinctMode>.toExpressionString(): String {
    val queryParts = listOf(
        distinctMode.getSerialName(),
        expression.toExpressionString().let { if (it.isNotBlank()) "($it)" else it },
        flags.joinToString(" ") { it.getSerialName() },
        sorting.mode.let { "order:${(it as Enum<*>).getSerialName()}" },
        sorting.order.let { "direction:${it.getSerialName()}" },
    )

    return queryParts.filter { it.isNotBlank() }.joinToString(" ") { it.trim() }
}

fun QueryExpression.toExpressionString(topLevel: Boolean = true): String {
    val string = when (this) {
        is BooleanQueryExpression -> ""
        is ValueLeafQueryExpression -> "${filter.keywords.minBy { it.length }}${operator.value}${property.valueDefinition.formatValue(value)}"
        is MultiValueLeafQueryExpression -> "${filter.keywords.minBy { it.length }}${initialOperator.value}${valueToken}"
        is FilterLeafQueryExpression -> "${filter.keywords.minBy { it.length }}${operator.value}${otherFilter.keywords.minBy { it.length }}"
        is QueryExpressionGroup -> {
            when (this.children.size) {
                1 -> return this.children.single().toExpressionString(topLevel)
                0 -> return ""
            }

            val operator = when (this.operator) {
                LogicalOperator.AND -> " "
                LogicalOperator.OR -> " or "
            }

            when {
                topLevel && !this.negate -> this.children.joinToString(operator) { it.toExpressionString(false) }
                else -> "(${this.children.joinToString(operator) { it.toExpressionString(false) }})"
            }
        }
    }

    return if (this.negate) "-$string" else string
}
