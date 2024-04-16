package elrond

fun String.toFullMatchRegex(): String {
    val regex = Regex("\\^?(.*)\\$?")
    val match = regex.find(this) ?: return "^.*$this.*$"
    return "^${match.groupValues[1]}$"
}
