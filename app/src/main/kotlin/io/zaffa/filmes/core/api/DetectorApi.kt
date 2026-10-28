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
 *
 * Quando nada responde, ele devolve o **resumo do que tentou**. Sem isso a tela
 * mostra "não encontrei" e ninguém consegue agir.
 */
class DetectorApi(private val http: HttpClient) {

    data class Descoberta(
        val endpoint: EndpointDescoberto,
        val conta: ContaInfo?,
    )

    /**
     * @param hostDigitado o que o usuário escreveu. Tolera `meupainel.com`,
     *        `http://meupainel.com`, `https meupainel.com`, `MEUPAINEL.COM/`,
     *        espaços no meio e porta junto (`meupainel.com:8080`).
     */
    suspend fun detectar(hostDigitado: String, credenciais: Credenciais): Resultado<Descoberta> {
        val host = normalizarHost(hostDigitado)
        if (!pareceHost(host)) return Resultado.Erro(Falha.EnderecoInvalido)

        var viuBloqueioDeRede = false
        val diagnostico = StringBuilder()

        for (base in candidatos(host)) {
            val cliente = ClienteXtream(http, base, credenciais)
            when (val r = cliente.autenticar()) {
                is Resultado.Ok ->
                    return Resultado.Ok(
                        Descoberta(EndpointDescoberto(base, TipoApi.XTREAM), r.valor),
                    )

                is Resultado.Erro -> when (val causa = r.causa) {
                    // Credencial errada é resposta definitiva do servidor CERTO:
                    // achamos o painel, o problema é o login. Parar aqui evita
                    // mostrar "servidor não encontrado" quando é só senha errada.
                    is Falha.CredenciaisInvalidas -> return r
                    is Falha.ContaExpirada -> return r

                    is Falha.RedeIntermediariaBloqueou -> {
                        viuBloqueioDeRede = true
                        diagnostico.appendLine("• ${rotulo(base)} — bloqueado pela rede")
                    }

                    is Falha.NaoEUmPainel ->
                        diagnostico.appendLine("• ${rotulo(base)} — respondeu, mas não é painel")

                    is Falha.SemConexao ->
                        diagnostico.appendLine("• ${rotulo(base)} — ${resumirFalhaDeRede(causa.detalhe)}")

                    else -> diagnostico.appendLine("• ${rotulo(base)} — sem resposta")
                }
            }
        }

        // Nenhum Xtream respondeu. Antes de desistir, testa lista M3U simples.
        for (base in candidatos(host)) {
            if (temListaM3u(base, credenciais)) {
                return Resultado.Ok(Descoberta(EndpointDescoberto(base, TipoApi.M3U), null))
            }
        }

        if (viuBloqueioDeRede) return Resultado.Erro(Falha.RedeIntermediariaBloqueou)

        return Resultado.Erro(
            Falha.NenhumEndpointRespondeu(
                tentativas = candidatos(host).size,
                resumo = diagnostico.toString().trimEnd(),
            ),
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

        /** "http://x:8080" -> "porta 8080"; "https://x" -> "porta 443 (https)". */
        fun rotulo(base: String): String {
            val semEsquema = base.substringAfter("://")
            val porta = semEsquema.substringAfter(':', "")
            return when {
                porta.isNotEmpty() -> "porta $porta"
                base.startsWith("https") -> "porta 443 (https)"
                else -> "porta 80"
            }
        }

        fun resumirFalhaDeRede(detalhe: String): String = when {
            detalhe.contains("timeout", true) -> "demorou demais"
            detalhe.contains("HTTP 4", true) -> "endereço não existe nesse servidor"
            detalhe.contains("HTTP 5", true) -> "servidor recusou"
            detalhe.startsWith("HTTP") -> detalhe
            else -> "sem resposta"
        }

        /**
         * Limpa o que o usuário digitou.
         *
         * Tolera de propósito as formas que as pessoas realmente escrevem:
         * "https pfsv.io" (com espaço, sem ://), "HTTP://X/", "  x.com  ".
         * Um usuário digitando "https " antes do endereço não deve virar
         * "servidor não encontrado" — já aconteceu no primeiro teste real.
         */
        fun normalizarHost(bruto: String): String {
            var s = bruto.trim().lowercase()
            // remove esquema com :// ou escrito solto antes do endereço
            s = s.removePrefix("https://").removePrefix("http://")
            s = s.removePrefix("https:").removePrefix("http:")
            s = s.removePrefix("https").removePrefix("http")
            s = s.trimStart(' ', ':', '/')
            // tira caminho, query e espaços internos
            s = s.substringBefore('/').substringBefore('?')
            s = s.filterNot { it.isWhitespace() }
            return s.trim('.', ':', '-')
        }

        /** Barra entradas que não têm como ser um endereço de servidor. */
        fun pareceHost(host: String): Boolean {
            if (host.length < 4) return false
            val semPorta = host.substringBefore(':')
            if (!semPorta.contains('.')) return false
            return semPorta.all { it.isLetterOrDigit() || it == '.' || it == '-' }
        }
    }
}
