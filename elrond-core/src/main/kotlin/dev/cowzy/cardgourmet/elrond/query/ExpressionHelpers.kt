package dev.cowzy.cardgourmet.elrond.query

import dev.cowzy.cardgourmet.commons.getJsonNames
import dev.cowzy.cardgourmet.commons.getSerialName
import dev.cowzy.cardgourmet.elrond.*
import dev.cowzy.cardgourmet.elrond.property.SearchQueryProperty
import dev.cowzy.cardgourmet.elrond.property.StaticSearchQueryProperty
import dev.cowzy.cardgourmet.elrond.tokenizer.*
import kotlin.reflect.KClass

data class QueryExpressionBuilderResult(
    val expression: QueryExpression = BooleanQueryExpression(true),
    val ignored: List<IgnoredQueryValue> = emptyList()
)

fun <T : Enum<T>> String.stripFlags(flags: Iterable<T>): Pair<String, Set<T>> {
    val strippedFlags = mutableListOf<T>()
    var strippedQuery = this

    flags.forEach { flag ->
        val names = flag.getJsonNames() + flag.getSerialName()
        names.forEach inner@{ name ->
            val regex = Regex("(?<=^|\\s)$name(\\s|\$)", RegexOption.IGNORE_CASE)
            if (!strippedQuery.contains(regex)) return@inner

            strippedQuery = strippedQuery.replace(regex, " ")
            strippedFlags.add(flag)
        }
    }

    return strippedQuery.trim() to strippedFlags.toSet()
}

inline fun <reified T : Enum<T>> String.stripFlags() = this.stripFlags(enumValues<T>().toList())

suspend fun QueryToken?.toQueryExpression(
    filters: List<QueryFilter>,
    fallbackFilter: QueryFilter? = null
): QueryExpressionBuilderResult {
    if (this == null) return QueryExpressionBuilderResult()

    val (rawExpression, rawIgnoredValues) = this.parseQueryExpression(filters, fallbackFilter)

    val ignoredValues = rawIgnoredValues.toMutableList()
    val expression = rawExpression ?: BooleanQueryExpression(true)

    val optimizedExpressions = expression.flattenExpressions().filterDuplicatesAndNegatedPairs { ignoredValues.add(it) }
    val optimizedExpression = when {
        optimizedExpressions.size == 1 -> optimizedExpressions.single()
        expression is QueryExpressionGroup -> QueryExpressionGroup(optimizedExpressions, expression.operator, expression.negate)
        else -> QueryExpressionGroup(optimizedExpressions, LogicalOperator.AND, false)
    }

    return QueryExpressionBuilderResult(optimizedExpression, ignoredValues)
}

