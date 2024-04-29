package dev.cowzy.cardgourmet.elrond.config.pcg

import dev.cowzy.cardgourmet.commons.database.card.CardPrice
import dev.cowzy.cardgourmet.commons.database.card.pcg.*
import dev.cowzy.cardgourmet.commons.database.game.GameType
import dev.cowzy.cardgourmet.commons.database.set.pcg.PcgEra
import dev.cowzy.cardgourmet.commons.database.set.pcg.PcgSet
import dev.cowzy.cardgourmet.commons.database.set.pcg.PcgSetTranslation
import dev.cowzy.cardgourmet.commons.i18n.Strings
import dev.cowzy.cardgourmet.elrond.QueryFilter
import dev.cowzy.cardgourmet.elrond.config.SearchQueryConfig
import dev.cowzy.cardgourmet.elrond.config.TableDependency
import dev.cowzy.cardgourmet.elrond.descriptor.EqualsDescriptor
import dev.cowzy.cardgourmet.elrond.descriptor.IsPresentDescriptor
import dev.cowzy.cardgourmet.elrond.descriptor.StringDescriptor
import dev.cowzy.cardgourmet.elrond.property.*
import dev.cowzy.cardgourmet.farbeagle.model.CardImage
import dev.cowzy.kuery.query.innerJoin
import dev.cowzy.kuery.query.leftJoin

private val propertyKeys = Strings.Query.Property

// Numerics
private val collectorNumberValue = NumericColumnProperty(PcgPrint::collectorNumberValue, propertyKey = propertyKeys.COLLECTOR_NUMBER)
private val healthPoints = NumericColumnProperty(PcgCard::hp, propertyKey = "DUMMY") // TODO
private val retreatCost = NumericColumnProperty(PcgCard::retreatCosts, propertyKey = "DUMMY") // TODO
private val setPrints = NumericColumnProperty(PcgSet::printedPublicly, propertyKey = "DUMMY") // TODO
private val totalSetPrints = NumericColumnProperty(PcgSet::printedTotal, propertyKey = "DUMMY") // TODO
private val startReleaseYear = YearOfDateProperty(PcgSet::releaseStartDate, propertyKey = propertyKeys.RELEASE_YEAR)
private val endReleaseYear = YearOfDateProperty(PcgSet::releaseEndDate, propertyKey = propertyKeys.RELEASE_YEAR) // TODO: custom property key
private val artistCount = ArrayCardinalityProperty(PcgPrint::illustrators, propertyKey = "DUMMY") // TODO
private val energyTypeCount = ArrayCardinalityProperty(PcgPrint::illustrators, propertyKey = "DUMMY") // TODO
private val abilityTypeCount = ArrayCardinalityProperty(PcgCard::abilityTypes, propertyKey = "DUMMY") // TODO
private val effectTypeCount = ArrayCardinalityProperty(PcgCard::effectTypes, propertyKey = "DUMMY") // TODO
private val ruleTypeCount = ArrayCardinalityProperty(PcgCard::ruleTypes, propertyKey = "DUMMY") // TODO
private val weaknessTypeCount = ArrayCardinalityProperty(PcgCard::weaknessTypes, propertyKey = "DUMMY") // TODO
private val resistanceTypeCount = ArrayCardinalityProperty(PcgCard::resistanceTypes, propertyKey = "DUMMY") // TODO

// Strings
private val collectorNumber = StringColumnProperty(PcgPrint::collectorNumber, descriptor = StringDescriptor(propertyKeys.COLLECTOR_NUMBER))
private val regulationMark = StringColumnProperty(PcgPrint::regulationMark, descriptor = StringDescriptor("DUMMY")) // TODO, value provider
private val name = StringColumnProperty(PcgCardTranslation::name, simpleColumn = PcgCardTranslation::simpleName, descriptor = StringDescriptor(propertyKeys.NAME))
private val text = StringColumnProperty(PcgCardTranslation::text, simpleColumn = PcgCardTranslation::simpleText, descriptor = StringDescriptor(propertyKeys.TEXT))
private val flavorText = StringColumnProperty(PcgPrintTranslation::flavorText, simpleColumn = PcgPrintTranslation::simpleFlavorText, descriptor = StringDescriptor(propertyKeys.FLAVOR_TEXT))
private val reminder = StringColumnProperty(PcgCardTranslation::reminderText, simpleColumn = PcgCardTranslation::simpleReminderText, descriptor = StringDescriptor("DUMMY")) // TODO
private val region = enumColumnProperty(PcgSet::region, propertyKey = "DUMMY") // TODO
private val setName = StringColumnProperty(PcgSetTranslation::name, descriptor = StringDescriptor(propertyKeys.SET_NAME))
private val setType = StringColumnProperty(PcgSet::expansionType, descriptor = StringDescriptor("DUMMY")) // TODO
private val rarity = enumColumnProperty(PcgPrint::rarity, propertyKey = propertyKeys.RARITY)
private val language = enumColumnProperty(PcgPrintTranslation::language, propertyKey = propertyKeys.LANGUAGE) // TODO: language mappings
private val evolutionStage = enumColumnProperty(PcgCard::evolutionStage, propertyKey = "DUMMY") // TODO
private val evolvesFrom = StringColumnProperty(PcgCard::evolvesFromName, descriptor = StringDescriptor("DUMMY")) // TODO, value provider
private val setCode = StringColumnProperty(PcgSet::setCode, descriptor = StringDescriptor(propertyKeys.SET_CODE)) // TODO: value provider
private val eraName = StringColumnProperty(PcgEra::name, descriptor = StringDescriptor("DUMMY")) // TODO

