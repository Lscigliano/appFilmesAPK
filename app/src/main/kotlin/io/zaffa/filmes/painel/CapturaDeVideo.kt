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

    /** Mídia servida pelo YouTube — o painel usa para trailers. */
    private val YOUTUBE = Regex(
        """(youtube\.com|youtu\.be|googlevideo\.com|ytimg\.com)""",
        RegexOption.IGNORE_CASE,
    )

    fun ehVideoDoPainel(url: String): Boolean = VIDEO_COM_TOKEN.matches(url.trim())

    fun encontrarNoTexto(texto: String): String? = VIDEO_COM_TOKEN.find(texto)?.value

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
