package dev.cowzy.cardgourmet.tcg.config.card.pcg

import dev.cowzy.cardgourmet.chef.commons.model.image.CardImage
import dev.cowzy.cardgourmet.chef.commons.model.image.CardImageColor
import dev.cowzy.cardgourmet.chef.commons.model.card.CardPrice
import dev.cowzy.cardgourmet.chef.commons.model.card.pcg.*
import dev.cowzy.cardgourmet.commons.database.game.GameType
import dev.cowzy.cardgourmet.chef.commons.model.set.pcg.PcgEra
import dev.cowzy.cardgourmet.chef.commons.model.set.pcg.PcgSet
import dev.cowzy.cardgourmet.chef.commons.model.set.pcg.PcgSetTranslation
import dev.cowzy.cardgourmet.chef.commons.model.set.pcg.PcgSetType
import dev.cowzy.cardgourmet.commons.getSerialName
import dev.cowzy.cardgourmet.commons.i18n.Strings
import dev.cowzy.cardgourmet.elrond.SearchQueryOperator
import dev.cowzy.cardgourmet.elrond.config.*
import dev.cowzy.cardgourmet.elrond.descriptor.AvailableInDescriptor
import dev.cowzy.cardgourmet.elrond.descriptor.SimplePropertyDescriptor
import dev.cowzy.cardgourmet.elrond.property.StringRegexProperty
import dev.cowzy.cardgourmet.elrond.values.autoArrayValues
import dev.cowzy.cardgourmet.elrond.values.autoValues
import dev.cowzy.cardgourmet.tcg.property.pcg.PcgRarityProperty
import dev.cowzy.cardgourmet.tcg.property.pcg.PcgStageProperty
import dev.cowzy.kuery.column.transformer.LocalDateColumnTransformer
import dev.cowzy.kuery.query.innerJoin
import dev.cowzy.kuery.query.leftJoin
import dev.cowzy.kuery.query.selectBuilder
import java.sql.Connection
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.reflect.KProperty1

private val propertyKeys = Strings.Query.Property
private val pcgPropertyKeys = Strings.Query.Pcg.Property

private fun getSetReleaseDates(dateColumn: KProperty1<*, *>, connection: Connection): Map<String, LocalDate> {
    return PcgSet::class.selectBuilder()
        .distinctOn(PcgSet::setCode)
        .select(PcgSet::setCode)
        .select(dateColumn)
        .get(connection) { row, index ->
            row.getString(index.getAndIncrement()) to LocalDateColumnTransformer.fromSql(row, index)
        }.mapNotNull { entry ->
            if (entry.first == null || entry.second == null) return@mapNotNull null
            entry.first to entry.second!!
        }.toMap()
}

private val getSetStartReleaseDates = { connection: Connection -> getSetReleaseDates(PcgSet::releaseStartDate, connection) }
private val getSetEndReleaseDates = { connection: Connection -> getSetReleaseDates(PcgSet::releaseEndDate, connection) }

private val getNames = { connection: Connection ->
    PcgCardTranslation::class.selectBuilder()
        .select(PcgCardTranslation::name)
        .select(PcgCardTranslation::language)
        .orderBy(PcgCardTranslation::name)
        .get(connection) { row, index ->
            val name = row.getString(index.getAndIncrement())
            val language = row.getString(index.getAndIncrement())
            language to name
        }.groupBy { it.first }.mapValues { it.value.associate { (_, name) -> name to name } }
}

private val getNameWordBank = { connection: Connection -> getNames(connection).toWordBank() }

