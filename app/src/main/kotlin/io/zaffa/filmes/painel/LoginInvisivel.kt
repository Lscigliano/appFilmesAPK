package io.zaffa.filmes.painel

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.zaffa.filmes.core.model.Padroes
import androidx.compose.ui.unit.dp
import org.json.JSONObject

/**
 * Faz o login do painel **usando o próprio formulário dele**, num WebView que o
 * usuário não vê.
 *
 * POR QUE ASSIM, E NÃO POR HTTP DIRETO:
 * a primeira tentativa montou a requisição de login à mão, a partir dos campos
 * vistos no HTML (`username`, `sifre`, `csrf_token`…). **Não funcionou** — o
 * painel recusava credenciais corretas, e sem ver a requisição real do navegador
 * não havia como saber o que faltava. Continuar tentando seria chutar.
 *
 * Aqui o problema desaparece: quem monta e envia a requisição é o navegador,
 * igualzinho ao que já funciona no computador. O app só preenche os campos e
 * aperta o botão. **A tela continua sendo a nossa** — é só o motor que é dele.
 *
 * Os cookies ficam no [CookieManager] compartilhado, então o catálogo abre
 * logado em seguida.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginInvisivel(
    urlLogin: String,
    usuario: String,
    senha: String,
    aoSucesso: () -> Unit,
    aoFalhar: (String) -> Unit,
) {
    // Guarda se o formulário já foi enviado. Sem isto, o app reenviaria a cada
    // carregamento de página e entraria em laço.
    val enviado = remember { mutableStateOf(false) }
    val concluido = remember { mutableStateOf(false) }

    AndroidView(
        // 1dp em vez de 0: um WebView sem tamanho não executa JavaScript de
        // layout em alguns aparelhos, e o formulário nunca seria preenchido.
        modifier = Modifier.size(1.dp),
        factory = { contexto ->
            WebView(contexto).apply {
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    userAgentString = Padroes.USER_AGENT
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        if (concluido.value) return

                        val aindaNoLogin = url.contains("page=login", ignoreCase = true)

                        when {
                            // Saiu da página de login => o painel aceitou.
                            !aindaNoLogin -> {
                                concluido.value = true
                                CookieManager.getInstance().flush()
                                aoSucesso()
                            }

                            // Primeira vez na página de login: preenche e envia.
                            !enviado.value -> {
                                enviado.value = true
                                view.evaluateJavascript(
                                    preencherEEnviar(usuario, senha),
                                    null,
                                )
                            }

                            // Já enviou e voltou para o login => credenciais recusadas.
                            else -> {
                                concluido.value = true
                                aoFalhar("Usuário ou senha incorretos.")
                            }
                        }
                    }

                    override fun onReceivedError(
                        view: WebView,
                        request: android.webkit.WebResourceRequest,
                        error: android.webkit.WebResourceError,
                    ) {
                        if (concluido.value || !request.isForMainFrame) return
                        concluido.value = true
                        aoFalhar(
                            "Não consegui falar com o servidor. " +
                                "Verifique a sua internet e tente de novo.",
                        )
                    }
                }

                loadUrl(urlLogin)
            }
        },
        onRelease = { webView ->
            webView.stopLoading()
            webView.destroy()
        },
    )

    DisposableEffect(Unit) {
        onDispose { concluido.value = true }
    }
}

/**
 * JavaScript que preenche o formulário e o envia.
 *
 * Os valores vão por [JSONObject.quote], que escapa aspas e acentos — sem isso,
 * uma senha com `'` ou `\` quebraria o script e o login falharia por um motivo
 * que ninguém encontraria.
 *
 * Dispara `input` e `change` antes de enviar porque a página pode ter validação
 * em JavaScript escutando esses eventos; sem eles, o formulário pode se recusar
 * a enviar mesmo com os campos preenchidos.
 */
private fun preencherEEnviar(usuario: String, senha: String): String {
    val u = JSONObject.quote(usuario)
    val s = JSONObject.quote(senha)
    return """
        (function () {
          var f = document.getElementById('login_form')
               || document.querySelector('form[name=login_form]')
               || document.querySelector('form');
          if (!f) { return 'sem-formulario'; }

          var campoUsuario = f.querySelector('[name=username]');
          var campoSenha   = f.querySelector('[name=sifre]')
                          || f.querySelector('input[type=password]');
          if (!campoUsuario || !campoSenha) { return 'sem-campos'; }

          function definir(el, valor) {
            el.value = valor;
            ['input', 'change', 'blur'].forEach(function (nome) {
              el.dispatchEvent(new Event(nome, { bubbles: true }));
            });
          }

          definir(campoUsuario, $u);
          definir(campoSenha, $s);

          var lembrar = f.querySelector('[name=beni_hatirla]');
          if (lembrar && lembrar.type === 'checkbox') { lembrar.checked = true; }

          // Prefere clicar no botão: alguns formulários só enviam por ele,
          // porque o handler está no clique e não no submit.
          var botao = f.querySelector('button[type=submit], input[type=submit]');
          if (botao) { botao.click(); } else { f.submit(); }
          return 'enviado';
        })();
    """.trimIndent()
}
