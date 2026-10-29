package io.zaffa.filmes.core.model

/**
 * Valores que o app já vem preenchido, para o usuário final não precisar
 * descobrir nada: abre o app, faz o login do painel, assiste.
 *
 * NOTA DE PRIVACIDADE: aqui mora apenas o *endereço do painel*, que é público.
 * **Usuário e senha nunca entram neste arquivo nem em nenhum outro do
 * repositório** — o login é feito pela própria página do painel, e a sessão fica
 * nos cookies do WebView. Ver docs/PROTOCOLO.md, seção 2.
 */
object Padroes {

    /**
     * Endereço do painel que o app abre ao iniciar.
     *
     * Confirmado funcionando em 2026-10-28: é por aqui que se faz login, navega
     * o catálogo e se obtém a URL do filme com token.
     *
     * (O endereço que o app oficial exibe na tela da conta — outro domínio — está
     * morto: não respondeu em 3 redes e 11 portas. Ver docs/DIARIO.md.)
     */
    const val PAINEL_URL: String = "http://vouver.me/"

    /**
     * Servidor de streaming, que fala o protocolo **Xtream Codes** (XUI 1.5.5).
     *
     * É dele que vem tudo o que o app mostra e toca: catálogo completo, séries,
     * canais e o link do vídeo. O painel acima é só a vitrine web desse mesmo
     * servidor — o app não precisa mais dele.
     *
     * Descoberto em 2026-10-28 lendo a página de detalhe do painel, que traz o
     * link do vídeo pronto neste host. `player_api.php` aqui responde com
     * `auth: 1` para o mesmo usuário e senha do painel. Ver docs/DIARIO.md.
     */
    const val SERVIDOR_URL: String = "http://crm1.cc"

    /**
     * User-Agent usado em **todas** as requisições do app: WebView, ExoPlayer e
     * libVLC.
     *
     * ⚠️ REQUISITO MEDIDO, NÃO PREFERÊNCIA. O servidor de vídeo responde
     * **HTTP 500** quando o User-Agent se identifica como VLC:
     *
     * ```
     * UA='okhttp/4.12.0'             -> HTTP 206  ✅
     * UA='Mozilla/5.0'               -> HTTP 206  ✅
     * UA='VLC/3.0.20 LibVLC/3.0.20'  -> HTTP 500  ❌
     * ```
     *
     * Ou seja: usar o motor libVLC **sem mascarar o User-Agent torna o vídeo
     * impossível de tocar**. Esta é a principal suspeita para o travamento do
     * app oficial. Nunca remova a configuração de UA dos motores.
     * (Medição em 2026-10-28, ver docs/DIARIO.md.)
     */
    const val USER_AGENT: String =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/131.0.0.0 Mobile Safari/537.36"
}
