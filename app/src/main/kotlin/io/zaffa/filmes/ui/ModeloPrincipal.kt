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
import kotlinx.coroutines.launch

/** Em qual tela o app está. Duas telas só — navegação por estado basta. */
sealed interface Tela {
    data object Carregando : Tela

    /** A página do painel: login, catálogo, escolha do filme. */
    data class Painel(val url: String) : Tela

    /** Nosso player, com o vídeo capturado da página. */
    data class Player(val url: String, val titulo: String) : Tela
}

class ModeloPrincipal(aplicacao: Application) : AndroidViewModel(aplicacao) {

    private val app = aplicacao as FilmesApp

    private val _tela = MutableStateFlow<Tela>(Tela.Carregando)
    val tela: StateFlow<Tela> = _tela

    private val _motor = MutableStateFlow(Motor.EXOPLAYER)
    val motor: StateFlow<Motor> = _motor

    init {
        viewModelScope.launch {
            _motor.value = app.preferencias.motor.first()
            _tela.value = Tela.Painel(app.preferencias.urlPainel.first())
        }
    }

    /**
     * Chamado quando a URL do filme aparece no tráfego da página.
     *
     * É o momento central do app: a partir daqui o vídeo deixa de ser
     * responsabilidade do painel e passa a ser do nosso player — que é onde
     * mora a correção do travamento ao avançar.
     */
    fun aoCapturarVideo(url: String) {
        if (!CapturaDeVideo.ehVideoDoPainel(url)) return
        if ((_tela.value as? Tela.Player)?.url == url) return
        _tela.value = Tela.Player(url = url, titulo = CapturaDeVideo.identificador(url))
    }

    fun trocarMotor() {
        val novo = _motor.value.outro()
        _motor.value = novo
        viewModelScope.launch { app.preferencias.salvarMotor(novo) }
    }

    /** Volta do player para o painel, de onde o usuário saiu. */
    fun voltarAoPainel() {
        viewModelScope.launch {
            _tela.value = Tela.Painel(app.preferencias.urlPainel.first())
        }
    }
}
