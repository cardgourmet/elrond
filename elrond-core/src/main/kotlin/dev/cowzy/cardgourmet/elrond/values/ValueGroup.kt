package dev.cowzy.cardgourmet.elrond.values

import dev.cowzy.cardgourmet.elrond.SearchQueryOperator
import java.util.*

data class ValueKey<T : Any>(
    val type: String,
    val value: T,
    val operator: SearchQueryOperator?,
    val uniqueifyer: String?
) {
    constructor(value: ProvidedValue<T>, uniqueifyer: String?) : this(
        value.type,
        value.resolvesTo.value,
        value.resolvesTo.operator,
        uniqueifyer
    )
}

class ValueGroup<T : Any>(values: Iterable<ProvidedValue<T>> = emptySet()) {

    private val values = mutableListOf<ProvidedValue<T>>()
    private val valuesByKey = mutableMapOf<ValueKey<T>, ProvidedValue<T>>()

    init {
        values.forEach(this::add)
    }

    fun add(value: ProvidedValue<T>, unique: Boolean = false) {
        value.aliases.removeIf { it.equals(value.input, true) }
        values.add(value)
        valuesByKey[ValueKey(value, uniqueifyer = if (unique) UUID.randomUUID().toString() else null)] = value
    }

    fun addOrUpdate(
        input: String,
        value: T,
        valueDisplay: String,
        operator: SearchQueryOperator?,
        type: String,
        autoAlias: Boolean,
        merge: Boolean = true
    ) {
        val aliases = mutableSetOf<String>()

        if (autoAlias) {
            aliases.add(input.replace(Regex("\\P{L}"), "").lowercase())
            aliases.add(valueDisplay.replace(Regex("\\P{L}"), "").lowercase())
        }

        aliases.removeIf {
            it.equals(input, true) || it.equals(valueDisplay, true)
        }

        val providedValue = ProvidedValue(
            input = input.lowercase(),
            resolvesTo = ResolvedValue(
                display = valueDisplay,
                value = value,
                operator = operator
            ),
            aliases = aliases,
            type = type
        )

        val existingValue = find(providedValue)
        if (merge && existingValue != null) {
            if (input != existingValue.input) {
                existingValue.aliases.add(input)
            }

            existingValue.aliases.addAll(providedValue.aliases.filter { alias ->
                !existingValue.aliases.any { it.equals(alias, true) }
            })
            return
        }

        add(providedValue, !merge)
    }

    fun getValues(): Iterable<ProvidedValue<T>> = values.toList()

    fun find(value: ProvidedValue<T>) = valuesByKey[ValueKey(value, null)]

}