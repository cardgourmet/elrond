package dev.cowzy.cardgourmet.elrond.tokenizer

import dev.cowzy.cardgourmet.elrond.SearchQueryOperator

fun String.tokenize(): List<Token> {
    val tokens = mutableListOf<Token>()
    var current = this.nextToken()

    while (current != null) {
        tokens.add(current.first)
        current = current.second.nextToken()
    }

    return tokens
}

fun String.nextToken(): Pair<Token, String>? {
    val cleaned = this.trim()

    val regex = Regex("""^(?:(-?\()|(\))|(:|!=|≠|=|>=|≥|>|<=|≤|<)|(\/(?:[^\/\\]|\\.)*\/)|(!?"(?:[^"\\]|\\.)*")|(!?'(?:[^'\\]|\\.)*')|(\d+\.\d+|\d+|\.\d+)(?=${'$'}|[\s()])|(-?!?[^\s=:><()≠≥≤!]+))""")
    val match = regex.find(cleaned) ?: return null

    val groups = match.groupValues
    val token = when {
        groups[1].isNotEmpty() -> OpenParenthesisToken(groups[1])
        groups[2].isNotEmpty() -> CloseParenthesisToken(groups[2])
        groups[3].isNotEmpty() -> {
            val isNotEquals = groups[3] == "≠" || groups[3] == "!="

            val operator = when {
                isNotEquals -> SearchQueryOperator.EQUALS
                else -> SearchQueryOperator.tryParse(groups[3]) ?: throw TokenizerException(groups[3], "invalid_operator", SearchQueryOperator.values().map { it.value })
            }

            OperatorToken(operator, isNotEquals, groups[3])
        }
        groups[4].isNotEmpty() -> RegexToken(Regex(groups[4].removeSurrounding("/")), groups[4])
        groups[5].isNotEmpty() -> QuotedStringToken(groups[5].removeExactAndNegateAndQuotes(), groups[5])
        groups[6].isNotEmpty() -> QuotedStringToken(groups[6].removeExactAndNegateAndQuotes(), groups[6])
        groups[7].isNotEmpty() -> NumberToken(groups[7].toDouble(), groups[7])
        else -> StringToken(groups[8].removeExactAndNegateAndQuotes(), groups[8])
    }

    return token to cleaned.substring(match.range.last + 1)
}

fun String.removeExactAndNegateAndQuotes(): String {
    var result = this
    if (result.startsWith("-")) result = result.substring(1)
    if (result.startsWith("!")) result = result.substring(1)

    result = when {
        result.startsWith("'") -> result.replace("\\'", "'")
        result.startsWith("\"") -> result.replace("\\\"", "\"")
        else -> result
    }

    return result.removeSurrounding("\"").removeSurrounding("'")
}
