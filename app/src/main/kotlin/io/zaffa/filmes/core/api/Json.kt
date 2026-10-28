package io.zaffa.filmes.core.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Leitura TOLERANTE de JSON.
 *
 * Por que não `@Serializable data class`: painéis Xtream são notoriamente
 * inconsistentes — o mesmo campo vem ora como número (`"stream_id": 123`),
 * ora como texto (`"stream_id": "123"`), ora ausente, ora como `false` em vez
 * de `null`. Com classes anotadas, qualquer uma dessas variações joga uma
 * exceção e o catálogo inteiro deixa de carregar por causa de um item.
 *
 * Além disso, este painel é um CORE/Coreplay, que pode nem seguir o Xtream à
 * risca. Ler campo a campo custa um pouco mais de código e sobrevive a tudo.
 */
internal val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
}

/** Texto de um campo, aceitando número ou string; `null` se ausente ou vazio. */
internal fun JsonObject.texto(vararg chaves: String): String? {
    for (chave in chaves) {
        val p = this[chave] as? JsonPrimitive ?: continue
        if (p.content.isBlank() || p.content == "null" || p.content == "false") continue
        return p.content
    }
    return null
}

/** Inteiro de um campo, aceitando número ou string numérica. */
internal fun JsonObject.inteiro(vararg chaves: String): Int? {
    for (chave in chaves) {
        val p = this[chave] as? JsonPrimitive ?: continue
        p.intOrNull?.let { return it }
        p.content.trim().toIntOrNull()?.let { return it }
    }
    return null
}

internal fun JsonObject.booleano(vararg chaves: String): Boolean? {
    for (chave in chaves) {
        val p = this[chave] as? JsonPrimitive ?: continue
        p.booleanOrNull?.let { return it }
        when (p.content.trim().lowercase()) {
            "1", "true", "sim" -> return true
            "0", "false", "nao", "não" -> return false
        }
    }
    return null
}

/** Lista de textos, aceitando array ou string separada por vírgula. */
internal fun JsonObject.listaDeTextos(chave: String): List<String> =
    when (val e = this[chave]) {
        is JsonArray -> e.mapNotNull { (it as? JsonPrimitive)?.content }
        is JsonPrimitive -> e.content.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        else -> emptyList()
    }

/**
 * Alguns painéis devolvem a lista direto (`[...]`), outros embrulham num objeto
 * (`{"movie_data": [...]}`). Esta função aceita as duas formas.
 */
internal fun JsonElement.comoLista(vararg chavesPossiveis: String): List<JsonObject> = when (this) {
    is JsonArray -> mapNotNull { it as? JsonObject }
    is JsonObject -> {
        val interna = chavesPossiveis.firstNotNullOfOrNull { this[it] as? JsonArray }
        interna?.mapNotNull { it as? JsonObject } ?: listOf(this)
    }
    else -> emptyList()
}

internal fun JsonElement.objetoOuNulo(chave: String): JsonObject? =
    (this as? JsonObject)?.get(chave)?.let { it as? JsonObject }

internal fun JsonElement.comoObjeto(): JsonObject? = this as? JsonObject

@Suppress("unused")
internal fun JsonObject.primitivoBruto(chave: String): String? =
    runCatching { this[chave]?.jsonPrimitive?.content }.getOrNull()

@Suppress("unused")
internal fun JsonElement.objetoSeguro(): JsonObject? = runCatching { jsonObject }.getOrNull()
