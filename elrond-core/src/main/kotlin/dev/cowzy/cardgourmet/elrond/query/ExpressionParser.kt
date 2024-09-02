package dev.cowzy.cardgourmet.elrond.query

import dev.cowzy.cardgourmet.commons.getSerialName
import dev.cowzy.cardgourmet.elrond.*
import dev.cowzy.cardgourmet.elrond.config.SearchQueryExecutor
import dev.cowzy.cardgourmet.elrond.property.SearchQueryProperty
import dev.cowzy.cardgourmet.elrond.property.StaticSearchQueryProperty
import dev.cowzy.cardgourmet.elrond.tokenizer.*
import dev.cowzy.kuery.Order
import kotlin.reflect.KClass

interface SearchQueryDistinctMode {
    val keywords: Array<String>
    val key: String
}

data class SearchQuery<SearchFlag : Enum<SearchFlag>, DistinctMode : Enum<DistinctMode>>(
    val expression: QueryExpression,
    val normalizedExpression: QueryExpression,
    val flags: List<SearchFlag>,
    val sorting: Sorting,
    val distinctMode: DistinctMode,
    val ignoredExpressions: List<IgnoredQueryValue>,
    val filters: List<String>,
    val filterExpressions: List<String>,
    val preferredLanguage: String?,
)

data class QueryExpressionBuilderResult(
    val expression: QueryExpression = BooleanQueryExpression(true),
    val ignored: List<IgnoredQueryValue> = emptyList()
)

data class SearchQueryParseConfig<SearchFlag : Enum<SearchFlag>, DistinctMode>(
    val overrideDistinctMode: DistinctMode? = null,
    val overrideFlags: Set<SearchFlag>? = null,
    val overrideSorting: Sorting? = null,
    val preferredLanguage: String? = null,
    val whitelistedFilters: Set<String> = emptySet(),
    val blacklistedFilters: Set<String> = emptySet(),
    val whitelistedValueTypes: Set<KClass<out QueryValue<*>>> = emptySet(),
    val validationRules: Set<QueryValidationRule> = emptySet()
) where DistinctMode : Enum<DistinctMode>, DistinctMode : SearchQueryDistinctMode {

    init {
        if (whitelistedFilters.any() && blacklistedFilters.any()) {
            throw IllegalArgumentException("Cannot have both blacklisted and whitelisted filters.")
        }
    }

    fun isFilterAllowed(filter: QueryFilter) = when {
        whitelistedFilters.any() -> filter.keywords.any { whitelistedFilters.contains(it) }
        blacklistedFilters.any() -> filter.keywords.none { blacklistedFilters.contains(it) }
        else -> true
    }

}

