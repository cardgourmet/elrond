package dev.cowzy.cardgourmet.elrond.config.dlc

import dev.cowzy.cardgourmet.chef.commons.model.image.CardImage
import dev.cowzy.cardgourmet.commons.database.card.CardPrice
import dev.cowzy.cardgourmet.commons.database.card.dlc.*
import dev.cowzy.cardgourmet.commons.database.game.GameType
import dev.cowzy.cardgourmet.commons.database.set.dlc.DlcSet
import dev.cowzy.cardgourmet.commons.getSerialName
import dev.cowzy.cardgourmet.commons.i18n.Strings
import dev.cowzy.cardgourmet.elrond.SearchQueryOperator
import dev.cowzy.cardgourmet.elrond.config.SearchQueryConfig
import dev.cowzy.cardgourmet.elrond.config.SearchQueryConfigBuilder
import dev.cowzy.cardgourmet.elrond.config.TableDependency
import dev.cowzy.cardgourmet.elrond.descriptor.SimplePropertyDescriptor
import dev.cowzy.cardgourmet.elrond.property.*
import dev.cowzy.cardgourmet.elrond.values.StaticValueProvider
import dev.cowzy.cardgourmet.elrond.values.dlc.DlcSetMarketReleaseDateMappingProvider
import dev.cowzy.cardgourmet.elrond.values.dlc.DlcSetReleaseDateMappingProvider
import dev.cowzy.kuery.query.innerJoin
import dev.cowzy.kuery.query.leftJoin

val dlcFinishes = StaticValueProvider(setOf("nonfoil", "foil"))
val dlcMediums = StaticValueProvider(setOf("paper"))
val dlcLanguages = StaticValueProvider(DlcLanguage.values().map { it.getSerialName() }.toSet())

val dlcLanguageMappings = DlcLanguage.values().associate { language ->
    language.name to language.getSerialName()
}

val dlcSetCodeMappings = mapOf("promo" to "P1", "tfc" to "1", "rof" to "2", "ink" to "3", "urs" to "4")

private val propertyKeys = Strings.Query.Property
private val dlcPropertyKeys = Strings.Query.Dlc.Property

