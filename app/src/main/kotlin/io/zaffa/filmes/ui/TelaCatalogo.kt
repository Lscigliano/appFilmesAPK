package io.zaffa.filmes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.zaffa.filmes.core.model.Filme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaCatalogo(modelo: ModeloPrincipal) {
    val estado by modelo.catalogo.collectAsStateWithLifecycle()
    val aviso = modelo.avisoDaConta()

    Scaffold(
        containerColor = Cores.Fundo,
        topBar = {
            TopAppBar(
                title = { Text("Filmes", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Cores.Superficie,
                    titleContentColor = Cores.Texto,
                ),
                actions = {
                    IconButton(onClick = modelo::sair) {
                        Icon(Icons.Filled.Logout, contentDescription = "Sair", tint = Cores.TextoFraco)
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {

            // Aviso de limite de telas: é causa real de travamento no meio do filme,
            // então aparece antes do usuário escolher o que assistir.
            if (aviso != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Cores.Perigo.copy(alpha = 0.15f)),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = aviso,
                        modifier = Modifier.padding(12.dp),
                        color = Cores.Perigo,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            OutlinedTextField(
                value = estado.busca,
                onValueChange = modelo::aoBuscar,
                placeholder = { Text("Buscar filme") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )

            if (estado.categorias.isNotEmpty()) {
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 8.dp),
                ) {
                    item {
                        FilterChip(
                            selected = estado.categoriaAtual == null,
                            onClick = { modelo.escolherCategoria(null) },
                            label = { Text("Todos") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Cores.Violeta,
                                selectedLabelColor = Cores.Texto,
                            ),
                        )
                    }
                    items(estado.categorias) { categoria ->
                        FilterChip(
                            selected = estado.categoriaAtual == categoria.id,
                            onClick = { modelo.escolherCategoria(categoria.id) },
                            label = { Text(categoria.nome, maxLines = 1) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Cores.Violeta,
                                selectedLabelColor = Cores.Texto,
                            ),
                        )
                    }
                }
            }

            when {
                estado.carregando -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = Cores.Violeta)
                }

                estado.erro != null -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    Text(
                        text = estado.erro.orEmpty(),
                        color = Cores.Perigo,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }

                estado.filmesVisiveis.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("Nenhum filme encontrado.", color = Cores.TextoFraco)
                }

                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 120.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(estado.filmesVisiveis, key = { it.id }) { filme ->
                        CartaoFilme(filme) { modelo.abrirFilme(filme) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CartaoFilme(filme: Filme, aoClicar: () -> Unit) {
    Column(modifier = Modifier.clickable(onClick = aoClicar)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .background(Cores.SuperficieAlta),
            contentAlignment = Alignment.Center,
        ) {
            if (filme.capaUrl.isNullOrBlank()) {
                Icon(Icons.Filled.Movie, contentDescription = null, tint = Cores.TextoFraco)
            } else {
                AsyncImage(
                    model = filme.capaUrl,
                    contentDescription = filme.nome,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = filme.nome,
            style = MaterialTheme.typography.bodySmall,
            color = Cores.Texto,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Suppress("unused")
@Composable
private fun LinhaInfo(rotulo: String, valor: String) {
    Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(rotulo, color = Cores.TextoFraco, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(4.dp))
        Text(valor, color = Cores.Texto, style = MaterialTheme.typography.bodySmall)
    }
}
