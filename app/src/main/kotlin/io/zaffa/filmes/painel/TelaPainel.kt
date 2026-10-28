package io.zaffa.filmes.painel

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.zaffa.filmes.core.model.Padroes
import io.zaffa.filmes.ui.Cores
import java.io.ByteArrayInputStream

/**
 * Tela que carrega o painel e **captura o vídeo**.
 *
 * POR QUE O APP USA A PÁGINA DO PAINEL EM VEZ DE UMA API PRÓPRIA:
 * o token que libera o filme só é emitido pelo painel, para uma sessão
 * autenticada — pedir o MP4 direto devolve 502 (medido). Além disso, é o painel
 * que decide se o título toca como MP4 próprio ou como trailer do YouTube.
 * Ver docs/ARQUITETURA.md.
 *
 * O que o app acrescenta — e é o motivo do projeto existir — é **trocar o
 * player**: assim que a URL do filme aparece, ela é desviada para o nosso player,
 * com ExoPlayer e VLC intercambiáveis e recuperação automática ao falhar.
 *
 * A barra de cima é nossa, não do painel: dá navegação (voltar / início /
 * recarregar) sem depender do layout do site, que muda sem aviso.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TelaPainel(
    urlInicial: String,
    urlRaiz: String,
    aoCapturarVideo: (url: String) -> Unit,
    aoNavegar: (url: String) -> Unit,
    aoSair: () -> Unit,
) {
    var progresso by remember { mutableStateOf(0) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    // Precisa ser estado, não leitura direta de `canGoBack()`: o Compose não
    // recompõe quando o histórico do WebView muda, e a seta ficaria apagada
    // mesmo havendo para onde voltar.
    var podeVoltar by remember { mutableStateOf(false) }

    // Avisa quando a seção pedida não existia e o app caiu na raiz do painel.
    var caiuNaRaiz by remember { mutableStateOf(false) }

    // Evita abrir o mesmo vídeo duas vezes: o WebView pede o arquivo mais de uma
    // vez (uma delas só para ler metadados), e sem isto o player reabriria.
    val jaCapturado = remember { mutableStateOf<String?>(null) }

    BackHandler {
        val wv = webView
        if (wv != null && wv.canGoBack()) wv.goBack() else aoSair()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Cores.Fundo)
            .statusBarsPadding(),
    ) {
        BarraDoPainel(
            podeVoltar = podeVoltar,
            aoVoltar = { webView?.let { if (it.canGoBack()) it.goBack() else aoSair() } },
            // Início leva à RAIZ do painel, não à página em que se entrou: esta
            // tela costuma abrir num link direto de seção, e recarregá-lo não
            // ajudaria ninguém — menos ainda se o caminho da seção estiver errado.
            aoInicio = { webView?.loadUrl(urlRaiz) },
            aoRecarregar = { webView?.reload() },
        )

        if (progresso in 1..99) {
            LinearProgressIndicator(
                progress = { progresso / 100f },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = Cores.Violeta,
                trackColor = Cores.Superficie,
            )
        }

        if (caiuNaRaiz) {
            androidx.compose.material3.Text(
                text = "Essa seção não existe neste painel — abri a página inicial. " +
                    "Use o menu do site para chegar onde você quer.",
                style = MaterialTheme.typography.bodySmall,
                color = Cores.Texto,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Cores.Violeta.copy(alpha = 0.22f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }

        Box(Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { contexto ->
                    WebView(contexto).apply {
                        webView = this

                        // A sessão do painel vive nestes cookies — é o que mantém
                        // o login entre aberturas, sem o app guardar senha.
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                        setBackgroundColor(android.graphics.Color.BLACK)

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            // O painel declara `width=device-width`, então ele TEM
                            // layout de celular. Estas duas fazem o WebView
                            // respeitar isso em vez de renderizar como desktop.
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            // Controles de zoom do navegador deixam cara de página
                            // web, não de app. O pinça-para-ampliar continua.
                            builtInZoomControls = true
                            displayZoomControls = false
                            mediaPlaybackRequiresUserGesture = false
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            userAgentString = Padroes.USER_AGENT
                        }

                        // Rede de segurança: se a interceptação não pegar a URL
                        // (p. ex. quando o player resolve o endereço só no
                        // JavaScript), lemos o `currentSrc` do elemento <video>.
                        addJavascriptInterface(
                            PonteDeVideo { url ->
                                post {
                                    if (jaCapturado.value != url && CapturaDeVideo.ehVideoDoPainel(url)) {
                                        jaCapturado.value = url
                                        aoCapturarVideo(url)
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

                                // Trailer do YouTube: deixa a página tocar.
                                if (CapturaDeVideo.ehYoutube(url)) return null

                                if (CapturaDeVideo.ehVideoDoPainel(url)) {
                                    if (jaCapturado.value != url) {
                                        jaCapturado.value = url
                                        view.post { aoCapturarVideo(url) }
                                    }
                                    // Resposta vazia: sem isto o WebView baixaria o
                                    // filme em paralelo com o nosso player, gastando
                                    // dados e abrindo uma segunda conexão.
                                    return WebResourceResponse(
                                        "video/mp4",
                                        "utf-8",
                                        ByteArrayInputStream(ByteArray(0)),
                                    )
                                }
                                return null
                            }

                            override fun onPageFinished(view: WebView, url: String) {
                                progresso = 100
                                view.evaluateJavascript(JS_OBSERVADOR, null)
                            }

                            /**
                             * Se a página não existe, cai na raiz do painel.
                             *
                             * Os caminhos das seções em `Secoes.kt` são deduzidos e
                             * podem estar errados — e estavam: só o da TV acertou.
                             * Sem isto o usuário via **tela preta sem saída**, que
                             * foi exatamente o que aconteceu no primeiro teste real.
                             * Nenhum palpite nosso pode deixar o app sem saída.
                             */
                            override fun onReceivedHttpError(
                                view: WebView,
                                request: WebResourceRequest,
                                errorResponse: WebResourceResponse,
                            ) {
                                if (!request.isForMainFrame) return
                                if (view.url == urlRaiz) return
                                caiuNaRaiz = true
                                view.loadUrl(urlRaiz)
                            }

                            override fun onReceivedError(
                                view: WebView,
                                request: WebResourceRequest,
                                error: android.webkit.WebResourceError,
                            ) {
                                if (!request.isForMainFrame) return
                                if (view.url == urlRaiz) return
                                caiuNaRaiz = true
                                view.loadUrl(urlRaiz)
                            }

                            /**
                             * Acompanha a navegação dentro do painel.
                             *
                             * Serve a duas coisas: manter a seta "voltar" com o
                             * estado certo, e guardar a página atual para que
                             * sair do player devolva o usuário **onde ele estava**,
                             * e não ao começo da seção. O WebView é destruído ao
                             * trocar de tela, então sem isto a posição se perde.
                             */
                            override fun doUpdateVisitedHistory(
                                view: WebView,
                                url: String,
                                isReload: Boolean,
                            ) {
                                podeVoltar = view.canGoBack()
                                if (!CapturaDeVideo.ehVideoDoPainel(url)) aoNavegar(url)
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView, novo: Int) {
                                progresso = novo
                            }
                        }

                        loadUrl(urlInicial)
                    }
                },
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.apply {
                stopLoading()
                destroy()
            }
            webView = null
        }
    }
}

