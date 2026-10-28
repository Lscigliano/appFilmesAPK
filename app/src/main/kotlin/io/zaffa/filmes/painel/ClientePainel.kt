package io.zaffa.filmes.painel

import android.webkit.CookieManager
import io.zaffa.filmes.core.model.Padroes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

/**
 * Faz o login no painel a partir do **nosso** formulário.
 *
 * Por que isto existe: o dono quer a tela de login com a cara do app, não a
 * página web do painel. O login em si continua sendo o do painel — só o
 * formulário é nosso.
 *
 * Depois de autenticar, os cookies de sessão são copiados para o
 * [CookieManager] do WebView. É isso que permite o catálogo abrir já logado,
 * **sem o app guardar a senha em lugar nenhum**.
 *
 * Campos do formulário do painel (observados no HTML):
 * `username`, `sifre` (senha), `csrf_token`, `beni_hatirla` (lembrar-me).
 * Há também um campo `recaptcha_response`, mas **o reCAPTCHA está desligado**
 * nesta instalação: a página não carrega script nem sitekey do Google.
 * Ver docs/DIARIO.md.
 */
class ClientePainel {

    private val cookies = mutableMapOf<String, MutableList<Cookie>>()

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookieList: List<Cookie>) {
                cookies.getOrPut(url.host) { mutableListOf() }.apply {
                    cookieList.forEach { novo -> removeAll { it.name == novo.name } }
                    addAll(cookieList)
                }
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> =
                cookies[url.host].orEmpty()
        })
        .build()

    sealed interface Resultado {
        data object Ok : Resultado
        data object CredenciaisInvalidas : Resultado
        data class Falhou(val motivo: String) : Resultado
    }

    suspend fun entrar(
        base: String = Padroes.PAINEL_URL,
        usuario: String,
        senha: String,
    ): Resultado = withContext(Dispatchers.IO) {
        val raiz = base.trimEnd('/')
        val urlLogin = "$raiz/index.php?page=login"

        try {
            // 1. Pega a página de login para obter o csrf_token e o cookie de sessão.
            val pagina = http.newCall(
                Request.Builder().url(urlLogin).header("User-Agent", Padroes.USER_AGENT).build(),
            ).execute().use { resposta ->
                resposta.body?.string().orEmpty()
            }

            val csrf = Jsoup.parse(pagina)
                .selectFirst("input[name=csrf_token]")
                ?.attr("value")
                .orEmpty()

            // 2. Envia as credenciais no mesmo formato do formulário do painel.
            val corpo = FormBody.Builder()
                .add("username", usuario)
                .add("sifre", senha)
                .add("csrf_token", csrf)
                .add("beni_hatirla", "on")
                .add("recaptcha_response", "")
                .build()

            val html = http.newCall(
                Request.Builder()
                    .url(urlLogin)
                    .post(corpo)
                    .header("User-Agent", Padroes.USER_AGENT)
                    .header("Referer", urlLogin)
                    .build(),
            ).execute().use { it.body?.string().orEmpty() }

            // 3. O painel devolve a própria página de login quando a senha erra.
            //    Procurar o formulário é mais confiável que olhar o código HTTP,
            //    que vem 200 nos dois casos.
            val aindaNoLogin = Jsoup.parse(html).selectFirst("form#login_form") != null
            if (aindaNoLogin) return@withContext Resultado.CredenciaisInvalidas

            copiarCookiesParaWebView(raiz)
            Resultado.Ok
        } catch (e: Exception) {
            Resultado.Falhou(e.message ?: e::class.simpleName.orEmpty())
        }
    }

    /**
     * Passa a sessão para o WebView.
     *
     * Sem isto o usuário logaria na nossa tela e o catálogo abriria deslogado,
     * pedindo login de novo — que é exatamente o que queremos evitar.
     */
    private fun copiarCookiesParaWebView(raiz: String) {
        val host = raiz.toHttpUrlOrNull()?.host ?: return
        val gerente = CookieManager.getInstance()
        gerente.setAcceptCookie(true)
        cookies[host]?.forEach { c ->
            gerente.setCookie(raiz, "${c.name}=${c.value}; path=/; domain=${c.domain}")
        }
        gerente.flush()
    }

    /** Apaga a sessão, do app e do WebView. */
    fun sair() {
        cookies.clear()
        CookieManager.getInstance().apply {
            removeAllCookies(null)
            flush()
        }
    }
}
