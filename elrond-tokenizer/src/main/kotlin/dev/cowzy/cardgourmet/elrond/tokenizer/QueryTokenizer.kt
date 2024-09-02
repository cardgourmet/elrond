package dev.cowzy.cardgourmet.elrond.tokenizer

import dev.cowzy.cardgourmet.elrond.SearchQueryOperator
import dev.cowzy.cardgourmet.elrond.negated
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.full.isSuperclassOf

private val andRegex = Regex("(and|&+)", RegexOption.IGNORE_CASE)
private val orRegex = Regex("(or|\\|+)", RegexOption.IGNORE_CASE)
private val notRegex = Regex("(not|-)", RegexOption.IGNORE_CASE)

@Serializable
data class IgnoredQueryValue(
    val value: String,
    val reason: String,
    val supportedValues: List<String>? = null
)

@Serializable
enum class LogicalOperator {
    @SerialName("and") AND,
    @SerialName("or") OR
}

fun LogicalOperator.invert() = when (this) {
    LogicalOperator.AND -> LogicalOperator.OR
    LogicalOperator.OR -> LogicalOperator.AND
}

/**
 * @param keywords The keywords used to match the filter.
 * @param values The value types that can be used with the filter and the operators that can be used with each of them. Include [FilterToken] to allow referencing other filters.
 * @param referenceBy The keywords by which this filter can be referenced as a value for other filters (i.e. when comparing two filters to each other).
 */
data class QueryTokenizerFilter(
    val keywords: List<String>,
    val values: List<QueryTokenizerFilterValue>,
    val referenceBy: List<String> = emptyList()
)

data class QueryTokenizerFilterValue(
    val type: KClass<out ValueToken>,
    val operators: List<SearchQueryOperator>
)

data class TokenizedQuery(
    val query: QueryToken?,
    val ignored: List<IgnoredQueryValue>
)

