package io.zaffa.filmes.player

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.media3.cast.Cast
import androidx.media3.cast.RemoteCastPlayer
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * O "terceiro motor": o vídeo toca **na TV** (Chromecast) e o celular vira
 * controle remoto.
 *
 * Cumpre o mesmo contrato dos outros dois, então a tela do player não muda:
 * pausar, avançar 30 s e a barra de progresso passam a comandar a TV. Entrar e
 * sair do Cast é uma troca de motor como ExoPlayer ⇄ VLC — **a posição do filme
 * é preservada** nos dois sentidos.
 *
 * O link enviado é o mesmo que o celular usaria: o Chromecast segue sozinho o
 * redirecionamento 302 para o arquivo com token. ⚠️ Ele leva usuário e senha
 * (protocolo Xtream) e vai para a TV da própria casa — nunca para log.
 *
 * O [player] pertence à tela, não a este motor: ele também avisa quando a TV
 * conecta e desconecta, e precisa viver antes e depois deste objeto.
 */
class MotorCast(
    private val player: RemoteCastPlayer,
    private val titulo: String,
) : MotorVideo {

    private val _estado = MutableStateFlow(EstadoPlayer())
    override val estado: StateFlow<EstadoPlayer> = _estado

    private val ouvinte = object : Player.Listener {
        override fun onEvents(p: Player, events: Player.Events) = atualizarProgresso()

        override fun onPlayerError(error: PlaybackException) {
            _estado.update {
                it.copy(
                    carregando = false,
                    erro = "A TV não conseguiu tocar este título. " +
                        "Volte a assistir no celular, ou tente outro título.",
                )
            }
        }
    }

    init {
        player.addListener(ouvinte)
    }

    /** Não há vídeo no celular durante o Cast: mostra para onde ele foi. */
    override fun criarView(context: Context): View {
        val tv = runCatching {
            Cast.getSingletonInstance(context).currentCastSession?.castDevice?.friendlyName
        }.getOrNull() ?: "TV"
        return TextView(context).apply {
            text = "📺\n\nTransmitindo para $tv"
            textSize = 18f
            setTextColor(0xFFF5F3FF.toInt())
            gravity = Gravity.CENTER
        }
    }

    override fun abrir(url: String, posicaoInicialMs: Long) {
        _estado.value = EstadoPlayer(carregando = true)
        val item = MediaItem.Builder()
            .setUri(url)
            // O Cast exige o tipo declarado: sem ele, o conversor do Media3
            // recusa o item. TV ao vivo é HLS; filme e episódio são arquivo.
            .setMimeType(tipoDe(url))
            .setMediaMetadata(MediaMetadata.Builder().setTitle(titulo).build())
            .build()
        player.setMediaItem(item, posicaoInicialMs)
        player.prepare()
        player.playWhenReady = true
    }

    override fun tocar() { player.playWhenReady = true }

    override fun pausar() { player.playWhenReady = false }

    override fun buscar(posicaoMs: Long) {
        player.seekTo(posicaoMs.coerceAtLeast(0L))
        atualizarProgresso()
    }

    override fun atualizarProgresso() {
        val duracao = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
        _estado.update {
            it.copy(
                tocando = player.isPlaying,
                carregando = player.playbackState == Player.STATE_BUFFERING,
                posicaoMs = player.currentPosition.coerceAtLeast(0L),
                duracaoMs = duracao,
                terminou = player.playbackState == Player.STATE_ENDED,
            )
        }
    }

    /** Só se desliga do player; parar a TV é decisão da tela, que é a dona dele. */
    override fun liberar() {
        player.removeListener(ouvinte)
    }

    private fun tipoDe(url: String): String {
        val ext = url.substringBefore('?').substringAfterLast('.', "").lowercase()
        return when (ext) {
            "m3u8" -> MimeTypes.APPLICATION_M3U8
            "mkv" -> MimeTypes.VIDEO_MATROSKA
            else -> MimeTypes.VIDEO_MP4
        }
    }
}
