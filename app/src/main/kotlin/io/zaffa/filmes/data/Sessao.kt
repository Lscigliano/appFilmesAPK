package io.zaffa.filmes.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.zaffa.filmes.core.model.Credenciais
import io.zaffa.filmes.player.Motor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.armazenamento by preferencesDataStore(name = "sessao")

/**
 * Guarda a sessão do usuário no aparelho.
 *
 * SEGURANÇA: as credenciais ficam SÓ aqui, no armazenamento privado do app, e
 * nunca entram no repositório git nem em log. O arquivo `local.properties` e
 * `credenciais.local.json` (usados só para desenvolvimento) estão no .gitignore.
 * Ver docs/PROTOCOLO.md, seção 2.
 */
class Sessao(private val context: Context) {

    val dados: Flow<DadosSessao?> = context.armazenamento.data.map { p -> ler(p) }

    private fun ler(p: Preferences): DadosSessao? {
        val host = p[HOST] ?: return null
        val usuario = p[USUARIO] ?: return null
        val senha = p[SENHA] ?: return null
        return DadosSessao(
            credenciais = Credenciais(host, usuario, senha),
            baseUrlDescoberta = p[BASE_URL],
            motorPreferido = p[MOTOR]?.let { nome ->
                runCatching { Motor.valueOf(nome) }.getOrNull()
            } ?: Motor.EXOPLAYER,
        )
    }

    suspend fun salvar(credenciais: Credenciais, baseUrl: String) {
        context.armazenamento.edit { p ->
            p[HOST] = credenciais.host
            p[USUARIO] = credenciais.usuario
            p[SENHA] = credenciais.senha
            p[BASE_URL] = baseUrl
        }
    }

    /**
     * Guarda o motor que o usuário escolheu.
     *
     * Isto importa mais do que parece: se o VLC resolveu o travamento, ele deve
     * continuar sendo o motor na próxima vez que o app abrir — sem o usuário
     * precisar descobrir de novo.
     */
    suspend fun salvarMotor(motor: Motor) {
        context.armazenamento.edit { p -> p[MOTOR] = motor.name }
    }

    suspend fun sair() {
        context.armazenamento.edit { p -> p.clear() }
    }

    private companion object {
        val HOST = stringPreferencesKey("host")
        val USUARIO = stringPreferencesKey("usuario")
        val SENHA = stringPreferencesKey("senha")
        val BASE_URL = stringPreferencesKey("base_url")
        val MOTOR = stringPreferencesKey("motor")
    }
}

data class DadosSessao(
    val credenciais: Credenciais,
    val baseUrlDescoberta: String?,
    val motorPreferido: Motor,
)
