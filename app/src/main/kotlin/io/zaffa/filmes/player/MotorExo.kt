package io.zaffa.filmes.player

import android.content.Context
import android.os.SystemClock
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
import io.zaffa.filmes.core.model.Padroes
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

    /** Quantas vezes já tentamos retomar desde a última reprodução estável. */
    private var tentativasDeRecuperacao = 0

    /** Quando a reprodução começou, para distinguir retomada real de engasgo. */
    private var inicioDaReproducaoMs = 0L

    private val player: ExoPlayer = run {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(Padroes.USER_AGENT)
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
            if (isPlaying) inicioDaReproducaoMs = SystemClock.elapsedRealtime()
            _estado.update { it.copy(tocando = isPlaying) }
        }

        /**
         * RECUPERAÇÃO AUTOMÁTICA — o coração da correção do bug relatado.
         *
         * Sintoma do app oficial: "avanço o filme para a metade e ele trava e não
         * volta mais". Medimos o servidor e ele **não** tem culpa — devolve 206
         * para qualquer posição em ~0,27s. Ou seja, quando a conexão falha depois
         * de um avanço, **basta tentar de novo**; o app oficial simplesmente
         * desiste e fica congelado para sempre.
         *
         * Aqui, em vez de desistir, reabrimos o fluxo na posição em que o usuário
         * estava. O resultado é uma engasgada de um ou dois segundos no lugar de
         * um travamento permanente.
         */
        override fun onPlayerError(error: PlaybackException) {
            val posicao = player.currentPosition.coerceAtLeast(0L)

            // Só devolve o orçamento de tentativas se o vídeo tocou de forma
            // ESTÁVEL antes de falhar. Zerar o contador a cada retomada curta
            // criaria um laço infinito: tocar meio segundo, falhar, zerar,
            // tentar de novo, para sempre. É exatamente o defeito observado no
            // player do painel, que segue pedindo e falhando até com o vídeo
            // pausado (relato do dono, 2026-10-28).
            val tocouDeVerdade = inicioDaReproducaoMs > 0L &&
                SystemClock.elapsedRealtime() - inicioDaReproducaoMs > REPRODUCAO_ESTAVEL_MS
            if (tocouDeVerdade) tentativasDeRecuperacao = 0

            if (tentativasDeRecuperacao < MAX_TENTATIVAS) {
                tentativasDeRecuperacao++
                _estado.update {
                    it.copy(
                        carregando = true,
                        erro = null,
                        posicaoMs = posicao,
                    )
                }
                player.prepare()
                player.seekTo(posicao)
                player.playWhenReady = true
                return
            }

            // Esgotadas as tentativas: aí sim avisamos, e sugerimos o outro motor.
            // A mensagem crua do ExoPlayer pode conter a URL — e a URL carrega o
            // token de acesso. Expor só o código do erro.
            _estado.update {
                it.copy(
                    erro = "Não consegui retomar o filme nesta posição " +
                        "(${error.errorCodeName}). Tente trocar para o motor VLC.",
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

        /**
         * Tentativas de retomada antes de desistir e avisar o usuário.
         *
         * Cinco é folgado de propósito: uma falha de rede passageira não pode
         * custar o filme. O contador zera sempre que a reprodução volta, então
         * o limite vale por incidente, não pelo filme inteiro.
         */
        const val MAX_TENTATIVAS = 5

        /**
         * Quanto tempo o vídeo precisa tocar sem falhar para o orçamento de
         * tentativas ser devolvido. Abaixo disto, considera-se que ele não
         * chegou a se recuperar de verdade.
         */
        const val REPRODUCAO_ESTAVEL_MS = 10_000L
    }
}
