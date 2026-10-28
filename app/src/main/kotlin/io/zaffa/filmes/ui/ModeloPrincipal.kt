package io.zaffa.filmes.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.zaffa.filmes.FilmesApp
import io.zaffa.filmes.painel.CapturaDeVideo
import io.zaffa.filmes.painel.ClientePainel
import io.zaffa.filmes.player.Motor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Em qual tela o app está.
 *
 * A divisão reflete a arquitetura:
 * - [Login] e [Inicio] são nossas, nativas — a identidade do app.
 * - [Painel] é a página do painel: catálogo, busca e categorias, mantidos por
 *   quem os publica.
 * - [Player] é nosso — onde mora o controle da reprodução.
 */
sealed interface Tela {
    data object Carregando : Tela
    data object Login : Tela
    data object Inicio : Tela
    data class Painel(val url: String, val raiz: String) : Tela
    data class Player(val url: String, val titulo: String) : Tela
}

data class EstadoLogin(
    val usuario: String = "",
    val senha: String = "",
    val ocupado: Boolean = false,
    val erro: String? = null,
)

class ModeloPrincipal(aplicacao: Application) : AndroidViewModel(aplicacao) {

    private val app = aplicacao as FilmesApp

    private val _tela = MutableStateFlow<Tela>(Tela.Carregando)
    val tela: StateFlow<Tela> = _tela

    private val _login = MutableStateFlow(EstadoLogin())
    val login: StateFlow<EstadoLogin> = _login

    private val _motor = MutableStateFlow(Motor.EXOPLAYER)
    val motor: StateFlow<Motor> = _motor

    /** De onde o player foi aberto, para o "voltar" cair no lugar certo. */
    private var ultimaPaginaDoPainel: String? = null

    /** Raiz do painel, usada pelo botão "início" da barra. */
    private var raizDoPainel: String? = null

    init {
        viewModelScope.launch {
            _motor.value = app.preferencias.motor.first()
            _tela.value = if (app.preferencias.sessaoAtiva.first()) Tela.Inicio else Tela.Login
        }
    }

    fun aoDigitarUsuario(v: String) = _login.update { it.copy(usuario = v, erro = null) }

    fun aoDigitarSenha(v: String) = _login.update { it.copy(senha = v, erro = null) }

    fun entrar() {
        val estado = _login.value
        if (estado.usuario.isBlank() || estado.senha.isBlank()) {
            _login.update { it.copy(erro = "Preencha usuário e senha.") }
            return
        }

        viewModelScope.launch {
            _login.update { it.copy(ocupado = true, erro = null) }
            val base = app.preferencias.urlPainel.first()

            when (app.clientePainel.entrar(base, estado.usuario, estado.senha)) {
                is ClientePainel.Resultado.Ok -> {
                    app.preferencias.marcarSessaoAtiva(true)
                    // A senha sai da memória assim que deixa de ser necessária.
                    _login.value = EstadoLogin()
                    _tela.value = Tela.Inicio
                }

                is ClientePainel.Resultado.CredenciaisInvalidas ->
                    _login.update {
                        it.copy(ocupado = false, erro = "Usuário ou senha incorretos.")
                    }

                is ClientePainel.Resultado.Falhou ->
                    _login.update {
                        it.copy(
                            ocupado = false,
                            erro = "Não consegui falar com o servidor. " +
                                "Verifique a sua internet e tente de novo.",
                        )
                    }
            }
        }
    }

    fun sair() {
        viewModelScope.launch {
            app.clientePainel.sair()
            app.preferencias.marcarSessaoAtiva(false)
            _login.value = EstadoLogin()
            _tela.value = Tela.Login
        }
    }

    /** Abre o painel já na seção escolhida, pulando o menu do site. */
    fun abrirSecao(secao: Secao) {
        viewModelScope.launch {
            val base = app.preferencias.urlPainel.first()
            val url = secao.url(base)
            raizDoPainel = base
            ultimaPaginaDoPainel = url
            _tela.value = Tela.Painel(url, base)
        }
    }

    /** Abre o painel na página inicial dele. */
    fun abrirPainel() {
        viewModelScope.launch {
            val url = app.preferencias.urlPainel.first()
            raizDoPainel = url
            ultimaPaginaDoPainel = url
            _tela.value = Tela.Painel(url, url)
        }
    }

    /**
     * Chamado quando a URL do filme aparece no tráfego da página.
     *
     * É o momento central do app: a partir daqui o vídeo deixa de ser
     * responsabilidade do painel e passa a ser do nosso player.
     */
    fun aoCapturarVideo(url: String, tituloDaPagina: String = "") {
        if (!CapturaDeVideo.ehVideoDoPainel(url)) return
        if ((_tela.value as? Tela.Player)?.url == url) return
        // O título da página é o nome do filme. Sem ele sobra o número do
        // arquivo, que não diz nada a quem está assistindo.
        val titulo = tituloDaPagina.ifBlank { CapturaDeVideo.identificador(url) }
        _tela.value = Tela.Player(url = url, titulo = titulo)
    }

    /**
     * Acompanha a navegação dentro do painel.
     *
     * O WebView é destruído ao sair da tela, então sem guardar a página atual o
     * usuário voltaria do player para o começo da seção, perdendo onde estava.
     */
    fun aoNavegarNoPainel(url: String) {
        ultimaPaginaDoPainel = url
    }

    fun trocarMotor() {
        val novo = _motor.value.outro()
        _motor.value = novo
        viewModelScope.launch { app.preferencias.salvarMotor(novo) }
    }

    /** Do player, volta exatamente para a página de onde o filme foi aberto. */
    fun voltarAoPainel() {
        val destino = ultimaPaginaDoPainel
        if (destino == null) {
            _tela.value = Tela.Inicio
        } else {
            _tela.value = Tela.Painel(destino, raizDoPainel ?: destino)
        }
    }

    /** Do painel, volta para a tela inicial. */
    fun voltarAoInicio() {
        _tela.value = Tela.Inicio
    }
}
