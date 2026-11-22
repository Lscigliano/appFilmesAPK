package io.zaffa.filmes

import android.app.Application
import androidx.media3.cast.Cast
import androidx.media3.cast.CastParams
import com.google.android.gms.cast.CastMediaControlIntent
import io.zaffa.filmes.data.Preferencias
import io.zaffa.filmes.xtream.Acervo
import io.zaffa.filmes.xtream.ApiXtream

/**
 * Container de dependências, feito à mão.
 *
 * O app conversa com um único lugar: a API Xtream do servidor de streaming
 * ([ApiXtream]). O [Acervo] guarda o catálogo carregado dela, aqui e não numa
 * tela, para sobreviver a abrir um filme e voltar.
 *
 * Decisão consciente de NÃO usar Hilt: um objeto num `Application` é mais legível
 * para quem (ou qual IA) chegar depois sem contexto.
 */
class FilmesApp : Application() {

    lateinit var preferencias: Preferencias
        private set

    val api: ApiXtream by lazy { ApiXtream() }

    val acervo: Acervo by lazy { Acervo(api) }

    override fun onCreate() {
        super.onCreate()
        preferencias = Preferencias(this)

        // Google Cast, para o botão de transmitir para a TV. O receptor é o
        // Default Media Receiver do Google: roda em qualquer Chromecast,
        // inclusive os antigos, sem precisarmos publicar um receptor nosso.
        // Aparelho sem Google Play Services não tem Cast — o app segue normal,
        // e o botão simplesmente não acha TV nenhuma.
        runCatching {
            Cast.getSingletonInstance(this).initialize(
                CastParams.Builder()
                    .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
                    .build(),
            )
        }
    }
}
