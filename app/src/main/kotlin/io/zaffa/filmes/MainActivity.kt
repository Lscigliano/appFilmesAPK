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
import io.zaffa.filmes.ui.Cores
import io.zaffa.filmes.ui.ModeloPrincipal
import io.zaffa.filmes.ui.Tela
import io.zaffa.filmes.ui.TelaBusca
import io.zaffa.filmes.ui.TelaCatalogo
import io.zaffa.filmes.ui.TelaInicio
import io.zaffa.filmes.ui.TelaLogin
import io.zaffa.filmes.ui.TelaPlayer
import io.zaffa.filmes.ui.TelaSerie
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
                            aoBuscar = modelo::abrirBusca,
                            acervo = modelo.acervo,
                            aoTentarDeNovo = { modelo.carregarAcervo(forcar = true) },
                            aoSair = modelo::sair,
                        )

                        is Tela.Catalogo -> TelaCatalogo(
                            secao = t.secao,
                            categoriaId = t.categoriaId,
                            acervo = modelo.acervo,
                            aoEscolherCategoria = { modelo.escolherCategoria(t.secao, it) },
                            aoAbrir = modelo::abrirTitulo,
                            aoBuscar = modelo::abrirBusca,
                            aoAtualizar = { modelo.carregarAcervo(forcar = true) },
                            aoVoltar = modelo::voltar,
                        )

                        is Tela.Busca -> TelaBusca(
                            acervo = modelo.acervo,
                            texto = modelo.textoBusca,
                            aoMudarTexto = { modelo.textoBusca = it },
                            aoAbrir = modelo::abrirTitulo,
                            aoVoltar = modelo::voltar,
                        )

                        is Tela.Serie -> TelaSerie(
                            serie = t.serie,
                            carregar = modelo::episodios,
                            aoAbrirEpisodio = { modelo.abrirEpisodio(t.serie, it) },
                            aoVoltar = modelo::voltar,
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
