package io.zaffa.filmes

import android.app.Application
import io.zaffa.filmes.data.Preferencias

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

    override fun onCreate() {
        super.onCreate()
        preferencias = Preferencias(this)
    }
}
