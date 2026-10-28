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
 * SOBRE AS CREDENCIAIS: quando o usuário marca "lembrar meus dados", usuário e
 * senha ficam aqui, no **armazenamento privado do app**. Nenhum outro aplicativo
 * consegue ler, e nada sai do aparelho — o app não tem servidor próprio.
 *
 * Isso é escolha do usuário, e ele pode desfazer: desmarcar a opção ou sair da
 * conta apaga o que estava guardado. Sem isso, toda vez que a sessão do painel
 * expirasse ele teria de digitar a senha de novo.
 *
 * ⚠️ Estas credenciais **nunca** podem ser escritas em log, arquivo do projeto ou
 * mensagem de erro. Ver docs/PROTOCOLO.md §2.
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

    /** Se há sessão do painel ativa (a sessão de verdade vive nos cookies). */
    val sessaoAtiva: Flow<Boolean> = context.armazenamento.data.map { p ->
        p[SESSAO] == "1"
    }

    suspend fun marcarSessaoAtiva(ativa: Boolean) {
        context.armazenamento.edit { p -> p[SESSAO] = if (ativa) "1" else "0" }
    }

    /**
     * Credenciais guardadas, ou `null` se o usuário não pediu para lembrar.
     *
     * É o que permite o app **entrar sozinho** quando a sessão do painel expira,
     * em vez de jogar o usuário numa tela de login do nada.
     */
    val credenciaisSalvas: Flow<Credenciais?> = context.armazenamento.data.map { p ->
        val usuario = p[USUARIO]
        val senha = p[SENHA]
        if (usuario.isNullOrBlank() || senha.isNullOrBlank()) {
            null
        } else {
            Credenciais(usuario, senha)
        }
    }

    suspend fun lembrarCredenciais(usuario: String, senha: String) {
        context.armazenamento.edit { p ->
            p[USUARIO] = usuario
            p[SENHA] = senha
        }
    }

    suspend fun esquecerCredenciais() {
        context.armazenamento.edit { p ->
            p.remove(USUARIO)
            p.remove(SENHA)
        }
    }

    suspend fun salvarUrlPainel(url: String) {
        context.armazenamento.edit { p -> p[URL_PAINEL] = url }
    }

    private companion object {
        val MOTOR = stringPreferencesKey("motor")
        val URL_PAINEL = stringPreferencesKey("url_painel")
        val SESSAO = stringPreferencesKey("sessao_ativa")
        val USUARIO = stringPreferencesKey("usuario")
        val SENHA = stringPreferencesKey("senha")
    }
}

/**
 * Credenciais do painel, em memória.
 *
 * `toString` é sobrescrito para a senha nunca aparecer por acidente num log ou
 * numa mensagem de erro.
 */
data class Credenciais(val usuario: String, val senha: String) {
    override fun toString(): String = "Credenciais(usuario=***, senha=***)"
}
