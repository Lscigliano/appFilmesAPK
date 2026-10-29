package io.zaffa.filmes.xtream

import java.text.Normalizer

/**
 * Os três tipos de conteúdo do servidor, com as ações da API de cada um e o
 * trecho do caminho usado no link de reprodução.
 */
enum class Tipo(
    val acaoCategorias: String,
    val acaoLista: String,
    val caminho: String,
    val rotulo: String,
) {
    FILME("get_vod_categories", "get_vod_streams", "movie", "Filme"),
    SERIE("get_series_categories", "get_series", "series", "Série"),
    CANAL("get_live_categories", "get_live_streams", "live", "TV"),
}

data class Categoria(val id: String, val nome: String)

/** Um filme, uma série ou um canal — o que aparece numa capa do catálogo. */
data class Titulo(
    val tipo: Tipo,
    val id: Long,
    val nome: String,
    val capa: String?,
    val ano: String?,
    val categorias: List<String>,
    /** Extensão do arquivo (filmes). Séries têm a extensão por episódio. */
    val extensao: String,
) {
    /** Nome já normalizado: a busca roda a cada tecla sobre dezenas de milhares. */
    val chave: String = normalizar(nome)
}

data class Episodio(
    val id: Long,
    val temporada: Int,
    val numero: Int,
    val titulo: String,
    val extensao: String,
    val capa: String?,
    val duracao: String?,
)

/** "Ação: Parte 2" → "acao parte 2". Ninguém digita acento no celular. */
fun normalizar(texto: String): String =
    Normalizer.normalize(texto.lowercase(), Normalizer.Form.NFD)
        .replace(ACENTOS, "")
        .replace(SEPARADORES, " ")
        .trim()

private val ACENTOS = Regex("\\p{Mn}+")
private val SEPARADORES = Regex("[^a-z0-9]+")