fun SearchQueryConfigBuilder.configureBasicDlcFilters() {
    val setReleaseDates = valueProviderPool.getOrPut("dlc_set_release_dates") { DlcSetReleaseDateMappingProvider(it, dlcSetCodeMappings) }
    val setMarketReleaseDates = valueProviderPool.getOrPut("dlc_set_market_release_dates") { DlcSetMarketReleaseDateMappingProvider(it, dlcSetCodeMappings) }

    filter("name", "n") {
        simpleString(DlcCardTranslation::name, DlcCardTranslation::simpleName, propertyKeys.NAME) { autoValues(false) }
    }

    filter("cost") {
        numeric(DlcCard::cost, dlcPropertyKeys.COST)
    }

    filter("ink", "inktype", "i", "color", "c", "id", "identity") {
        numeric(DlcCard::cost, dlcPropertyKeys.COST)
        enum(DlcCard::inkType, dlcPropertyKeys.INK_TYPE)
    }

    filter("strength", "power", "pow", "str") {
        numeric(DlcCard::strength, dlcPropertyKeys.STRENGTH)
    }

    filter("willpower", "will", "wp") {
        numeric(DlcCard::willpower, dlcPropertyKeys.WILLPOWER)
    }

    filter("move", "movecost", "movement") {
        numeric(DlcCard::moveCost, dlcPropertyKeys.MOVE_COST)
    }

    filter("lore", "lorevalue") {
        numeric(DlcCard::loreValue, dlcPropertyKeys.LORE)
    }

    filter("lang", "language", "printlang", "printlanguage") {
        enum(DlcPrintTranslation::language, propertyKeys.PRINT_LANGUAGE)
    }

    filter("cardlang", "cardlanguage") {
        enum(DlcCardTranslation::language, propertyKeys.CARD_LANGUAGE)
    }

    filter("keyword", "keywords", "key") {
        stringArrayAndCardinality(DlcCard::keywords, propertyKeys.KEYWORD_COUNT, propertyKeys.KEYWORD)
    }

    filter("class", "classes", "classification", "classifications", "trait", "traits", "subtype", "subtypes") {
        stringArrayAndCardinality(DlcCard::classifications, dlcPropertyKeys.CLASSIFICATION_COUNT, dlcPropertyKeys.CLASSIFICATION)
    }

    filter("type", "t", "types") {
        string(DlcCard::type, dlcPropertyKeys.TYPE) { autoValues() }
        stringArray(DlcCard::classifications, dlcPropertyKeys.CLASSIFICATION)
    }

    filter("supertype") {
        string(DlcCard::type, dlcPropertyKeys.TYPE) { autoValues() }
    }

    filter("date", "releasedate") {
        dateByMapping(DlcSet::releaseDate, setReleaseDates, propertyKey = propertyKeys.RELEASE_DATE)
        date(DlcSet::releaseDate, propertyKey = propertyKeys.RELEASE_DATE)
    }

    filter("year", "releaseyear") {
        yearByMapping(DlcSet::releaseDate, setReleaseDates, propertyKey = propertyKeys.RELEASE_YEAR)
        year(DlcSet::releaseDate, propertyKey = propertyKeys.RELEASE_YEAR)
    }

    filter("marketdate", "marketreleasedate") {
        dateByMapping(DlcSet::marketReleaseDate, setMarketReleaseDates, propertyKey = dlcPropertyKeys.MARKET_RELEASE_DATE)
        date(DlcSet::marketReleaseDate, propertyKey = dlcPropertyKeys.MARKET_RELEASE_YEAR)
    }

    filter("marketyear", "marketreleaseyear") {
        yearByMapping(DlcSet::marketReleaseDate, setMarketReleaseDates, propertyKey = dlcPropertyKeys.MARKET_RELEASE_YEAR)
        year(DlcSet::marketReleaseDate, propertyKey = dlcPropertyKeys.MARKET_RELEASE_YEAR)
    }

    filter("artist", "illustrator") {
        string(DlcPrint::artist, propertyKeys.ARTIST)
    }

    filter("set", "s", "e", "edition", "expansion") {
        uuid(DlcSet::id, propertyKeys.SET_ID)
        string(DlcSet::code, propertyKeys.SET_CODE) {
            autoValues()
            mappings(dlcSetCodeMappings)
        }
    }

    filter("setid") { uuid(DlcSet::id, propertyKeys.SET_ID) }
    filter("setname") {
        string(DlcSet::name, propertyKeys.SET_NAME) { autoValues(false) }
    }

    filter("setcode") {
        string(DlcSet::code, propertyKeys.SET_CODE) {
            autoValues()
            mappings(dlcSetCodeMappings)
        }
    }

    filter("cn", "number", "collectornumber") {
        numericAndString(DlcPrint::collectorNumberValue, DlcPrint::collectorNumber, propertyKeys.COLLECTOR_NUMBER)
    }

    filter("title") {
        simpleString(DlcCardTranslation::title, DlcCardTranslation::simpleTitle, dlcPropertyKeys.TITLE) { autoValues(false) }
    }

    filter("text", "description", "abilities", "actions", "oracle", "oracletext", "o") {
        simpleString(DlcCardTranslation::text, DlcCardTranslation::simpleText, propertyKeys.TEXT)
    }

    filter("ability", "action", "actionname", "abilityname") {
        property(StringRegexProperty(
            DlcCardTranslation::text,
            { value, operator ->
                when (operator) {
                    SearchQueryOperator.CONTAINS -> "\\[\"[^\"]*$value[^\"]*\"]"
                    SearchQueryOperator.EQUALS -> "\\[\"$value\"]"
                    else -> value
                }
            },
            propertyKey = dlcPropertyKeys.ABILITY_NAME
        ))
    }

    filter("fulltext", "fulldescription", "fulloracle", "fulloracletext", "fo") {
        simpleString(DlcCardTranslation::fullText, DlcCardTranslation::simpleFullText, propertyKeys.TEXT_WITH_REMINDERS)
    }

    filter("flavor", "flavortext", "ft") {
        simpleString(DlcPrintTranslation::flavorText, DlcPrintTranslation::simpleFlavorText, propertyKeys.FLAVOR_TEXT)
    }

    filter("separator") {
        exactString(DlcPrint::separator, dlcPropertyKeys.SEPARATOR) { autoValues() }
    }

    filter("franchise") {
        string(DlcCard::franchise, dlcPropertyKeys.FRANCHISE) { autoValues(false) }
    }

    filter("is:inkwell") {
        property(StaticColumnProperty(DlcCard::inkwell, descriptor = SimplePropertyDescriptor(Strings.Query.Dlc.Comparison.IsInkwell.KEY, propertyKeys.PRINT), key = "is_inkwell"))
    }

    filter("not:inkwell") {
        inverted(true)
        property(StaticColumnProperty(DlcCard::inkwell, descriptor = SimplePropertyDescriptor(Strings.Query.Dlc.Comparison.IsInkwell.KEY, propertyKeys.PRINT), key = "is_inkwell"))
    }

    filter("tag", "tags", "is", "has") {
        string(DlcCard::type, dlcPropertyKeys.TYPE) { autoValues() }
        stringArray(DlcCard::classifications, dlcPropertyKeys.CLASSIFICATION) { autoValues() }
    }

    filter("not") {
        inverted(true)
        string(DlcCard::type, dlcPropertyKeys.TYPE) { autoValues() }
        stringArray(DlcCard::classifications, dlcPropertyKeys.CLASSIFICATION) { autoValues() }
    }

    filter("print", "printid") {
        uuid(DlcPrint::id, propertyKeys.PRINT_ID)
    }

    filter("card", "cardid", "oracleid") {
        uuid(DlcCard::id, propertyKeys.PRINT_ID)
    }

    // TODO: new/in
    // TODO: is/has/not properties
    // TODO: rarity
    // TODO: prints/sets (reprints)
    // TODO: new/in
}

