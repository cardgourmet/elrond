package dev.cowzy.cardgourmet.elrond.config.pcg

import dev.cowzy.cardgourmet.chef.commons.model.image.CardImage
import dev.cowzy.cardgourmet.commons.database.card.CardPrice
import dev.cowzy.cardgourmet.commons.database.card.pcg.*
import dev.cowzy.cardgourmet.commons.database.game.GameType
import dev.cowzy.cardgourmet.commons.database.set.pcg.PcgEra
import dev.cowzy.cardgourmet.commons.database.set.pcg.PcgSet
import dev.cowzy.cardgourmet.commons.database.set.pcg.PcgSetTranslation
import dev.cowzy.cardgourmet.commons.i18n.Strings
import dev.cowzy.cardgourmet.elrond.config.SearchQueryConfig
import dev.cowzy.cardgourmet.elrond.config.SearchQueryConfigBuilder
import dev.cowzy.cardgourmet.elrond.config.TableDependency
import dev.cowzy.cardgourmet.elrond.values.pcg.PcgSetEndReleaseDateMappingProvider
import dev.cowzy.cardgourmet.elrond.values.pcg.PcgSetStartReleaseDateMappingProvider
import dev.cowzy.kuery.query.innerJoin
import dev.cowzy.kuery.query.leftJoin

private val propertyKeys = Strings.Query.Property

fun SearchQueryConfigBuilder.configureBasicPcgFilters() {
    val setStartReleaseDates = valueProviderPool.getOrPut("pcg_set_start_release_dates") { PcgSetStartReleaseDateMappingProvider(it) }
    val setEndReleaseDates = valueProviderPool.getOrPut("pcg_set_end_release_dates") { PcgSetEndReleaseDateMappingProvider(it) }

    filter("name", "n") {
        simpleString(PcgCardTranslation::name, PcgCardTranslation::simpleName, propertyKeys.NAME) { autoValues(false) }
    }

    filter("cn", "number", "collectornumber") {
        numericAndString(PcgPrint::collectorNumberValue, PcgPrint::collectorNumber, propertyKeys.COLLECTOR_NUMBER)
    }

    filter("rarity") {
        enum(PcgPrint::rarity, propertyKeys.RARITY)
    }

    filter("mark", "regulationmark") {
        exactString(PcgPrint::regulationMark, "DUMMY") { autoValues() }
    }

    filter("artist", "illustrator", "artists", "illustrators") {
        stringArrayAndCardinality(PcgPrint::illustrators, "DUMMY", propertyKeys.ARTIST) // TODO
    }

    filter("lang", "language") {
        enum(PcgPrintTranslation::language, propertyKeys.LANGUAGE)
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

    filter("energy", "energytypes", "energies", "energytypes") {
        enumArray(PcgCard::energies, "DUMMY") // TODO
    }

    filter("stage", "evolution", "evolutionstage") {
        enum(PcgCard::evolutionStage, "DUMMY") // TODO
    }

    filter("evolves", "evolvesfrom") {
        string(PcgCard::evolvesFromName, "DUMMY") { autoValues(false) }
    }

    filter("ability", "abilities", "abilitytype", "abilitytypes") {
        enumArray(PcgCard::abilityTypes, "DUMMY") // TODO
    }

    filter("effect", "effects", "effecttype", "effecttypes") {
        enumArray(PcgCard::effectTypes, "DUMMY") // TODO
    }

    filter("ruletype", "ruletypes") {
        enumArray(PcgCard::ruleTypes, "DUMMY") // TODO
    }

    filter("weakness", "weaknesses", "weaknesstype", "weaknesstypes") {
        enumArray(PcgCard::weaknessTypes, "DUMMY") // TODO
    }

    filter("resistance", "resistances", "resistancetype", "resistancetypes") {
        enumArray(PcgCard::resistanceTypes, "DUMMY") // TODO
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
        enum(PcgSet::region, "DUMMY") // TODO
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
