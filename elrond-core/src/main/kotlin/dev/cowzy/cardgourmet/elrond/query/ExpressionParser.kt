package dev.cowzy.cardgourmet.elrond.query

import dev.cowzy.cardgourmet.commons.getSerialName
import dev.cowzy.cardgourmet.elrond.*
import dev.cowzy.cardgourmet.elrond.config.SearchQueryDistinctMode
import dev.cowzy.cardgourmet.elrond.config.SearchQueryExecutor
import dev.cowzy.cardgourmet.elrond.property.SearchQueryProperty
import dev.cowzy.cardgourmet.elrond.property.StaticSearchQueryProperty
import dev.cowzy.cardgourmet.elrond.tokenizer.*
import dev.cowzy.kuery.Order
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

data class SearchQuery<T : Enum<T>>(
    val expression: QueryExpression,
    val normalizedExpression: QueryExpression,
    val flags: List<T>,
    val sorting: Sorting,
    val distinctMode: SearchQueryDistinctMode,
    val ignoredExpressions: List<IgnoredQueryValue>,
    val preferredLanguage: String?,
)

data class QueryExpressionBuilderResult(
    val expression: QueryExpression = BooleanQueryExpression(true),
    val ignored: List<IgnoredQueryValue> = emptyList()
)

@Serializable
data class IgnoredQueryValue(
    val value: String,
    val reason: String,
    val supportedValues: List<String>? = null
)

suspend fun <T : Enum<T>> SearchQueryExecutor<T>.parse(
    query: String,
    overrideDistinctMode: SearchQueryDistinctMode? = null,
    overrideFlags: Set<T>? = null,
    overrideSorting: Sorting? = null,
    preferredLanguage: String? = null,
    strict: Boolean = false
): SearchQuery<T> {
    val (queryWithoutDistinctMode, distinctMode) = query.stripFlags<SearchQueryDistinctMode> { it.keywords.toSet() + it.getSerialName() }
    val (queryWithoutFlags, queryFlags) = queryWithoutDistinctMode.stripFlags(flags)
    val (queryWithoutSorting, sorting) = queryWithoutFlags.stripSorting(sortModes)

    val token = queryWithoutSorting.tokenizeToQuery(strict)
    val result = token.toQueryExpression(filters, fallbackFilter)
    val normalizedExpression = result.expression.normalize()

    return SearchQuery(
        expression = result.expression,
        normalizedExpression = normalizedExpression,
        flags = (overrideFlags ?: queryFlags).sortedBy { it.getSerialName() },
        sorting = overrideSorting ?: sorting ?: fallbackSortMode(normalizedExpression).let { Sorting(it, it.defaultOrder) },
        distinctMode = overrideDistinctMode ?: distinctMode.firstOrNull() ?: SearchQueryDistinctMode.UNIQUE_CARDS,
        ignoredExpressions = result.ignored,
        preferredLanguage = preferredLanguage,
    )
}

fun <T : Enum<T>> SearchQueryExecutor<T>.tryTransform(query: SearchQuery<T>, attempt: Int): SearchQuery<T>? {
    if (attempt <= 0) return query
    if (attempt >= attemptTransformers.size) return null

    val transformer = attemptTransformers[attempt - 1]
    return transformer(query)
}

private fun String.stripSorting(sortModes: List<SortMode>): Pair<String, Sorting?> {
    val (queryWithoutSortModes, parsedSortModes) = this.stripValues(sortModes) { mode ->
        mode.keywords.map { "order:$it" }.toSet()
    }

    val (queryWithoutDirections, parsedDirections) = queryWithoutSortModes.stripValues(Order.values()) {
        setOf("direction:${it.getSerialName()}", "direction:${it.getSerialName()}ending")
    }

    val sortMode = parsedSortModes.firstOrNull() ?: return queryWithoutDirections to null
    val order = parsedDirections.firstOrNull() ?: sortMode.defaultOrder
    return queryWithoutDirections to Sorting(sortMode, order)
}

fun <T : Enum<T>> String.stripFlags(
    flags: Iterable<T>,
    toString: (T) -> Set<String> = { setOf(it.getSerialName()) }
) = stripValues(flags.toList(), toString)

inline fun <reified T : Enum<T>> String.stripFlags(
    noinline toString: (T) -> Set<String> = { setOf(it.getSerialName()) }
) = this.stripFlags(enumValues<T>().toList(), toString)

fun <T> String.stripValues(values: Array<T>, toString: (T) -> Set<String>) = this.stripValues(values.toList(), toString)

fun <T> String.stripValues(values: Iterable<T>, toString: (T) -> Set<String>): Pair<String, Set<T>> {
    val strippedValues = mutableSetOf<T>()
    var strippedQuery = this

    values.forEach { value ->
        val stringValues = toString(value)
        for (stringValue in stringValues) {
            val regex = Regex("(?<=^|\\s)$stringValue(\\s|\$)", RegexOption.IGNORE_CASE)
            if (!strippedQuery.contains(regex)) continue

            strippedQuery = strippedQuery.replace(regex, " ")
            strippedValues.add(value)
            break
        }
    }

    return strippedQuery.trim() to strippedValues
}

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
                        value.value,
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
                        ignoredValues.add(
                            IgnoredQueryValue(
                            this.toString(),
                            "unsupported_operator",
                            propertyCandidates.asSequence().map { (prop, _) ->
                                prop.supportedOperators.toList()
                            }.flatten().distinct().map {
                                it.value
                            }.sorted().toList()
                        )
                        )
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

    val expressions = this.children.map { it.flattenExpressions() }

    return when {
        operator == LogicalOperator.AND && !negate -> expressions.flatten()
        operator == LogicalOperator.OR && negate -> expressions.flatten().map { it.apply { negate = !negate } }
        else -> listOf(QueryExpressionGroup(
            expressions.mapNotNull {
                when (it.size) {
                    0 -> null
                    1 -> it.single()
                    else -> QueryExpressionGroup(it, LogicalOperator.AND, false)
                }
            },
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
            if (expression !is PropertyQueryExpression) continue

            val duplicateExpression = expressions
                .filterIsInstance<PropertyQueryExpression>()
                .filter { expression != it }
                .filter { it.negate == expression.negate && it.operator == expression.operator }
                .filter { it::class == expression::class }
                .find {
                    when (it) {
                        is ValueLeafQueryExpression -> it.property.key == (expression as ValueLeafQueryExpression).property.key && it.value == expression.value
                        is FilterLeafQueryExpression -> it.property.key == (expression as FilterLeafQueryExpression).property.key && it.otherProperty.key == expression.otherProperty.key
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
                .filterIsInstance<PropertyQueryExpression>()
                .filter { expression != it }
                .filter { (it.negate != expression.negate && it.operator == expression.operator) || (it.negate == expression.negate && it.operator.negated() == expression.operator) }
                .filter { it::class == expression::class }
                .find {
                    when (it) {
                        is ValueLeafQueryExpression -> it.property.key == (expression as ValueLeafQueryExpression).property.key && it.value == expression.value
                        is FilterLeafQueryExpression -> it.property.key == (expression as FilterLeafQueryExpression).property.key && it.otherProperty.key == expression.otherProperty.key
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

