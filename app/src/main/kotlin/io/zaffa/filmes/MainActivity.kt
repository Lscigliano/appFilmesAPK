package io.zaffa.filmes

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.zaffa.filmes.painel.TelaPainel
import io.zaffa.filmes.ui.Cores
import io.zaffa.filmes.ui.ModeloPrincipal
import io.zaffa.filmes.ui.Tela
import io.zaffa.filmes.ui.TelaInicio
import io.zaffa.filmes.ui.TelaLogin
import io.zaffa.filmes.ui.TelaPlayer
import io.zaffa.filmes.ui.TemaFilmes

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Filme rodando não pode deixar a tela apagar.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            TemaFilmes {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val modelo: ModeloPrincipal = viewModel()
                    val tela by modelo.tela.collectAsStateWithLifecycle()

                    when (val t = tela) {
                        is Tela.Carregando ->
                            Box(Modifier.fillMaxSize(), Alignment.Center) {
                                CircularProgressIndicator(color = Cores.Violeta)
                            }

                        is Tela.Login -> TelaLogin(modelo)

                        is Tela.Inicio -> TelaInicio(
                            aoEscolher = modelo::abrirSecao,
                            aoAbrirPainel = modelo::abrirPainel,
                            aoSair = modelo::sair,
                        )

                        is Tela.Painel -> TelaPainel(
                            urlInicial = t.url,
                            urlRaiz = t.raiz,
                            aoCapturarVideo = modelo::aoCapturarVideo,
                            aoNavegar = modelo::aoNavegarNoPainel,
                            aoSair = modelo::voltarAoInicio,
                        )

                        is Tela.Player -> TelaPlayer(
                            modelo = modelo,
                            titulo = t.titulo,
                            url = t.url,
                        )
                    }
                }
            }
        }
    }
}
