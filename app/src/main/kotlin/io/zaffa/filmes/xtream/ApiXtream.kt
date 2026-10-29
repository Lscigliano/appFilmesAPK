package io.zaffa.filmes.xtream

import android.util.JsonReader
import android.util.JsonToken
import io.zaffa.filmes.core.model.Padroes
import io.zaffa.filmes.data.Credenciais
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Falha de conversa com o servidor, com uma mensagem que pode ir para a tela. */
class FalhaApi(mensagem: String) : Exception(mensagem)

/**
 * Cliente da API Xtream Codes do servidor de streaming.
 *
 * POR QUE ISTO VOLTOU: o projeto começou como cliente Xtream e abandonou a
 * ideia porque `player_api.php` dava 404 — **no host do painel**. O servidor
 * de vídeo é outro host, e lá a API responde (medido em 2026-10-28). Com ela o
 * app deixa de depender de páginas do painel em WebViews escondidos: o
 * catálogo vem inteiro numa chamada e o link do filme é montado direto.
 *
 * ⚠️ O protocolo leva usuário e senha **na URL** — é assim que o Xtream
 * funciona. Por isso nenhuma URL daqui pode ir para log nem para mensagem de
 * erro: os erros abaixo são reescritos em texto nosso, nunca o da exceção.
 */
class ApiXtream(servidor: String = Padroes.SERVIDOR_URL) {

    private val base: HttpUrl = servidor.trimEnd('/').toHttpUrl()

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        // A lista de filmes tem ~1,7 MB e a de séries ~2,8 MB; em 4G fraco
        // isso leva bem mais que o padrão de 10 s.
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    sealed interface Autenticacao {
        data object Ok : Autenticacao
        data object CredenciaisInvalidas : Autenticacao
        data class ContaInativa(val status: String) : Autenticacao
        data class Falhou(val mensagem: String) : Autenticacao
    }

    suspend fun autenticar(c: Credenciais): Autenticacao = withContext(Dispatchers.IO) {
        try {
            val corpo = chamarTexto(c, acao = null)
            val info = JSONObject(corpo).optJSONObject("user_info")
                ?: return@withContext Autenticacao.CredenciaisInvalidas
            val status = info.optString("status")
            when {
                info.optInt("auth", 0) != 1 -> Autenticacao.CredenciaisInvalidas
                !status.equals("Active", ignoreCase = true) -> Autenticacao.ContaInativa(status)
                else -> Autenticacao.Ok
            }
        } catch (e: FalhaApi) {
            Autenticacao.Falhou(e.message.orEmpty())
        } catch (e: Exception) {
            Autenticacao.Falhou("O servidor respondeu algo inesperado. Tente de novo.")
        }
    }

    suspend fun categorias(c: Credenciais, tipo: Tipo): List<Categoria> = withContext(Dispatchers.IO) {
        chamar(c, tipo.acaoCategorias) { leitor ->
            val lista = mutableListOf<Categoria>()
            leitor.beginArray()
            while (leitor.hasNext()) {
                var id: String? = null
                var nome: String? = null
                leitor.beginObject()
                while (leitor.hasNext()) {
                    when (leitor.nextName()) {
                        "category_id" -> id = leitor.textoOuNulo()
                        "category_name" -> nome = leitor.textoOuNulo()
                        else -> leitor.skipValue()
                    }
                }
                leitor.endObject()
                if (id != null && !nome.isNullOrBlank()) lista += Categoria(id, nome.trim())
            }
            leitor.endArray()
            lista
        }
    }

    /**
     * Todos os títulos de um tipo, numa chamada só.
     *
     * Lido em fluxo (sem montar a árvore JSON inteira): são dezenas de milhares
     * de itens. O mesmo título vem repetido quando está em mais de uma
     * categoria — junta-se num item só, com todas as categorias.
     */
    suspend fun titulos(c: Credenciais, tipo: Tipo): List<Titulo> = withContext(Dispatchers.IO) {
        chamar(c, tipo.acaoLista) { leitor ->
            val porId = LinkedHashMap<Long, Titulo>()
            leitor.beginArray()
            while (leitor.hasNext()) {
                val t = lerTitulo(leitor, tipo) ?: continue
                val existente = porId[t.id]
                porId[t.id] = if (existente == null) {
                    t
                } else {
                    existente.copy(categorias = (existente.categorias + t.categorias).distinct())
                }
            }
            leitor.endArray()
            porId.values.toList()
        }
    }

    private fun lerTitulo(leitor: JsonReader, tipo: Tipo): Titulo? {
        var id: Long? = null
        var nome: String? = null
        var capa: String? = null
        var ano: String? = null
        var extensao = "mp4"
        val categorias = mutableListOf<String>()

        leitor.beginObject()
        while (leitor.hasNext()) {
            when (leitor.nextName()) {
                "stream_id", "series_id" -> id = leitor.textoOuNulo()?.toLongOrNull()
                "name" -> nome = leitor.textoOuNulo()
                "stream_icon", "cover" -> capa = leitor.textoOuNulo()?.takeIf { it.isNotBlank() }
                "year" -> ano = leitor.textoOuNulo()?.takeIf { it.isNotBlank() }
                "container_extension" -> leitor.textoOuNulo()?.takeIf { it.isNotBlank() }?.let { extensao = it }
                "category_id" -> leitor.textoOuNulo()?.let { if (it !in categorias) categorias += it }
                "category_ids" -> if (leitor.peek() == JsonToken.BEGIN_ARRAY) {
                    leitor.beginArray()
                    while (leitor.hasNext()) {
                        leitor.textoOuNulo()?.let { if (it !in categorias) categorias += it }
                    }
                    leitor.endArray()
                } else {
                    leitor.skipValue()
                }
                else -> leitor.skipValue()
            }
        }
        leitor.endObject()

        if (id == null || nome.isNullOrBlank()) return null
        return Titulo(tipo, id, nome.trim(), capa, ano, categorias, extensao)
    }

