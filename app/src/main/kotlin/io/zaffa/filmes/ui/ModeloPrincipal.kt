package io.zaffa.filmes.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.zaffa.filmes.FilmesApp
import io.zaffa.filmes.data.Credenciais
import io.zaffa.filmes.player.Motor
import io.zaffa.filmes.xtream.ApiXtream
import io.zaffa.filmes.xtream.Episodio
import io.zaffa.filmes.xtream.FalhaApi
import io.zaffa.filmes.xtream.Titulo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Em qual tela o app está. **Todas são nossas** — o site do painel não aparece
 * em lugar nenhum: tudo vem da API do servidor de streaming.
 */
sealed interface Tela {
    data object Carregando : Tela
    data object Login : Tela
    data object Inicio : Tela

    /** Catálogo de uma seção; [categoriaId] nulo é "Todos". */
    data class Catalogo(val secao: Secao, val categoriaId: String? = null) : Tela

    /** Busca pelo nome em todo o acervo, de qualquer seção e categoria. */
    data object Busca : Tela

    /** Temporadas e episódios de uma série. */
    data class Serie(val serie: Titulo) : Tela

    data class Player(val url: String, val titulo: String) : Tela {
        // A URL leva usuário e senha (é o protocolo Xtream). Nunca imprimir.
        override fun toString(): String = "Player(titulo=$titulo)"
    }
}

data class EstadoLogin(
    val usuario: String = "",
    val senha: String = "",
    /** "Lembrar meus dados" — guarda as credenciais no aparelho. */
    val lembrar: Boolean = true,
    val ocupado: Boolean = false,
    val erro: String? = null,
)

class ModeloPrincipal(aplicacao: Application) : AndroidViewModel(aplicacao) {

    private val app = aplicacao as FilmesApp

    val acervo get() = app.acervo

    private val _tela = MutableStateFlow<Tela>(Tela.Carregando)
    val tela: StateFlow<Tela> = _tela

    private val _login = MutableStateFlow(EstadoLogin())
    val login: StateFlow<EstadoLogin> = _login

    private val _motor = MutableStateFlow(Motor.EXOPLAYER)
    val motor: StateFlow<Motor> = _motor

    /** Texto da busca, guardado aqui para sobreviver a abrir um filme e voltar. */
    var textoBusca by mutableStateOf("")

    /**
     * Credenciais da sessão, **só em memória**. Montam os links de vídeo, então
     * precisam existir mesmo quando o usuário não pede para lembrar.
     */
    private var credenciais: Credenciais? = null

    /** De onde se veio, para o "voltar" cair sempre na tela anterior. */
    private val pilha = ArrayDeque<Tela>()

    init {
        viewModelScope.launch {
            _motor.value = app.preferencias.motor.first()
            val salvas = app.preferencias.credenciaisSalvas.first()
            if (salvas != null) {
                // Dados guardados: entra sozinho. A tela de login fica por baixo
                // com os campos preenchidos, caso a rede falhe.
                _login.update { it.copy(usuario = salvas.usuario, senha = salvas.senha) }
                _tela.value = Tela.Login
                entrar()
            } else {
                _tela.value = Tela.Login
            }
        }
    }

    fun aoDigitarUsuario(v: String) = _login.update { it.copy(usuario = v, erro = null) }

    fun aoDigitarSenha(v: String) = _login.update { it.copy(senha = v, erro = null) }

    fun aoAlternarLembrar() = _login.update { it.copy(lembrar = !it.lembrar) }

