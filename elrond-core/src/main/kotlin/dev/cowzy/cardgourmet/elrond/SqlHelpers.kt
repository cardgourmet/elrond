package dev.cowzy.cardgourmet.elrond

fun createSqlAlias(length: Int = 8): String {
    val allowedChars = ('a'..'z')
    return (1..length).map { allowedChars.random() }.joinToString("")
}
