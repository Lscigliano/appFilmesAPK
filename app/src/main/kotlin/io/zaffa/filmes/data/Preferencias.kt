package io.zaffa.filmes.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.zaffa.filmes.core.model.Padroes
import io.zaffa.filmes.player.Motor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.armazenamento by preferencesDataStore(name = "preferencias")

/**
 * Preferências do app.
 *
 * Não guarda credenciais: quem cuida do login é a página do painel, dentro do
 * WebView, e a sessão fica nos cookies do próprio WebView. Isso é melhor do que
 * o app guardar senha — não há o que vazar daqui.
 */
class Preferencias(private val context: Context) {

    val motor: Flow<Motor> = context.armazenamento.data.map { p ->
        p[MOTOR]?.let { nome -> runCatching { Motor.valueOf(nome) }.getOrNull() } ?: Motor.EXOPLAYER
    }

    val urlPainel: Flow<String> = context.armazenamento.data.map { p ->
        p[URL_PAINEL]?.takeIf { it.isNotBlank() } ?: Padroes.PAINEL_URL
    }

    /**
     * Guarda o motor escolhido.
     *
     * Importa mais do que parece: se o VLC resolveu o travamento, ele precisa
     * continuar sendo o motor na próxima vez — sem o usuário redescobrir.
     */
    suspend fun salvarMotor(motor: Motor) {
        context.armazenamento.edit { p -> p[MOTOR] = motor.name }
    }

    suspend fun salvarUrlPainel(url: String) {
        context.armazenamento.edit { p -> p[URL_PAINEL] = url }
    }

    private companion object {
        val MOTOR = stringPreferencesKey("motor")
        val URL_PAINEL = stringPreferencesKey("url_painel")
    }
}