suspend inline fun <SearchFlag : Enum<SearchFlag>, reified DistinctMode> SearchQueryExecutor<SearchFlag, DistinctMode>.parse(
    query: String,
    config: SearchQueryParseConfig<SearchFlag, DistinctMode> = SearchQueryParseConfig()
) : SearchQuery<SearchFlag, DistinctMode> where DistinctMode : Enum<DistinctMode>, DistinctMode : SearchQueryDistinctMode {
    val failedValidations = mutableSetOf<QueryValidationRule>()

    val (queryWithoutDistinctMode, distinctMode) = query.stripFlags<DistinctMode> { it.keywords.toSet() + it.getSerialName() }
    val (queryWithoutFlags, queryFlags) = queryWithoutDistinctMode.stripFlags(flags)
    val (queryWithoutSorting, sorting) = queryWithoutFlags.stripSorting(sortModes)

    if (config.validationRules.contains(QueryValidationRule.NO_CUSTOM_DISTINCT_MODE) && distinctMode.any()) {
        failedValidations.add(QueryValidationRule.NO_CUSTOM_DISTINCT_MODE)
    }

    if (config.validationRules.contains(QueryValidationRule.NO_CUSTOM_SORTING) && sorting != null) {
        failedValidations.add(QueryValidationRule.NO_CUSTOM_SORTING)
    }

    if (config.validationRules.contains(QueryValidationRule.NO_CUSTOM_FLAGS) && queryFlags.any()) {
        failedValidations.add(QueryValidationRule.NO_CUSTOM_FLAGS)
    }

    val allowedFilters = filters.filter(config::isFilterAllowed)
    val fallbackFilter = fallbackFilter?.takeIf(config::isFilterAllowed)

    val whitelistedValueTypes = config.whitelistedValueTypes.mapNotNull {
        when (it) {
            StringValue::class -> StringToken::class
            NumberValue::class -> NumberToken::class
            RegexValue::class -> RegexToken::class
            FilterValue::class -> FilterToken::class
            else -> null
        }
    }.toSet()

    val tokenizerFilters = allowedFilters.map(QueryFilter::toTokenizerFilter)
    val tokenizer = QueryTokenizer(tokenizerFilters, fallbackFilter?.toTokenizerFilter(), whitelistedValueTypes)

    val (token, ignored) = tokenizer.tokenizeToQuery(queryWithoutSorting)
    val result = token.toQueryExpression(allowedFilters, fallbackFilter)
    val normalizedExpression = result.expression.normalize()

    val (filters, filterExpressions) = normalizedExpression.extractFilterExpressions().unzip()

    if (config.validationRules.contains(QueryValidationRule.NO_IGNORED_VALUES) && (ignored.any() || result.ignored.any())) {
        failedValidations.add(QueryValidationRule.NO_IGNORED_VALUES)
    }

    if (config.validationRules.contains(QueryValidationRule.NOT_EMPTY) && normalizedExpression is BooleanQueryExpression) {
        failedValidations.add(QueryValidationRule.NOT_EMPTY)
    }

    if (failedValidations.any()) {
        throw QueryParserValidationException(failedValidations)
    }

    return SearchQuery(
        expression = result.expression,
        normalizedExpression = normalizedExpression,
        flags = (config.overrideFlags ?: queryFlags).sortedBy { it.getSerialName() },
        sorting = config.overrideSorting ?: sorting ?: fallbackSortMode(normalizedExpression).let { Sorting(it, it.defaultOrder) },
        distinctMode = config.overrideDistinctMode ?: distinctMode.firstOrNull() ?: fallbackDistinctMode,
        ignoredExpressions = ignored + result.ignored,
        preferredLanguage = config.preferredLanguage,
        filters = filters,
        filterExpressions = filterExpressions
    )
}

fun QueryFilter.toTokenizerFilter(): QueryTokenizerFilter {
    val values = properties.flatMap { property ->
        property.valueDefinition.supportedValueTypes.mapNotNull { type ->
            val valueTokenType = when (type) {
                StringValue::class -> StringToken::class
                NumberValue::class -> NumberToken::class
                RegexValue::class -> RegexToken::class
                else -> return@mapNotNull null
            }

            valueTokenType to property.supportedOperators
        } + property.comparableTo.let {
            when {
                it.any() -> listOf(FilterToken::class to property.supportedOperators)
                else -> emptyList()
            }
        } + (property.valueDefinition.provider?.let {
            listOf(StringToken::class to property.supportedOperators)
        } ?: emptyList())
    }.groupBy { type ->
        type.first
    }.mapValues { (_, value) ->
        value.flatMap { entry -> entry.second.toList() }.distinct().sortedBy { operator -> operator.ordinal }
    }.map { QueryTokenizerFilterValue(it.key, it.value) }.toMutableList()

    return QueryTokenizerFilter(
        keywords = keywords,
        values = values,
        referenceBy = (keywords - ignoreReferenceKeywords).takeIf { properties.any { it.comparableTo.any() } } ?: emptyList()
    )
}

fun <SearchFlag : Enum<SearchFlag>, DistinctMode : Enum<DistinctMode>> SearchQueryExecutor<SearchFlag, DistinctMode>.tryTransform(
    query: SearchQuery<SearchFlag, DistinctMode>,
    attempt: Int
): SearchQuery<SearchFlag, DistinctMode>? {
    if (attempt <= 0) return query
    if (attempt > attemptTransformers.size) return null

    val transformer = attemptTransformers[attempt - 1]
    return transformer(query)
}

