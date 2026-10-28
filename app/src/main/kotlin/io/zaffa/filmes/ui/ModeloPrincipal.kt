package io.zaffa.filmes.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.zaffa.filmes.FilmesApp
import io.zaffa.filmes.core.api.ClienteXtream
import io.zaffa.filmes.core.model.Categoria
import io.zaffa.filmes.core.model.ContaInfo
import io.zaffa.filmes.core.model.Credenciais
import io.zaffa.filmes.core.model.Falha
import io.zaffa.filmes.core.model.Padroes
import io.zaffa.filmes.core.model.Filme
import io.zaffa.filmes.core.model.Resultado
import io.zaffa.filmes.player.Motor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Em qual tela o app está. Navegação por estado — simples o bastante para 3 telas. */
sealed interface Tela {
    data object Carregando : Tela
    data object Login : Tela
    data object Catalogo : Tela
    data class Player(val filme: Filme, val url: String) : Tela
}

data class EstadoLogin(
    /** Já vem preenchido com [Padroes.SERVIDOR]; o usuário pode trocar. */
    val host: String = Padroes.SERVIDOR,
    val usuario: String = "",
    val senha: String = "",
    val ocupado: Boolean = false,
    val erro: String? = null,
    /** Mensagem do tipo "procurando servidor em 8080…" durante a detecção. */
    val progresso: String? = null,
)

data class EstadoCatalogo(
    val carregando: Boolean = true,
    val conta: ContaInfo? = null,
    val categorias: List<Categoria> = emptyList(),
    val categoriaAtual: String? = null,
    val filmes: List<Filme> = emptyList(),
    val busca: String = "",
    val erro: String? = null,
) {
    val filmesVisiveis: List<Filme>
        get() = if (busca.isBlank()) {
            filmes
        } else {
            filmes.filter { it.nome.contains(busca.trim(), ignoreCase = true) }
        }
}

class ModeloPrincipal(aplicacao: Application) : AndroidViewModel(aplicacao) {

    private val app = aplicacao as FilmesApp

    private val _tela = MutableStateFlow<Tela>(Tela.Carregando)
    val tela: StateFlow<Tela> = _tela

    private val _login = MutableStateFlow(EstadoLogin())
    val login: StateFlow<EstadoLogin> = _login

    private val _catalogo = MutableStateFlow(EstadoCatalogo())
    val catalogo: StateFlow<EstadoCatalogo> = _catalogo

    private val _motor = MutableStateFlow(Motor.EXOPLAYER)
    val motor: StateFlow<Motor> = _motor

    private var cliente: ClienteXtream? = null

    init {
        viewModelScope.launch {
            val salvo = app.sessao.dados.first()
            if (salvo == null) {
                _tela.value = Tela.Login
                return@launch
            }
            _motor.value = salvo.motorPreferido
            // Sessão salva: reaproveita o endpoint já descoberto e entra direto.
            val base = salvo.baseUrlDescoberta
            if (base != null) {
                cliente = ClienteXtream(app.http, base, salvo.credenciais)
                _tela.value = Tela.Catalogo
                carregarCatalogo()
            } else {
                _login.update { it.copy(host = salvo.credenciais.host, usuario = salvo.credenciais.usuario) }
                _tela.value = Tela.Login
            }
        }
    }

    fun aoDigitarHost(v: String) = _login.update { it.copy(host = v, erro = null) }
    fun aoDigitarUsuario(v: String) = _login.update { it.copy(usuario = v, erro = null) }
    fun aoDigitarSenha(v: String) = _login.update { it.copy(senha = v, erro = null) }

    fun entrar() {
        val estado = _login.value
        if (estado.host.isBlank() || estado.usuario.isBlank() || estado.senha.isBlank()) {
            _login.update { it.copy(erro = "Preencha servidor, usuário e senha.") }
            return
        }

        viewModelScope.launch {
            _login.update { it.copy(ocupado = true, erro = null, progresso = "Procurando o servidor…") }

            val credenciais = Credenciais(estado.host, estado.usuario, estado.senha)
            when (val r = app.detector.detectar(estado.host, credenciais)) {
                is Resultado.Erro -> _login.update {
                    it.copy(ocupado = false, progresso = null, erro = r.causa.mensagem)
                }

                is Resultado.Ok -> {
                    val base = r.valor.endpoint.baseUrl
                    app.sessao.salvar(credenciais, base)
                    cliente = ClienteXtream(app.http, base, credenciais)
                    _catalogo.update { it.copy(conta = r.valor.conta) }
                    _login.update { it.copy(ocupado = false, progresso = null, senha = "") }
                    _tela.value = Tela.Catalogo
                    carregarCatalogo()
                }
            }
        }
    }

    fun carregarCatalogo() {
        val c = cliente ?: return
        viewModelScope.launch {
            _catalogo.update { it.copy(carregando = true, erro = null) }

            if (_catalogo.value.conta == null) {
                (c.autenticar() as? Resultado.Ok)?.let { ok ->
                    _catalogo.update { it.copy(conta = ok.valor) }
                }
            }

            when (val cats = c.categoriasDeFilmes()) {
                is Resultado.Ok -> _catalogo.update { it.copy(categorias = cats.valor) }
                is Resultado.Erro -> Unit // catálogo sem categorias ainda é utilizável
            }

            when (val fs = c.filmes(_catalogo.value.categoriaAtual)) {
                is Resultado.Ok -> _catalogo.update {
                    it.copy(carregando = false, filmes = fs.valor)
                }

                is Resultado.Erro -> _catalogo.update {
                    it.copy(carregando = false, erro = fs.causa.mensagem)
                }
            }
        }
    }

    fun escolherCategoria(id: String?) {
        _catalogo.update { it.copy(categoriaAtual = id) }
        carregarCatalogo()
    }

    fun aoBuscar(v: String) = _catalogo.update { it.copy(busca = v) }

    /**
     * Abre o filme. Busca o detalhe antes porque a extensão real do arquivo
     * (`mp4`/`mkv`) às vezes só vem no detalhe — e extensão errada na URL
     * devolve 404, fazendo o filme simplesmente "não abrir".
     */
    fun abrirFilme(filme: Filme) {
        val c = cliente ?: return
        viewModelScope.launch {
            val completo = when (val d = c.detalhe(filme)) {
                is Resultado.Ok -> d.valor.filme
                is Resultado.Erro -> filme
            }
            _tela.value = Tela.Player(completo, c.urlDoFilme(completo))
        }
    }

    fun trocarMotor() {
        val novo = _motor.value.outro()
        _motor.value = novo
        viewModelScope.launch { app.sessao.salvarMotor(novo) }
    }

    fun voltarAoCatalogo() {
        _tela.value = Tela.Catalogo
    }

    fun sair() {
        viewModelScope.launch {
            app.sessao.sair()
            cliente = null
            _catalogo.value = EstadoCatalogo()
            _login.value = EstadoLogin()
            _tela.value = Tela.Login
        }
    }

    /** Mensagem de aviso sobre a conta, quando houver algo que afete a reprodução. */
    fun avisoDaConta(): String? {
        val conta = _catalogo.value.conta ?: return null
        return if (conta.noLimiteDeConexoes) Falha.LimiteDeConexoes.mensagem else null
    }
}
