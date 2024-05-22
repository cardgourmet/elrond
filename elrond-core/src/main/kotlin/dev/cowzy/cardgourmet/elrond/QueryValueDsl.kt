package dev.cowzy.cardgourmet.elrond

import dev.cowzy.cardgourmet.commons.i18n.LocalizationService
import dev.cowzy.cardgourmet.commons.i18n.UserLanguage
import dev.cowzy.cardgourmet.elrond.values.ValueProvider
import dev.cowzy.cardgourmet.elrond.values.ValueProviderBuilder
import dev.cowzy.cardgourmet.elrond.values.ValueProviderPool
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

class QueryValueDefinition<Output : Any>(init: QueryValueDefinition<Output>.() -> Unit = {}) {

    private val mappings = mutableMapOf<KClass<out QueryValue<*>>, QueryValueMapping<*, out QueryValue<*>, Output>>()

    var provider: ValueProvider<Output>? = null

    var display: suspend (Output, LocalizationService, UserLanguage) -> String = { it, _, _ ->
        when (it) {
            is Number -> "`${if (it.toDouble() % 1.0 == 0.0) it.toInt().toString() else it.toDouble()}`"
            is NumberValue -> "`${if (it.value.toDouble() % 1.0 == 0.0) it.value.toInt().toString() else it.value.toDouble()}`"
            is RegexValue -> "`${it.value.pattern.replace("/", "\\/")}`"
            is StringValue -> "\"${it.value}\""
            else -> "`$it`"
        }
    }

    val supportedValueTypes get() = mappings.keys

    init {
        init.invoke(this)
    }

    fun provider(provider: ValueProvider<Output>?) {
        this.provider = provider
    }

    fun provider(key: String, pool: ValueProviderPool, init: ValueProviderBuilder<Output>.() -> Unit) {
        this.provider = pool.getOrPut(key) { ValueProviderBuilder<Output>(it).apply(init).build() }
    }

    fun provider(column: KProperty1<*, *>, pool: ValueProviderPool, init: ValueProviderBuilder<Output>.() -> Unit) {
        this.provider = pool.getOrPut(column) { ValueProviderBuilder<Output>(it).apply(init).build() }
    }

    fun provider(vararg columns: KProperty1<*, *>, pool: ValueProviderPool, init: ValueProviderBuilder<Output>.() -> Unit) {
        this.provider = pool.getOrPut(columns = columns) { ValueProviderBuilder<Output>(it).apply(init).build() }
    }

    fun display(transform: suspend (Output, LocalizationService, UserLanguage) -> String) {
        this.display = transform
    }

    operator fun <Value : Any, Input : QueryValue<Value>> KClass<Input>.invoke(init: QueryValueMappingBuilder<Value, Input, Output>.() -> Unit) {
        val builder = QueryValueMappingBuilder<Value, Input, Output>().apply(init)
        mappings[this] = builder.build()
    }

    operator fun <Value : Any, Input : QueryValue<Value>> KClass<Input>.invoke() = invoke { }

    @Suppress("UNCHECKED_CAST")
    fun <Input : QueryValue<*>> getDefinition(type: KClass<Input>): QueryValueMapping<*, Input, Output> {
        return mappings[type]!! as QueryValueMapping<*, Input, Output>
    }

}

data class QueryValueMapping<Value : Any, Input : QueryValue<Value>, Output>(
    val transform: suspend (Input, SearchQueryOperator) -> Pair<Output, SearchQueryOperator>?,
    val match: suspend (Output) -> Boolean,
    val format: String?
)

class QueryValueMappingBuilder<Value : Any, Input : QueryValue<Value>, Output : Any> {

    var format: String? = null
    var pattern: String? = null

    private var transform: suspend (Input, SearchQueryOperator) -> Pair<Output, SearchQueryOperator>? = { it, operator ->
        try {
            @Suppress("UNCHECKED_CAST")
            (it.value as Output) to operator
        }  catch (e: ClassCastException) {
            null
        }
    }

    private var match: suspend (Output) -> Boolean = { true }

    fun transform(transform: suspend (Input) -> Output?) {
        this.transformWithOperator { input, operator -> transform(input)?.let { it to operator } }
    }

    fun transformWithOperator(transform: suspend (Input, SearchQueryOperator) -> Pair<Output, SearchQueryOperator>?) {
        this.transform = transform
    }

    fun match(predicate: suspend (Output) -> Boolean) {
        this.match = predicate
    }

    internal fun build() = QueryValueMapping(
        transform = transform,
        match = match,
        format = format
    )

}

fun <T : Enum<T>> enumToMappings(enumValues: Array<T>, findKeywords: ((T) -> List<String>)? = null): Map<String, T> {
    return enumValues.map { value ->
        ((findKeywords?.invoke(value) ?: emptyList()) + value.name.lowercase()).map {
            it.lowercase()
        }.map { keyword ->
            when {
                keyword.contains("_") -> listOf(keyword.replace("_", "") to value, keyword to value)
                else -> listOf(keyword to value)
            }
        }.flatten()
    }.flatten().distinctBy { it.first.lowercase() }.toMap()
}
