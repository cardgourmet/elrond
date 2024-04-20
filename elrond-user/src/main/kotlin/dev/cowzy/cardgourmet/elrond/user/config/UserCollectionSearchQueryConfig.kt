package dev.cowzy.cardgourmet.elrond.user.config

import dev.cowzy.cardgourmet.commons.i18n.Strings
import dev.cowzy.cardgourmet.commons.user.UserCard
import dev.cowzy.cardgourmet.commons.user.UserCardAcquisition
import dev.cowzy.cardgourmet.commons.user.UserCardBinder
import dev.cowzy.cardgourmet.elrond.QueryFilter
import dev.cowzy.cardgourmet.elrond.descriptor.EqualsDescriptor
import dev.cowzy.cardgourmet.elrond.descriptor.IsPresentDescriptor
import dev.cowzy.cardgourmet.elrond.descriptor.SimplePropertyDescriptor
import dev.cowzy.cardgourmet.elrond.descriptor.StringDescriptor
import dev.cowzy.cardgourmet.elrond.property.*
import dev.cowzy.cardgourmet.elrond.user.property.PrintConditionProperty

private val propertyKeys = Strings.Query.Property
private val collectionPropertyKeys = Strings.Query.Collection.Property

private val userCardId = UuidColumnProperty(UserCard::id, descriptor = EqualsDescriptor(propertyKeys.USER_CARD_ID))
private val collectionQuantity = NumericColumnProperty(UserCard::amount, propertyKey = collectionPropertyKeys.QUANTITY)
private val collectionCondition = PrintConditionProperty()
private val collectionCreatedAt = DateProperty(UserCard::createdAt, propertyKey = propertyKeys.CREATED_AT)
private val collectionUpdatedAt = DateProperty(UserCard::updatedAt, propertyKey = propertyKeys.UPDATED_AT)
private val collectionTags = StringArrayColumnProperty(UserCard::tags, descriptor = IsPresentDescriptor(propertyKeys.TAG))
private val collectionTagCount = ArrayCardinalityProperty(UserCard::tags, propertyKey = propertyKeys.TAG_COUNT)
private val collectionAltered = StaticColumnProperty(UserCard::isAltered, descriptor = SimplePropertyDescriptor(Strings.Query.Mtg.Comparison.IsAltered.KEY, propertyKeys.PRINT))
private val collectionProxy = StaticColumnProperty(UserCard::isProxy, descriptor = SimplePropertyDescriptor(Strings.Query.Mtg.Comparison.IsProxy.KEY, propertyKeys.PRINT))
private val collectionSigned = StaticColumnProperty(UserCard::isSigned, descriptor = SimplePropertyDescriptor(Strings.Query.Mtg.Comparison.IsSigned.KEY, propertyKeys.PRINT))
private val collectionNotAltered = StaticColumnProperty(UserCard::isAltered, inverted = true, descriptor = SimplePropertyDescriptor(Strings.Query.Mtg.Comparison.IsAltered.KEY, propertyKeys.PRINT, true))
private val collectionNotProxy = StaticColumnProperty(UserCard::isProxy, inverted = true, descriptor = SimplePropertyDescriptor(Strings.Query.Mtg.Comparison.IsProxy.KEY, propertyKeys.PRINT, true))
private val collectionNotSigned = StaticColumnProperty(UserCard::isSigned, inverted = true, descriptor = SimplePropertyDescriptor(Strings.Query.Mtg.Comparison.IsSigned.KEY, propertyKeys.PRINT, true))
private val collectionAcquiredNull = StaticNullColumnProperty(UserCard::acquiredAt, descriptor = SimplePropertyDescriptor(Strings.Query.Comparison.IsSet.KEY, collectionPropertyKeys.ACQUIRED_AT, true))
private val collectionAcquired = DateProperty(UserCard::acquiredAt, propertyKey = collectionPropertyKeys.ACQUIRED_AT)
private val collectionAcquiredPrice = NumericColumnProperty(UserCard::acquiredPrice, propertyKey = collectionPropertyKeys.ACQUIRED_PRICE)
private val collectionAcquiredCurrency = StringColumnProperty(UserCard::acquiredPriceCurrency, mapContainsToEquals = true, descriptor = EqualsDescriptor(collectionPropertyKeys.ACQUIRED_CURRENCY))
private val collectionBinderId = UuidColumnProperty(UserCard::binderId, descriptor = EqualsDescriptor(collectionPropertyKeys.BINDER_ID))
private val collectionBinderName = StringColumnProperty(UserCardBinder::name, descriptor = StringDescriptor(collectionPropertyKeys.BINDER))
private val collectionBinderNull = StaticNullColumnProperty(UserCard::binderId, descriptor = SimplePropertyDescriptor(Strings.Query.Comparison.IsSet.KEY, collectionPropertyKeys.BINDER, true))
private val collectionAcquisitionId = UuidColumnProperty(UserCard::acquisitionId, descriptor = EqualsDescriptor(collectionPropertyKeys.ACQUISITION_ID))
private val collectionAcquisitionName = StringColumnProperty(UserCardAcquisition::name, descriptor = StringDescriptor(collectionPropertyKeys.ACQUISITION))
private val collectionAcquisitionNull = StaticNullColumnProperty(UserCard::acquisitionId, descriptor = SimplePropertyDescriptor(Strings.Query.Comparison.IsSet.KEY, collectionPropertyKeys.ACQUISITION, true))

fun createCollectionSearchQueryFilters(): List<QueryFilter> {
    return listOf(
        QueryFilter(arrayOf("id"), userCardId),
        QueryFilter(arrayOf("quantity", "qty", "amount"), collectionQuantity),
        QueryFilter(arrayOf("condition", "cond"), collectionCondition),
        QueryFilter(arrayOf("created"), collectionCreatedAt),
        QueryFilter(arrayOf("updated"), collectionUpdatedAt),
        QueryFilter(arrayOf("acquired:none"), collectionAcquiredNull),
        QueryFilter(arrayOf("acquired"), collectionAcquired),
        QueryFilter(arrayOf("acquiredprice", "acquiredfor"), collectionAcquiredPrice),
        QueryFilter(arrayOf("acquiredcurrency"), collectionAcquiredCurrency),
        QueryFilter(arrayOf("tags", "tag"), collectionTagCount, collectionTags),
        QueryFilter(arrayOf("is:altered"), collectionAltered),
        QueryFilter(arrayOf("is:proxy"), collectionProxy),
        QueryFilter(arrayOf("is:signed"), collectionSigned),
        QueryFilter(arrayOf("not:altered"), collectionNotAltered),
        QueryFilter(arrayOf("not:proxy"), collectionNotProxy),
        QueryFilter(arrayOf("not:signed"), collectionNotSigned),
        QueryFilter(arrayOf("binder", "bindername"), collectionBinderId, collectionBinderName),
        QueryFilter(arrayOf("binderid"), collectionBinderId),
        QueryFilter(arrayOf("acquisition", "acquisitionname"), collectionAcquisitionId, collectionAcquisitionName),
        QueryFilter(arrayOf("acquisitionid"), collectionAcquisitionId),
        QueryFilter(arrayOf("binder:none"), collectionBinderNull),
        QueryFilter(arrayOf("acquisition:none"), collectionAcquisitionNull),
        // TODO: notes
        // TODO: has:image
    )
}
