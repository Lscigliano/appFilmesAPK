package io.zaffa.filmes.painel

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.zaffa.filmes.core.model.Padroes
import kotlinx.coroutines.delay
import java.io.ByteArrayInputStream

/**
 * Abre a página de detalhe **sem mostrá-la** e entrega a URL do vídeo.
 *
 * É o que faz o app ir do nosso catálogo direto para o nosso player, sem o
 * usuário ver a página do painel no meio do caminho — pedido direto do dono.
 *
 * A página ainda é necessária porque **só o painel emite o token** do vídeo
 * (pedir o MP4 direto devolve 502, medido). A diferença é que agora ela roda
 * escondida: o app carrega, fecha o trailer se houver, aperta o play, captura o
 * endereço e descarta a página.
 *
 * Se nada for capturado a tempo, o app **mostra a página de verdade** em vez de
 * deixar o usuário olhando para um carregamento infinito.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AbridorDeVideo(
    urlDetalhe: String,
    aoCapturar: (url: String, titulo: String) -> Unit,
    aoDesistir: () -> Unit,
) {
    val concluido = remember { mutableStateOf(false) }

    // Teto de espera. Sem isto, um título que só tem trailer do YouTube — ou
    // uma página que mudou de estrutura — deixaria o app carregando para sempre.
    LaunchedEffect(urlDetalhe) {
        delay(ESPERA_MAXIMA_MS)
        if (!concluido.value) {
            concluido.value = true
            aoDesistir()
        }
    }

    AndroidView(
        modifier = Modifier.size(1.dp),
        factory = { contexto ->
            WebView(contexto).apply {
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    userAgentString = Padroes.USER_AGENT
                }

                addJavascriptInterface(
                    PonteDeVideo { url ->
                        post {
                            if (!concluido.value && CapturaDeVideo.ehVideoDoPainel(url)) {
                                concluido.value = true
                                aoCapturar(url, tituloDaPagina(title))
                            }
                        }
                    },
                    "AppFilmes",
                )

                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest,
                    ): WebResourceResponse? {
                        val url = request.url.toString()
                        if (CapturaDeVideo.ehYoutube(url)) return null

                        if (CapturaDeVideo.ehVideoDoPainel(url)) {
                            if (!concluido.value) {
                                concluido.value = true
                                view.post { aoCapturar(url, tituloDaPagina(view.title)) }
                            }
                            // Resposta vazia: o WebView não precisa baixar o
                            // filme — quem vai tocar é o nosso player.
                            return WebResourceResponse(
                                "video/mp4",
                                "utf-8",
                                ByteArrayInputStream(ByteArray(0)),
                            )
                        }
                        return null
                    }

                    override fun onPageFinished(view: WebView, url: String) {
                        if (concluido.value) return
                        view.evaluateJavascript(JS_OBSERVADOR, null)
                        view.evaluateJavascript(JS_DAR_PLAY, null)
                        // O play às vezes só fica disponível depois que a página
                        // termina de montar; uma segunda tentativa cobre isso.
                        view.postDelayed(
                            { if (!concluido.value) view.evaluateJavascript(JS_DAR_PLAY, null) },
                            SEGUNDA_TENTATIVA_MS,
                        )
                    }

                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: android.webkit.WebResourceError,
                    ) {
                        if (concluido.value || !request.isForMainFrame) return
                        concluido.value = true
                        aoDesistir()
                    }
                }

                loadUrl(urlDetalhe)
            }
        },
        onRelease = { webView ->
            webView.stopLoading()
            webView.destroy()
        },
    )

    DisposableEffect(urlDetalhe) {
        onDispose { concluido.value = true }
    }
}

private fun tituloDaPagina(bruto: String?): String =
    bruto?.trim().orEmpty()
        .substringBefore(" - Pronto para assistir")
        .substringBefore(" | ")
        .trim()

/**
 * Fecha o trailer e aperta o play.
 *
 * O painel abre alguns títulos com um **trailer do YouTube por cima** da página
 * do filme, com um botão "Fechar" — foi o que confundiu o diagnóstico no começo
 * do projeto. Aqui esse overlay é fechado antes de procurar o play.
 *
 * A busca é por **texto do botão**, não por classe CSS: o texto é o que o painel
 * precisa manter para o usuário entender a tela, enquanto classes mudam a cada
 * ajuste visual.
 */
private const val JS_DAR_PLAY = """
(function () {
  function textoDe(el) {
    return ((el.innerText || el.textContent || '') + ' ' +
            (el.getAttribute('title') || '') + ' ' +
            (el.getAttribute('aria-label') || '')).toLowerCase();
  }

  var clicaveis = Array.prototype.slice.call(
    document.querySelectorAll('button, a, [role=button], .btn')
  );

  // 1) fecha o trailer, se estiver por cima
  var fechar = clicaveis.filter(function (el) {
    return /fechar|close/.test(textoDe(el));
  })[0];
  if (fechar) { try { fechar.click(); } catch (e) {} }

  // 2) aperta o play do filme
  var play = clicaveis.filter(function (el) {
    return /assistir|reproduzir|^play$|ver agora|continuar assistindo/.test(textoDe(el));
  })[0];
  if (play) { try { play.click(); } catch (e) {} return 'play'; }

  // 3) se já existe um <video> na página, manda tocar direto
  var v = document.querySelector('video');
  if (v) { try { v.play(); } catch (e) {} return 'video'; }

  return 'nada';
})();
"""

private const val JS_OBSERVADOR = """
(function () {
  if (window.__appFilmesAbrindo) return;
  window.__appFilmesAbrindo = true;
  function avisar(url) {
    try {
      if (url && url.indexOf('token=') !== -1) AppFilmes.videoDetectado(url);
    } catch (e) {}
  }
  setInterval(function () {
    document.querySelectorAll('video').forEach(function (v) {
      avisar(v.currentSrc || v.src);
    });
  }, 500);
})();
"""

private const val ESPERA_MAXIMA_MS = 25_000L
private const val SEGUNDA_TENTATIVA_MS = 2_500L
