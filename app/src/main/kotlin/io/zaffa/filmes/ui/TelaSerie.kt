package io.zaffa.filmes.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.zaffa.filmes.xtream.Episodio
import io.zaffa.filmes.xtream.FalhaApi
import io.zaffa.filmes.xtream.Titulo

/**
 * Temporadas e episódios de uma série, lidos da API (`get_series_info`).
 * Tocar num episódio vai direto para o player.
 */
@Composable
fun TelaSerie(
    serie: Titulo,
    carregar: suspend (Titulo) -> List<Episodio>,
    aoAbrirEpisodio: (Episodio) -> Unit,
    aoVoltar: () -> Unit,
) {
    BackHandler { aoVoltar() }

    var episodios by remember(serie) { mutableStateOf<List<Episodio>?>(null) }
    var erro by remember(serie) { mutableStateOf<String?>(null) }
    var tentativa by remember(serie) { mutableIntStateOf(0) }
    var temporada by rememberSaveable(serie.id) { mutableStateOf<Int?>(null) }

    LaunchedEffect(serie, tentativa) {
        erro = null
        try {
            episodios = carregar(serie)
        } catch (e: FalhaApi) {
            erro = e.message
        }
    }

    val temporadas = remember(episodios) { episodios.orEmpty().map { it.temporada }.distinct() }
    val atual = temporada ?: temporadas.firstOrNull()
    val daTemporada = remember(episodios, atual) { episodios.orEmpty().filter { it.temporada == atual } }

    Column(
        Modifier
            .fillMaxSize()
            .background(Cores.Fundo)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Cores.Superficie)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = aoVoltar) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Cores.Texto)
            }
            Text(
                text = serie.nome,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Cores.Texto,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }

        when {
            erro != null -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(erro.orEmpty(), color = Cores.Perigo, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { tentativa += 1 }) { Text("Tentar de novo") }
                }
            }

            episodios == null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = Cores.Violeta)
            }

            episodios!!.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                Text("Esta série ainda não tem episódios.", color = Cores.TextoFraco)
            }

            else -> {
                LazyRow(
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(temporadas) { t ->
                        val selecionada = t == atual
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (selecionada) Cores.Violeta else Cores.SuperficieAlta)
                                .clickable { temporada = t }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Text(
                                "Temporada $t",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (selecionada) FontWeight.Bold else FontWeight.Normal,
                                color = if (selecionada) Cores.Texto else Cores.TextoFraco,
                            )
                        }
                    }
                }
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(daTemporada, key = { it.id }) { ep ->
                        LinhaEpisodio(ep) { aoAbrirEpisodio(ep) }
                    }
                }
            }
        }
    }
}

@Composable
private fun LinhaEpisodio(ep: Episodio, aoClicar: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Cores.Superficie)
            .clickable(onClick = aoClicar)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Cores.VioletaClaro, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "Episódio ${ep.numero}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = Cores.Texto,
            )
            Text(
                text = listOfNotNull(ep.titulo, ep.duracao).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = Cores.TextoFraco,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
