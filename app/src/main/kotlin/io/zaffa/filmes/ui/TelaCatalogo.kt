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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.zaffa.filmes.xtream.Acervo
import io.zaffa.filmes.xtream.Tipo
import io.zaffa.filmes.xtream.Titulo

/**
 * Catálogo de uma seção, como no painel: categorias em chips e a grade de capas.
 *
 * A barra de busca daqui **não filtra a categoria aberta** — ela leva à busca
 * global, que procura no acervo inteiro. Pedido explícito do dono: achar
 * qualquer filme pelo nome, "independente de estar em aventura ou o que for".
 */
@Composable
fun TelaCatalogo(
    secao: Secao,
    categoriaId: String?,
    acervo: Acervo,
    aoEscolherCategoria: (String?) -> Unit,
    aoAbrir: (Titulo) -> Unit,
    aoBuscar: () -> Unit,
    aoAtualizar: () -> Unit,
    aoVoltar: () -> Unit,
) {
    // Dentro de uma categoria, "voltar" volta para "Todos" antes de sair.
    BackHandler { if (categoriaId != null) aoEscolherCategoria(null) else aoVoltar() }

    val prateleira = acervo.de(secao.tipo)
    val titulos = remember(prateleira, categoriaId) { prateleira?.daCategoria(categoriaId).orEmpty() }

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
            Column(Modifier.weight(1f)) {
                Text(
                    text = secao.titulo,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Cores.Texto,
                )
                if (prateleira != null) {
                    Text(
                        text = buildString {
                            append("${titulos.size} títulos")
                            categoriaId?.let { id -> prateleira.nomeDaCategoria(id)?.let { append(" · $it") } }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Cores.TextoFraco,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            val pronto = acervo.estado !is Acervo.Estado.Carregando
            IconButton(onClick = aoAtualizar, enabled = pronto) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = "Atualizar catálogo",
                    tint = if (pronto) Cores.Texto else Cores.TextoFraco,
                )
            }
        }

        BarraDeBusca(
            texto = "Buscar qualquer título pelo nome",
            aoClicar = aoBuscar,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )

        if (prateleira != null && prateleira.categoriasComTitulos.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 6.dp),
            ) {
                item {
                    ChipCategoria("Todos", selecionado = categoriaId == null) { aoEscolherCategoria(null) }
                }
                items(prateleira.categoriasComTitulos, key = { it.id }) { cat ->
                    ChipCategoria(cat.nome, selecionado = categoriaId == cat.id) {
                        aoEscolherCategoria(cat.id)
                    }
                }
            }
        }

        val estado = acervo.estado
        when {
            prateleira == null && estado is Acervo.Estado.Falhou -> Box(
                Modifier.fillMaxSize().padding(24.dp),
                Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = estado.mensagem,
                        color = Cores.Perigo,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = aoAtualizar) { Text("Tentar de novo") }
                }
            }

            prateleira == null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Cores.Violeta)
                    Spacer(Modifier.height(16.dp))
                    Text("Carregando ${secao.titulo.lowercase()}…", color = Cores.TextoFraco)
                }
            }

            titulos.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                Text("Nada nesta categoria.", color = Cores.TextoFraco, textAlign = TextAlign.Center)
            }

            else -> GradeDeTitulos(titulos, legenda = { it.ano }, aoAbrir = aoAbrir)
        }
    }
}

/** Grade de capas, compartilhada entre catálogo e busca. */
@Composable
internal fun GradeDeTitulos(
    titulos: List<Titulo>,
    legenda: (Titulo) -> String?,
    aoAbrir: (Titulo) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 108.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(titulos, key = { "${it.tipo}-${it.id}" }) { t ->
            Cartaz(t, legenda(t)) { aoAbrir(t) }
        }
    }
}

/**
 * Parece um campo de texto, mas abre a busca global. Fica igual na tela
 * inicial e no catálogo, para ninguém confundir com um filtro da categoria.
 */
@Composable
internal fun BarraDeBusca(texto: String, aoClicar: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Cores.SuperficieAlta)
            .clickable(onClick = aoClicar)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = Cores.VioletaClaro)
        Spacer(Modifier.width(12.dp))
        Text(
            text = texto,
            style = MaterialTheme.typography.bodyLarge,
            color = Cores.TextoFraco,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Chip de categoria.
 *
 * Feito à mão em vez de `FilterChip` para a cor selecionada ser a do app, e não
 * a do tema padrão do Material — o painel usa laranja, o nosso é violeta.
 */
@Composable
private fun ChipCategoria(texto: String, selecionado: Boolean, aoClicar: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selecionado) Cores.Violeta else Cores.SuperficieAlta)
            .clickable(onClick = aoClicar)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = texto,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (selecionado) FontWeight.Bold else FontWeight.Normal,
            color = if (selecionado) Cores.Texto else Cores.TextoFraco,
            maxLines = 1,
        )
    }
}

/** Capa e nome. [legenda] é a linha de baixo: ano, ou o tipo na busca. */
@Composable
private fun Cartaz(titulo: Titulo, legenda: String?, aoClicar: () -> Unit) {
    Column(Modifier.clickable(onClick = aoClicar)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .background(Cores.SuperficieAlta),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Movie,
                contentDescription = null,
                tint = Cores.TextoFraco,
                modifier = Modifier.size(28.dp),
            )
            if (!titulo.capa.isNullOrBlank()) {
                AsyncImage(
                    model = titulo.capa,
                    contentDescription = titulo.nome,
                    // Logo de canal é larga e cortá-la some com o nome; capa de
                    // filme é retrato e preenche o quadro.
                    contentScale = if (titulo.tipo == Tipo.CANAL) ContentScale.Fit else ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(if (titulo.tipo == Tipo.CANAL) 10.dp else 0.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = titulo.nome,
            style = MaterialTheme.typography.bodySmall,
            color = Cores.Texto,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (!legenda.isNullOrBlank()) {
            Text(
                text = legenda,
                style = MaterialTheme.typography.labelSmall,
                color = Cores.TextoFraco,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
