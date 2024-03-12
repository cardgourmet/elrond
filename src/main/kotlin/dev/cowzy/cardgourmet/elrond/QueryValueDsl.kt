package dev.cowzy.cardgourmet.elrond

import dev.cowzy.cardgourmet.commons.i18n.LocalizationService
import dev.cowzy.cardgourmet.commons.i18n.UserLanguage
import kotlin.reflect.KClass

class QueryValueDefinition<Output : Any>(init: QueryValueDefinition<Output>.() -> Unit = {}) {

    private val mappings = mutableMapOf<KClass<out QueryValue<*>>, QueryValueMapping<out QueryValue<*>, Output>>()

    val supportedValueTypes get() = mappings.keys

    init {
        init.invoke(this)
    }

    operator fun <Input, T : QueryValue<Input>> KClass<T>.invoke(init: QueryValueMappingBuilder<T, Output>.() -> Unit) {
        val builder = QueryValueMappingBuilder<T, Output>(this).apply(init)
        mappings[this] = builder.build()
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : QueryValue<*>> getMapping(type: KClass<T>): QueryValueMapping<T, Output> {
        return mappings[type]!! as QueryValueMapping<T, Output>
    }

}

data class QueryValueMapping<Input : QueryValue<*>, Output>(
    val transform: suspend (Input, SearchQueryOperator) -> Pair<Output, SearchQueryOperator>?,
    val match: suspend (Output) -> Boolean,
    val display: suspend (Output, LocalizationService, UserLanguage) -> String
)

class QueryValueMappingBuilder<Input : QueryValue<*>, Output : Any>(private val type: KClass<Input>) {

    private var transform: suspend (Input, SearchQueryOperator) -> Pair<Output, SearchQueryOperator>? = { it, operator ->
        try {
            @Suppress("UNCHECKED_CAST")
            (it as Output) to operator
        }  catch (e: ClassCastException) {
            null
        }
    }

    private var match: suspend (Output) -> Boolean = { true }

    private var display: suspend (Output, LocalizationService, UserLanguage) -> String = { it, _, _ ->
        when (it) {
            is Number -> "`${if (it.toDouble() % 1.0 == 0.0) it.toInt().toString() else it.toDouble()}`"
            is NumberValue -> "`${if (it.value.toDouble() % 1.0 == 0.0) it.value.toInt().toString() else it.value.toDouble()}`"
            is RegexValue -> "`${it.value.pattern.replace("/", "\\/")}`"
            is StringValue -> "\"${it.value}\""
            else -> "`$it`"
        }
    }

    fun transform(transform: suspend (Input) -> Output?) {
        this.transformWithOperator { input, operator -> transform(input)?.let { it to operator } }
    }

    fun transformWithOperator(transform: suspend (Input, SearchQueryOperator) -> Pair<Output, SearchQueryOperator>?) {
        this.transform = transform
    }

    fun match(predicate: suspend (Output) -> Boolean) {
        this.match = predicate
    }

    fun display(transform: suspend (Output, LocalizationService, UserLanguage) -> String) {
        this.display = transform
    }

    internal fun build() = QueryValueMapping(
        transform = transform,
        match = match,
        display = display
    )

}
