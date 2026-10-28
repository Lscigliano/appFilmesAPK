package io.zaffa.filmes.core.api

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.zaffa.filmes.core.model.ContaInfo
import io.zaffa.filmes.core.model.Credenciais
import io.zaffa.filmes.core.model.EndpointDescoberto
import io.zaffa.filmes.core.model.Falha
import io.zaffa.filmes.core.model.Resultado
import io.zaffa.filmes.core.model.TipoApi

/**
 * Descobre, em tempo de execução, por onde o painel atende.
 *
 * POR QUE ISTO EXISTE (e não é over-engineering):
 * o dono da conta não sabia qual API o painel usa; o endereço mostrado na tela da
 * conta não é necessariamente o host da API, e o portal web do painel fica atrás
 * de Cloudflare e devolve 404 em `player_api.php`. Chutar um endereço fixo no
 * código deixaria o app quebrado para sempre. (Detalhes em docs/DIARIO.md.)
 *
 * O detector tenta, em ordem, as combinações de esquema e porta que os painéis
 * dessa família usam, e devolve a primeira que **autenticar de verdade** — não
 * basta responder HTTP 200.
 */
class DetectorApi(private val http: HttpClient) {

    data class Descoberta(
        val endpoint: EndpointDescoberto,
        val conta: ContaInfo?,
    )

    /**
     * @param hostDigitado o que o usuário escreveu. Aceita `meupainel.com`,
     *        `http://meupainel.com`, `http://meupainel.com:8080/` e variações
     *        com espaço em volta.
     */
    suspend fun detectar(hostDigitado: String, credenciais: Credenciais): Resultado<Descoberta> {
        val host = normalizarHost(hostDigitado)
        if (host.isBlank()) return Resultado.Erro(Falha.NenhumEndpointRespondeu)

        var viuBloqueioDeRede = false
        var viuCredencialRuim = false

        for (base in candidatos(host)) {
            val cliente = ClienteXtream(http, base, credenciais)
            when (val r = cliente.autenticar()) {
                is Resultado.Ok ->
                    return Resultado.Ok(
                        Descoberta(EndpointDescoberto(base, TipoApi.XTREAM), r.valor),
                    )

                is Resultado.Erro -> when (r.causa) {
                    // Credencial errada é resposta definitiva do servidor certo:
                    // continuar testando portas não ajuda, mas registramos para
                    // dar a mensagem correta no fim.
                    is Falha.CredenciaisInvalidas -> viuCredencialRuim = true
                    is Falha.ContaExpirada -> return r
                    is Falha.RedeIntermediariaBloqueou -> viuBloqueioDeRede = true
                    else -> Unit // porta fechada / timeout: tenta a próxima
                }
            }
        }

        // Nenhum Xtream respondeu. Antes de desistir, testa lista M3U simples.
        if (!viuCredencialRuim) {
            for (base in candidatos(host)) {
                if (temListaM3u(base, credenciais)) {
                    return Resultado.Ok(
                        Descoberta(EndpointDescoberto(base, TipoApi.M3U), null),
                    )
                }
            }
        }

        return Resultado.Erro(
            when {
                viuCredencialRuim -> Falha.CredenciaisInvalidas
                viuBloqueioDeRede -> Falha.RedeIntermediariaBloqueou
                else -> Falha.NenhumEndpointRespondeu
            },
        )
    }

    private suspend fun temListaM3u(base: String, c: Credenciais): Boolean = try {
        val corpo = http.get("$base/get.php") {
            header("User-Agent", ClienteXtream.UA)
            parameter("username", c.usuario)
            parameter("password", c.senha)
            parameter("type", "m3u_plus")
            parameter("output", "ts")
        }.bodyAsText()
        corpo.trimStart().startsWith("#EXTM3U")
    } catch (_: Exception) {
        false
    }

    private companion object {

        /** Portas usadas pelos painéis dessa família, em ordem de probabilidade. */
        fun candidatos(host: String): List<String> {
            // Se o usuário já informou porta, respeitamos a escolha dele primeiro.
            if (host.contains(':')) return listOf("http://$host", "https://$host")
            return listOf(
                "http://$host",
                "http://$host:8080",
                "https://$host",
                "http://$host:2082",
                "http://$host:2086",
                "http://$host:25461",
                "http://$host:8000",
            )
        }

        fun normalizarHost(bruto: String): String =
            bruto.trim()
                .removePrefix("https://")
                .removePrefix("http://")
                .substringBefore('/')
                .trim()
    }
}
