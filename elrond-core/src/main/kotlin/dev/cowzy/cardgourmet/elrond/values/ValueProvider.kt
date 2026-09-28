package dev.cowzy.cardgourmet.elrond.values

import dev.cowzy.cardgourmet.elrond.ContextAttributes

abstract class ValueProvider<T : Any>(val strictValues: Boolean) {

    abstract suspend fun getValues(attributes: ContextAttributes, language: String? = null): Iterable<ProvidedValue<T>>

    open suspend fun getValues(attributes: ContextAttributes, filter: String, language: String?): Iterable<ProvidedValue<T>> {
        return getValues(attributes)
            .filter { language == null || it.languages.isEmpty() || it.languages.contains(language) }
            .filter { it.input.contains(filter, ignoreCase = true) || it.aliases.any { alias -> alias.contains(filter, ignoreCase = true) } }
    }

    abstract suspend fun findValue(attributes: ContextAttributes, value: String): ProvidedValue<T>?

}