private suspend fun QueryToken.parseQueryExpression(
    filters: List<QueryFilter>,
    fallbackFilter: QueryFilter? = null,
): Pair<QueryExpression?, List<IgnoredQueryValue>> {
    val ignoredValues = mutableListOf<IgnoredQueryValue>()

    when (this) {
        is QueryTokenGroup -> {
            val results = this.children.map { it.parseQueryExpression(filters, fallbackFilter) }
            val expressions = results.mapNotNull { it.first }
            ignoredValues.addAll(results.map { it.second }.flatten())

            return when {
                expressions.isEmpty() -> null to ignoredValues
                else -> QueryExpressionGroup(expressions, this.operator, this.negate) to ignoredValues
            }
        }

        is QueryFilterToken -> {
            val filter = when (this.keyword) {
                null -> fallbackFilter
                else -> filters.findStaticFilter(this) ?: filters.findFilter(this.keyword)
            }

            if (filter == null) {
                ignoredValues.add(IgnoredQueryValue(this.toString(), "unknown_filter"))
                return null to ignoredValues
            }

            val operator = when {
                this.exactValue -> SearchQueryOperator.EQUALS
                else -> this.operator ?: SearchQueryOperator.CONTAINS
            }

            val supportedValueTypes = filter.properties
                .map { prop ->
                    when {
                        prop.valueDefinition.provider?.getValues()?.any() == true -> prop.valueDefinition.supportedValueTypes + StringValue::class
                        else -> prop.valueDefinition.supportedValueTypes
                    }
                }
                .flatten()
                .distinct()
                .toTypedArray()

            val values = this.value?.let { listOf(it) }?.parseExpressionValues(supportedValueTypes, filter, filters) ?: emptyList()

            if (values.isEmpty()) {
                ignoredValues.add(IgnoredQueryValue(this.toString(), "empty_value"))
                return null to ignoredValues
            }

            val expressions = values.map { value ->
                if (value == null) {
                    ignoredValues.add(IgnoredQueryValue(this.toString(), "unsupported_value"))
                    return@map null
                }

                var negated = this.negate
                if (filter.inverted) negated = !negated

                if (value is FilterValue) {
                    val properties = value.properties

                    if (properties.first == properties.second) {
                        ignoredValues.add(IgnoredQueryValue(this.toString(), "useless_comparison"))
                        return@map null
                    }

                    FilterLeafQueryExpression(
                        filter,
                        properties.first,
                        operator,
                        properties.second,
                        negated,
                        this.toString()
                    )
                } else {
                    val propertyCandidates = filter.properties.mapNotNull inner@{ prop ->
                        val supportsValueType = prop.valueDefinition.supportedValueTypes.any { it.isInstance(value) }
                        val supportsValueMappings = value is StringValue && (supportsValueType || prop.valueDefinition.provider?.getValues()?.any() == true)
                        if (!supportsValueType && !supportsValueMappings) return@inner null

                        val provider = prop.valueDefinition.provider
                        if (value is StringValue && provider != null) {
                            val matchingValue = provider.findValue(value.value)
                            if (matchingValue != null) {
                                val mappedOperator = when (operator) {
                                    SearchQueryOperator.CONTAINS -> matchingValue.resolvesTo.operator
                                    else -> operator
                                }

                                return@inner prop to (matchingValue.resolvesTo.value to mappedOperator)
                            }

                            if (provider.strictValues) return@inner null
                        }

                        if (!supportsValueType) return@inner null
                        val definition = prop.valueDefinition.getDefinition(value::class) as QueryValueMapping<*, QueryValue<*>, Any>

                        try {
                            val transformedValue = definition.transform(value, operator)
                            if (transformedValue == null || !definition.match(transformedValue.first)) return@inner null
                            return@inner prop to transformedValue
                        } catch (ex: Exception) {
                            return@inner null
                        }
                    }

                    if (propertyCandidates.isEmpty()) {
                        ignoredValues.add(IgnoredQueryValue(this.toString(), "unsupported_value"))
                        return@map null
                    }

                    val matchingProperty = propertyCandidates.find { (prop, _) ->
                        prop.supportedOperators.contains(operator)
                    }

                    if (matchingProperty == null) {
                        ignoredValues.add(IgnoredQueryValue(
                            this.toString(),
                            "unsupported_operator",
                            propertyCandidates.asSequence().map { (prop, _) ->
                                prop.supportedOperators.toList()
                            }.flatten().distinct().map {
                                it.value
                            }.sorted().toList()
                        ))
                        return@map null
                    }

                    if (matchingProperty.second.first.let { it is StringValue && it.value.isBlank() }) {
                        ignoredValues.add(IgnoredQueryValue(this.toString(),"empty_value"))
                        return@map null
                    }

                    ValueLeafQueryExpression(
                        filter,
                        matchingProperty.first as SearchQueryProperty<Any>,
                        matchingProperty.second.second ?: operator,
                        matchingProperty.second.first,
                        negated,
                        this.toString()
                    )
                }
            }.filterNotNull()

            return when (expressions.size) {
                1 -> expressions.single()
                else -> QueryExpressionGroup(expressions, LogicalOperator.AND, false)
            } to ignoredValues
        }
    }
}

private fun QueryExpression.flattenExpressions(): List<QueryExpression> {
    if (this !is QueryExpressionGroup) return listOf(this)

    val expressions = this.children.map { it.flattenExpressions() }.flatten()

    return when {
        operator == LogicalOperator.AND && !negate -> expressions
        operator == LogicalOperator.OR && negate -> expressions.map { it.apply { negate = !negate } }
        else -> listOf(QueryExpressionGroup(
            expressions,
            operator,
            negate
        ))
    }
}