/**
 * Barra de navegação própria.
 *
 * Existe para o app ter navegação que **não depende do layout do painel**: o site
 * pode mudar os botões dele a qualquer momento, mas voltar, início e recarregar
 * continuam no mesmo lugar.
 */
@Composable
private fun BarraDoPainel(
    podeVoltar: Boolean,
    aoVoltar: () -> Unit,
    aoInicio: () -> Unit,
    aoRecarregar: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Cores.Superficie)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        IconButton(onClick = aoVoltar, modifier = Modifier.size(44.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Voltar",
                tint = if (podeVoltar) Cores.Texto else Cores.TextoFraco,
            )
        }
        IconButton(onClick = aoInicio, modifier = Modifier.size(44.dp)) {
            Icon(Icons.Filled.Home, contentDescription = "Início", tint = Cores.Texto)
        }

        androidx.compose.material3.Text(
            text = "Filmes",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Cores.VioletaClaro,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        )

        IconButton(onClick = aoRecarregar, modifier = Modifier.size(44.dp)) {
            Icon(Icons.Filled.Refresh, contentDescription = "Recarregar", tint = Cores.TextoFraco)
        }
    }
}

/** Recebe do JavaScript o endereço que o elemento `<video>` está tocando. */
private class PonteDeVideo(private val aoReceber: (String) -> Unit) {
    @android.webkit.JavascriptInterface
    fun videoDetectado(url: String) {
        if (url.isNotBlank()) aoReceber(url)
    }
}

/**
 * Observa o DOM à procura de um `<video>` tocando.
 *
 * Rede de segurança: a interceptação de rede é o caminho principal, mas se o
 * player do painel montar a URL só no JavaScript, ou se o Android servir o vídeo
 * de cache, a requisição pode não passar por `shouldInterceptRequest`.
 */
private const val JS_OBSERVADOR = """
(function () {
  if (window.__appFilmesObservando) return;
  window.__appFilmesObservando = true;

  function avisar(url) {
    try {
      if (url && url.indexOf('token=') !== -1) AppFilmes.videoDetectado(url);
    } catch (e) {}
  }

  function ligar(v) {
    if (!v || v.__appFilmesLigado) return;
    v.__appFilmesLigado = true;
    ['loadstart', 'loadedmetadata', 'play', 'playing'].forEach(function (ev) {
      v.addEventListener(ev, function () { avisar(v.currentSrc || v.src); });
    });
    avisar(v.currentSrc || v.src);
  }

  setInterval(function () {
    document.querySelectorAll('video').forEach(ligar);
  }, 1000);
})();
"""
