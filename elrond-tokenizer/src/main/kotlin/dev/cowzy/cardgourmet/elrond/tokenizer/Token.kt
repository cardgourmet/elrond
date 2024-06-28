package dev.cowzy.cardgourmet.elrond.tokenizer

import dev.cowzy.cardgourmet.elrond.SearchQueryOperator

sealed class Token(val raw: String) {
    override fun toString(): String = this.raw
}

class OpenParenthesisToken(raw: String) : Token(raw)

class CloseParenthesisToken(raw: String) : Token(raw)

class OperatorToken(val value: SearchQueryOperator, val negate: Boolean, raw: String) : Token(raw)

sealed class ValueToken(raw: String) : Token(raw) {
    abstract fun isSimilarTo(other: ValueToken): Boolean
}

class NumberToken(val value: Number, raw: String = value.toString()) : ValueToken(raw) {
    override fun isSimilarTo(other: ValueToken) = other is NumberToken && other.value == value
}

class RegexToken(val value: Regex, raw: String) : ValueToken(raw) {
    override fun isSimilarTo(other: ValueToken) = other is RegexToken && other.value.pattern == value.pattern
}

open class StringToken(val value: String, raw: String = value) : ValueToken(raw) {
    override fun isSimilarTo(other: ValueToken) = other is StringToken && other.value == value
}

class QuotedStringToken(value: String, raw: String = value) : StringToken(value, raw)

class FilterToken(val filter: QueryTokenizerFilter, val keyword: String, raw: String) : ValueToken(raw) {
    override fun isSimilarTo(other: ValueToken) = other is FilterToken && other.filter == filter
}