// String Arrays
private val artists = StringArrayColumnProperty(PcgPrint::illustrators, descriptor = IsPresentDescriptor(propertyKeys.ARTIST))
private val energyTypes = enumArrayColumnProperty(PcgCard::energies, propertyKey = "DUMMY") // TODO
private val abilityTypes = enumArrayColumnProperty(PcgCard::abilityTypes, propertyKey = "DUMMY") // TODO
private val effectTypes = enumArrayColumnProperty(PcgCard::effectTypes, propertyKey = "DUMMY") // TODO
private val ruleTypes = enumArrayColumnProperty(PcgCard::ruleTypes, propertyKey = "DUMMY") // TODO
private val weaknessTypes = enumArrayColumnProperty(PcgCard::weaknessTypes, propertyKey = "DUMMY") // TODO
private val resistanceTypes = enumArrayColumnProperty(PcgCard::resistanceTypes, propertyKey = "DUMMY") // TODO

// Dates
private val startReleaseDate = DateProperty(PcgSet::releaseStartDate, propertyKey = propertyKeys.RELEASE_DATE)
private val endReleaseDate = DateProperty(PcgSet::releaseEndDate, propertyKey = propertyKeys.RELEASE_DATE) // TODO: custom property key

// Identifiers
private val printId = UuidColumnProperty(PcgPrint::id, EqualsDescriptor(propertyKeys.PRINT_ID))
private val cardId = UuidColumnProperty(PcgCard::id, EqualsDescriptor(propertyKeys.CARD_ID))
private val setId = UuidColumnProperty(PcgSet::id, EqualsDescriptor("DUMMY")) // TODO
private val eraId = UuidColumnProperty(PcgEra::id, EqualsDescriptor("DUMMY")) // TODO

val pcgNameFilter = QueryFilter(arrayOf("n", "name"), name)

fun createBasicPcgSearchQueryFilters(): List<QueryFilter> {
    return listOf(
        // Print filters
        QueryFilter(arrayOf("printid", "print"), printId),
        QueryFilter(arrayOf("cn", "number", "collectornumber"), collectorNumberValue, collectorNumber),
        QueryFilter(arrayOf("rarity"), rarity), // TODO: numeric comparison
        QueryFilter(arrayOf("mark", "regulationmark"), regulationMark),
        QueryFilter(arrayOf("artist", "illustrator", "artists", "illustrators"), artistCount, artists),

        // Translation filters
        QueryFilter(arrayOf("lang", "language"), language),
        QueryFilter(arrayOf("flavor", "flavortext"), flavorText),
        pcgNameFilter,
        QueryFilter(arrayOf("text", "o", "oracle", "oracletext", "fulloracle", "fo", "fulloracletext"), text),
        QueryFilter(arrayOf("reminder", "reminders", "rule", "rules"), reminder),

        // Card filters
        QueryFilter(arrayOf("cardid", "card"), cardId),
        QueryFilter(arrayOf("hp", "health", "healthpoints"), healthPoints),
        QueryFilter(arrayOf("energy", "energytypes", "energies", "energytypes"), energyTypeCount, energyTypes),
        QueryFilter(arrayOf("stage", "evolution", "evolutionstage"), evolutionStage),
        QueryFilter(arrayOf("evolves", "evolvesfrom"), evolvesFrom),
        QueryFilter(arrayOf("ability", "abilities", "abilitytype", "abilitytypes"), abilityTypeCount, abilityTypes),
        QueryFilter(arrayOf("effect", "effects", "effecttype", "effecttypes"), effectTypeCount, effectTypes),
        QueryFilter(arrayOf("ruletype", "ruletypes"), ruleTypeCount, ruleTypes),
        QueryFilter(arrayOf("weakness", "weaknesses", "weaknesstype", "weaknesstypes"), weaknessTypeCount, weaknessTypes),
        QueryFilter(arrayOf("resistance", "resistances", "resistancetype", "resistancetypes"), resistanceTypeCount, resistanceTypes),
        QueryFilter(arrayOf("retreat", "retreatcost"), retreatCost),

        // Set filters
        QueryFilter(arrayOf("set", "s", "edition", "e", "expansion"), setId, setCode, setName),
        QueryFilter(arrayOf("setid"), setId),
        QueryFilter(arrayOf("setname"), setName),
        QueryFilter(arrayOf("setcode"), setCode),
        QueryFilter(arrayOf("region"), region),
        QueryFilter(arrayOf("settype"), setType),
        QueryFilter(arrayOf("setprints", "publicsetprints"), setPrints),
        QueryFilter(arrayOf("allsetprints", "totalsetprints"), totalSetPrints),
        QueryFilter(arrayOf("date", "releasedate", "startreleasedate"), startReleaseDate),
        QueryFilter(arrayOf("releasedate"), startReleaseYear), // TODO: mappings by set code
        QueryFilter(arrayOf("year", "releaseyear", "startreleaseyear"), endReleaseDate),
        QueryFilter(arrayOf("endreleaseyear"), endReleaseYear), // TODO: mappings by set code

        // Era filters
        QueryFilter(arrayOf("era"), eraId, eraName),
        QueryFilter(arrayOf("eraid"), eraId),
        QueryFilter(arrayOf("eraname"), eraName),

        // TODO: is/has/not
        // TODO: new/in
        // TODO: prints/sets (reprints)
    )
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
