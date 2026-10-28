package io.zaffa.filmes

import android.app.Application
import io.zaffa.filmes.data.Preferencias
import io.zaffa.filmes.painel.ClientePainel

/**
 * Container de dependências, feito à mão.
 *
 * Ficou pequeno de propósito. O app não fala com nenhuma API: quem conversa com
 * o painel é a própria página dele, dentro do WebView. O que sobra para o app é
 * capturar a URL do vídeo e tocá-la no nosso player.
 *
 * Decisão consciente de NÃO usar Hilt: um objeto num `Application` é mais legível
 * para quem (ou qual IA) chegar depois sem contexto.
 */
class FilmesApp : Application() {

    lateinit var preferencias: Preferencias
        private set

    /** Faz o login do painel a partir do nosso formulário. */
    val clientePainel: ClientePainel by lazy { ClientePainel() }

    override fun onCreate() {
        super.onCreate()
        preferencias = Preferencias(this)
    }
}
