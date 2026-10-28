package io.zaffa.filmes.player

import android.content.Context
import android.net.Uri
import android.view.View
import io.zaffa.filmes.core.api.ClienteXtream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * Motor baseado no libVLC.
 *
 * POR QUE ELE EXISTE AQUI: o VLC tolera servidores que o ExoPlayer não tolera.
 * Em especial, servidores que **ignoram o cabeçalho HTTP Range** e devolvem o
 * arquivo do início quando o player pede um trecho do meio. O ExoPlayer confia
 * na resposta e congela; o VLC detecta a inconsistência e reabre o fluxo.
 *
 * Se o filme travar ao avançar no ExoPlayer, este motor é a saída — e é por isso
 * que a troca é um botão dentro do player, não uma configuração escondida.
 *
 * As opções abaixo são deliberadas:
 * - `--network-caching=3000`: 3 s de cache de rede. Menos que isso faz o vídeo
 *   engasgar em painéis IPTV; muito mais atrasa demais o início.
 * - `--no-drop-late-frames` / `--no-skip-frames`: sem isto, depois de um seek o
 *   VLC descarta quadros para "alcançar" o áudio e a imagem fica picotada.
 * - `--http-reconnect`: reabre a conexão sozinho se o servidor derrubar — comum
 *   quando a conta está perto do limite de telas simultâneas.
 */
class MotorVlc(context: Context) : MotorVideo {

    private val _estado = MutableStateFlow(EstadoPlayer())
    override val estado: StateFlow<EstadoPlayer> = _estado

    private val libVlc = LibVLC(
        context.applicationContext,
        arrayListOf(
            "--network-caching=3000",
            "--no-drop-late-frames",
            "--no-skip-frames",
            "--http-reconnect",
            "--http-user-agent=${ClienteXtream.UA}",
            "--audio-time-stretch",
            "-vv",
        ),
    )

    private val player = MediaPlayer(libVlc)
    private var layout: VLCVideoLayout? = null

    init {
        player.setEventListener { evento ->
            when (evento.type) {
                MediaPlayer.Event.Playing ->
                    _estado.update { it.copy(tocando = true, carregando = false, erro = null) }

                MediaPlayer.Event.Paused, MediaPlayer.Event.Stopped ->
                    _estado.update { it.copy(tocando = false) }

                MediaPlayer.Event.Buffering ->
                    // O VLC emite Buffering com percentual; só é "carregando" abaixo de 100.
                    _estado.update { it.copy(carregando = evento.buffering < 100f) }

                MediaPlayer.Event.TimeChanged ->
                    _estado.update { it.copy(posicaoMs = evento.timeChanged.coerceAtLeast(0L)) }

                MediaPlayer.Event.LengthChanged ->
                    _estado.update { it.copy(duracaoMs = evento.lengthChanged.coerceAtLeast(0L)) }

                MediaPlayer.Event.EndReached ->
                    _estado.update { it.copy(terminou = true, tocando = false) }

                MediaPlayer.Event.EncounteredError ->
                    _estado.update {
                        it.copy(
                            erro = "Falha no VLC ao abrir o vídeo. " +
                                "Tente trocar para o motor ExoPlayer.",
                            carregando = false,
                        )
                    }
            }
        }
    }

    override fun criarView(context: Context): View =
        VLCVideoLayout(context).also { novo ->
            layout = novo
            player.attachViews(
                novo,
                /* displayManager = */ null,
                /* enableSubtitles = */ true,
                /* useTextureView = */ false,
            )
        }

    override fun abrir(url: String, posicaoInicialMs: Long) {
        val media = Media(libVlc, Uri.parse(url)).apply {
            setHWDecoderEnabled(true, false)
            addOption(":network-caching=3000")
            addOption(":http-reconnect")
            // Começar já posicionado é mais confiável que abrir e dar seek logo
            // em seguida: evita o servidor abrir duas conexões para o mesmo filme.
            if (posicaoInicialMs > 0L) {
                addOption(":start-time=${posicaoInicialMs / 1000}")
            }
        }
        player.media = media
        media.release()
        player.play()
        _estado.update { it.copy(carregando = true, erro = null, terminou = false) }
    }

    override fun tocar() {
        player.play()
    }

    override fun pausar() {
        player.pause()
    }

    override fun buscar(posicaoMs: Long) {
        if (!player.isSeekable) {
            // Servidor não permite busca. Dizer isso é melhor que congelar calado.
            _estado.update {
                it.copy(erro = "Este filme não permite avançar neste servidor.")
            }
            return
        }
        player.time = posicaoMs.coerceAtLeast(0L)
    }

    override fun atualizarProgresso() {
        // O VLC já empurra TimeChanged/LengthChanged pelos eventos; manter o fluxo
        // em dia aqui cobre o caso de a tela voltar do segundo plano.
        _estado.update {
            it.copy(
                posicaoMs = player.time.coerceAtLeast(0L),
                duracaoMs = player.length.coerceAtLeast(0L),
            )
        }
    }

    override fun liberar() {
        runCatching { player.stop() }
        runCatching { player.detachViews() }
        layout = null
        runCatching { player.release() }
        runCatching { libVlc.release() }
    }
}