fun String.stripSorting(sortModes: List<SortMode>): Pair<String, Sorting?> {
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
    val expression = rawExpression ?: return QueryExpressionBuilderResult()

    val optimizedExpressions = expression.flattenExpressions().filterDuplicatesAndNegatedPairs { ignoredValues.add(it) }
    val optimizedExpression = when {
        optimizedExpressions.isEmpty() -> return QueryExpressionBuilderResult()
        optimizedExpressions.size == 1 -> optimizedExpressions.single()
        expression is QueryExpressionGroup -> QueryExpressionGroup(
            optimizedExpressions,
            expression.operator,
            expression.negate
        )

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
            val staticFilter = filters.findStaticFilter(this)
            if (staticFilter != null) {
                return ValueLeafQueryExpression(
                    staticFilter,
                    staticFilter.properties.first { it is StaticSearchQueryProperty } as SearchQueryProperty<Any>,
                    this.operator,
                    Unit,
                    this.negate,
                    this.value,
                    this.raw
                ) to ignoredValues
            }

            val filter = when (this.keyword) {
                null -> fallbackFilter
                else -> filters.findFilter(this.keyword!!)
            }

            if (filter == null) {
                ignoredValues.add(IgnoredQueryValue(this.toString(), "unknown_filter"))
                return null to ignoredValues
            }

            val supportedValueTypes = filter.properties
                .map { prop ->
                    when {
                        prop.valueDefinition.provider?.getValues()
                            ?.any() == true -> prop.valueDefinition.supportedValueTypes + StringValue::class

                        else -> prop.valueDefinition.supportedValueTypes
                    }
                }
                .flatten()
                .distinct()
                .toTypedArray()

            val values = listOf(this.value).parseExpressionValues(supportedValueTypes, filter, filters)

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
                        val supportsValueMappings =
                            value is StringValue && (supportsValueType || prop.valueDefinition.provider?.getValues()
                                ?.any() == true)
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
                        val definition =
                            prop.valueDefinition.getDefinition(value::class) as QueryValueMapping<*, QueryValue<*>, Any>

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
                        ignoredValues.add(IgnoredQueryValue(this.toString(), "empty_value"))
                        return@map null
                    }

                    ValueLeafQueryExpression(
                        filter,
                        matchingProperty.first as SearchQueryProperty<Any>,
                        matchingProperty.second.second ?: operator,
                        matchingProperty.second.first,
                        negated,
                        valueToken = this.value,
                        rawValue = this.toString()
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
        else -> listOf(
            QueryExpressionGroup(
                expressions.mapNotNull {
                    when (it.size) {
                        0 -> null
                        1 -> it.single()
                        else -> QueryExpressionGroup(it, LogicalOperator.AND, false)
                    }
                },
                operator,
                negate
            )
        )
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
                .asSequence()
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

                ignoreValue(
                    IgnoredQueryValue(
                        duplicateExpression.rawValue!!,
                        "duplicate_filter"
                    )
                )

                changed = true
                break
            }

            val negatedExpression = expressions
                .asSequence()
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

                ignoreValue(
                    IgnoredQueryValue(
                        "${expression.rawValue} ${negatedExpression.rawValue}",
                        "negated_filters"
                    )
                )

                changed = true
                break
            }
        }
    } while (changed)

    return mutableExpressions
}

private fun Iterable<QueryFilter>.findStaticFilter(token: QueryFilterToken): QueryFilter? {
    return this.filter { filter ->
        filter.keywords.any { keyword -> keyword.equals(token.raw, ignoreCase = true) }
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

            is NumberToken -> {
                if (supportedValueTypes.contains(NumberValue::class)) {
                    return@map NumberValue(token.value)
                } else if (supportedValueTypes.contains(StringValue::class)) {
                    return@map StringValue(token.value.toString())
                } else {
                    return@map null
                }
            }

            is FilterToken -> {
                val matchingFilter = filters.findFilter(token.keyword, true)
                val matchingProperties = matchingFilter?.let { filter.findComparableProperties(it) }

                if (matchingFilter != null && matchingProperties != null) {
                    return@map FilterValue(matchingFilter, matchingProperties)
                } else {
                    return@map null
                }
            }

            is StringToken -> {
                if (token.value.isNotBlank() && supportedValueTypes.contains(StringValue::class)) {
                    return@map StringValue(token.value)
                } else {
                    return@map null
                }
            }
        }
    }
}

fun QueryExpression.extractFilterExpressions(): List<Pair<String, String>> {
    return when (this) {
        is BooleanQueryExpression -> emptyList()
        is ValueLeafQueryExpression -> {
            var expression =
                "${filter.keywords.minBy { it.length }}${operator.value}${property.valueDefinition.formatValue(value)}"
            if (value is StringValue && value.exact) expression = "!$expression"
            listOf(filter.keywords.minBy { it.length } to expression)
        }

        is FilterLeafQueryExpression -> listOf(filter.keywords.minBy { it.length } to "${filter.keywords.minBy { it.length }}${operator.value}${otherFilter.keywords.minBy { it.length }}")
        is QueryExpressionGroup -> this.children.flatMap { it.extractFilterExpressions() }
    }
}

