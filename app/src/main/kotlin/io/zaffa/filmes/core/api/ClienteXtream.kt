package io.zaffa.filmes.core.api

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.zaffa.filmes.core.model.Categoria
import io.zaffa.filmes.core.model.ContaInfo
import io.zaffa.filmes.core.model.Credenciais
import io.zaffa.filmes.core.model.DetalheFilme
import io.zaffa.filmes.core.model.Falha
import io.zaffa.filmes.core.model.Filme
import io.zaffa.filmes.core.model.Resultado
import kotlinx.serialization.json.JsonObject

/**
 * Cliente do protocolo Xtream Codes.
 *
 * Kotlin puro: recebe o [HttpClient] já montado, sem saber qual motor HTTP está
 * por baixo. É o que permite reusar esta classe no projeto web.
 *
 * @param baseUrl endereço que respondeu de fato, descoberto pelo [DetectorApi]
 *        (ex.: `http://host:8080`), já sem barra no final.
 */
class ClienteXtream(
    private val http: HttpClient,
    val baseUrl: String,
    private val credenciais: Credenciais,
) {

    private suspend fun chamar(acao: String?, extras: Map<String, String> = emptyMap()): Resultado<String> =
        try {
            val resposta = http.get("$baseUrl/player_api.php") {
                header("User-Agent", UA)
                parameter("username", credenciais.usuario)
                parameter("password", credenciais.senha)
                if (acao != null) parameter("action", acao)
                extras.forEach { (k, v) -> parameter(k, v) }
            }
            val corpo = resposta.bodyAsText()
            when {
                ehPaginaDeBloqueio(corpo) -> Resultado.Erro(Falha.RedeIntermediariaBloqueou)
                !resposta.status.isSuccess() ->
                    Resultado.Erro(Falha.SemConexao("HTTP ${resposta.status.value}"))
                else -> Resultado.Ok(corpo)
            }
        } catch (e: Exception) {
            Resultado.Erro(Falha.SemConexao(e.message ?: e::class.simpleName.orEmpty()))
        }

    /** Autentica e devolve o estado da conta. */
    suspend fun autenticar(): Resultado<ContaInfo> {
        val corpo = when (val r = chamar(null)) {
            is Resultado.Erro -> return r
            is Resultado.Ok -> r.valor
        }

        val raiz = runCatching { json.parseToJsonElement(corpo) }.getOrNull()
            ?: return Resultado.Erro(Falha.NenhumEndpointRespondeu)

        val userInfo = raiz.objetoOuNulo("user_info")
            ?: return Resultado.Erro(Falha.CredenciaisInvalidas)

        val auth = userInfo.inteiro("auth")
        val status = userInfo.texto("status") ?: "Desconhecido"
        if (auth == 0) return Resultado.Erro(Falha.CredenciaisInvalidas)
        if (!status.equals("Active", ignoreCase = true)) return Resultado.Erro(Falha.ContaExpirada)

        return Resultado.Ok(
            ContaInfo(
                status = status,
                expiraEm = userInfo.texto("exp_date"),
                conexoesAtivas = userInfo.inteiro("active_cons") ?: 0,
                conexoesMaximas = userInfo.inteiro("max_connections") ?: 0,
                formatosPermitidos = userInfo.listaDeTextos("allowed_output_formats"),
            ),
        )
    }

    suspend fun categoriasDeFilmes(): Resultado<List<Categoria>> {
        val corpo = when (val r = chamar("get_vod_categories")) {
            is Resultado.Erro -> return r
            is Resultado.Ok -> r.valor
        }
        val lista = runCatching { json.parseToJsonElement(corpo).comoLista() }.getOrNull().orEmpty()
        return Resultado.Ok(
            lista.mapNotNull { obj ->
                val id = obj.texto("category_id") ?: return@mapNotNull null
                val nome = obj.texto("category_name") ?: return@mapNotNull null
                Categoria(id, nome)
            },
        )
    }

    suspend fun filmes(categoriaId: String? = null): Resultado<List<Filme>> {
        val extras = categoriaId?.let { mapOf("category_id" to it) } ?: emptyMap()
        val corpo = when (val r = chamar("get_vod_streams", extras)) {
            is Resultado.Erro -> return r
            is Resultado.Ok -> r.valor
        }
        val lista = runCatching { json.parseToJsonElement(corpo).comoLista("movie_data") }
            .getOrNull().orEmpty()
        return Resultado.Ok(lista.mapNotNull { paraFilme(it) })
    }

    private fun paraFilme(obj: JsonObject): Filme? {
        val id = obj.texto("stream_id", "id", "num") ?: return null
        val nome = obj.texto("name", "title", "o_name") ?: return null
        return Filme(
            id = id,
            nome = nome,
            capaUrl = obj.texto("stream_icon", "cover", "movie_image", "cover_big"),
            // Sem extensão declarada o padrão do Xtream é mp4; usar "" quebraria a URL.
            extensao = obj.texto("container_extension") ?: "mp4",
            nota = obj.texto("rating"),
            adicionadoEm = obj.texto("added"),
            categoriaId = obj.texto("category_id"),
        )
    }

    suspend fun detalhe(filme: Filme): Resultado<DetalheFilme> {
        val corpo = when (val r = chamar("get_vod_info", mapOf("vod_id" to filme.id))) {
            is Resultado.Erro -> return r
            is Resultado.Ok -> r.valor
        }
        val raiz = runCatching { json.parseToJsonElement(corpo) }.getOrNull()
        val info = raiz?.objetoOuNulo("info")
        val dados = raiz?.objetoOuNulo("movie_data")

        // A extensão real só aparece no detalhe em alguns painéis; se vier, vale mais
        // que a da listagem — URL com extensão errada devolve 404 e o filme "não abre".
        val extensaoReal = dados?.texto("container_extension") ?: filme.extensao

        return Resultado.Ok(
            DetalheFilme(
                filme = filme.copy(extensao = extensaoReal),
                sinopse = info?.texto("plot", "description", "o_name"),
                duracao = info?.texto("duration", "episode_run_time"),
                lancamento = info?.texto("releasedate", "release_date", "releaseDate"),
                genero = info?.texto("genre"),
                elenco = info?.texto("cast", "actors"),
            ),
        )
    }

    /**
     * URL de reprodução do filme.
     *
     * Formato Xtream: `{base}/movie/{usuario}/{senha}/{id}.{extensao}`.
     * As credenciais vão no caminho — por isso esta URL **nunca** pode ir para log.
     */
    fun urlDoFilme(filme: Filme): String =
        "$baseUrl/movie/${credenciais.usuario}/${credenciais.senha}/${filme.id}.${filme.extensao}"

    companion object {
        /**
         * Vários painéis respondem diferente (ou recusam) conforme o User-Agent.
         * Este é o da família Smarters, que sabidamente funciona nesta conta.
         */
        const val UA: String = "okhttp/4.12.0"
    }
}

/**
 * Detecta a página de bloqueio de um filtro de rede.
 *
 * Descoberto na prática: a rede corporativa devolvia HTTP 502 com 74 KB de HTML
 * contendo a assinatura "Sophos". Sem esta checagem, o app diria "servidor fora
 * do ar" e o usuário culparia a assinatura. Ver docs/DIARIO.md (2026-10-28).
 */
internal fun ehPaginaDeBloqueio(corpo: String): Boolean {
    if (corpo.length < 200) return false
    val amostra = corpo.take(80_000)
    val pareceHtml = amostra.contains("<html", true) || amostra.contains("<!doctype", true)
    if (!pareceHtml) return false
    return FILTROS_CONHECIDOS.any { amostra.contains(it, ignoreCase = true) }
}

private val FILTROS_CONHECIDOS = listOf(
    "sophos", "zscaler", "bluecoat", "fortiguard", "forcepoint",
    "websense", "mcafee web gateway", "access denied", "blocked",
)
