package dev.cowzy.cardgourmet.elrond.config

import dev.cowzy.cardgourmet.commons.getSerialName
import dev.cowzy.cardgourmet.commons.i18n.LocalizationService
import dev.cowzy.cardgourmet.commons.i18n.UserLanguage
import dev.cowzy.cardgourmet.elrond.*
import dev.cowzy.cardgourmet.elrond.descriptor.EqualsDescriptor
import dev.cowzy.cardgourmet.elrond.descriptor.IsPresentDescriptor
import dev.cowzy.cardgourmet.elrond.descriptor.PropertyDescriptor
import dev.cowzy.cardgourmet.elrond.descriptor.StringDescriptor
import dev.cowzy.cardgourmet.elrond.property.*
import dev.cowzy.cardgourmet.elrond.values.*
import dev.cowzy.kuery.reflection.columnName
import java.time.LocalDate
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

class SearchQueryConfigBuilder(
    val valueProviderPool: ValueProviderPool,
    init: SearchQueryConfigBuilder.() -> Unit
) {

    private val filters = mutableListOf<QueryFilter>()

    init {
        this.init()
    }

    fun filter(vararg keywords: String, builder: QueryFilterBuilder.() -> Unit) {
        val existingFilters = filters.filter { filter -> keywords.any(filter.keywords::contains) }
        filters.removeAll(existingFilters)

        val filterBuilder = QueryFilterBuilder(keywords.toList(), valueProviderPool).apply(builder)
        val filter = filterBuilder.build()
        filters.add(filter)
    }

    fun build(): List<QueryFilter> = filters

}

class QueryFilterBuilder(private val keywords: List<String>, private val valueProviderPool: ValueProviderPool) {

    private val properties = mutableListOf<SearchQueryProperty<out Any>>()
    private val ignoreReferenceKeywords = mutableSetOf<String>()
    private var inverted = false

    fun property(property: SearchQueryProperty<out Any>) {
        if (properties.contains(property)) throw IllegalArgumentException("Filter already contains property: $property")

        val valueTypes = property.valueDefinition.supportedValueTypes
        val handledValueTypes = valueTypes.filter { isValueHandled(it) }
        if (handledValueTypes.isNotEmpty() && handledValueTypes.size >= valueTypes.size) {
            throw IllegalArgumentException("All supported value types are already handled by other properties: $property")
        }

        // Validate that all properties requiring strict values have at least one value/mapping provider set.
        valueTypes.forEach { type ->
            val definition = property.valueDefinition.getDefinition(type)
            if (definition.useStrictValues) {
                if (definition.valueProvider == null && definition.mappingsProvider == null) {
                    throw IllegalArgumentException("Property requires strict values but no provider is set for values or mappings: $property")
                }
            }
        }

        properties.add(property)
    }

    fun numeric(column: KProperty1<*, Number?>, propertyKey: String, offset: Double = 0.0) {
        property(NumericColumnProperty(column, offset = offset, propertyKey = propertyKey))
    }

    fun numeric(vararg columns: KProperty1<*, Number?>, propertyKey: String, offset: Double = 0.0) {
        property(NumericColumnProperty(*columns, offset = offset, propertyKey = propertyKey))
    }

    fun numericAndString(
        numericColumn: KProperty1<*, Number?>,
        stringColumn: KProperty1<*, String?>,
        propertyKey: String,
        configure: (StringPropertyConfig.() -> Unit)? = null
    ) {
        numeric(numericColumn, propertyKey)
        string(stringColumn, propertyKey, configure = configure)
    }

    fun string(
        column: KProperty1<*, String?>,
        propertyKey: String,
        configure: (StringPropertyConfig.() -> Unit)? = null
    ) {
        val config = StringPropertyConfig(column, valueProviderPool).apply { configure?.invoke(this) }
        property(
            StringColumnProperty(
                column,
                descriptor = StringDescriptor(propertyKey),
                mappingProvider = config.mappingsProvider,
                valueProvider = config.valueProvider,
                useStrictValues = config.useStrictValues,
            )
        )
    }

    fun simpleString(
        column: KProperty1<*, String?>,
        simpleColumn: KProperty1<*, String?>,
        propertyKey: String,
        configure: (StringPropertyConfig.() -> Unit)? = null
    ) {
        val config = StringPropertyConfig(column, valueProviderPool).apply { configure?.invoke(this) }
        property(
            StringColumnProperty(
                column,
                simpleColumn = simpleColumn,
                descriptor = StringDescriptor(propertyKey),
                mappingProvider = config.mappingsProvider,
                useStrictValues = config.useStrictValues,
                valueProvider = config.valueProvider
            )
        )
    }

