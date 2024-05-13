package dev.cowzy.cardgourmet.elrond.values

import dev.cowzy.cardgourmet.elrond.SearchQueryOperator

data class ValueKey<T : Any>(
    val type: String,
    val value: T,
    val operator: SearchQueryOperator?
) {
    constructor(value: ProvidedValue<T>) : this(value.type, value.resolvesTo.value, value.resolvesTo.operator)
}

class ValueGroup<T : Any>(values: Iterable<ProvidedValue<T>> = emptySet()) {

    private val values = mutableListOf<ProvidedValue<T>>()
    private val valuesByKey = mutableMapOf<ValueKey<T>, ProvidedValue<T>>()

    init {
        values.forEach(this::add)
    }

    fun add(value: ProvidedValue<T>) {
        value.aliases.removeIf { it.equals(value.input, true) }
        values.add(value)
        valuesByKey[ValueKey(value)] = value
    }

    fun addOrUpdate(
        input: String,
        value: T,
        valueDisplay: String,
        operator: SearchQueryOperator?,
        type: String,
        autoAlias: Boolean
    ) {
        val aliases = mutableSetOf(input, valueDisplay)

        if (autoAlias) {
            aliases.add(input.replace(Regex("\\P{L}"), ""))
            aliases.add(valueDisplay.replace(Regex("\\P{L}"), ""))
        }

        val providedValue = ProvidedValue(
            input = input,
            resolvesTo = ResolvedValue(
                display = valueDisplay,
                value = value,
                operator = operator
            ),
            aliases = aliases,
            type = type
        )

        val existingValue = find(providedValue)
        if (existingValue != null) {
            existingValue.aliases.add(input)
            existingValue.aliases.addAll(providedValue.aliases)
            return
        }

        add(providedValue)
    }

    fun getValues(): Iterable<ProvidedValue<T>> = values.toList()

    fun find(value: ProvidedValue<T>) = valuesByKey[ValueKey(value)]

}