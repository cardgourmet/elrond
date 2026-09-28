package dev.cowzy.cardgourmet.elrond.user

import dev.cowzy.cardgourmet.commons.user.User
import dev.cowzy.cardgourmet.elrond.ContextKey

/**
 * The [ContextKey] under which the currently authenticated [User] (if any) is stored in the
 * [dev.cowzy.cardgourmet.elrond.ContextAttributes].
 */
val UserKey = ContextKey<User>("user")
