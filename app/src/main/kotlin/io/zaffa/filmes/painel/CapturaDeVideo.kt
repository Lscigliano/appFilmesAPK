package io.zaffa.filmes.painel

/**
 * Reconhece a URL do vídeo no meio do tráfego do painel.
 *
 * COMO SE SABE QUE É ISTO (medido em 2026-10-28, ver docs/DIARIO.md):
 * o painel entrega o filme como `http://<ip>/vauth/<numero>.mp4?token=<token>`.
 * O pedido SEM token devolve 502 — quem emite o token é o painel, numa resposta
 * 302. Portanto só interessa a URL **já com token**: ela funciona sozinha,
 * sem cookie nenhum (confirmado por teste).
 */
object CapturaDeVideo {

    /**
     * Só casa a URL **com token**.
     *
     * Capturar a URL sem token seria um erro grave: é ela que, ao ser seguida,
     * gera o token no redirecionamento. Interceptá-la mataria o fluxo antes de
     * o token existir.
     */
    private val VIDEO_COM_TOKEN = Regex(
        """https?://[^\s"']+/vauth/[^/?\s"']+\.(mp4|mkv|avi)\?[^\s"']*token=[^\s"'&]+""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Transmissão ao vivo: TV e rádio.
     *
     * São HLS (`.m3u8`), não arquivo fechado como os filmes — por isso o padrão
     * é outro. O ExoPlayer já traz suporte a HLS no projeto.
     *
     * Casa **apenas o manifesto** (`.m3u8`), nunca os segmentos (`.ts`): os
     * segmentos chegam às centenas durante a reprodução, e capturar cada um
     * reabriria o player sem parar.
     */
    private val AO_VIVO = Regex(
        """https?://[^\s"']+\.m3u8(\?[^\s"']*)?""",
        RegexOption.IGNORE_CASE,
    )

    /** Mídia servida pelo YouTube — o painel usa para trailers. */
    private val YOUTUBE = Regex(
        """(youtube\.com|youtu\.be|googlevideo\.com|ytimg\.com)""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Se esta URL é mídia do painel que o **nosso** player deve tocar.
     *
     * Cobre filme/série (arquivo com token) e TV/rádio ao vivo (manifesto HLS).
     * Trailers do YouTube ficam de fora de propósito — ver [ehYoutube].
     */
    fun ehVideoDoPainel(url: String): Boolean {
        val limpa = url.trim()
        if (ehYoutube(limpa)) return false
        return VIDEO_COM_TOKEN.matches(limpa) || AO_VIVO.matches(limpa)
    }

    /** Se a mídia é transmissão contínua, e não um arquivo com início e fim. */
    fun ehAoVivo(url: String): Boolean = AO_VIVO.matches(url.trim())

    fun encontrarNoTexto(texto: String): String? =
        VIDEO_COM_TOKEN.find(texto)?.value ?: AO_VIVO.find(texto)?.value

    /**
     * Trailer do YouTube. O app **não** sequestra estes: deixa a página tocar.
     * Sem esta checagem, o nosso player receberia uma URL que ele não sabe tocar
     * e ficaria girando para sempre — exatamente o sintoma que o dono viu no
     * navegador ao abrir um trailer.
     */
    fun ehYoutube(url: String): Boolean = YOUTUBE.containsMatchIn(url)

    /**
     * Nome do arquivo, para exibir enquanto o título real não é conhecido.
     * Ex.: ".../vauth/430022.mp4?token=..." -> "430022"
     */
    fun identificador(url: String): String =
        url.substringAfterLast('/')
            .substringBefore('?')
            .substringBeforeLast('.')
            .ifBlank { "Vídeo" }
}
