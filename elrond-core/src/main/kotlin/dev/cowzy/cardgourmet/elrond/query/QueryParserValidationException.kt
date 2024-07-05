package dev.cowzy.cardgourmet.elrond.query

class QueryParserValidationException(val failedRules: Set<QueryValidationRule>, cause: Throwable? = null)
    : Exception("One or more validation rules failed while parsing the query: [${failedRules.joinToString()}]", cause)