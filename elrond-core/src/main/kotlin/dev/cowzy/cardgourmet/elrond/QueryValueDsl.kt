package dev.cowzy.cardgourmet.elrond

import dev.cowzy.cardgourmet.commons.i18n.LocalizationService
import dev.cowzy.cardgourmet.commons.i18n.UserLanguage
import dev.cowzy.cardgourmet.elrond.values.MappingProvider
import dev.cowzy.cardgourmet.elrond.values.StaticValueProvider
import dev.cowzy.cardgourmet.elrond.values.ValueProvider
import kotlin.reflect.KClass

class QueryValueDefinition<Output : Any>(init: QueryValueDefinition<Output>.() -> Unit = {}) {

    private val mappings = mutableMapOf<KClass<out QueryValue<*>>, QueryValueMapping<*, out QueryValue<*>, Output>>()

    val supportedValueTypes get() = mappings.keys

    init {
        init.invoke(this)
    }

    operator fun <Value : Any, Input : QueryValue<Value>> KClass<Input>.invoke(init: QueryValueMappingBuilder<Value, Input, Output>.() -> Unit) {
        val builder = QueryValueMappingBuilder<Value, Input, Output>(this).apply(init)
        mappings[this] = builder.build()
    }

    @Suppress("UNCHECKED_CAST")
    fun <Input : QueryValue<*>> getDefinition(type: KClass<Input>): QueryValueMapping<*, Input, Output> {
        return mappings[type]!! as QueryValueMapping<*, Input, Output>
    }

}

data class QueryValueMapping<Value : Any, Input : QueryValue<Value>, Output>(
    val transform: suspend (Input, SearchQueryOperator) -> Pair<Output, SearchQueryOperator>?,
    val match: suspend (Output) -> Boolean,
    val display: suspend (Output, LocalizationService, UserLanguage) -> String,
    val format: String?,
    val valueProvider: ValueProvider<Value>?,
    val useStrictValues: Boolean,
    val mappingsProvider: ValueProvider<Pair<Value, Pair<Output, SearchQueryOperator?>>>?,
)

class QueryValueMappingBuilder<Value : Any, Input : QueryValue<Value>, Output : Any>(private val type: KClass<Input>) {

    var format: String? = null
    var pattern: String? = null

    var mappingsProvider: MappingProvider<Value, Output>? = null
    var valueProvider: ValueProvider<Value>? = null
    var useStrictValues: Boolean = false

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

    fun values(values: Iterable<Value>, strict: Boolean = true) = values(StaticValueProvider(values.toSet()), strict)

    fun values(valueProvider: ValueProvider<Value>?, strict: Boolean = true) {
        this.valueProvider = valueProvider
        this.useStrictValues = valueProvider != null && strict
    }

    fun mappings(mappings: Map<Value, Output>?) {
        if (mappings == null) return
        mappings(StaticValueProvider(mappings.entries.map { it.key to it.value }.toSet()))
    }

    fun mappings(mappings: Iterable<Pair<Value, Output>>?) {
        if (mappings == null) return
        mappings(StaticValueProvider(mappings.toSet()))
    }

    fun mappings(mappingsProvider: ValueProvider<Pair<Value, Output>>?) {
        if (mappingsProvider == null) return
        mappingsWithOperator(WrapOperatorValueProvider(mappingsProvider))
    }

    fun mappingsWithOperator(mappings: Map<Value, Pair<Output, SearchQueryOperator?>>?) {
        if (mappings == null) return
        this.mappingsProvider = StaticValueProvider(mappings.entries.map { it.toPair() }.toSet())
    }

    fun mappingsWithOperator(mappingsProvider: MappingProvider<Value, Output>?) {
        if (mappingsProvider == null) return
        this.mappingsProvider = mappingsProvider
    }

    fun <T> mappingsWithOperator(mappingsProvider: MappingProvider<Value, T>?, transform: (T) -> Output) {
        if (mappingsProvider == null) return
        this.mappingsProvider = TransformMappingProvider(mappingsProvider, transform)
    }

    internal fun build() = QueryValueMapping(
        transform = transform,
        match = match,
        display = display,
        format = format,
        valueProvider = valueProvider,
        useStrictValues = useStrictValues,
        mappingsProvider = mappingsProvider
    )

}

class WrapOperatorValueProvider<Value, Output>(private val valueProvider: ValueProvider<Pair<Value, Output>>) : ValueProvider<Pair<Value, Pair<Output, SearchQueryOperator?>>> {
    override suspend fun getValues(): Iterable<Pair<Value, Pair<Output, SearchQueryOperator?>>> = valueProvider.getValues().map { it.first to (it.second to null as SearchQueryOperator?) }.toSet()
}

class TransformMappingProvider<Value, T, Output>(private val valueProvider: MappingProvider<Value, T>, private val transform: (T) -> Output) : ValueProvider<Pair<Value, Pair<Output, SearchQueryOperator?>>> {
    override suspend fun getValues(): Iterable<Pair<Value, Pair<Output, SearchQueryOperator?>>> = valueProvider.getValues().map { it.first to (transform(it.second.first) to it.second.second) }.toSet()
}

inline fun <reified T : Enum<T>> enumToMappings(findKeywords: (T) -> List<String>): Map<String, T> {
    return enumToMappings(enumValues<T>(), findKeywords)
}

inline fun <T : Enum<T>> enumToMappings(enumValues: Array<T>, findKeywords: (T) -> List<String>): Map<String, T> {
    return enumValues.map { value ->
        val keywords = findKeywords(value)
        keywords.map { keyword ->
            when {
                keyword.contains("_") -> listOf(keyword.replace("_", "") to value, keyword to value)
                else -> listOf(keyword to value)
            }
        }.flatten()
    }.flatten().toMap()
}
