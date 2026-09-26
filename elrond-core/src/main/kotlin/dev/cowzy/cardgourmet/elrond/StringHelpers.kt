package dev.cowzy.cardgourmet.elrond

fun String.toFullMatchRegex(): String {
    val regex = Regex("\\^?(.*)\\$?")
    val match = regex.find(this) ?: return "^.*$this.*$"
    return "^${match.groupValues[1]}$"
}

fun String.toPostgresRegex(): String {
    return this
        // Replace "\b" with "\y" as postgres uses other word boundaries
        .replace(Regex("\\\\b"), "\\\\y")
}
