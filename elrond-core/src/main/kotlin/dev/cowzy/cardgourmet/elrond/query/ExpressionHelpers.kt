package dev.cowzy.cardgourmet.elrond.query

import dev.cowzy.cardgourmet.commons.getJsonNames
import dev.cowzy.cardgourmet.commons.getSerialName
import dev.cowzy.cardgourmet.elrond.*
import dev.cowzy.cardgourmet.elrond.property.SearchQueryProperty
import dev.cowzy.cardgourmet.elrond.property.StaticSearchQueryProperty
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toList
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

@Suppress("UNCHECKED_CAST")
suspend fun String.parseQueryExpression(
    filters: List<QueryFilter>,
    fallbackFilter: QueryFilter? = null,
): QueryExpressionBuilderResult {
    val expressionRegex = Regex("(?<negate>-?)(?:(?<property>\\p{L}+)(?<operator>:|<=|<|>=|>|=|[^\\p{L}\\s\\].,-_'*]+)(?<scopedValue>(?:(?:\"(?:\\\\\"|[^\"])*?\"|'(?:\\\\'|[^'])*?'|\\\\/(?:\\\\\\\\/|[^\\\\/])*?\\\\/|[^\\s()]+),?)+)|(?<unscopedValue>!?(?:\"(?:\\\\\"|[^\"])*?\"|'(?:\\\\'|[^'])*?'|[\\p{L}.,-_'*]+)))")
    // (?<negate>-?)(?:(?<property>\p{L}+)(?<operator>:|<=|<|>=|>|=|[^\p{L}\s\].,-_'*]+)(?<scopedValue>(?:(?:"(?:\"|[^"])*?"|'(?:\'|[^'])*?'|\/(?:\/|[^\/])*?\/|[^\s()]+),?)+)|(?<unscopedValue>!?(?:"(?:\"|[^"])*?"|'(?:\'|[^'])*?'|[\p{L}.,-_'*]+)))

    var query = this.trim()
    if (query.isEmpty()) return QueryExpressionBuilderResult()

    val ignoredValues = mutableListOf<IgnoredQueryValue>()

    val orRegex = Regex("or|\\|+")
    val andRegex = Regex("and|&+")

    val matches = expressionRegex.findAll(query).filter { match ->
        // Ignore matches for and/or keywords.
        val rawValue = match.value.lowercase()
        return@filter !andRegex.matches(rawValue) && !orRegex.matches(rawValue)
    }.asFlow().mapNotNull { match ->
        val negate = match.groups["negate"]?.value ?: ""
        val property = match.groups["property"]?.value ?: ""
        val operator = match.groups["operator"]?.value ?: ""
        val scopedValue = match.groups["scopedValue"]?.value ?: ""
        val unscopedValue = match.groups["unscopedValue"]?.value ?: ""

//        val (negate, property, operator, rawValue) = match.destructured
        if (match.value.isBlank() || match.value.trim() == "-") return@mapNotNull null

        val staticFilter = filters.findStaticFilter("$property$operator$scopedValue")

        if (staticFilter != null) {
            var negated = negate.isNotEmpty()
            if (staticFilter.inverted) negated = !negated

            return@mapNotNull match to ValueLeafQueryExpression(
                staticFilter,
                staticFilter.properties.find { it is StaticSearchQueryProperty }!! as SearchQueryProperty<Any>,
                SearchQueryOperator.CONTAINS,
                QueryValue.EMPTY,
                negated
            )
        }

        // If the raw match and the value string are equal, there is no property or operator.
        // Assign default filter and operator.
        val isFallback = match.value == negate + unscopedValue
        val isExactFallback = isFallback && unscopedValue.startsWith("!")

        val filter = when {
            isFallback && fallbackFilter != null -> fallbackFilter
            else -> filters.findFilter(property)
        }

        if (filter == null) {
            ignoredValues.add(IgnoredQueryValue(match.value, "unknown_filter"))
            return@mapNotNull match to null
        }

        val expressionOperator = when {
            isExactFallback -> SearchQueryOperator.EQUALS
            isFallback -> SearchQueryOperator.CONTAINS
            else -> SearchQueryOperator.tryParse(operator)
        }

        if (expressionOperator == null) {
            ignoredValues.add(IgnoredQueryValue(
                match.value, "unknown_operator",
                SearchQueryOperator.values().map { it.value }.sorted()
            ))
            return@mapNotNull match to null
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

        val values = when {
            isExactFallback -> unscopedValue.replaceFirst("!", "")
            isFallback -> unscopedValue
            else -> scopedValue
        }.parseExpressionValues(supportedValueTypes, filter, filters)

        if (values.isEmpty()) {
            ignoredValues.add(IgnoredQueryValue(match.value, "empty_value"))
            return@mapNotNull match to null
        }

        val expressions = values.map { (raw, value) ->
            if (value == null) {
                ignoredValues.add(IgnoredQueryValue(
                    "$negate$property$operator$raw",
                    "unsupported_value"
                ))
                return@map null
            }

            var negated = negate.isNotEmpty()
            if (filter.inverted) negated = !negated

            if (value is FilterValue) {
                val properties = value.properties

                if (properties.first == properties.second) {
                    ignoredValues.add(IgnoredQueryValue(
                        "$negate$property$operator$raw",
                        "useless_comparison"
                    ))
                    return@map null
                }

                FilterLeafQueryExpression(
                    filter,
                    properties.first,
                    expressionOperator,
                    properties.second,
                    negated,
                    "$negate$property$operator$raw"
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
                            val mappedOperator = when (expressionOperator) {
                                SearchQueryOperator.CONTAINS -> matchingValue.resolvesTo.operator
                                else -> expressionOperator
                            }

                            return@inner prop to (matchingValue.resolvesTo.value to mappedOperator)
                        }

                        if (provider.strictValues) return@inner null
                    }

                    if (!supportsValueType) return@inner null
                    val definition = prop.valueDefinition.getDefinition(value::class) as QueryValueMapping<*, QueryValue<*>, Any>

                    try {
                        val transformedValue = definition.transform(value, expressionOperator)
                        if (transformedValue == null || !definition.match(transformedValue.first)) return@inner null
                        return@inner prop to transformedValue
                    } catch (ex: Exception) {
                        return@inner null
                    }
                }

                if (propertyCandidates.isEmpty()) {
                    ignoredValues.add(IgnoredQueryValue(
                        "$negate$property$operator$raw",
                        "unsupported_value"
                    ))
                    return@map null
                }

                val matchingProperty = propertyCandidates.find { (prop, _) ->
                    prop.supportedOperators.contains(expressionOperator)
                }

                if (matchingProperty == null) {
                    ignoredValues.add(IgnoredQueryValue(
                        "$negate$property$operator$raw",
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
                    ignoredValues.add(IgnoredQueryValue(
                        "$negate$property$operator$raw",
                        "empty_value"
                    ))
                    return@map null
                }

                ValueLeafQueryExpression(
                    filter,
                    matchingProperty.first as SearchQueryProperty<Any>,
                    matchingProperty.second.second ?: expressionOperator,
                    matchingProperty.second.first,
                    negated,
                    "$negate$property$operator$raw"
                )
            }
        }.filterNotNull()

        return@mapNotNull match to when (expressions.size) {
            1 -> expressions.single()
            else -> QueryExpressionGroup(expressions, LogicalOperator.AND, false)
        }
    }.toList()

    var index = 0
    matches.forEach { entry ->
        val replaceWith = entry.second?.let { "${index++} " } ?: ""
        query = query.replaceFirst(entry.first.value, replaceWith)
    }

    query = query.matchBrackets()

    val expressions = matches.mapNotNull { it.second }
    val expression = query.parseQueryExpression(false, { expressions[it] }, { ignoredValues.add(it) }) ?: BooleanQueryExpression(true)

    val optimizedExpressions = expression.flattenExpressions().filterDuplicatesAndNegatedPairs { ignoredValues.add(it) }
    val optimizedExpression = when {
        optimizedExpressions.size == 1 -> optimizedExpressions.single()
        expression is QueryExpressionGroup -> QueryExpressionGroup(optimizedExpressions, expression.operator, expression.negate)
        else -> QueryExpressionGroup(optimizedExpressions, LogicalOperator.AND, false)
    }

    return QueryExpressionBuilderResult(optimizedExpression, ignoredValues)
}

private fun String.parseQueryExpression(
    negate: Boolean,
    findExpression: (Int) -> QueryExpression,
    ignoreValue: (IgnoredQueryValue) -> Unit
): QueryExpression? {
    val orRegex = Regex("\\s+(or|\\|+)\\s+")
    val andRegex = Regex("(\\s+(and|&+)\\s+|\\s+)")

    val bracketRegex = Regex("(?:-)?(?=\\()(?:(?=.*?\\((?!.*?\\1)(.*\\)(?!.*\\2).*))(?=.*?\\)(?!.*?\\2)(.*)).)+?.*?(?=\\1)[^(]*(?=\\2(?:\\n|\$))")

    var query = this.trim()
    val groupExpressions = bracketRegex.findAll(query).map {
        val value = it.value
        val negateGroup = value.startsWith("-")
        query = query.replaceFirst(value, "\$")
        return@map value
            .substring(if (negateGroup) 2 else 1, value.length - 1)
            .parseQueryExpression(negateGroup, findExpression, ignoreValue)
    }.toList()

    var groupIndex = 0
    val orExpressions = query.split(orRegex).mapNotNull { split ->
        val andExpressions = split.split(andRegex).mapNotNull { value ->
            if (value == "\$") {
                groupExpressions[groupIndex++]
            } else if (value.trim().isEmpty()) {
                null
            } else {
                val index = value.toIntOrNull()

                if (index == null) {
                    ignoreValue(IgnoredQueryValue(value, "syntax_error"))
                    null
                } else {
                    findExpression(index)
                }
            }
        }

        when {
            andExpressions.isEmpty() -> null
            andExpressions.size == 1 -> andExpressions.single()
            else -> QueryExpressionGroup(andExpressions, LogicalOperator.AND, false)
        }
    }

    return when {
        orExpressions.any() -> when {
            orExpressions.size == 1 -> orExpressions.single().apply { this.negate = if (negate) !this.negate else this.negate }
            else -> QueryExpressionGroup(orExpressions, LogicalOperator.OR, negate)
        }

        else -> null
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
//
//private fun List<QueryExpression>.flattenExpressions(operator: LogicalOperator): List<QueryExpression> {
//    return this.map { expression ->
//        if (expression !is QueryExpressionGroup) return@map listOf(expression)
//        if (expression.operator != operator) return@map listOf(expression)
//
//        if (!expression.negate) {
//            return@map expression.children.flattenExpressions(operator)
//        }
//
//        if (operator == LogicalOperator.OR) {
//            listOf(QueryExpressionGroup(
//                expression.children.map { it.apply { negate = !negate } },
//                LogicalOperator.AND,
//                false
//            ))
//        } else {
//            listOf(expression)
//        }
//    }.flatten()
//}

/**
 * Ensures there are equal amounts of opening and closing brackets.
 * If there is a mismatch, brackets will be added to the start or end of string respectively.
 */
private fun String.matchBrackets(): String {
    var result = this
    var openBracketCount = Regex("\\(").findAll(this).count()
    var closeBracketCount = Regex("\\)").findAll(this).count()

    while (openBracketCount > closeBracketCount) {
        result += ")"
        closeBracketCount += 1
    }

    while (openBracketCount < closeBracketCount) {
        result = "($result"
        openBracketCount += 1
    }

    return result
}

private fun Iterable<QueryFilter>.findStaticFilter(key: String) = this.filter { filter ->
    filter.keywords.any { keyword -> keyword.equals(key.trim(), ignoreCase = true) }
}.find { filter ->
    filter.properties.any { it is StaticSearchQueryProperty }
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

private fun String.parseExpressionValues(
    supportedValueTypes: Array<KClass<out QueryValue<*>>>,
    filter: QueryFilter,
    filters: Iterable<QueryFilter>
): List<Pair<String, QueryValue<*>?>> {
    val valueRegex = Regex("((\"(?:\\\\\"|[^\"])*?\")|(\'(?:\\\\\'|[^\'])*?\')|((?:\\/(?:\\\\\\/|[^\\/])*?\\/))|([^\\s,\\[\\]()\"]+))")

    return valueRegex.findAll(this).mapNotNull { match ->
        if (match.groupValues[5].isNotEmpty()) {
            val value = match.groupValues[5].trim()

            if (value.isBlank()) return@mapNotNull value to null

            val matchingFilter = filters.findFilter(value, true)
            val matchingProperties = matchingFilter?.let { filter.findComparableProperties(it) }
            if (matchingFilter != null && matchingProperties != null) {
                return@mapNotNull value to FilterValue(matchingFilter, matchingProperties)
            }

            val numberValue = value.toDoubleOrNull()
            if (numberValue != null && supportedValueTypes.contains(NumberValue::class)) {
                return@mapNotNull value to NumberValue(numberValue)
            } else if (supportedValueTypes.contains(StringValue::class)) {
                if (match.groupValues[5].isEmpty()) return@mapNotNull null
                return@mapNotNull value to StringValue(match.groupValues[5])
            } else {
                return@mapNotNull value to null
            }
        } else if (match.groupValues[2].isNotEmpty() && supportedValueTypes.contains(StringValue::class)) {
            val string = match.groupValues[2]
            val value = string.substring(1, string.length - 1)
            if (value.isEmpty()) return@mapNotNull null
            return@mapNotNull value to StringValue(value, true)
        } else if (match.groupValues[3].isNotEmpty() && supportedValueTypes.contains(StringValue::class)) {
            val string = match.groupValues[3]
            val value = string.substring(1, string.length - 1)
            if (value.isEmpty()) return@mapNotNull null
            return@mapNotNull value to StringValue(value, true)
        } else if (match.groupValues[4].isNotEmpty() && supportedValueTypes.contains(RegexValue::class)) {
            val value = match.groupValues[4]
                .replace("\\\"", "\"")
                .replace("\\/", "/")

            val pattern = value.substring(1, value.length - 1)
            if (pattern.isEmpty()) return@mapNotNull null

            return@mapNotNull value to RegexValue(Regex(pattern))
        } else {
            return@mapNotNull match.value to null
        }
    }.toList()
}