    fun exactString(
        column: KProperty1<*, String?>, propertyKey: String,
        configure: (StringPropertyConfig.() -> Unit)? = null
    ) {
        val config = StringPropertyConfig(column, valueProviderPool).apply { configure?.invoke(this) }
        property(
            StringColumnProperty(
                column,
                mapContainsToEquals = true,
                valueProvider = config.valueProvider,
                useStrictValues = config.valueProvider != null,
                mappingProvider = config.mappingsProvider,
                descriptor = EqualsDescriptor(propertyKey)
            )
        )
    }

    fun stringArray(
        column: KProperty1<*, List<String>?>,
        propertyKey: String,
        configure: (StringArrayPropertyConfig.() -> Unit)? = null
    ) {
        stringArray(column, IsPresentDescriptor(propertyKey), propertyKey, configure)
    }

    fun stringArray(
        column: KProperty1<*, List<String>?>,
        descriptor: PropertyDescriptor,
        key: String,
        configure: (StringArrayPropertyConfig.() -> Unit)? = null
    ) {
        val config = StringArrayPropertyConfig(column, valueProviderPool).apply { configure?.invoke(this) }
        property(
            StringArrayColumnProperty(
                column,
                valueProvider = config.valueProvider ?: valueProviderPool.getAutoStringArrayProvider(column),
                mappingProvider = config.mappingsProvider,
                descriptor = descriptor,
                key = key.split(".").last()
            )
        )
    }

    fun stringArrayAndCardinality(
        column: KProperty1<*, List<String>?>,
        cardinalityPropertyKey: String,
        arrayPropertyKey: String,
        configure: (StringArrayPropertyConfig.() -> Unit)? = null
    ) {
        cardinality(column, cardinalityPropertyKey)
        stringArray(column, arrayPropertyKey, configure)
    }

    fun stringArrayAndCardinality(
        column: KProperty1<*, List<String>?>,
        cardinalityPropertyKey: String,
        descriptor: PropertyDescriptor,
        contentKey: String,
        configure: (StringArrayPropertyConfig.() -> Unit)? = null
    ) {
        cardinality(column, cardinalityPropertyKey)
        stringArray(column, descriptor, contentKey, configure)
    }

    fun cardinality(
        column: KProperty1<*, List<*>?>,
        propertyKey: String,
        mappings: Map<String, Pair<Number, SearchQueryOperator>>? = null
    ) {
        property(ArrayCardinalityProperty(column, mappings = mappings, propertyKey = propertyKey))
    }

    fun uuid(column: KProperty1<*, UUID?>, propertyKey: String) {
        property(UuidColumnProperty(column, descriptor = EqualsDescriptor(propertyKey)))
    }

    fun date(column: KProperty1<*, *>, propertyKey: String) {
        property(DateProperty(column, propertyKey))
    }

    fun year(column: KProperty1<*, *>, propertyKey: String) {
        property(YearOfDateProperty(column, propertyKey))
    }

    fun dateByMapping(column: KProperty1<*, *>, mappingProvider: ValueProvider<Pair<String, LocalDate>>, propertyKey: String) {
        property(DateByMappingProperty(column, mappingProvider, propertyKey))
    }

    fun yearByMapping(column: KProperty1<*, *>, mappingProvider: ValueProvider<Pair<String, LocalDate>>, propertyKey: String) {
        property(YearByMappingProperty(column, DataYearMappingProvider(mappingProvider), propertyKey))
    }

    inline fun <reified T : Enum<T>> enum(
        column: KProperty1<*, T?>,
        propertyKey: String,
        noinline display: ((T, LocalizationService, UserLanguage) -> String)? = null,
        noinline aliasResolver: ((T) -> List<String>)? = null,
    ) = enum(column, EqualsDescriptor(propertyKey), propertyKey, display, aliasResolver)

    inline fun <reified T : Enum<T>> enum(
        column: KProperty1<*, T?>,
        descriptor: PropertyDescriptor,
        key: String,
        noinline display: ((T, LocalizationService, UserLanguage) -> String)? = null,
        noinline aliasResolver: ((T) -> List<String>)? = null,
    ) = property(enumColumnProperty(column, descriptor, key.split(".").last(), aliasResolver, display))

