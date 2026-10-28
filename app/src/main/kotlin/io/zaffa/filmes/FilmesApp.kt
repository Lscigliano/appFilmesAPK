package io.zaffa.filmes

import android.app.Application
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.zaffa.filmes.core.api.DetectorApi
import io.zaffa.filmes.data.Sessao

/**
 * Container de dependências, feito à mão.
 *
 * Decisão consciente de NÃO usar Hilt: o projeto é pequeno e precisa ser legível
 * para quem (ou qual IA) chegar depois sem contexto nenhum. Três objetos num
 * `Application` são mais fáceis de seguir do que um grafo anotado.
 * Ver docs/PROTOCOLO.md, seção 4.
 */
class FilmesApp : Application() {

    lateinit var http: HttpClient
        private set

    lateinit var sessao: Sessao
        private set

    lateinit var detector: DetectorApi
        private set

    override fun onCreate() {
        super.onCreate()

        http = HttpClient(OkHttp) {
            // Sem isto, um painel fora do ar deixa o app pendurado para sempre.
            // Os valores são folgados de propósito: estes servidores são lentos
            // para responder depois de um seek grande.
            install(HttpTimeout) {
                connectTimeoutMillis = 20_000
                requestTimeoutMillis = 40_000
                socketTimeoutMillis = 40_000
            }
            // Redirecionamento http→https ou para CDN é rotina nestes painéis.
            followRedirects = true
            expectSuccess = false
        }

        sessao = Sessao(this)
        detector = DetectorApi(http)
    }
}
