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
    /** "Lembrar meus dados" — guarda as credenciais no aparelho. */
    val lembrar: Boolean = true,
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
            val salvas = app.preferencias.credenciaisSalvas.first()

            when {
                // Sessão ainda válida: entra direto.
                app.preferencias.sessaoAtiva.first() -> _tela.value = Tela.Inicio

                // Sem sessão, mas com dados guardados: entra sozinho. É isto que
                // evita o usuário cair numa tela de login do nada, só porque a
                // sessão do painel expirou.
                salvas != null -> {
                    _login.update {
                        it.copy(usuario = salvas.usuario, senha = salvas.senha, ocupado = true)
                    }
                    entrar()
                }

                else -> _tela.value = Tela.Login
            }
        }
    }

    fun aoDigitarUsuario(v: String) = _login.update { it.copy(usuario = v, erro = null) }

    fun aoDigitarSenha(v: String) = _login.update { it.copy(senha = v, erro = null) }

    fun aoAlternarLembrar() = _login.update { it.copy(lembrar = !it.lembrar) }

    /**
     * Marca que estamos usando a página do painel para logar, e não o nosso
     * formulário.
     */
    private var logandoPelaPagina = false

    /**
     * Abre a página de login do próprio painel.
     *
     * Existe como caminho garantido: o login nativo ([entrar]) monta a
     * requisição a partir dos campos do formulário, e isso **pode não bastar** —
     * o painel pode exigir algo que não enxergamos. Quando ele falha, o usuário
     * não pode ficar sem acesso ao app.
     *
     * Aqui quem faz o login é a página do painel, exatamente como no navegador.
     * Os cookies ficam no WebView, e o resto do app funciona igual.
     */
    fun entrarPelaPagina() {
        viewModelScope.launch {
            val base = app.preferencias.urlPainel.first()
            val raiz = base.trimEnd('/')
            val urlLogin = "$raiz/index.php?page=login"
            logandoPelaPagina = true
            raizDoPainel = raiz
            ultimaPaginaDoPainel = urlLogin
            _tela.value = Tela.Painel(urlLogin, raiz)
        }
    }

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
                    if (estado.lembrar) {
                        app.preferencias.lembrarCredenciais(estado.usuario, estado.senha)
                    } else {
                        app.preferencias.esquecerCredenciais()
                    }
                    // A senha sai da memória assim que deixa de ser necessária.
                    _login.value = EstadoLogin()
                    _tela.value = Tela.Inicio
                }

                is ClientePainel.Resultado.CredenciaisInvalidas -> {
                    // Senha guardada não serve mais (trocaram no painel): apaga,
                    // senão o app tentaria entrar sozinho e falhar para sempre.
                    app.preferencias.esquecerCredenciais()
                    _login.update {
                        it.copy(ocupado = false, senha = "", erro = "Usuário ou senha incorretos.")
                    }
                    _tela.value = Tela.Login
                }

                is ClientePainel.Resultado.Falhou -> {
                    // Falha de rede não apaga os dados guardados: a senha pode
                    // estar certa e só a internet ter caído.
                    _login.update {
                        it.copy(
                            ocupado = false,
                            erro = "Não consegui falar com o servidor. " +
                                "Verifique a sua internet e tente de novo.",
                        )
                    }
                    _tela.value = Tela.Login
                }
            }
        }
    }

    fun sair() {
        viewModelScope.launch {
            app.clientePainel.sair()
            app.preferencias.marcarSessaoAtiva(false)
            // Sair tem de apagar os dados guardados, senão o app entraria sozinho
            // de novo na abertura seguinte e "sair" não teria significado algum.
            app.preferencias.esquecerCredenciais()
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

        // Saiu da página de login => o painel aceitou as credenciais. É assim
        // que detectamos sucesso quando o login é feito pela página dele.
        if (logandoPelaPagina && !url.contains("page=login", ignoreCase = true)) {
            logandoPelaPagina = false
            viewModelScope.launch { app.preferencias.marcarSessaoAtiva(true) }
        }
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
