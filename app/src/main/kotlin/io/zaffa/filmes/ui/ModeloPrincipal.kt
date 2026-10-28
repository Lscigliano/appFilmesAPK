package io.zaffa.filmes.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.zaffa.filmes.FilmesApp
import io.zaffa.filmes.painel.CapturaDeVideo
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
    /** Catálogo na nossa tela, lido da página do painel. */
    data class Catalogo(val secao: Secao) : Tela

    /**
     * Abrindo um título: a página de detalhe roda escondida só para o painel
     * emitir o token do vídeo. O usuário vê a nossa tela de carregamento.
     */
    data class Abrindo(val urlDetalhe: String, val titulo: String) : Tela
    data class Painel(val url: String, val raiz: String) : Tela
    data class Player(val url: String, val titulo: String) : Tela
}

/** Dados que o WebView invisível precisa para enviar o formulário. */
data class PedidoLogin(
    val urlLogin: String,
    val usuario: String,
    val senha: String,
)

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

    /** Pedido de login em andamento, consumido pelo WebView invisível. */
    private val _pedidoLogin = MutableStateFlow<PedidoLogin?>(null)
    val pedidoLogin: StateFlow<PedidoLogin?> = _pedidoLogin

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
                    _login.update { it.copy(usuario = salvas.usuario, senha = salvas.senha) }
                    // A tela de login precisa estar montada: é nela que vive o
                    // WebView invisível que envia o formulário.
                    _tela.value = Tela.Login
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

    /**
     * Dispara o login.
     *
     * Quem envia o formulário é um WebView invisível ([LoginInvisivel]), não um
     * cliente HTTP nosso: montar a requisição à mão não funcionou, e o navegador
     * já sabe fazer isso certo. A tela continua sendo a nossa.
     */
    fun entrar() {
        val estado = _login.value
        if (estado.usuario.isBlank() || estado.senha.isBlank()) {
            _login.update { it.copy(erro = "Preencha usuário e senha.") }
            return
        }

        viewModelScope.launch {
            _login.update { it.copy(ocupado = true, erro = null) }
            val raiz = app.preferencias.urlPainel.first().trimEnd('/')
            _pedidoLogin.value = PedidoLogin(
                urlLogin = "$raiz/index.php?page=login",
                usuario = estado.usuario,
                senha = estado.senha,
            )
        }
    }

    /** O painel aceitou: guarda o que foi pedido e entra. */
    fun aoLoginConcluido() {
        val estado = _login.value
        _pedidoLogin.value = null
        viewModelScope.launch {
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
    }

    /** O painel recusou, ou a rede falhou. */
    fun aoLoginFalhou(mensagem: String) {
        _pedidoLogin.value = null
        val credencialRuim = mensagem.contains("incorret", ignoreCase = true)
        viewModelScope.launch {
            // Senha guardada que não serve mais é apagada, senão o app tentaria
            // entrar sozinho e falhar para sempre. Falha de rede NÃO apaga: a
            // senha pode estar certa e só a internet ter caído.
            if (credencialRuim) app.preferencias.esquecerCredenciais()
            _login.update {
                it.copy(
                    ocupado = false,
                    senha = if (credencialRuim) "" else it.senha,
                    erro = mensagem,
                )
            }
            _tela.value = Tela.Login
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

    /**
     * Abre a seção escolhida.
     *
     * Filmes e Séries abrem no **nosso** catálogo — são as seções cujo endereço
     * está confirmado. As demais abrem a página do painel, que segue
     * funcionando. Quando os outros caminhos forem confirmados, migram para cá.
     */
    fun abrirSecao(secao: Secao) {
        viewModelScope.launch {
            val base = app.preferencias.urlPainel.first()
            raizDoPainel = base
            val nossoCatalogo = secao == Secao.FILMES || secao == Secao.SERIES
            if (nossoCatalogo) {
                _tela.value = Tela.Catalogo(secao)
            } else {
                val url = secao.url(base)
                ultimaPaginaDoPainel = url
                _tela.value = Tela.Painel(url, base)
            }
        }
    }

    /**
     * Do nosso catálogo, vai **direto para o nosso player**.
     *
     * A página de detalhe ainda precisa rodar, porque só o painel emite o token
     * do vídeo — mas roda escondida. O usuário vê a nossa tela de carregamento
     * com o nome do filme, e depois o player. A página do painel não aparece.
     */
    fun abrirItem(url: String, titulo: String) {
        viewModelScope.launch {
            raizDoPainel = app.preferencias.urlPainel.first()
            ultimaPaginaDoPainel = url
            _tela.value = Tela.Abrindo(url, titulo)
        }
    }

    /** O vídeo foi capturado da página escondida: toca no nosso player. */
    fun aoAbrirCapturado(url: String, tituloDaPagina: String) {
        val titulo = tituloDaPagina.ifBlank {
            (_tela.value as? Tela.Abrindo)?.titulo.orEmpty()
        }.ifBlank { CapturaDeVideo.identificador(url) }
        _tela.value = Tela.Player(url, titulo)
    }

    /**
     * Não deu para capturar o vídeo escondido.
     *
     * Acontece, por exemplo, quando o título só tem trailer do YouTube, ou
     * quando a página mudou de estrutura. Em vez de deixar o usuário preso num
     * carregamento, mostramos a página de verdade — lá ele dá o play e o vídeo
     * é capturado como antes.
     */
    fun aoDesistirDeAbrir() {
        val atual = _tela.value as? Tela.Abrindo ?: return
        val raiz = raizDoPainel ?: atual.urlDetalhe
        _tela.value = Tela.Painel(atual.urlDetalhe, raiz)
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
