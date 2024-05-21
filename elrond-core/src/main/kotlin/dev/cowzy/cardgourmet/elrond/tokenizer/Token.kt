package dev.cowzy.cardgourmet.elrond.tokenizer

import dev.cowzy.cardgourmet.elrond.SearchQueryOperator

sealed class Token(val raw: String) {
    override fun toString(): String = this.raw
}

class OpenParenthesisToken(raw: String) : Token(raw)

class CloseParenthesisToken(raw: String) : Token(raw)

class OperatorToken(val value: SearchQueryOperator, raw: String) : Token(raw)

sealed class ValueToken(raw: String) : Token(raw)

class RegexToken(val value: Regex, raw: String) : ValueToken(raw)

open class StringToken(val value: String, raw: String = value) : ValueToken(raw)

class QuotedStringToken(value: String, raw: String = value) : StringToken(value, raw)
