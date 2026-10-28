package io.zaffa.filmes.player

import android.content.Context
import android.view.View
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import io.zaffa.filmes.core.api.ClienteXtream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Motor baseado no ExoPlayer (Media3).
 *
 * AJUSTES FEITOS DE PROPÓSITO CONTRA O BUG DE AVANÇAR O FILME — não mexer sem
 * ler docs/DIARIO.md antes:
 *
 * 1. `setAllowCrossProtocolRedirects(true)` — painéis IPTV costumam redirecionar
 *    http→https ou para um CDN em outro host. Sem isto o ExoPlayer ABORTA o
 *    redirecionamento no meio de um seek e o vídeo congela sem erro visível.
 *    É a causa nº 1 de "avancei e não volta mais".
 *
 * 2. Timeouts generosos (30 s) — o servidor leva vários segundos para posicionar
 *    o arquivo depois de um seek grande. O padrão de 8 s estoura e vira erro.
 *
 * 3. Buffers grandes via [DefaultLoadControl] — depois de um seek o buffer zera;
 *    buffer pequeno faz o player entrar em ciclo de rebuffer infinito.
 *
 * 4. `setBackBuffer` — mantém o trecho já visto em memória, para voltar atrás
 *    sem nova requisição ao servidor (que pode recusar por limite de conexões).
 */
@OptIn(UnstableApi::class)
class MotorExo(context: Context) : MotorVideo {

    private val _estado = MutableStateFlow(EstadoPlayer())
    override val estado: StateFlow<EstadoPlayer> = _estado

    private val player: ExoPlayer = run {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(ClienteXtream.UA)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(TIMEOUT_MS)
            .setReadTimeoutMs(TIMEOUT_MS)
            .setKeepPostFor302Redirects(true)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 30_000,
                /* maxBufferMs = */ 120_000,
                /* bufferForPlaybackMs = */ 2_500,
                /* bufferForPlaybackAfterRebufferMs = */ 5_000,
            )
            .setBackBuffer(/* backBufferDurationMs = */ 30_000, /* retainBackBufferFromKeyframe = */ true)
            .build()

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .setLoadControl(loadControl)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(30_000)
            .build()
    }

    private val ouvinte = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            _estado.update {
                it.copy(
                    carregando = playbackState == Player.STATE_BUFFERING,
                    terminou = playbackState == Player.STATE_ENDED,
                    duracaoMs = player.duration.coerceAtLeast(0L),
                )
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _estado.update { it.copy(tocando = isPlaying) }
        }

        override fun onPlayerError(error: PlaybackException) {
            // A mensagem crua do ExoPlayer pode conter a URL — e a URL do Xtream
            // carrega usuário e senha no caminho. Expor só o código do erro.
            _estado.update {
                it.copy(
                    erro = "Falha no ExoPlayer (${error.errorCodeName}). " +
                        "Tente trocar para o motor VLC.",
                    carregando = false,
                )
            }
        }
    }

    init {
        player.addListener(ouvinte)
    }

    override fun criarView(context: Context): View =
        PlayerView(context).apply {
            useController = false // controles são do Compose, iguais nos dois motores
            setKeepContentOnPlayerReset(true)
            this.player = this@MotorExo.player
        }

    override fun abrir(url: String, posicaoInicialMs: Long) {
        player.setMediaItem(MediaItem.fromUri(url))
        if (posicaoInicialMs > 0L) player.seekTo(posicaoInicialMs)
        player.prepare()
        player.playWhenReady = true
    }

    override fun tocar() {
        player.play()
    }

    override fun pausar() {
        player.pause()
    }

    override fun buscar(posicaoMs: Long) {
        player.seekTo(posicaoMs.coerceAtLeast(0L))
    }

    override fun atualizarProgresso() {
        _estado.update {
            it.copy(
                posicaoMs = player.currentPosition.coerceAtLeast(0L),
                duracaoMs = player.duration.coerceAtLeast(0L),
            )
        }
    }

    override fun liberar() {
        player.removeListener(ouvinte)
        player.release()
    }

    private companion object {
        const val TIMEOUT_MS = 30_000
    }
}
