package dev.cowzy.cardgourmet.elrond.values

import dev.cowzy.cardgourmet.elrond.SearchQueryOperator

class ValueGroup<T : Any>(values: Iterable<ProvidedValue<T>> = emptySet()) {

    private val values = mutableListOf<ProvidedValue<T>>()
    private val valuesByTypeAndValue = mutableMapOf<Pair<String, T>, ProvidedValue<T>>()

    init {
        values.forEach(this::add)
    }

    fun add(value: ProvidedValue<T>) {
        values.add(value)
        valuesByTypeAndValue[value.type to value.resolvesTo.value] = value
    }

    fun addOrUpdate(
        input: String,
        value: T,
        valueDisplay: String,
        operator: SearchQueryOperator?,
        type: String,
        autoAlias: Boolean
    ) {
        val alias = if (autoAlias) input.replace(Regex("\\W"), "") else null

        val providedValue = ProvidedValue(
            input = input,
            resolvesTo = ResolvedValue(
                display = valueDisplay,
                value = value,
                operator = operator
            ),
            aliases = alias?.let { mutableSetOf(alias) } ?: mutableSetOf(),
            type = type
        )

        val existingValue = find(type, value)
        if (existingValue != null) {
            existingValue.aliases.add(input)
            existingValue.aliases.addAll(providedValue.aliases)
            return
        }

        add(providedValue)
    }

    fun getValues(): Iterable<ProvidedValue<T>> = values.toList()

    fun find(type: String, value: T) = valuesByTypeAndValue[type to value]

}