    inline fun <reified T : Enum<T>> enumArray(
        column: KProperty1<*, List<T>>,
        propertyKey: String,
        noinline aliasResolver: ((T) -> List<String>)? = null,
    ) = enumArray(column, IsPresentDescriptor(propertyKey), propertyKey, aliasResolver)

    inline fun <reified T : Enum<T>> enumArray(
        column: KProperty1<*, List<T>>,
        descriptor: PropertyDescriptor,
        key: String,
        noinline aliasResolver: ((T) -> List<String>)? = null,
    ) = property(enumArrayColumnProperty(column, descriptor, key.split(".").last(), aliasResolver))

    inline fun <reified T : Enum<T>> enumArrayAndCardinality(
        column: KProperty1<*, List<T>>,
        cardinalityPropertyKey: String,
        arrayPropertyKey: String,
        noinline aliasResolver: ((T) -> List<String>)? = null,
    ) {
        cardinality(column, cardinalityPropertyKey)
        enumArray(column, arrayPropertyKey, aliasResolver)
    }

    inline fun <reified T : Enum<T>> enumArrayAndCardinality(
        column: KProperty1<*, List<T>>,
        cardinalityPropertyKey: String,
        descriptor: PropertyDescriptor,
        key: String,
        noinline aliasResolver: ((T) -> List<String>)? = null,
    ) {
        cardinality(column, cardinalityPropertyKey)
        enumArray(column, descriptor, key, aliasResolver)
    }

    fun inverted(inverted: Boolean) = this.apply { this.inverted = inverted }

    fun ignoreReference(keyword: String) {
        if (!keywords.contains(keyword)) throw IllegalArgumentException("Keyword not part of filter: $keyword")
        this.ignoreReferenceKeywords.add(keyword)
    }

    fun build(): QueryFilter {
        return QueryFilter(
            keywords = keywords,
            properties = properties,
            inverted = inverted,
            ignoreReferenceKeywords = ignoreReferenceKeywords
        )
    }

    private fun isValueHandled(valueType: KClass<out QueryValue<*>>): Boolean {
        // Find all properties that support the given value type
        val matchingProperties = properties.filter { it.valueDefinition.supportedValueTypes.contains(valueType) }
        if (matchingProperties.isEmpty()) return false

        // Check if any of the properties allows arbitrary values
        // If so, the value type is handled.
        val allowsArbitraryValues = matchingProperties.any { !it.valueDefinition.getDefinition(valueType).useStrictValues }
        return allowsArbitraryValues
    }

}

open class StringPropertyConfig(protected val column: KProperty1<*, *>, protected val pool: ValueProviderPool) {

    var valueProvider: ValueProvider<String>? = null
    var mappingsProvider: MappingProvider<String, String>? = null
    var useStrictValues = false

    fun values(values: Iterable<String>, strict: Boolean = true) = values(StaticValueProvider(values.toSet()), strict)

    fun values(valueProvider: ValueProvider<String>?, strict: Boolean = true) {
        this.valueProvider = valueProvider
        this.useStrictValues = valueProvider != null && strict
    }

    open fun autoValues(strict: Boolean = true) {
        this.valueProvider = pool.getOrPut(column) { AutoStringValueProvider(it, column) }
        this.useStrictValues = strict
    }

    fun mappings(mappings: Map<String, String>?) {
        if (mappings == null) return
        mappings(StaticValueProvider(mappings.entries.map { it.key to (it.value to null) }.toSet()))
    }

    fun mappings(mappingsProvider: MappingProvider<String, String>?) {
        if (mappingsProvider == null) return
        this.mappingsProvider = mappingsProvider
    }

    fun autoMappings(customMappings: Map<String, String>? = null) {
        if (valueProvider == null) {
            autoValues()
        }

        mappingsProvider = pool.getOrPut("${column.columnName()}-mappings") { AutoMappingProvider(valueProvider!!, customMappings) }
    }

}

class StringArrayPropertyConfig(column: KProperty1<*, List<String>?>, pool: ValueProviderPool) : StringPropertyConfig(column, pool) {

    override fun autoValues(strict: Boolean) {
        this.valueProvider = pool.getOrPut(column) { AutoStringArrayValueProvider(it, column) }
        this.useStrictValues = strict
    }

}
