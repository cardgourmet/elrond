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
private val pcgPropertyKeys = Strings.Query.Pcg.Property

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
        stringArrayAndCardinality(PcgPrint::illustrators, propertyKeys.ARTIST_COUNT, propertyKeys.ARTIST)
    }

    filter("lang", "language", "printlang", "printlanguage") {
        enum(PcgPrintTranslation::language, propertyKeys.PRINT_LANGUAGE) { it.keys }
    }

    filter("cardlang", "cardlanguage") {
        enum(PcgCardTranslation::language, propertyKeys.CARD_LANGUAGE) { it.keys }
    }

    filter("flavor", "flavortext") {
        simpleString(PcgPrintTranslation::flavorText, PcgPrintTranslation::simpleFlavorText, propertyKeys.FLAVOR_TEXT)
    }

    filter("text", "o", "oracle", "oracletext", "fulloracle", "fo", "fulloracletext") {
        simpleString(PcgCardTranslation::text, PcgCardTranslation::simpleText, propertyKeys.TEXT)
    }

    filter("reminder", "reminders", "rule", "rules") {
        cardinality(PcgCard::ruleTypes, pcgPropertyKeys.RULE_COUNT)
        simpleString(PcgCardTranslation::reminderText, PcgCardTranslation::simpleReminderText, pcgPropertyKeys.RULE)
    }

    filter("hp", "hitpoints", "health", "healthpoints") {
        numeric(PcgCard::hp, pcgPropertyKeys.HIT_POINTS)
    }

    filter("type", "t") {
        enum(PcgCard::superType, pcgPropertyKeys.SUPERTYPE) { it.keys }
        stringArray(PcgCard::subTypes, pcgPropertyKeys.SUBTYPE) { autoMappings(subTypeMappings) }
        enumArray(PcgCard::types, pcgPropertyKeys.ENERGY_TYPE) { it.keys }
    }

    filter("supertype") {
        enum(PcgCard::superType, pcgPropertyKeys.SUPERTYPE) { it.keys }
    }

    filter("types", "energy", "energies", "energytypes", "color", "c", "colors") {
        enumArrayAndCardinality(PcgCard::types, pcgPropertyKeys.ENERGY_TYPE_COUNT, pcgPropertyKeys.ENERGY_TYPE) { it.keys }
    }

    filter("subtype", "subtypes") {
        stringArrayAndCardinality(PcgCard::subTypes, pcgPropertyKeys.SUBTYPE_COUNT, pcgPropertyKeys.SUBTYPE) {
            autoMappings(subTypeMappings)
        }
    }

    filter("stage", "evolution", "evolutionstage") {
        enum(PcgCard::evolutionStage, pcgPropertyKeys.EVOLUTION_STAGE)
    }

    filter("evolves", "evolvesfrom") {
        string(PcgCard::evolvesFromName, pcgPropertyKeys.EVOLVES_FROM) { autoValues(false) }
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
            propertyKey = pcgPropertyKeys.ABILITY_NAME
        ))
    }

    filter("damage", "attackdamage") {
        property(StringRegexProperty(
            PcgCardTranslation::text,
            { value, operator ->
                when (operator) {
                    SearchQueryOperator.CONTAINS -> "\\[(\\S+\\s)?\"[^\"]+\"\\s$value\\D?]"
                    SearchQueryOperator.EQUALS -> "\\[(\\S+\\s)?\"[^\"]+\"\\s$value]"
                    else -> value
                }
            }, // TODO: range checks
            propertyKey = pcgPropertyKeys.ATTACK_DAMAGE
        ))
    }

    // TODO: custom property for value display/transform
    filter("attackcost") {
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
            propertyKey = pcgPropertyKeys.ATTACK_COST
        ))
    }

    filter("ability", "abilities", "abilitytype", "abilitytypes") {
        ignoreReference("ability")
        enumArrayAndCardinality(PcgCard::abilityTypes, pcgPropertyKeys.ABILITY_COUNT, pcgPropertyKeys.ABILITY_TYPE) { it.keys }
    }

    filter("effect", "effects", "effecttype", "effecttypes") {
        enumArrayAndCardinality(PcgCard::effectTypes, pcgPropertyKeys.EFFECT_COUNT, pcgPropertyKeys.EFFECT_TYPE) { it.keys }
    }

    filter("ruletype", "ruletypes") {
        enumArrayAndCardinality(PcgCard::ruleTypes, pcgPropertyKeys.RULE_COUNT, pcgPropertyKeys.RULE_TYPE) { it.keys }
    }

    filter("weakness", "weaknesses") {
        enumArrayAndCardinality(PcgCard::weaknessTypes, pcgPropertyKeys.WEAKNESS_COUNT, "DUMMY") { it.keys } // TODO: custom descriptor
        string(PcgCard::weaknessModifier, pcgPropertyKeys.WEAKNESS_MODIFIER) { autoValues(false) }
    }

    filter("weaknesstype", "weaknesstypes") {
        enumArrayAndCardinality(PcgCard::weaknessTypes, pcgPropertyKeys.WEAKNESS_COUNT, "DUMMY") { it.keys } // TODO: custom descriptor
    }

    filter("weaknessmodifier") {
        string(PcgCard::weaknessModifier, pcgPropertyKeys.WEAKNESS_MODIFIER) { autoValues(false) }
    }

    filter("resistance", "resistances") {
        enumArrayAndCardinality(PcgCard::resistanceTypes, pcgPropertyKeys.RESISTANCE_COUNT, "DUMMY") { it.keys } // TODO: custom descriptor
        string(PcgCard::resistanceModifier, pcgPropertyKeys.RESISTANCE_MODIFIER) { autoValues(false) }
    }

    filter("resistancetype", "resistancetypes") {
        enumArrayAndCardinality(PcgCard::resistanceTypes, pcgPropertyKeys.RESISTANCE_COUNT, "DUMMY") { it.keys } // TODO: custom descriptor
    }

    filter("resistancemodifier") {
        string(PcgCard::resistanceModifier, pcgPropertyKeys.RESISTANCE_MODIFIER) { autoValues(false) }
    }

    filter("retreat", "retreatcost") {
        numeric(PcgCard::retreatCosts, pcgPropertyKeys.RETREAT_COST)
    }

    filter("set", "s", "edition", "e", "expansion") {
        uuid(PcgSet::id, propertyKeys.SET_ID)
        string(PcgSet::setCode, propertyKeys.SET_CODE) { autoValues() }
    }

    filter("setid") { uuid(PcgSet::id, propertyKeys.SET_ID) }
    filter("setname") {
        string(PcgSetTranslation::name, propertyKeys.SET_NAME) { autoValues(false) }
    }

    filter("setcode") {
        string(PcgSet::setCode, propertyKeys.SET_CODE) { autoValues() }
    }

    filter("settype") { enum(PcgSet::type, propertyKeys.SET_TYPE) }

    filter("region", "setregion") {
        enum(PcgSet::region, pcgPropertyKeys.REGION) { it.aliases }
    }

    filter("setprints", "publicsetprints") { numeric(PcgSet::printedPublicly, pcgPropertyKeys.PUBLIC_PRINT_COUNT) }
    filter("allsetprints", "totalsetprints") { numeric(PcgSet::printedTotal, pcgPropertyKeys.TOTAL_PRINT_COUNT) }

    filter("date", "releasedate", "startreleasedate") {
        dateByMapping(PcgSet::releaseStartDate, setStartReleaseDates, pcgPropertyKeys.START_RELEASE_DATE)
        date(PcgSet::releaseStartDate, pcgPropertyKeys.START_RELEASE_DATE)
    }

    filter("year", "releaseyear", "startreleaseyear") {
        yearByMapping(PcgSet::releaseStartDate, setStartReleaseDates, pcgPropertyKeys.START_RELEASE_YEAR)
        year(PcgSet::releaseStartDate, pcgPropertyKeys.START_RELEASE_YEAR)
    }

    filter("enddate", "endreleasedate") {
        dateByMapping(PcgSet::releaseEndDate, setEndReleaseDates, pcgPropertyKeys.END_RELEASE_DATE)
        date(PcgSet::releaseEndDate, pcgPropertyKeys.END_RELEASE_DATE)
    }

    filter("endyear", "endreleaseyear") {
        yearByMapping(PcgSet::releaseEndDate, setEndReleaseDates, pcgPropertyKeys.END_RELEASE_YEAR)
        year(PcgSet::releaseEndDate, pcgPropertyKeys.END_RELEASE_YEAR)
    }

    filter("era", "block") {
        uuid(PcgEra::id, pcgPropertyKeys.ERA_ID)
        string(PcgEra::name, pcgPropertyKeys.ERA_NAME) { autoValues(false) }
    }

    filter("eraid", "blockid") {
        uuid(PcgEra::id, pcgPropertyKeys.ERA_ID) // TODO
    }

    filter("eraname", "blockname") {
        string(PcgEra::name, pcgPropertyKeys.ERA_NAME) { autoValues(false) }
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
