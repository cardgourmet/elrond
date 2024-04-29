package dev.cowzy.cardgourmet.elrond.user.config.pcg

import dev.cowzy.cardgourmet.commons.database.card.pcg.PcgCardTranslation
import dev.cowzy.cardgourmet.commons.database.card.pcg.PcgLanguage
import dev.cowzy.cardgourmet.commons.database.card.pcg.PcgPrint
import dev.cowzy.cardgourmet.commons.database.game.GameType
import dev.cowzy.cardgourmet.commons.getSerialName
import dev.cowzy.cardgourmet.commons.i18n.Strings
import dev.cowzy.cardgourmet.commons.user.User
import dev.cowzy.cardgourmet.commons.user.UserCard
import dev.cowzy.cardgourmet.commons.user.UserCardAcquisition
import dev.cowzy.cardgourmet.commons.user.UserCardBinder
import dev.cowzy.cardgourmet.elrond.QueryFilter
import dev.cowzy.cardgourmet.elrond.config.TableDependency
import dev.cowzy.cardgourmet.elrond.config.pcg.pcgBasicSearchQueryConfig
import dev.cowzy.cardgourmet.elrond.descriptor.EqualsDescriptor
import dev.cowzy.cardgourmet.elrond.descriptor.StringDescriptor
import dev.cowzy.cardgourmet.elrond.property.ArrayCardinalityProperty
import dev.cowzy.cardgourmet.elrond.property.StringColumnProperty
import dev.cowzy.cardgourmet.elrond.user.config.createCollectionSearchQueryFilters
import dev.cowzy.cardgourmet.elrond.values.StaticValueProvider
import dev.cowzy.kuery.query.innerJoin
import dev.cowzy.kuery.query.leftJoin

private val propertyKeys = Strings.Query.Property
private val collectionPropertyKeys = Strings.Query.Collection.Property

private val languages = StaticValueProvider(PcgLanguage.values().map { it.getSerialName() }.toSet())
private val pcgLanguageMappings = PcgLanguage.values().associate { language -> language.name to language.getSerialName() }

// Collection properties
private val collectionFinishCount = ArrayCardinalityProperty(UserCard::finishes, propertyKey = propertyKeys.FINISH_COUNT)
//private val collectionFinishes = StringArrayColumnProperty(UserCard::finishes, valueProvider = StaticPcgProviders.finishes, descriptor = IsPresentDescriptor(propertyKeys.FINISH))
private val collectionMedium = StringColumnProperty(UserCard::medium, valueProvider = StaticValueProvider(arrayOf("paper")), mapContainsToEquals = true, descriptor = EqualsDescriptor(propertyKeys.MEDIUM))
private val collectionLanguage = StringColumnProperty(UserCard::language, mapContainsToEquals = true, valueProvider = languages, mappings = pcgLanguageMappings, descriptor = EqualsDescriptor(collectionPropertyKeys.LANGUAGE))

private val name = StringColumnProperty(PcgCardTranslation::name, simpleColumn = PcgCardTranslation::simpleName, descriptor = StringDescriptor(propertyKeys.NAME))
val pcgCollectionNameFilter = QueryFilter(arrayOf("name", "n"), name) // TODO (?)

fun createPcgCollectionSearchQueryFilters(): List<QueryFilter> {
    return createCollectionSearchQueryFilters() + listOf(
        pcgCollectionNameFilter,
//        QueryFilter(arrayOf("finishes", "finish"), collectionFinishCount, collectionFinishes),
        QueryFilter(arrayOf("medium", "mediums"), collectionMedium),
        QueryFilter(arrayOf("lang", "language", "userlang", "userlanguage"), collectionLanguage),
        // TODO: rarity
        // TODO: is:foil/not:foil
    )
}

private val tableDependencies = mapOf(
    UserCard::class to TableDependency(PcgPrint::class, PcgCardTranslation::class) { builder ->
        builder.leftJoin(UserCard::class) {
            it
                .whereColumn(UserCard::printId, PcgPrint::id)
                .where(UserCard::game, GameType.DISNEY_LORCANA)
        }
    },
    UserCardBinder::class to TableDependency(UserCard::class) { builder ->
        builder.leftJoin(UserCardBinder::class) { it.whereColumn(UserCardBinder::id, UserCard::binderId) }
    },
    UserCardAcquisition::class to TableDependency(UserCard::class) { builder ->
        builder.leftJoin(UserCardAcquisition::class) { it.whereColumn(UserCardAcquisition::id, UserCard::acquisitionId) }
    },
    User::class to TableDependency(UserCard::class) { builder ->
        builder.innerJoin(User::class) { it.whereColumn(User::id, UserCard::userId) }
    },
)

val pcgSearchQueryConfig = pcgBasicSearchQueryConfig.copy(
    languageColumns = arrayOf(UserCard::language, *pcgBasicSearchQueryConfig.languageColumns),
    tableDependencies = pcgBasicSearchQueryConfig.tableDependencies + tableDependencies,
)
