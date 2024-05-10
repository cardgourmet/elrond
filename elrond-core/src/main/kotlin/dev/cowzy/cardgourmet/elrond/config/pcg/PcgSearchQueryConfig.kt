package dev.cowzy.cardgourmet.elrond.config.pcg

import dev.cowzy.cardgourmet.chef.commons.model.image.CardImage
import dev.cowzy.cardgourmet.commons.database.card.CardPrice
import dev.cowzy.cardgourmet.commons.database.card.pcg.*
import dev.cowzy.cardgourmet.commons.database.game.GameType
import dev.cowzy.cardgourmet.commons.database.set.pcg.PcgEra
import dev.cowzy.cardgourmet.commons.database.set.pcg.PcgSet
import dev.cowzy.cardgourmet.commons.database.set.pcg.PcgSetTranslation
import dev.cowzy.cardgourmet.commons.getSerialName
import dev.cowzy.cardgourmet.commons.i18n.Strings
import dev.cowzy.cardgourmet.elrond.SearchQueryOperator
import dev.cowzy.cardgourmet.elrond.config.SearchQueryConfig
import dev.cowzy.cardgourmet.elrond.config.SearchQueryConfigBuilder
import dev.cowzy.cardgourmet.elrond.config.TableDependency
import dev.cowzy.cardgourmet.elrond.enumToMappings
import dev.cowzy.cardgourmet.elrond.property.StringRegexProperty
import dev.cowzy.cardgourmet.elrond.values.pcg.PcgSetEndReleaseDateMappingProvider
import dev.cowzy.cardgourmet.elrond.values.pcg.PcgSetStartReleaseDateMappingProvider
import dev.cowzy.kuery.query.innerJoin
import dev.cowzy.kuery.query.leftJoin

private val propertyKeys = Strings.Query.Property

