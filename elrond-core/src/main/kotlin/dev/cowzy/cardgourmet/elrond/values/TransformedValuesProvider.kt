package dev.cowzy.cardgourmet.elrond.values

import dev.cowzy.cardgourmet.elrond.ContextAttributes

class TransformedValuesProvider<Input : Any, Output : Any>(
    private val baseProvider: ValueProvider<Input>,
    private val transform: (Input) -> Output
) : ValueProvider<Output>(baseProvider.strictValues) {

    override suspend fun getValues(
        attributes: ContextAttributes,
        language: String?
    ) = baseProvider.getValues(attributes, language = language).map { transform(it) }

    override suspend fun findValue(
        attributes: ContextAttributes,
        value: String
    ) = baseProvider.findValue(attributes, value = value)?.let { transform(it) }

    private fun transform(it: ProvidedValue<Input>): ProvidedValue<Output> {
        return ProvidedValue(
            input = it.input,
            aliases = it.aliases,
            type = it.type,
            resolvesTo = ResolvedValue(
                value = transform(it.resolvesTo.value),
                display = it.resolvesTo.display,
                operator = it.resolvesTo.operator
            ),
            languages = it.languages
        )
    }

}

fun <Input : Any, Output : Any> ValueProvider<Input>.withTransform(transform: (Input) -> Output): ValueProvider<Output> {
    return TransformedValuesProvider(this, transform)
}