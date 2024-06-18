package dev.cowzy.cardgourmet.elrond.config

import dev.cowzy.cardgourmet.commons.i18n.LocalizationService
import dev.cowzy.cardgourmet.commons.i18n.UserLanguage
import dev.cowzy.cardgourmet.commons.snakecase
import dev.cowzy.cardgourmet.elrond.*
import dev.cowzy.cardgourmet.elrond.descriptor.EqualsDescriptor
import dev.cowzy.cardgourmet.elrond.descriptor.IsPresentDescriptor
import dev.cowzy.cardgourmet.elrond.descriptor.PropertyDescriptor
import dev.cowzy.cardgourmet.elrond.descriptor.StringDescriptor
import dev.cowzy.cardgourmet.elrond.property.*
import dev.cowzy.cardgourmet.elrond.values.*
import dev.cowzy.kuery.reflection.simpleColumnName
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.typeOf

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

        val filterBuilder =
            QueryFilterBuilder(keywords.toList(), valueProviderPool).apply(builder)
        val filter = filterBuilder.build()
        filters.add(filter)
    }

    fun build(): List<QueryFilter> = filters

}

class QueryFilterBuilder(
    private val keywords: List<String>,
    private val valueProviderPool: ValueProviderPool
) {

    private val properties = mutableListOf<SearchQueryProperty<out Any>>()
    private val ignoreReferenceKeywords = mutableSetOf<String>()
    private var inverted = false

    fun <T : Any> property(
        property: SearchQueryProperty<T>,
        provider: ValueProvider<T>?
    ) {
        if (properties.contains(property)) throw IllegalArgumentException("Filter already contains property: ${property.key}")

        val valueTypes = property.valueDefinition.supportedValueTypes
        val handledValueTypes = valueTypes.filter { isValueHandled(it) }
        if (handledValueTypes.isNotEmpty() && handledValueTypes.size >= valueTypes.size) {
            throw IllegalArgumentException("All supported value types are already handled by other properties: ${property.key}")
        }

        if (provider != null) {
            property.valueDefinition.provider(provider)
        }

        properties.add(property)
    }

    fun <T : Any> property(
        property: SearchQueryProperty<T>,
        configureProvider: (ValueProviderBuilder<T>.() -> Unit)? = null
    ) {
        val provider = configureProvider?.let {
            valueProviderPool.getOrPut(property.key) { pool ->
                ValueProviderBuilder<T>(pool).apply {
                    it.invoke(this)
                }.build()
            }
        }

        property(property, provider)
    }

    fun numeric(
        column: KProperty1<*, Number?>,
        propertyKey: String,
        offset: Double = 0.0,
        configureProvider: (ValueProviderBuilder<Number>.() -> Unit)? = null
    ) {
        property(NumericColumnProperty(column, offset = offset, propertyKey = propertyKey), configureProvider)
    }

    fun numeric(
        vararg columns: KProperty1<*, Number?>,
        propertyKey: String,
        offset: Double = 0.0,
        configureProvider: (ValueProviderBuilder<Number>.() -> Unit)? = null
    ) {
        property(NumericColumnProperty(*columns, offset = offset, propertyKey = propertyKey), configureProvider)
    }

    fun numericAndString(
        numericColumn: KProperty1<*, Number?>,
        stringColumn: KProperty1<*, String?>,
        propertyKey: String,
        configureStringProvider: (ValueProviderBuilder<String>.() -> Unit)? = null
    ) {
        numeric(numericColumn, propertyKey)
        string(stringColumn, propertyKey, configureProvider = configureStringProvider)
    }

    fun string(
        column: KProperty1<*, String?>,
        propertyKey: String,
        configureProvider: (ValueProviderBuilder<String>.() -> Unit)? = null
    ) {
        val property = StringColumnProperty(column, descriptor = StringDescriptor(propertyKey))
        val provider = configureProvider?.let { configure ->
            valueProviderPool.getOrPut(property.key) {
                ValueProviderBuilder<String>(it).apply(configure).build()
            }
        }

        property(
            StringColumnProperty(
                column,
                descriptor = StringDescriptor(propertyKey),
            ),
            provider?.withTransform { StringValue(it) }
        )
    }

    fun simpleString(
        column: KProperty1<*, String?>,
        simpleColumn: KProperty1<*, String?>,
        propertyKey: String,
        configureProvider: (ValueProviderBuilder<String>.() -> Unit)? = null
    ) {
        val property = StringColumnProperty(column, descriptor = StringDescriptor(propertyKey))
        val provider = configureProvider?.let { configure ->
            valueProviderPool.getOrPut(property.key) {
                ValueProviderBuilder<String>(it).apply(configure).build()
            }
        }

        property(
            StringColumnProperty(
                column,
                simpleColumn = simpleColumn,
                descriptor = StringDescriptor(propertyKey),
            ),
            provider?.withTransform { StringValue(it) }
        )
    }

    fun exactString(
        column: KProperty1<*, String?>, propertyKey: String,
        configureProvider: (ValueProviderBuilder<String>.() -> Unit)? = null
    ) {
        val property = StringColumnProperty(column, descriptor = StringDescriptor(propertyKey))
        val provider = configureProvider?.let { configure ->
            valueProviderPool.getOrPut(property.key) {
                ValueProviderBuilder<String>(it).apply(configure).build()
            }
        }

        property(
            StringColumnProperty(
                column,
                mapContainsToEquals = true,
                descriptor = EqualsDescriptor(propertyKey)
            ),
            provider?.withTransform { StringValue(it) }
        )
    }

    fun stringArray(
        column: KProperty1<*, List<String>?>,
        propertyKey: String,
        configureProvider: (ValueProviderBuilder<String>.() -> Unit)? = null
    ) {
        stringArray(column, IsPresentDescriptor(propertyKey), propertyKey, configureProvider)
    }

    fun stringArray(
        column: KProperty1<*, List<String>?>,
        descriptor: PropertyDescriptor,
        key: String,
        configureProvider: (ValueProviderBuilder<String>.() -> Unit)? = null
    ) {
        property(
            StringArrayColumnProperty(
                column,
                descriptor = descriptor,
                key = key.split(".").last()
            ),
            configureProvider ?: {
                // Basic plural removal
                val valueType = column.simpleColumnName().let {
                    when {
                        it.endsWith("s") -> it.dropLast(1)
                        else -> it
                    }
                }

                strict(true)
                autoArrayValues(column, valueType, autoAlias = true)
            }
        )
    }

    fun stringArrayAndCardinality(
        column: KProperty1<*, List<String>?>,
        cardinalityPropertyKey: String,
        arrayPropertyKey: String,
        configureProvider: (ValueProviderBuilder<String>.() -> Unit)? = null
    ) {
        cardinality(column, cardinalityPropertyKey)
        stringArray(column, arrayPropertyKey, configureProvider)
    }

    fun stringArrayAndCardinality(
        column: KProperty1<*, List<String>?>,
        cardinalityPropertyKey: String,
        descriptor: PropertyDescriptor,
        contentKey: String,
        configureProvider: (ValueProviderBuilder<String>.() -> Unit)? = null
    ) {
        cardinality(column, cardinalityPropertyKey)
        stringArray(column, descriptor, contentKey, configureProvider)
    }

    fun cardinality(
        column: KProperty1<*, List<*>?>,
        propertyKey: String,
        mappings: Map<String, Pair<Number, SearchQueryOperator?>>? = null,
        mappingsType: String = "string"
    ) {
        val configureProvider: (ValueProviderBuilder<Number>.() -> Unit)? = mappings?.let {
            { valuesWithOperator(mappings, mappingsType) }
        }

        property(ArrayCardinalityProperty(column, propertyKey = propertyKey), configureProvider)
    }

    fun cardinality(
        vararg columns: KProperty1<*, List<*>?>,
        propertyKey: String,
        mappings: Map<String, Pair<Number, SearchQueryOperator?>>? = null,
        mappingsType: String = "string"
    ) {
        val configureProvider: (ValueProviderBuilder<Number>.() -> Unit)? = mappings?.let {
            { valuesWithOperator(mappings, mappingsType) }
        }

        property(ArrayCardinalityProperty(columns = columns, propertyKey = propertyKey), configureProvider)
    }

    fun uuid(column: KProperty1<*, UUID?>, propertyKey: String) {
        property(UuidColumnProperty(column, descriptor = EqualsDescriptor(propertyKey)))
    }

    fun date(
        column: KProperty1<*, *>,
        propertyKey: String,
        configureProvider: (ValueProviderBuilder<String>.() -> Unit)? = null
    ) {
        property(DateProperty(column, propertyKey), configureProvider)
    }

    fun year(
        column: KProperty1<*, *>,
        propertyKey: String,
        configureProvider: (ValueProviderBuilder<Number>.() -> Unit)? = null
    ) {
        property(YearOfDateProperty(column, propertyKey), configureProvider)
    }

    inline fun <reified T : Enum<T>> enum(
        column: KProperty1<*, *>,
        propertyKey: String,
        noinline display: ((T, LocalizationService, UserLanguage) -> String)? = null,
        noinline aliasResolver: ((T) -> List<String>)? = null,
    ) = enum(column, EqualsDescriptor(propertyKey), propertyKey, display, aliasResolver)

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T : Enum<T>> enum(
        column: KProperty1<*, *>,
        descriptor: PropertyDescriptor,
        key: String,
        noinline display: ((T, LocalizationService, UserLanguage) -> String)? = null,
        noinline aliasResolver: ((T) -> List<String>)? = null,
    ) = property(enumColumnProperty(column, descriptor, key.split(".").last(), display)) {
        val type = (typeOf<T>().classifier as KClass<T>).simpleName!!.snakecase()

        strict(true)
        enumValues(type, kotlin.enumValues<T>(), aliasResolver ?: { emptyList() })
    }

    inline fun <reified T : Enum<T>> enumArray(
        column: KProperty1<*, List<T>>,
        propertyKey: String,
        noinline aliasResolver: ((T) -> List<String>)? = null,
    ) = enumArray(column, IsPresentDescriptor(propertyKey), propertyKey, aliasResolver)

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T : Enum<T>> enumArray(
        column: KProperty1<*, List<T>>,
        descriptor: PropertyDescriptor,
        key: String,
        noinline aliasResolver: ((T) -> List<String>)? = null,
    ) = property(enumArrayColumnProperty(column, descriptor, key.split(".").last())) {
        val type = (typeOf<T>().classifier as KClass<T>).simpleName!!.snakecase()

        strict(true)
        enumValues(type, kotlin.enumValues<T>(), aliasResolver ?: { emptyList() })
    }

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
            key = keywords.first(), // TODO: allow overriding the key
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

        val allowsArbitraryValues = when (valueType) {
            StringValue::class -> matchingProperties.any { it.valueDefinition.provider?.strictValues != true }
            else -> true
        }

        return allowsArbitraryValues
    }

}