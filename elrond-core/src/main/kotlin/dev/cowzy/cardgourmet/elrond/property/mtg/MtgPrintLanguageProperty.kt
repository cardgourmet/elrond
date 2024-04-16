package dev.cowzy.cardgourmet.elrond.property.mtg

import dev.cowzy.kuery.query.WhereQueryBuilder
import dev.cowzy.kuery.reflection.placeholder
import dev.cowzy.kuery.reflection.table
import dev.cowzy.cardgourmet.commons.database.card.mtg.MtgLanguage
import dev.cowzy.cardgourmet.commons.getSerialName
import dev.cowzy.cardgourmet.commons.i18n.Strings
import dev.cowzy.cardgourmet.elrond.*
import dev.cowzy.cardgourmet.elrond.descriptor.AvailableInDescriptor
import dev.cowzy.cardgourmet.elrond.descriptor.EqualsDescriptor
import dev.cowzy.cardgourmet.elrond.property.SearchQueryProperty
import dev.cowzy.kuery.query.orWhere
import kotlin.reflect.KProperty1

class MtgPrintLanguageProperty(
    private val languagesColumn: KProperty1<*, *>,
    private vararg val languageColumns: KProperty1<*, *>,
    mappings: Map<String, String> = emptyMap()
) : SearchQueryProperty<MtgLanguage>(
    supportedOperators = stringQueryOperators,
    affectedTables = arrayOf(languagesColumn.table(), *languageColumns.map { it.table() }.toTypedArray()),
    descriptor = EqualsDescriptor(propertyKey = Strings.Query.Property.LANGUAGE)
) {

    override val valueDefinition = QueryValueDefinition {
        StringValue::class {
            transform { value ->
                val transformed = mappings.entries.find { it.key.equals(value.value, ignoreCase = true) }?.value ?: value.value
                MtgLanguage.values().find { it.getSerialName().equals(transformed, ignoreCase = true) }
            }

            display { language, i18n, locale ->
                i18n.translate(locale, "${Strings.Query.Mtg.Language.KEY}.${(language as MtgLanguage).getSerialName()}")
            }
        }
    }

    override suspend fun <T : WhereQueryBuilder<T>> applyCondition(
        builder: T,
        operator: SearchQueryOperator,
        value: MtgLanguage
    ) {
        builder.whereRaw(languagesColumn, "@>", "ARRAY[${languagesColumn.placeholder()}]::text[]") { stmt, index ->
            stmt.setString(index.getAndIncrement(), value.getSerialName())
        }

        builder.where { inner ->
            languageColumns.forEach {
                inner.orWhere(it, value.getSerialName())
            }
        }
    }

}