    /** Valida usuário e senha direto na API do servidor. */
    fun entrar() {
        val estado = _login.value
        if (estado.usuario.isBlank() || estado.senha.isBlank()) {
            _login.update { it.copy(erro = "Preencha usuário e senha.") }
            return
        }
        val c = Credenciais(estado.usuario.trim(), estado.senha.trim())

        viewModelScope.launch {
            _login.update { it.copy(ocupado = true, erro = null) }
            when (val r = app.api.autenticar(c)) {
                ApiXtream.Autenticacao.Ok -> {
                    credenciais = c
                    if (estado.lembrar) {
                        app.preferencias.lembrarCredenciais(c.usuario, c.senha)
                    } else {
                        app.preferencias.esquecerCredenciais()
                    }
                    _login.value = EstadoLogin()
                    pilha.clear()
                    _tela.value = Tela.Inicio
                    carregarAcervo()
                }

                ApiXtream.Autenticacao.CredenciaisInvalidas -> {
                    // Senha guardada que não serve mais é apagada, senão o app
                    // tentaria entrar sozinho e falhar para sempre.
                    app.preferencias.esquecerCredenciais()
                    falhaDeLogin("Usuário ou senha incorretos.", limparSenha = true)
                }

                is ApiXtream.Autenticacao.ContaInativa ->
                    falhaDeLogin("A sua assinatura não está ativa (${r.status}).", limparSenha = false)

                // Falha de rede NÃO apaga a senha: ela pode estar certa e só a
                // internet ter caído.
                is ApiXtream.Autenticacao.Falhou -> falhaDeLogin(r.mensagem, limparSenha = false)
            }
        }
    }

    private fun falhaDeLogin(mensagem: String, limparSenha: Boolean) {
        _login.update {
            it.copy(ocupado = false, senha = if (limparSenha) "" else it.senha, erro = mensagem)
        }
        _tela.value = Tela.Login
    }

    fun sair() {
        viewModelScope.launch {
            // Sair tem de apagar os dados guardados, senão o app entraria sozinho
            // de novo na abertura seguinte e "sair" não teria significado algum.
            app.preferencias.esquecerCredenciais()
            credenciais = null
            acervo.esquecer()
            textoBusca = ""
            pilha.clear()
            _login.value = EstadoLogin()
            _tela.value = Tela.Login
        }
    }

    /** Carrega (ou recarrega) filmes, séries e canais da API. */
    fun carregarAcervo(forcar: Boolean = false) {
        val c = credenciais ?: return
        viewModelScope.launch { acervo.carregar(c, forcar) }
    }

    fun abrirSecao(secao: Secao) = irPara(Tela.Catalogo(secao))

    /** Troca a categoria sem empilhar: voltar sai da seção, não da categoria. */
    fun escolherCategoria(secao: Secao, categoriaId: String?) {
        _tela.value = Tela.Catalogo(secao, categoriaId)
    }

    fun abrirBusca() = irPara(Tela.Busca)

    /** Série abre a lista de episódios; filme e canal vão direto para o player. */
    fun abrirTitulo(titulo: Titulo) {
        val c = credenciais ?: return
        if (titulo.tipo == io.zaffa.filmes.xtream.Tipo.SERIE) {
            irPara(Tela.Serie(titulo))
        } else {
            irPara(Tela.Player(app.api.urlDe(c, titulo), titulo.nome))
        }
    }

    fun abrirEpisodio(serie: Titulo, episodio: Episodio) {
        val c = credenciais ?: return
        val nome = "${serie.nome} · T${episodio.temporada} E${episodio.numero}"
        irPara(Tela.Player(app.api.urlDe(c, episodio), nome))
    }

    /** @throws FalhaApi com mensagem pronta para a tela. */
    suspend fun episodios(serie: Titulo): List<Episodio> {
        val c = credenciais ?: throw FalhaApi("Sessão encerrada. Entre de novo.")
        return app.api.episodios(c, serie.id)
    }

    fun trocarMotor() {
        val novo = _motor.value.outro()
        _motor.value = novo
        viewModelScope.launch { app.preferencias.salvarMotor(novo) }
    }

    private fun irPara(destino: Tela) {
        pilha.addLast(_tela.value)
        _tela.value = destino
    }

    /** Volta para a tela de onde se veio — do player, cai no catálogo/busca/série. */
    fun voltar() {
        _tela.value = pilha.removeLastOrNull() ?: Tela.Inicio
    }
}