fun SearchQueryConfigBuilder.configureBasicPcgFilters() {
    val setStartReleaseDates = valueProviderPool.getOrPut("pcg_set_start_release_dates") { PcgSetStartReleaseDateMappingProvider(it) }
    val setEndReleaseDates = valueProviderPool.getOrPut("pcg_set_end_release_dates") { PcgSetEndReleaseDateMappingProvider(it) }

    val subTypeMappings = enumToMappings<PcgPokemonSubType> { it.keys }.mapValues { it.value.getSerialName() } +
            enumToMappings<PcgTrainerSubType> { it.keys }.mapValues { it.value.getSerialName() } +
            enumToMappings<PcgEnergySubType> { it.keys }.mapValues { it.value.getSerialName() }

    filter("name", "n") {
        simpleString(PcgCardTranslation::name, PcgCardTranslation::simpleName, propertyKeys.NAME) { autoValues(false) }
    }

    filter("cn", "number", "collectornumber") {
        numericAndString(PcgPrint::collectorNumberValue, PcgPrint::collectorNumber, propertyKeys.COLLECTOR_NUMBER)
    }

    filter("rarity") {
        numeric(PcgPrint::rarityValue, propertyKeys.RARITY)
        enum(PcgPrint::rarity, propertyKeys.RARITY) { it.keys }
    }

    filter("mark", "regulationmark") {
        exactString(PcgPrint::regulationMark, "DUMMY") { autoValues() }
    }

    filter("artist", "illustrator", "artists", "illustrators") {
        stringArrayAndCardinality(PcgPrint::illustrators, "DUMMY", propertyKeys.ARTIST) // TODO
    }

    filter("lang", "language", "printlang", "printlanguage") {
        enum(PcgPrintTranslation::language, propertyKeys.LANGUAGE) { it.keys } // TODO: PRINT_LANGUAGE
    }

    filter("cardlang", "cardlanguage") {
        enum(PcgCardTranslation::language, propertyKeys.LANGUAGE) { it.keys } // TODO: CARD_LANGUAGE
    }

    filter("flavor", "flavortext") {
        simpleString(PcgPrintTranslation::flavorText, PcgPrintTranslation::simpleFlavorText, propertyKeys.FLAVOR_TEXT)
    }

    filter("text", "o", "oracle", "oracletext", "fulloracle", "fo", "fulloracletext") {
        simpleString(PcgCardTranslation::text, PcgCardTranslation::simpleText, propertyKeys.TEXT)
    }

    filter("reminder", "reminders", "rule", "rules") {
        simpleString(PcgCardTranslation::reminderText, PcgCardTranslation::simpleReminderText, "DUMMY") // TODO
    }

    filter("hp", "health", "healthpoints") {
        numeric(PcgCard::hp, "DUMMY") // TODO
    }

    filter("type", "t") {
        enum(PcgCard::superType, "DUMMY") { it.keys } // TODO
        stringArray(PcgCard::subTypes, "DUMMY") { autoMappings(subTypeMappings) } // TODO
        enumArray(PcgCard::types, "DUMMY") { it.keys } // TODO
    }

    filter("supertype") {
        enum(PcgCard::superType, "DUMMY") { it.keys } // TODO
    }

    filter("types", "energy", "energies", "energytypes") {
        enumArrayAndCardinality(PcgCard::types, "DUMMY", "DUMMY") { it.keys } // TODO
    }

    filter("subtype", "subtypes") {
        stringArrayAndCardinality(PcgCard::subTypes, "DUMMY", "DUMMY") { // TODO
            autoMappings(subTypeMappings)
        }
    }

    filter("stage", "evolution", "evolutionstage") {
        enum(PcgCard::evolutionStage, "DUMMY") // TODO
    }

    filter("evolves", "evolvesfrom") {
        string(PcgCard::evolvesFromName, "DUMMY") { autoValues(false) }
    }

    filter("attack", "attackname", "abilityname") {
        property(StringRegexProperty(
            PcgCardTranslation::text,
            { value, operator ->
                when (operator) {
                    SearchQueryOperator.CONTAINS -> "\\[(\\S+\\s)?\"[^\"]*$value[^\"]*\"(\\s\\S+)?]"
                    SearchQueryOperator.EQUALS -> "\\[(\\S+\\s)?\"$value\"(\\s\\S+)?]"
                    else -> value
                }
            },
            propertyKey = "DUMMY" // TODO
        ))
    }

    filter("damage", "attackdamage", "abilitydamage") {
        property(StringRegexProperty(
            PcgCardTranslation::text,
            { value, operator ->
                when (operator) {
                    SearchQueryOperator.CONTAINS -> "\\[(\\S+\\s)?\"[^\"]+\"\\s$value\\D?]"
                    SearchQueryOperator.EQUALS -> "\\[(\\S+\\s)?\"[^\"]+\"\\s$value]"
                    else -> value
                }
            }, // TODO: range checks
            propertyKey = "DUMMY" // TODO
        ))
    }

    // TODO: custom property for value display/transform
    filter("attackcost", "abilitycost") {
        property(StringRegexProperty(
            PcgCardTranslation::text,
            { value, operator ->
                PcgType

                val values = Regex("(?:\\{(.)}|(.))").findAll(value).map {
                    it.groupValues[1].ifBlank { null } ?: it.groupValues[2]
                }.sortedBy { typeValue ->
                    val type = PcgType.values().find { it.keys.contains(typeValue.lowercase()) }
                    when (type) {
                        PcgType.COLORLESS -> Int.MAX_VALUE
                        else -> type?.ordinal ?: Int.MAX_VALUE
                    }
                }.joinToString("") { "\\{$it}" }

                when (operator) {
                    SearchQueryOperator.CONTAINS -> "\\[$values\\s\"[^\"]+\"(\\s\\S+)?]"
                    SearchQueryOperator.EQUALS -> "\\[\\S*$values\\S*\\s\"[^\"]+\"(\\s\\S+)?]"
                    else -> value
                }
            },
            propertyKey = "DUMMY" // TODO
        ))
    }

    filter("ability", "abilities", "abilitytype", "abilitytypes") {
        ignoreReference("ability")
        enumArrayAndCardinality(PcgCard::abilityTypes, "DUMMY", "DUMMY") { it.keys }  // TODO
    }

    filter("effect", "effects", "effecttype", "effecttypes") {
        enumArrayAndCardinality(PcgCard::effectTypes, "DUMMY", "DUMMY") { it.keys }  // TODO
    }

    filter("ruletype", "ruletypes") {
        enumArrayAndCardinality(PcgCard::ruleTypes, "DUMMY", "DUMMY") { it.keys }  // TODO
    }

    filter("weakness", "weaknesses") {
        enumArray(PcgCard::weaknessTypes, "DUMMY") { it.keys }  // TODO
        string(PcgCard::weaknessModifier, "DUMMY") { autoValues(false) } // TODO
    }

    filter("weaknesses", "weaknesstype", "weaknesstypes") {
        enumArrayAndCardinality(PcgCard::weaknessTypes, "DUMMY", "DUMMY") { it.keys }  // TODO
    }

    filter("weaknessmodifier") {
        string(PcgCard::weaknessModifier, "DUMMY") { autoValues(false) } // TODO
    }

    filter("resistance") {
        enumArray(PcgCard::resistanceTypes, "DUMMY") { it.keys } // TODO
        string(PcgCard::resistanceModifier, "DUMMY") { autoValues(false) } // TODO
    }

    filter("resistances", "resistancetype", "resistancetypes") {
        enumArrayAndCardinality(PcgCard::resistanceTypes, "DUMMY", "DUMMY") { it.keys } // TODO
    }

    filter("resistancemodifier") {
        string(PcgCard::resistanceModifier, "DUMMY") { autoValues(false) } // TODO
    }

    filter("retreat", "retreatcost") {
        numeric(PcgCard::retreatCosts, "DUMMY") // TODO
    }

    filter("set", "s", "edition", "e", "expansion") {
        uuid(PcgSet::id, "DUMMY") // TODO
        string(PcgSet::setCode, propertyKeys.SET_CODE) { autoValues() }
    }

    filter("setid") {
        uuid(PcgSet::id, "DUMMY") // TODO
    }

    filter("setname") {
        string(PcgSetTranslation::name, propertyKeys.SET_NAME) { autoValues(false) }
    }

    filter("setcode") {
        string(PcgSet::setCode, propertyKeys.SET_CODE) { autoValues() }
    }

    filter("settype") {
        enum(PcgSet::type, "DUMMY") // TODO
    }

    filter("region", "setregion") {
        enum(PcgSet::region, "DUMMY") { it.aliases } // TODO
    }

    filter("setprints", "publicsetprints") {
        numeric(PcgSet::printedPublicly, "DUMMY") // TODO
    }

    filter("allsetprints", "totalsetprints") {
        numeric(PcgSet::printedTotal, "DUMMY") // TODO
    }

    filter("date", "releasedate", "startreleasedate") {
        dateByMapping(PcgSet::releaseStartDate, setStartReleaseDates, propertyKeys.RELEASE_DATE)
        date(PcgSet::releaseStartDate, propertyKeys.RELEASE_DATE)
    }

    filter("year", "releaseyear", "startreleaseyear") {
        yearByMapping(PcgSet::releaseStartDate, setStartReleaseDates, propertyKeys.RELEASE_YEAR)
        year(PcgSet::releaseStartDate, propertyKeys.RELEASE_YEAR)
    }

    filter("enddate", "endreleasedate") {
        dateByMapping(PcgSet::releaseEndDate, setEndReleaseDates, "DUMMY") // TODO
        date(PcgSet::releaseEndDate, "DUMMY") // TODO
    }

    filter("endyear", "endreleaseyear") {
        yearByMapping(PcgSet::releaseEndDate, setEndReleaseDates, "DUMMY") // TODO
        year(PcgSet::releaseEndDate, "DUMMY") // TODO
    }

    filter("era", "block") {
        uuid(PcgEra::id, "DUMMY") // TODO
        string(PcgEra::name, "DUMMY") { autoValues(false) } // TODO
    }

    filter("eraid", "blockid") {
        uuid(PcgEra::id, "DUMMY") // TODO
    }

    filter("eraname", "blockname") {
        string(PcgEra::name, "DUMMY") { autoValues(false) } // TODO
    }

    filter("cardid", "card") { uuid(PcgCard::id, propertyKeys.CARD_ID) }
    filter("printid", "print") { uuid(PcgPrint::id, propertyKeys.PRINT_ID) }

    // TODO: is/has/not
    // TODO: new/in
    // TODO: prints/sets (reprints)
}