private val tableDependencies = mapOf(
    DlcCard::class to TableDependency(DlcPrint::class) { builder ->
        builder.innerJoin(DlcCard::class) { it.whereColumn(DlcCard::id, DlcPrint::cardId) }
    },
    DlcCardTranslation::class to TableDependency(DlcCard::class) { builder ->
        builder.innerJoin(DlcCardTranslation::class) { it.whereColumn(DlcCard::id, DlcCardTranslation::cardId) }
    },
    DlcPrintTranslation::class to TableDependency(DlcPrint::class) { builder ->
        builder.innerJoin(DlcPrintTranslation::class) { it.whereColumn(DlcPrint::id, DlcPrintTranslation::printId) }
    },
    DlcPrintIdentifier::class to TableDependency(DlcPrint::class) { builder ->
        builder.innerJoin(DlcPrintIdentifier::class) { it.whereColumn(DlcPrint::id, DlcPrintIdentifier::printId) }
    },
    DlcSet::class to TableDependency(DlcPrint::class) { builder ->
        builder.innerJoin(DlcSet::class) { it.whereColumn(DlcPrint::setId, DlcSet::id) }
    },
    CardPrice::class to TableDependency(DlcPrint::class) { builder ->
        builder.leftJoin(CardPrice::class) {
            it
                .whereColumn(DlcPrint::id, CardPrice::cardId)
                .where(CardPrice::game, GameType.DISNEY_LORCANA)
        }
    },
    CardImage::class to TableDependency(DlcPrintTranslation::class) { builder ->
        builder.leftJoin(CardImage::class) { it.whereColumn(CardImage::printTranslationId, DlcPrintTranslation::id) }
    }
)

val dlcBasicSearchQueryConfig = SearchQueryConfig(
    table = DlcPrint::class,
    printIdColumn = DlcPrint::id,
    faceIndexColumn = null,
    languageColumns = arrayOf(DlcPrintTranslation::language, DlcCardTranslation::language),
    tableDependencies = tableDependencies,
)
