package dev.cowzy.cardgourmet.elrond.user

import dev.cowzy.cardgourmet.commons.database.deck.UserDeck
import dev.cowzy.cardgourmet.commons.i18n.Strings
import dev.cowzy.cardgourmet.commons.user.User
import dev.cowzy.cardgourmet.commons.user.UserCard
import dev.cowzy.cardgourmet.elrond.descriptor.EqualsDescriptor
import dev.cowzy.cardgourmet.elrond.descriptor.StringDescriptor
import dev.cowzy.cardgourmet.elrond.property.StringColumnProperty
import dev.cowzy.cardgourmet.elrond.property.UuidColumnProperty

private val propertyKeys = Strings.Query.Property

private val username = StringColumnProperty(User::username, descriptor = StringDescriptor(propertyKeys.USERNAME))
private val userId = UuidColumnProperty(UserCard::userId, descriptor = EqualsDescriptor(propertyKeys.USER_ID))

// TODO: deck name
private val deckId = UuidColumnProperty(UserDeck::deckId, descriptor = EqualsDescriptor(propertyKeys.USER_ID))

// TODO: userId, username (with auth)
// TODO: in:collection (with auth)
// TODO: deck:<id> / deck:<name> (with auth or public decks)