private fun List<QueryExpression>.filterDuplicatesAndNegatedPairs(ignoreValue: (IgnoredQueryValue) -> Unit): List<QueryExpression> {
    val mutableExpressions = this.toMutableList()

    do {
        var changed = false

        val expressions = mutableExpressions.toList()
        for (expression in expressions) {
            val duplicateExpression = expressions
                .filter { expression != it }
                .filter { it.negate == expression.negate }
                .filter { it::class == expression::class }
                .find {
                    when (it) {
                        is ValueLeafQueryExpression -> it.property == (expression as ValueLeafQueryExpression).property && it.value == expression.value
                        is FilterLeafQueryExpression -> it.property == (expression as FilterLeafQueryExpression).property && it.otherProperty == expression.otherProperty
                        else -> false
                    }
                }

            if (duplicateExpression != null) {
                mutableExpressions.remove(duplicateExpression)

                ignoreValue(IgnoredQueryValue(
                    duplicateExpression.rawValue!!,
                    "duplicate_filter"
                ))

                changed = true
                break
            }

            val negatedExpression = expressions
                .filter { expression != it }
                .filter { it.negate != expression.negate }
                .filter { it::class == expression::class }
                .find {
                    when (it) {
                        is ValueLeafQueryExpression -> it.property == (expression as ValueLeafQueryExpression).property && it.value == expression.value
                        is FilterLeafQueryExpression -> it.property == (expression as FilterLeafQueryExpression).property && it.otherProperty == expression.otherProperty
                        else -> false
                    }
                }

            if (negatedExpression != null) {
                mutableExpressions.remove(expression)
                mutableExpressions.remove(negatedExpression)

                ignoreValue(IgnoredQueryValue(
                    "${expression.rawValue} ${negatedExpression.rawValue}",
                    "negated_filters"
                ))

                changed = true
                break
            }
        }
    } while (changed)

    return mutableExpressions
}

private fun Iterable<QueryFilter>.findStaticFilter(token: QueryFilterToken): QueryFilter? {
    if (token.keyword == null || token.operator == null) return null
    val value = "${token.keyword}${token.operator}${token.value}".trim()

    return this.filter { filter ->
        filter.keywords.any { keyword -> keyword.equals(value, ignoreCase = true) }
    }.find { filter ->
        filter.properties.any { it is StaticSearchQueryProperty }
    }
}

private fun Iterable<QueryFilter>.findFilter(key: String, restricted: Boolean = false) = this.find { filter ->
    filter.keywords
        .filter { !restricted || !filter.ignoreReferenceKeywords.contains(it) }
        .any { keyword -> keyword.equals(key.trim(), ignoreCase = true) }
}

@Suppress("UNCHECKED_CAST")
private fun QueryFilter.findComparableProperties(filter: QueryFilter): Pair<SearchQueryProperty<Any>, SearchQueryProperty<Any>>? {
    val properties = this.properties
    val otherProperties = filter.properties

    for (property in properties) {
        for (otherProperty in otherProperties) {
            if (property.comparableTo.any { it.isInstance(otherProperty) }) {
                return Pair(
                    property as SearchQueryProperty<Any>,
                    otherProperty as SearchQueryProperty<Any>
                )
            }
        }
    }

    return null
}

private fun List<ValueToken>.parseExpressionValues(
    supportedValueTypes: Array<KClass<out QueryValue<*>>>,
    filter: QueryFilter,
    filters: Iterable<QueryFilter>
): List<QueryValue<*>?> {
    return this.map { token ->
        when (token) {
            is RegexToken -> {
                if (supportedValueTypes.contains(RegexValue::class)) {
                    return@map RegexValue(token.value)
                } else {
                    return@map null
                }
            }

            is QuotedStringToken -> {
                if (token.value.isNotBlank() && supportedValueTypes.contains(StringValue::class)) {
                    return@map StringValue(token.value, true)
                } else {
                    return@map null
                }
            }

            is StringToken -> {
                val matchingFilter = filters.findFilter(token.value, true)
                val matchingProperties = matchingFilter?.let { filter.findComparableProperties(it) }

                val number = token.value.toDoubleOrNull()

                if (matchingFilter != null && matchingProperties != null) {
                    return@map FilterValue(matchingFilter, matchingProperties)
                } else if (number != null && supportedValueTypes.contains(NumberValue::class)) {
                    return@map NumberValue(number)
                } else if (token.value.isNotBlank() && supportedValueTypes.contains(StringValue::class)) {
                    return@map StringValue(token.value)
                } else {
                    return@map null
                }
            }
        }
    }
}