    /** Episódios de uma série, já em ordem de temporada e número. */
    suspend fun episodios(c: Credenciais, serieId: Long): List<Episodio> = withContext(Dispatchers.IO) {
        val corpo = chamarTexto(c, "get_series_info", mapOf("series_id" to serieId.toString()))
        val raiz = runCatching { JSONObject(corpo) }.getOrNull()
            ?: throw FalhaApi("O servidor não devolveu os episódios desta série.")
        // "episodes" é um objeto { "1": [...], "2": [...] } — mas séries sem
        // episódio algum vêm com uma lista vazia no lugar.
        val porTemporada = raiz.optJSONObject("episodes") ?: return@withContext emptyList()

        val lista = mutableListOf<Episodio>()
        porTemporada.keys().forEach { chave ->
            val eps = porTemporada.optJSONArray(chave) ?: return@forEach
            for (i in 0 until eps.length()) {
                val e = eps.optJSONObject(i) ?: continue
                val id = e.optString("id").toLongOrNull() ?: continue
                val info = e.optJSONObject("info")
                lista += Episodio(
                    id = id,
                    temporada = e.optString("season").toIntOrNull() ?: chave.toIntOrNull() ?: 0,
                    numero = e.optString("episode_num").toIntOrNull() ?: (i + 1),
                    titulo = e.optString("title").ifBlank { "Episódio ${i + 1}" },
                    extensao = e.optString("container_extension").ifBlank { "mp4" },
                    capa = info?.optString("movie_image")?.takeIf { it.isNotBlank() },
                    duracao = info?.optString("duration")?.takeIf { it.isNotBlank() },
                )
            }
        }
        lista.sortedWith(compareBy({ it.temporada }, { it.numero }))
    }

    /**
     * Link de reprodução. O servidor responde **302** para o arquivo com token
     * num IP de vídeo (medido) — os dois motores seguem o redirecionamento.
     * Canais saem em HLS (`.m3u8`), que é o formato que a conta permite.
     */
    fun urlDe(c: Credenciais, titulo: Titulo): String =
        if (titulo.tipo == Tipo.CANAL) {
            urlMidia(c, Tipo.CANAL, titulo.id, "m3u8")
        } else {
            urlMidia(c, titulo.tipo, titulo.id, titulo.extensao)
        }

    fun urlDe(c: Credenciais, episodio: Episodio): String =
        urlMidia(c, Tipo.SERIE, episodio.id, episodio.extensao)

    private fun urlMidia(c: Credenciais, tipo: Tipo, id: Long, extensao: String): String =
        base.newBuilder()
            .addPathSegment(tipo.caminho)
            .addPathSegment(c.usuario)
            .addPathSegment(c.senha)
            .addPathSegment("$id.$extensao")
            .build()
            .toString()

    private fun chamarTexto(c: Credenciais, acao: String?, extras: Map<String, String> = emptyMap()): String =
        executar(c, acao, extras) { it.string() }

    private fun <T> chamar(
        c: Credenciais,
        acao: String?,
        extras: Map<String, String> = emptyMap(),
        ler: (JsonReader) -> T,
    ): T = executar(c, acao, extras) { corpo ->
        try {
            JsonReader(corpo.charStream()).use(ler)
        } catch (e: IllegalStateException) {
            throw FalhaApi("O servidor respondeu algo inesperado. Tente de novo.")
        }
    }

    private fun <T> executar(
        c: Credenciais,
        acao: String?,
        extras: Map<String, String>,
        ler: (ResponseBody) -> T,
    ): T {
        val url = base.newBuilder()
            .addPathSegment("player_api.php")
            .addQueryParameter("username", c.usuario)
            .addQueryParameter("password", c.senha)
            .apply {
                if (acao != null) addQueryParameter("action", acao)
                extras.forEach { (k, v) -> addQueryParameter(k, v) }
            }
            .build()
        val pedido = Request.Builder().url(url).header("User-Agent", Padroes.USER_AGENT).build()

        try {
            http.newCall(pedido).execute().use { resposta ->
                if (!resposta.isSuccessful) {
                    throw FalhaApi("O servidor recusou o pedido (HTTP ${resposta.code}).")
                }
                val corpo = resposta.body
                // Rede com filtro devolve uma página HTML no lugar do JSON
                // (ver tasks/lessons.md). Dizer isso é melhor que "erro".
                if (corpo.contentType()?.subtype?.contains("html") == true) {
                    throw FalhaApi("A rede em que você está bloqueou o servidor. Tente pelo 4G/5G.")
                }
                return ler(corpo)
            }
        } catch (e: IOException) {
            throw FalhaApi("Sem conexão com o servidor. Verifique a sua internet.")
        }
    }
}

private fun JsonReader.textoOuNulo(): String? = when (peek()) {
    JsonToken.NULL -> { nextNull(); null }
    JsonToken.STRING, JsonToken.NUMBER -> nextString()
    JsonToken.BOOLEAN -> nextBoolean().toString()
    else -> { skipValue(); null }
}