fun SearchQueryFilterBuilder.configureBasicPcgCardFilters() {
    val weaknessDescriptor = SimplePropertyDescriptor(Strings.Query.Pcg.Comparison.WeakAgainst.TRUE, Strings.Query.Pcg.Comparison.WeakAgainst.FALSE, propertyKey = propertyKeys.CARD)
    val resistanceDescriptor = SimplePropertyDescriptor(Strings.Query.Pcg.Comparison.ResistantAgainst.TRUE, Strings.Query.Pcg.Comparison.ResistantAgainst.FALSE, propertyKey = propertyKeys.CARD)

    filter("name", "n") {
        simpleString(PcgCardTranslation::name, PcgCardTranslation::simpleName, propertyKeys.NAME) {
            valuesWithLanguage(getNames, { it }, "name") // TODO: { StringValue(it, true) }
            valuesWithLanguage(getNameWordBank, { it }, "name_part")
        }
    }

    filter("cn", "number", "collectornumber") {
        numericAndString(PcgPrint::collectorNumberValue, PcgPrint::collectorNumber, propertyKeys.COLLECTOR_NUMBER)
    }

    filter("sortorder", "sortvalue", "setorder", "releaseorder") {
        numeric(PcgPrint::sortValue, pcgPropertyKeys.SORT_VALUE)
    }

    filter("rarity") {
        property(PcgRarityProperty(valueProviderPool))
    }

    filter("mark", "regulationmark") {
        exactString(PcgPrint::regulationMark, pcgPropertyKeys.REGULATION_MARK) {
            strict(true)
            autoValues(PcgPrint::regulationMark)
        }
    }

    filter("artist", "illustrator", "artists", "illustrators") {
        stringArrayAndCardinality(PcgPrint::illustrators, propertyKeys.ARTIST_COUNT, propertyKeys.ARTIST) {
            autoArrayValues(PcgPrint::illustrators, "artist", false)
        }
    }

    filter("lang", "language", "printlang", "printlanguage") {
        enum<PcgLanguage>(
            PcgPrintTranslation::language,
            AvailableInDescriptor(propertyKeys.PRINT),
            "print_language",
            aliasResolver = { it.keys },
            display = { value, i18n, locale ->
                i18n.translate(locale, "${Strings.Query.Pcg.Languages.KEY}.${value.getSerialName()}")
            }
        )
    }

    filter("cardlang", "cardlanguage") {
        enum<PcgLanguage>(
            PcgCardTranslation::language,
            AvailableInDescriptor(propertyKeys.PRINT),
            "card_language",
            aliasResolver = { it.keys },
            display = { value, i18n, locale ->
                i18n.translate(locale, "${Strings.Query.Pcg.Languages.KEY}.${value.getSerialName()}")
            }
        )
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
        enum<PcgCardSuperType>(PcgCard::superType, pcgPropertyKeys.SUPERTYPE) { it.keys }
        stringArray(PcgCard::subTypes, pcgPropertyKeys.SUBTYPE) {
            strict(true)
            autoArrayValues(PcgCard::subTypes, "subtype", true)
        }
        enumArray(PcgCard::types, pcgPropertyKeys.ENERGY_TYPE) { it.keys }
    }

    filter("basetype", "supertype") {
        enum<PcgCardSuperType>(PcgCard::superType, pcgPropertyKeys.SUPERTYPE) { it.keys }
    }

    filter("energytype", "energytypes", "types", "energy", "energies", "color", "c", "colors") {
        enumArrayAndCardinality(PcgCard::types, pcgPropertyKeys.ENERGY_TYPE_COUNT, pcgPropertyKeys.ENERGY_TYPE) { it.keys }
    }

    filter("subtype", "subtypes") {
        stringArrayAndCardinality(PcgCard::subTypes, pcgPropertyKeys.SUBTYPE_COUNT, pcgPropertyKeys.SUBTYPE) {
            strict(true)
            autoArrayValues(PcgCard::subTypes, "subtype", true)
        }
    }

    filter("stage", "evolution", "evolutionstage") {
        property(PcgStageProperty(valueProviderPool))
        enum<PcgEvolutionStage>(PcgCard::evolutionStage, pcgPropertyKeys.EVOLUTION_STAGE)
    }

    filter("evolves", "evolvesfrom") {
        string(PcgCard::evolvesFromName, pcgPropertyKeys.EVOLVES_FROM) {
            autoValues(PcgCard::evolvesFromName)
        }
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
        enumArrayAndCardinality(PcgCard::weaknessTypes, pcgPropertyKeys.WEAKNESS_COUNT, weaknessDescriptor, "weakness_type") { it.keys }
        string(PcgCard::weaknessModifier, pcgPropertyKeys.WEAKNESS_MODIFIER)
    }

    filter("weaknesstype", "weaknesstypes") {
        enumArrayAndCardinality(PcgCard::weaknessTypes, pcgPropertyKeys.WEAKNESS_COUNT, weaknessDescriptor, "weakness_type") { it.keys }
    }

    filter("weaknessmodifier") {
        string(PcgCard::weaknessModifier, pcgPropertyKeys.WEAKNESS_MODIFIER)
    }

    filter("resistance", "resistances") {
        enumArrayAndCardinality(PcgCard::resistanceTypes, pcgPropertyKeys.RESISTANCE_COUNT, resistanceDescriptor, "resistance_type") { it.keys }
        string(PcgCard::resistanceModifier, pcgPropertyKeys.RESISTANCE_MODIFIER)
    }

    filter("resistancetype", "resistancetypes") {
        enumArrayAndCardinality(PcgCard::resistanceTypes, pcgPropertyKeys.RESISTANCE_COUNT, resistanceDescriptor, "resistance_type") { it.keys }
    }

    filter("resistancemodifier") {
        string(PcgCard::resistanceModifier, pcgPropertyKeys.RESISTANCE_MODIFIER)
    }

    filter("retreat", "retreatcost") {
        numeric(PcgCard::retreatCosts, pcgPropertyKeys.RETREAT_COST)
    }

    filter("set", "s", "edition", "e", "expansion") {
        uuid(PcgSet::id, propertyKeys.SET_ID)
        string(PcgSet::setCode, propertyKeys.SET_CODE) { autoValues(PcgSet::setCode) }
    }

    filter("setid") { uuid(PcgSet::id, propertyKeys.SET_ID) }
    filter("setname") {
        string(PcgSetTranslation::name, propertyKeys.SET_NAME) { autoValues(PcgSetTranslation::name) }
    }

    filter("setcode") {
        string(PcgSet::setCode, propertyKeys.SET_CODE) { autoValues(PcgSet::setCode) }
    }

    filter("settype") { enum<PcgSetType>(PcgSet::type, propertyKeys.SET_TYPE) }

    filter("region", "setregion") {
        enum<PcgRegion>(PcgSet::region, pcgPropertyKeys.REGION) { it.aliases }
    }

    filter("setprints", "publicsetprints") { numeric(PcgSet::printedPublicly, pcgPropertyKeys.PUBLIC_PRINT_COUNT) }
    filter("allsetprints", "totalsetprints") { numeric(PcgSet::printedTotal, pcgPropertyKeys.TOTAL_PRINT_COUNT) }

    filter("date", "releasedate", "startreleasedate") {
        date(PcgSet::releaseStartDate, pcgPropertyKeys.START_RELEASE_DATE) {
            values(getSetStartReleaseDates, { it.format(DateTimeFormatter.ISO_DATE) }, "set_code", merge = false)
        }
    }

    filter("year", "releaseyear", "startreleaseyear") {
        year(PcgSet::releaseStartDate, pcgPropertyKeys.START_RELEASE_YEAR) {
            values(getSetStartReleaseDates, { it.year }, "set_code", merge = false)
        }
    }

    filter("enddate", "endreleasedate") {
        date(PcgSet::releaseEndDate, pcgPropertyKeys.END_RELEASE_DATE) {
            values(getSetEndReleaseDates, { it.format(DateTimeFormatter.ISO_DATE) }, "set_code", merge = false)
        }
    }

    filter("endyear", "endreleaseyear") {
        year(PcgSet::releaseEndDate, pcgPropertyKeys.END_RELEASE_YEAR) {
            values(getSetEndReleaseDates, { it.year }, "set_code", merge = false)
        }
    }

    filter("era", "block") {
        uuid(PcgEra::id, pcgPropertyKeys.ERA_ID)
        string(PcgEra::name, pcgPropertyKeys.ERA_NAME) { autoValues(PcgEra::name) }
    }

    filter("eraid", "blockid") {
        uuid(PcgEra::id, pcgPropertyKeys.ERA_ID) // TODO
    }

    filter("eraname", "blockname") {
        string(PcgEra::name, pcgPropertyKeys.ERA_NAME) { autoValues(PcgEra::name) }
    }

    filter("cardid", "card") { uuid(PcgCard::id, propertyKeys.CARD_ID) }
    filter("printid", "print") { uuid(PcgPrint::id, propertyKeys.PRINT_ID) }

    filter("property", "properties", "tag", "tags") {
        enumArrayAndCardinality(PcgPrint::tags, propertyKeys.TAG_COUNT, propertyKeys.TAG) { it.keys }
    }

    val applyTagProperties: QueryFilterBuilder.() -> Unit = {
        enum<PcgRarity>(PcgPrint::rarity, propertyKeys.RARITY) { it.keys }
        enum<PcgCardSuperType>(PcgCard::superType, pcgPropertyKeys.SUPERTYPE) { it.keys }
        stringArray(PcgCard::subTypes, pcgPropertyKeys.SUBTYPE) {
            strict(true)
            autoArrayValues(PcgCard::subTypes, "subtype", true)
        }
        enumArray(PcgCard::types, pcgPropertyKeys.ENERGY_TYPE) { it.keys }
        enum<PcgEvolutionStage>(PcgCard::evolutionStage, pcgPropertyKeys.EVOLUTION_STAGE)
        enum<PcgRegion>(PcgSet::region, pcgPropertyKeys.REGION) { it.aliases }
        enumArray(PcgPrint::tags, propertyKeys.TAG) { it.keys }
    }

    filter("is", "has") {
        applyTagProperties()
    }

    filter("not") {
        inverted(true)
        applyTagProperties()
    }

    filter("artworkcolor", "artcolor") {
        stringArray(CardImageColor::nearestColors, propertyKeys.ARTWORK_COLOR)
    }

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
        builder.leftJoin(PcgPrintTranslation::class) { it.whereColumn(PcgPrint::id, PcgPrintTranslation::printId) }
    },
    PcgSet::class to TableDependency(PcgPrint::class) { builder ->
        builder.innerJoin(PcgSet::class) { it.whereColumn(PcgPrint::setId, PcgSet::id) }
    },
    PcgSetTranslation::class to TableDependency(PcgSet::class) { builder ->
        builder.leftJoin(PcgSetTranslation::class) { it.whereColumn(PcgSet::id, PcgSetTranslation::setId) }
    },
    PcgEra::class to TableDependency(PcgSet::class) { builder ->
        builder.innerJoin(PcgEra::class) { it.whereColumn(PcgEra::id, PcgSet::eraId) }
    },
    CardPrice::class to TableDependency(PcgPrint::class) { builder ->
        builder.leftJoin(CardPrice::class) {
            it
                .whereColumn(PcgPrint::id, CardPrice::cardId)
                .where(CardPrice::game, GameType.POKEMON_CARD_GAME)
        }
    },
    CardImage::class to TableDependency(PcgPrintTranslation::class) { builder ->
        builder.leftJoin(CardImage::class) { it.whereColumn(CardImage::printTranslationId, PcgPrintTranslation::id) }
    },
    CardImageColor::class to TableDependency(PcgPrintTranslation::class) { builder ->
        builder.leftJoin(CardImageColor::class) {
            it
                .where(CardImageColor::game, GameType.POKEMON_CARD_GAME)
                .whereColumn(CardImageColor::printTranslationId, PcgPrintTranslation::id)
        }
    }
)

val pcgBasicCardSearchQueryConfig = SearchQuerySqlConfig(
    baseTable = PcgPrint::class,
    tableDependencies = tableDependencies,
    customFields = mapOf(
        "printId" to CustomField(PcgPrint::id),
        "language" to CustomField(PcgPrintTranslation::language, PcgCardTranslation::language, PcgSetTranslation::language),
    )
)