private val tableDependencies = mapOf(
    PcgCard::class to TableDependency(PcgPrint::class) { builder ->
        builder.innerJoin(PcgCard::class) { it.whereColumn(PcgCard::id, PcgPrint::cardId) }
    },
    PcgCardTranslation::class to TableDependency(PcgCard::class) { builder ->
        builder.innerJoin(PcgCardTranslation::class) { it.whereColumn(PcgCard::id, PcgCardTranslation::cardId) }
    },
    PcgPrintTranslation::class to TableDependency(PcgPrint::class) { builder ->
        builder.innerJoin(PcgPrintTranslation::class) { it.whereColumn(PcgPrint::id, PcgPrintTranslation::printId) }
    },
    PcgSet::class to TableDependency(PcgPrint::class) { builder ->
        builder.innerJoin(PcgSet::class) { it.whereColumn(PcgPrint::setId, PcgSet::id) }
    },
    PcgSetTranslation::class to TableDependency(PcgSet::class) { builder ->
        builder.innerJoin(PcgSetTranslation::class) { it.whereColumn(PcgSet::id, PcgSetTranslation::setId) }
    },
    PcgEra::class to TableDependency(PcgSet::class) { builder ->
        builder.innerJoin(PcgEra::class) { it.whereColumn(PcgEra::id, PcgSet::eraId) }
    },
    CardPrice::class to TableDependency(PcgPrint::class) { builder ->
        builder.leftJoin(CardPrice::class) {
            it
                .whereColumn(PcgPrint::id, CardPrice::cardId)
                .where(CardPrice::game, GameType.DISNEY_LORCANA)
        }
    },
    CardImage::class to TableDependency(PcgPrintTranslation::class) { builder ->
        builder.leftJoin(CardImage::class) { it.whereColumn(CardImage::printTranslationId, PcgPrintTranslation::id) }
    }
)

val pcgBasicSearchQueryConfig = SearchQueryConfig(
    table = PcgPrint::class,
    printIdColumn = PcgPrint::id,
    faceIndexColumn = null,
    languageColumns = arrayOf(PcgPrintTranslation::language, PcgCardTranslation::language, PcgSetTranslation::language),
    tableDependencies = tableDependencies,
)
