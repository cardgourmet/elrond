package dev.cowzy.cardgourmet.elrond.user.config

import dev.cowzy.cardgourmet.commons.Currency
import dev.cowzy.cardgourmet.commons.i18n.Strings
import dev.cowzy.cardgourmet.commons.user.UserCard
import dev.cowzy.cardgourmet.commons.user.UserCardAcquisition
import dev.cowzy.cardgourmet.commons.user.UserCardBinder
import dev.cowzy.cardgourmet.elrond.config.SearchQueryConfigBuilder
import dev.cowzy.cardgourmet.elrond.descriptor.SimplePropertyDescriptor
import dev.cowzy.cardgourmet.elrond.property.*
import dev.cowzy.cardgourmet.elrond.user.property.PrintConditionProperty

private val propertyKeys = Strings.Query.Property
private val collectionPropertyKeys = Strings.Query.Collection.Property

fun SearchQueryConfigBuilder.configureCollectionFilters() {
    filter("id") { uuid(UserCard::id, propertyKeys.USER_CARD_ID) }
    filter("quantity", "qty", "amount") { numeric(UserCard::amount, collectionPropertyKeys.QUANTITY) }
    filter("condition", "cond") { property(PrintConditionProperty()) }
    filter("created", "createdat") { date(UserCard::createdAt, propertyKeys.CREATED_AT) }
    filter("updated", "updatedat") { date(UserCard::updatedAt, propertyKeys.UPDATED_AT) }

    filter("acquired:none") {
        property(StaticNullColumnProperty(UserCard::acquiredAt, descriptor = SimplePropertyDescriptor(Strings.Query.Comparison.IsSet.KEY, collectionPropertyKeys.ACQUIRED_AT, true)))
    }

    filter("acquired", "acquiredat") { date(UserCard::acquiredAt, collectionPropertyKeys.ACQUIRED_AT) }
    filter("acquiredprice", "acquiredfor") { numeric(UserCard::acquiredPrice, collectionPropertyKeys.ACQUIRED_PRICE) }
    filter("acquiredcurrency") { enum<Currency>(UserCard::acquiredPriceCurrency, collectionPropertyKeys.ACQUIRED_CURRENCY) }
    filter("tags", "tag") { stringArray(UserCard::tags, propertyKeys.TAG) }

    filter("is:altered") {
        property(StaticColumnProperty(UserCard::isAltered, descriptor = SimplePropertyDescriptor(Strings.Query.Mtg.Comparison.IsAltered.KEY, propertyKeys.PRINT), key = "is_altered"))
    }

    filter("not:altered") {
        inverted(true)
        property(StaticColumnProperty(UserCard::isAltered, descriptor = SimplePropertyDescriptor(Strings.Query.Mtg.Comparison.IsAltered.KEY, propertyKeys.PRINT), key = "is_altered"))
    }

    filter("is:proxy") {
        property(StaticColumnProperty(UserCard::isProxy, descriptor = SimplePropertyDescriptor(Strings.Query.Mtg.Comparison.IsProxy.KEY, propertyKeys.PRINT), key = "is_proxy"))
    }

    filter("not:proxy") {
        inverted(true)
        property(StaticColumnProperty(UserCard::isProxy, descriptor = SimplePropertyDescriptor(Strings.Query.Mtg.Comparison.IsProxy.KEY, propertyKeys.PRINT), key = "is_proxy"))
    }

    filter("is:signed") {
        property(StaticColumnProperty(UserCard::isSigned, descriptor = SimplePropertyDescriptor(Strings.Query.Mtg.Comparison.IsSigned.KEY, propertyKeys.PRINT), key = "is_signed"))
    }

    filter("not:signed") {
        inverted(true)
        property(StaticColumnProperty(UserCard::isSigned, descriptor = SimplePropertyDescriptor(Strings.Query.Mtg.Comparison.IsSigned.KEY, propertyKeys.PRINT), key = "is_signed"))
    }

    filter("binder") {
        uuid(UserCard::binderId, collectionPropertyKeys.BINDER_ID)
        string(UserCardBinder::name, collectionPropertyKeys.BINDER)
    }

    filter("bindername") { string(UserCardBinder::name, collectionPropertyKeys.BINDER) }
    filter("binderid") { uuid(UserCard::binderId, collectionPropertyKeys.BINDER_ID) }

    filter("binder:none") {
        property(StaticNullColumnProperty(UserCard::binderId, descriptor = SimplePropertyDescriptor(Strings.Query.Comparison.IsSet.KEY, collectionPropertyKeys.BINDER, true)))
    }

    filter("acquisition") {
        uuid(UserCard::acquisitionId, collectionPropertyKeys.ACQUISITION_ID)
        string(UserCardAcquisition::name, collectionPropertyKeys.ACQUISITION)
    }

    filter("acquisitionname") { string(UserCardAcquisition::name, collectionPropertyKeys.ACQUISITION) }
    filter("acquisitionid") { uuid(UserCard::acquisitionId, collectionPropertyKeys.ACQUISITION_ID) }

    filter("acquisition:none") {
        property(StaticNullColumnProperty(UserCard::acquisitionId, descriptor = SimplePropertyDescriptor(Strings.Query.Comparison.IsSet.KEY, collectionPropertyKeys.ACQUISITION, true)))
    }

    // TODO: notes
    // TODO: has:image
}
