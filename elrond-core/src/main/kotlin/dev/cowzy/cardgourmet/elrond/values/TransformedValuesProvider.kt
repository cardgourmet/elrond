package dev.cowzy.cardgourmet.elrond.values

class TransformedValuesProvider<Input : Any, Output : Any, PrincipalType : Any>(
    private val baseProvider: ValueProvider<Input, PrincipalType>,
    private val transform: (Input) -> Output
) : ValueProvider<Output, PrincipalType>(baseProvider.strictValues) {

    override suspend fun getValues(
        principal: PrincipalType?,
        language: String?
    ) = baseProvider.getValues(principal = null, language = language).map { transform(it) }

    override suspend fun findValue(
        principal: PrincipalType?,
        value: String
    ) = baseProvider.findValue(principal = null, value = value)?.let { transform(it) }

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

fun <Input : Any, Output : Any, PrincipalType : Any> ValueProvider<Input, PrincipalType>.withTransform(transform: (Input) -> Output): ValueProvider<Output, PrincipalType> {
    return TransformedValuesProvider(this, transform)
}