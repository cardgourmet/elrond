package dev.cowzy.cardgourmet.elrond.tokenizer

import dev.cowzy.cardgourmet.elrond.query.LogicalOperator
import java.util.*

private val andRegex = Regex("(and|&+)", RegexOption.IGNORE_CASE)
private val orRegex = Regex("(or|\\|+)", RegexOption.IGNORE_CASE)
private val notRegex = Regex("(not|-)", RegexOption.IGNORE_CASE)

fun String.tokenizeToQuery(strict: Boolean): QueryToken? {
    val tokens = this.tokenize().toQueryTokens(strict)
    if (tokens.isEmpty()) return null
    return tokens.toGroup()
}

fun List<Token>.toQueryTokens(strict: Boolean): List<QueryToken> {
    val tokens = LinkedList(this)
    if (tokens.isEmpty()) return emptyList()

    val parsedTokens = mutableListOf<QueryToken>()

    var negateNext = false

    while (tokens.isNotEmpty()) {
        val token = tokens.peek()

        if (token is OpenParenthesisToken) {
            tokens.poll()

            if (token.raw.startsWith("-")) {
                parsedTokens.add(tokens.parseTokenGroup(strict).toGroup(negate = !negateNext))
            } else if (negateNext) {
                parsedTokens.add(tokens.parseTokenGroup(strict).toGroup(negate = true))
            } else {
                parsedTokens.addAll(tokens.parseTokenGroup(strict))
            }

            negateNext = false
            continue
        } else if (token is StringToken) {
            when {
                andRegex.matches(token.value) -> {
                    tokens.poll()
                    negateNext = false
                    continue
                }

                orRegex.matches(token.value) -> {
                    tokens.poll()
                    val group =
                        (parsedTokens.toGroup() + tokens.toQueryTokens(strict)).toGroup(LogicalOperator.OR, negateNext)
                    return listOf(group)
                }

                notRegex.matches(token.value) -> {
                    tokens.poll()
                    negateNext = true
                    continue
                }
            }
        }

        if (negateNext) {
            val filterToken = tokens.parseFilter(strict)
            parsedTokens.add(filterToken.copy(negate = !filterToken.negate))
        } else {
            parsedTokens.add(tokens.parseFilter(strict))
        }

        negateNext = false
    }

    return parsedTokens
}

fun List<QueryToken>.toGroup(operator: LogicalOperator = LogicalOperator.AND, negate: Boolean = false): QueryToken {
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

fun Queue<Token>.parseFilter(strict: Boolean): QueryFilterToken {
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
            val operator = (this.poll() as OperatorToken).value
            val second = this.poll()

            if (strict && second is QuotedStringToken && (!second.raw.startsWith("\"") && !second.raw.startsWith("'"))) {
                throw TokenizerException("Unexpected token: ${second.raw}")
            }

            return when {
                second is ValueToken -> QueryFilterToken(stringValue, operator, second, exactValue, negate)
                strict -> throw TokenizerException("Unexpected token: ${second.raw}")
                else -> QueryFilterToken(stringValue, operator, StringToken(second.raw), exactValue, negate)
            }
        }
    }

    return when {
        first is StringToken -> QueryFilterToken(null, null, StringToken(stringValue!!), exactValue, negate)
        first is RegexToken -> QueryFilterToken(null, null, first, exactValue = false, negate = false)
        strict -> throw TokenizerException("Unexpected token: ${first.raw}")
        else -> QueryFilterToken(null, null, StringToken(first.raw), exactValue = false, negate = false)
    }
}

fun Queue<Token>.parseTokenGroup(strict: Boolean): List<QueryToken> {
    if (this.isEmpty()) return emptyList()

    val tokensWithinGroup = mutableListOf<Token>()
    var openingParenthesis = 0

    while (this.isNotEmpty()) {
        val token = this.poll()
        if (token is OpenParenthesisToken) {
            openingParenthesis++
        } else if (token is CloseParenthesisToken) {
            if (openingParenthesis == 0) {
                return tokensWithinGroup.toQueryTokens(strict)
            }
            openingParenthesis--
        }
        tokensWithinGroup.add(token)
    }

    when {
        strict -> throw TokenizerException("Unexpected end of group.")
        else -> return tokensWithinGroup.toQueryTokens(false)
    }
}

operator fun QueryToken.plus(other: List<QueryToken>) = listOf(this) + other