class QueryTokenizer(
    private val filters: List<QueryTokenizerFilter>,
    private val fallbackFilter: QueryTokenizerFilter? = null,
    private val whitelistedValueTypes: Set<KClass<out ValueToken>> = emptySet()
) {

    fun tokenizeToQuery(query: String): TokenizedQuery {
        val ignored = mutableListOf<IgnoredQueryValue>()
        val token = query.tokenize().toQueryTokens(ignored::add).takeIf { it.isNotEmpty() }?.toGroup()

        val optimizedTokens = token?.flatten()?.filterDuplicatesAndNegatedPairs(ignored::add) ?: emptyList()
        val optimizedToken = when {
            optimizedTokens.size == 1 -> optimizedTokens.single()
            token is QueryTokenGroup -> optimizedTokens.toGroup(token.operator, token.negate)
            else -> QueryTokenGroup(optimizedTokens, LogicalOperator.AND, false)
        }

        return TokenizedQuery(optimizedToken, ignored)
    }

    private fun List<Token>.toQueryTokens(ignoreValue: (IgnoredQueryValue) -> Unit): List<QueryToken> {
        val tokenQueue = LinkedList(this)
        if (tokenQueue.isEmpty()) return emptyList()

        val parsedTokens = mutableListOf<QueryToken>()

        var negateNext = false

        while (tokenQueue.isNotEmpty()) {
            val token = tokenQueue.peek()

            if (token is OpenParenthesisToken) {
                tokenQueue.poll()

                if (token.raw.startsWith("-")) {
                    parsedTokens.add(tokenQueue.parseTokenGroup(ignoreValue).toGroup(negate = !negateNext))
                } else if (negateNext) {
                    parsedTokens.add(tokenQueue.parseTokenGroup(ignoreValue).toGroup(negate = true))
                } else {
                    parsedTokens.addAll(tokenQueue.parseTokenGroup(ignoreValue))
                }

                negateNext = false
                continue
            } else if (token is StringToken) {
                when {
                    andRegex.matches(token.value) -> {
                        tokenQueue.poll()
                        negateNext = false
                        continue
                    }

                    orRegex.matches(token.value) -> {
                        tokenQueue.poll()
                        val group = (parsedTokens.toGroup() + tokenQueue.toQueryTokens(ignoreValue)).toGroup(LogicalOperator.OR, negateNext)
                        return listOf(group)
                    }

                    notRegex.matches(token.value) -> {
                        tokenQueue.poll()
                        negateNext = true
                        continue
                    }
                }
            }

            tokenQueue.parseFilter(ignoreValue)?.let {
                parsedTokens.add(it.let { if (negateNext) it.negated() else it })
            }

            negateNext = false
        }

        return parsedTokens
    }

    private fun List<QueryToken>.toGroup(operator: LogicalOperator = LogicalOperator.AND, negate: Boolean = false): QueryToken {
        val children = this.filter { it !is QueryTokenGroup || it.children.isNotEmpty() }

        if (children.size == 1) {
            val token = this.first()
            if (!negate) return token

            return when (token) {
                is QueryTokenGroup -> token.copy(negate = !token.negate)
                is QueryFilterToken -> token.copy(negate = !token.negate)
            }
        }

        val newChildren = mutableListOf<QueryToken>()

        children.forEach {
            if (it is QueryTokenGroup && it.operator == operator && !it.negate) {
                newChildren.addAll(it.children)
            } else {
                newChildren.add(it)
            }
        }

        return QueryTokenGroup(newChildren, operator, negate)
    }

    private fun Queue<Token>.parseFilter(ignoreValue: (IgnoredQueryValue) -> Unit): QueryToken? {
        val first = this.poll()

        var stringValue: String? = null
        var negate = false
        var exactValue = false

        if (first is StringToken) {
            var rawValue = first.raw
            negate = rawValue.startsWith("-")
            if (negate) rawValue = rawValue.substring(1)
            exactValue = rawValue.startsWith("!")

            stringValue = first.raw.removeExactAndNegateAndQuotes()

            if (this.peek() is OperatorToken) {
                val operatorToken = this.poll() as OperatorToken
                val operator = operatorToken.value
                val second = this.poll()

                val raw = "${first.raw}${operatorToken.raw}${second?.raw ?: ""}"

                if (operatorToken.negate) {
                    negate = !negate
                }

                val staticFilter = filters.find { it.keywords.any { keyword -> keyword.equals(raw, true) } }
                if (staticFilter != null) {
                    return QueryFilterToken(staticFilter, stringValue, operatorToken.value, StringToken(second.raw), negate, raw)
                }

                val filter = filters.find { it.keywords.any { keyword -> keyword.equals(stringValue, true) } }
                if (filter == null) {
                    ignoreValue(IgnoredQueryValue(raw, "unknown_filter"))
                    return null
                }

                val supportedOperators = filter.values.flatMap { it.operators }.distinct()
                if (!supportedOperators.contains(operator)) {
                    ignoreValue(IgnoredQueryValue(raw, "invalid_operator", supportedOperators.map { it.value }))
                    return null
                }

                val mappedOperator = when {
                    operator == SearchQueryOperator.CONTAINS && exactValue -> SearchQueryOperator.EQUALS
                    else -> operator
                }

                fun isSupported(type: KClass<out Token>): Boolean {
                    val isWhitelisted = whitelistedValueTypes.isEmpty() || whitelistedValueTypes.any { it.isSuperclassOf(type) }
                    val entry = filter.values.find { it.type.isSuperclassOf(type) } ?: return false
                    return isWhitelisted && entry.operators.contains(mappedOperator)
                }

                return when (second) {
                    is NumberToken -> {
                        if (isSupported(second::class)) {
                            QueryFilterToken(filter, stringValue, mappedOperator, second, negate, raw)
                        } else if (isSupported(StringToken::class)) {
                            QueryFilterToken(filter, stringValue, mappedOperator, StringToken(second.raw), negate, raw)
                        } else {
                            ignoreValue(IgnoredQueryValue(raw, "unsupported_value"))
                            return null
                        }
                    }

                    is RegexToken -> {
                        if (isSupported(second::class)) {
                            QueryFilterToken(filter, stringValue, mappedOperator, second, negate, raw)
                        } else {
                            ignoreValue(IgnoredQueryValue(raw, "unsupported_value"))
                            return null
                        }
                    }

                    is StringToken -> {
                        when {
                            second !is QuotedStringToken -> {
                                second.value.split(",")
                                    .mapNotNull {
                                        val matchingFilters = filters
                                            .filter { filter -> filter.values.map { value -> value.type }.any(::isSupported) }
                                            .filter { filter -> filter.referenceBy.any { keyword -> keyword.equals(it, true) } }

                                        return@mapNotNull  when {
                                            matchingFilters.size == 1 && matchingFilters.first() == filter -> {
                                                ignoreValue(IgnoredQueryValue("${first.raw}${operatorToken.raw}$it", "self_reference"))
                                                null
                                            }

                                            matchingFilters.isNotEmpty() && isSupported(FilterToken::class) -> FilterToken(matchingFilters.first(), it, it)

                                            isSupported(StringToken::class) -> StringToken(it)

                                            else -> {
                                                ignoreValue(IgnoredQueryValue("${first.raw}${operatorToken.raw}$it", "unsupported_value"))
                                                null
                                            }
                                        }
                                    }
                                    .map { QueryFilterToken(filter, stringValue, mappedOperator, it, false, "${first.raw}${operatorToken.raw}$it") }
                                    .toGroup(LogicalOperator.AND, negate)
                            }

                            isSupported(second::class) -> QueryFilterToken(filter, stringValue, mappedOperator, second, negate, raw)

                            else -> {
                                ignoreValue(IgnoredQueryValue(raw, "unsupported_value"))
                                return null
                            }
                        }
                    }

                    null -> {
                        ignoreValue(IgnoredQueryValue(raw, "missing_value"))
                        return null
                    }

                    else -> QueryFilterToken(filter, stringValue, mappedOperator, StringToken(second.raw), negate, raw)
                }
            }
        }

        if (fallbackFilter == null) {
            ignoreValue(IgnoredQueryValue(first.raw, "unknown_filter"))
            return null
        }

        fun isSupported(type: KClass<out Token>): Boolean {
            val isWhitelisted = whitelistedValueTypes.isEmpty() || whitelistedValueTypes.any { it.isSuperclassOf(type) }
            return isWhitelisted && fallbackFilter.values.any { it.type.isSuperclassOf(type) }
        }

        fun getOperator(type: KClass<out Token>): SearchQueryOperator {
            val entry = fallbackFilter.values.first { it.type.isSuperclassOf(type) }
            return when {
                exactValue && entry.operators.contains(SearchQueryOperator.EQUALS) -> SearchQueryOperator.EQUALS
                else -> entry.operators.firstOrNull()
            } ?: throw IllegalArgumentException("No operator found for type ${type.simpleName} in filter ${fallbackFilter.keywords.first()}.")
        }

        return when {
            first is StringToken && isSupported(StringToken::class) -> QueryFilterToken(fallbackFilter, null, getOperator(StringToken::class), StringToken(stringValue!!), negate, first.raw)
            first is ValueToken && isSupported(first::class) -> QueryFilterToken(fallbackFilter, null, getOperator(first::class), first, negate = false, first.raw)
            first == null -> return null

            first is ValueToken && !isSupported(first::class) -> {
                ignoreValue(IgnoredQueryValue(first.raw, "unsupported_value"))
                null
            }

            else -> {
                ignoreValue(IgnoredQueryValue(first.raw, "unknown_filter"))
                return null
            }
        }
    }

    private fun Queue<Token>.parseTokenGroup(ignoreValue: (IgnoredQueryValue) -> Unit): List<QueryToken> {
        if (this.isEmpty()) return emptyList()

        val tokensWithinGroup = mutableListOf<Token>()
        var openingParenthesis = 0

        while (this.isNotEmpty()) {
            val token = this.poll()
            if (token is OpenParenthesisToken) {
                openingParenthesis++
            } else if (token is CloseParenthesisToken) {
                if (openingParenthesis == 0) {
                    return tokensWithinGroup.toQueryTokens(ignoreValue)
                }
                openingParenthesis--
            }
            tokensWithinGroup.add(token)
        }

        return tokensWithinGroup.toQueryTokens(ignoreValue)
    }

    private fun List<QueryToken>.filterDuplicatesAndNegatedPairs(ignoreValue: (IgnoredQueryValue) -> Unit): List<QueryToken> {
        val mutableTokens = this.toMutableList()

        do {
            var changed = false

            val tokens = mutableTokens.toList()
            for (token in tokens) {
                if (token !is QueryFilterToken) continue

                val duplicateToken = tokens
                    .asSequence()
                    .filterIsInstance<QueryFilterToken>()
                    .filter { token != it }
                    .filter { it.negate == token.negate && it.operator == token.operator }
                    .filter { it::class == token::class }
                    .find { it.filter == token.filter && it.operator == token.operator && it.value.isSimilarTo(token.value) }

                if (duplicateToken != null) {
                    mutableTokens.remove(duplicateToken)

                    ignoreValue(IgnoredQueryValue(
                        duplicateToken.raw,
                        "duplicate_filter"
                    ))

                    changed = true
                    break
                }

                val negatedToken = tokens
                    .asSequence()
                    .filterIsInstance<QueryFilterToken>()
                    .filter { token != it }
                    .filter { (it.negate != token.negate && it.operator == token.operator) || (it.negate == token.negate && it.operator.negated() == token.operator) }
                    .filter { it::class == token::class }
                    .find { it.filter == token.filter && it.operator == token.operator && it.value.isSimilarTo(token.value) }

                if (negatedToken != null) {
                    mutableTokens.remove(token)
                    mutableTokens.remove(negatedToken)

                    ignoreValue(IgnoredQueryValue(
                        "${token.raw} ${negatedToken.raw}",
                        "negated_filters"
                    ))

                    changed = true
                    break
                }
            }
        } while (changed)

        return mutableTokens
    }

    private fun QueryToken.flatten(): List<QueryToken> {
        if (this !is QueryTokenGroup) return listOf(this)

        val expressions = this.children.map { it.flatten() }

        return when {
            operator == LogicalOperator.AND && !negate -> expressions.flatten()
            operator == LogicalOperator.OR && negate -> expressions.flatten().map { it.negated() }
            else -> listOf(
                QueryTokenGroup(
                    expressions.mapNotNull {
                        when (it.size) {
                            0 -> null
                            1 -> it.single()
                            else -> QueryTokenGroup(it, LogicalOperator.AND, false)
                        }
                    },
                    operator,
                    negate
                )
            )
        }
    }

}
