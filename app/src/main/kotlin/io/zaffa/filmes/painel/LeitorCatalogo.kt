package io.zaffa.filmes.painel

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.zaffa.filmes.core.model.Padroes

/**
 * Carrega uma página do painel sem mostrá-la e devolve os títulos encontrados.
 *
 * É o que permite o catálogo ser **nosso** sem precisarmos de uma API: a página
 * é carregada já autenticada (os cookies são os mesmos), lida, e descartada. O
 * usuário vê apenas a nossa grade.
 *
 * Como a leitura é sempre da página viva, **títulos novos aparecem sozinhos** —
 * não existe lista nossa para manter atualizada.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LeitorCatalogo(
    url: String,
    aoLer: (List<Item>) -> Unit,
    aoFalhar: (String) -> Unit,
) {
    val concluido = remember { mutableStateOf(false) }
    val rolagens = remember { mutableIntStateOf(0) }
    val ultimaContagem = remember { mutableIntStateOf(-1) }
    val vazias = remember { mutableIntStateOf(0) }

    AndroidView(
        // 1dp: um WebView sem tamanho não executa layout em alguns aparelhos, e
        // imagens com carregamento sob demanda nunca revelariam o endereço real.
        modifier = Modifier.size(1.dp),
        factory = { contexto ->
            WebView(contexto).apply {
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    userAgentString = Padroes.USER_AGENT
                    loadsImagesAutomatically = false // só precisamos dos endereços
                    blockNetworkImage = true
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, pagina: String) {
                        if (concluido.value) return
                        extrairComRolagem(view, rolagens, ultimaContagem, vazias, concluido, aoLer)
                    }

                    override fun onReceivedError(
                        view: WebView,
                        request: android.webkit.WebResourceRequest,
                        error: android.webkit.WebResourceError,
                    ) {
                        if (concluido.value || !request.isForMainFrame) return
                        concluido.value = true
                        aoFalhar("Não consegui carregar o catálogo. Verifique a sua internet.")
                    }
                }

                loadUrl(url)
            }
        },
        onRelease = { webView ->
            webView.stopLoading()
            webView.destroy()
        },
    )

    DisposableEffect(url) {
        onDispose { concluido.value = true }
    }
}

/**
 * Rola a página até parar de aparecer item novo, e então extrai.
 *
 * Catálogos grandes usam rolagem infinita: sem isto, só os primeiros títulos
 * existem no DOM e a lista viria curta sem nenhum erro aparente — o pior tipo
 * de falha, porque parece que o catálogo é pequeno.
 */
private fun extrairComRolagem(
    view: WebView,
    rolagens: androidx.compose.runtime.MutableIntState,
    ultimaContagem: androidx.compose.runtime.MutableIntState,
    vazias: androidx.compose.runtime.MutableIntState,
    concluido: androidx.compose.runtime.MutableState<Boolean>,
    aoLer: (List<Item>) -> Unit,
) {
    view.evaluateJavascript(Catalogo.JS_ROLAR) { resultado ->
        if (concluido.value) return@evaluateJavascript

        val contagem = resultado?.trim()?.toIntOrNull() ?: 0
        // Exige TRÊS rodadas sem item novo antes de parar: o próximo lote pode
        // demorar um ciclo para chegar, e parar cedo cortava o catálogo.
        val semNovidade = contagem == ultimaContagem.intValue
        vazias.intValue = if (semNovidade) vazias.intValue + 1 else 0
        val parou = vazias.intValue >= 3
        ultimaContagem.intValue = contagem
        rolagens.intValue += 1

        // Para quando a rolagem deixa de trazer novidade, ou no teto de
        // segurança — nenhum catálogo justifica rolar para sempre.
        if (parou || rolagens.intValue >= MAX_ROLAGENS) {
            view.evaluateJavascript(Catalogo.JS_EXTRAIR) { json ->
                if (concluido.value) return@evaluateJavascript
                concluido.value = true
                aoLer(Catalogo.lerJson(json))
            }
        } else {
            // Dá tempo de a página carregar o próximo lote antes de rolar de novo.
            view.postDelayed(
                { if (!concluido.value) extrairComRolagem(view, rolagens, ultimaContagem, vazias, concluido, aoLer) },
                ESPERA_ENTRE_ROLAGENS_MS,
            )
        }
    }
}

// Teto alto de propósito: o primeiro uso real trouxe só ~300 títulos de um
// acervo maior, porque 25 rolagens não davam conta. Cada rolagem é barata
// (nenhuma imagem é baixada), e o laço para sozinho quando não aparece item novo.
private const val MAX_ROLAGENS = 200
private const val ESPERA_ENTRE_ROLAGENS_MS = 450L
