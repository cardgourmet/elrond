package dev.cowzy.cardgourmet.elrond.user.config.mtg

import dev.cowzy.cardgourmet.commons.database.card.mtg.MtgCardFaceTranslation
import dev.cowzy.cardgourmet.commons.database.card.mtg.MtgPrint
import dev.cowzy.cardgourmet.commons.database.game.GameType
import dev.cowzy.cardgourmet.commons.i18n.Strings
import dev.cowzy.cardgourmet.commons.user.User
import dev.cowzy.cardgourmet.commons.user.UserCard
import dev.cowzy.cardgourmet.commons.user.UserCardAcquisition
import dev.cowzy.cardgourmet.commons.user.UserCardBinder
import dev.cowzy.cardgourmet.elrond.QueryFilter
import dev.cowzy.cardgourmet.elrond.config.TableDependency
import dev.cowzy.cardgourmet.elrond.config.mtg.MtgValueProviders
import dev.cowzy.cardgourmet.elrond.config.mtg.StaticMtgProviders
import dev.cowzy.cardgourmet.elrond.config.mtg.mtgBasicSearchQueryConfig
import dev.cowzy.cardgourmet.elrond.descriptor.EqualsDescriptor
import dev.cowzy.cardgourmet.elrond.descriptor.IsPresentDescriptor
import dev.cowzy.cardgourmet.elrond.property.ArrayCardinalityProperty
import dev.cowzy.cardgourmet.elrond.property.StringArrayColumnProperty
import dev.cowzy.cardgourmet.elrond.property.StringColumnProperty
import dev.cowzy.cardgourmet.elrond.property.mtg.MtgNameProperty
import dev.cowzy.cardgourmet.elrond.property.mtg.MtgUserNameProperty
import dev.cowzy.cardgourmet.elrond.user.config.createCollectionSearchQueryFilters
import dev.cowzy.cardgourmet.elrond.user.property.mtg.MtgUserCardFoilProperty
import dev.cowzy.kuery.query.innerJoin
import dev.cowzy.kuery.query.leftJoin

private val propertyKeys = Strings.Query.Property
private val collectionPropertyKeys = Strings.Query.Collection.Property

// Collection properties
private val collectionFinishCount = ArrayCardinalityProperty(UserCard::finishes, propertyKey = propertyKeys.FINISH_COUNT)
private val collectionFinishes = StringArrayColumnProperty(UserCard::finishes, valueProvider = StaticMtgProviders.finishes, mappings = StaticMtgProviders.mtgFinishMappings, descriptor = IsPresentDescriptor(propertyKeys.FINISH))
private val collectionMedium = StringColumnProperty(UserCard::medium, valueProvider = StaticMtgProviders.mediums, mappings = StaticMtgProviders.mtgMediumMappings, mapContainsToEquals = true, descriptor = EqualsDescriptor(propertyKeys.MEDIUM))
private val collectionLanguage = StringColumnProperty(UserCard::language, mapContainsToEquals = true, valueProvider = StaticMtgProviders.languages, mappings = StaticMtgProviders.mtgLanguageMappings, descriptor = EqualsDescriptor(collectionPropertyKeys.LANGUAGE))
private val collectionFoil = MtgUserCardFoilProperty()
private val collectionNotFoil = MtgUserCardFoilProperty(inverted = true)

fun createMtgCollectionDefaultFilter(providers: MtgValueProviders): QueryFilter {
    val property = MtgUserNameProperty(valueProvider = providers.names)
    return QueryFilter(arrayOf("name", "n"), property)
}

fun createMtgCollectionSearchQueryFilters(): List<QueryFilter> {
    return createCollectionSearchQueryFilters() + listOf(
        QueryFilter(arrayOf("finishes", "finish"), collectionFinishCount, collectionFinishes),
        QueryFilter(arrayOf("medium", "mediums"), collectionMedium),
        QueryFilter(arrayOf("lang", "language", "userlang", "userlanguage"), collectionLanguage),
        QueryFilter(arrayOf("is:foil"), collectionFoil),
        QueryFilter(arrayOf("not:foil"), collectionNotFoil),
    )
}

private val tableDependencies = mapOf(
    UserCard::class to TableDependency(MtgPrint::class, MtgCardFaceTranslation::class) { builder ->
        builder.leftJoin(UserCard::class) {
            it
                .whereColumn(UserCard::printId, MtgPrint::id)
                .where(UserCard::game, GameType.MAGIC_THE_GATHERING)
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

val mtgSearchQueryConfig = mtgBasicSearchQueryConfig.copy(
    languageColumns = arrayOf(UserCard::language, *mtgBasicSearchQueryConfig.languageColumns),
    tableDependencies = mtgBasicSearchQueryConfig.tableDependencies + tableDependencies,
)
