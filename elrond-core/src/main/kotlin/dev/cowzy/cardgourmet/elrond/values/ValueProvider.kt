package dev.cowzy.cardgourmet.elrond.values

abstract class ValueProvider<T : Any, PrincipalType : Any>(val strictValues: Boolean) {

    abstract suspend fun getValues(principal: PrincipalType?, language: String? = null): Iterable<ProvidedValue<T>>

    open suspend fun getValues(principal: PrincipalType?, filter: String, language: String?): Iterable<ProvidedValue<T>> {
        return getValues(principal)
            .filter { language == null || it.languages.isEmpty() || it.languages.contains(language) }
            .filter { it.input.contains(filter, ignoreCase = true) || it.aliases.any { alias -> alias.contains(filter, ignoreCase = true) } }
    }

    abstract suspend fun findValue(principal: PrincipalType?, value: String): ProvidedValue<T>?

}