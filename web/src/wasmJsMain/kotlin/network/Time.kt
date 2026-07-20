package network

/** Timestamp ISO-8601 actual del navegador, para filtros de vigencia en PostgREST. */
fun nowIso(): String = js("new Date().toISOString()")
