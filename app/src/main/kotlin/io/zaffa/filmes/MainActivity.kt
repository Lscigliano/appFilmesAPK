package io.zaffa.filmes

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.zaffa.filmes.ui.ModeloPrincipal
import io.zaffa.filmes.ui.TelaCarregando
import io.zaffa.filmes.ui.TelaCatalogo
import io.zaffa.filmes.ui.TelaLogin
import io.zaffa.filmes.ui.TelaPlayer
import io.zaffa.filmes.ui.TemaFilmes
import io.zaffa.filmes.ui.Tela

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
                        is Tela.Carregando -> TelaCarregando()
                        is Tela.Login -> TelaLogin(modelo)
                        is Tela.Catalogo -> TelaCatalogo(modelo)
                        is Tela.Player -> TelaPlayer(
                            modelo = modelo,
                            filme = t.filme,
                            url = t.url,
                        )
                    }
                }
            }
        }
    }
}
