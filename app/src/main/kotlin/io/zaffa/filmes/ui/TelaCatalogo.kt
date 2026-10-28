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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.zaffa.filmes.painel.Item
import io.zaffa.filmes.painel.LeitorCatalogo

/**
 * Catálogo na **nossa** tela.
 *
 * Os títulos vêm da página do painel, lida por um WebView invisível
 * ([LeitorCatalogo]) — nunca de uma lista nossa. Isso responde ao pedido do
 * dono: *"se for adicionado mais filmes, tem que entrar na nossa lista os novos
 * itens sem precisar atualizar nada"*. Como a leitura é sempre da página viva,
 * título novo no painel é título novo aqui.
 *
 * Tocar num item abre a página de detalhe do painel, onde o play acontece — e
 * daí o vídeo é capturado para o nosso player, como já funcionava.
 */
@Composable
fun TelaCatalogo(
    secao: Secao,
    aoAbrirItem: (Item) -> Unit,

    aoVoltar: () -> Unit,
) {
    var itens by remember(secao) { mutableStateOf<List<Item>?>(null) }
    var erro by remember(secao) { mutableStateOf<String?>(null) }
    var busca by remember(secao) { mutableStateOf("") }

    // Contador de recargas. Serve de chave para o leitor: mudá-lo descarta o
    // WebView anterior e cria um novo, que lê a página de novo do zero. É o que
    // faz o botão "atualizar" realmente buscar títulos novos.
    var recarga by remember(secao) { mutableIntStateOf(0) }

    // As categorias do painel (Ação, Comédia, Lançamentos…). Importam mais do
    // que parece: a página principal da seção é uma vitrine e mostra só uma
    // fatia do acervo. **O resto só se alcança entrando nas categorias.**
    var categorias by remember(secao) { mutableStateOf<List<Item>>(emptyList()) }
    var categoriaAtual by remember(secao) { mutableStateOf<Item?>(null) }

    BackHandler {
        if (categoriaAtual != null) {
            categoriaAtual = null
            itens = null
        } else {
            aoVoltar()
        }
    }

    val urlAtual = categoriaAtual?.url
        ?: secao.url(io.zaffa.filmes.core.model.Padroes.PAINEL_URL)

    // Carrega enquanto a lista ainda não chegou. O WebView tem 1dp e sai da
    // árvore assim que termina.
    if (itens == null && erro == null) {
        key(recarga, urlAtual) {
            LeitorCatalogo(
                url = urlAtual,
                aoLer = { titulos, cats ->
                    itens = titulos
                    // Só substitui se a página trouxe categorias: as páginas de
                    // categoria podem não repetir a lista, e perdê-la deixaria
                    // o usuário sem como voltar às outras.
                    if (cats.isNotEmpty()) categorias = cats
                },
                aoFalhar = { erro = it },
            )
        }
    }

    fun atualizar() {
        itens = null
        erro = null
        busca = ""
        recarga += 1
    }

    fun escolherCategoria(categoria: Item?) {
        categoriaAtual = categoria
        itens = null
        erro = null
        busca = ""
    }

    val visiveis = remember(itens, busca) {
        val lista = itens.orEmpty()
        if (busca.isBlank()) lista else lista.filter { it.titulo.contains(busca.trim(), true) }
    }

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
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Voltar",
                    tint = Cores.Texto,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = secao.titulo,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Cores.Texto,
                )
                if (itens != null) {
                    Text(
                        text = buildString {
                            append("${itens!!.size} títulos")
                            categoriaAtual?.let { append(" · ${it.titulo}") }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Cores.TextoFraco,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Relê a página do painel. Como a lista nunca é guardada, isto basta
            // para títulos novos aparecerem.
            IconButton(
                onClick = { atualizar() },
                enabled = itens != null || erro != null,
            ) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = "Atualizar lista",
                    tint = if (itens != null || erro != null) Cores.Texto else Cores.TextoFraco,
                )
            }
        }

        if (itens != null && itens!!.isNotEmpty()) {
            OutlinedTextField(
                value = busca,
                onValueChange = { busca = it },
                placeholder = { Text("Buscar em ${secao.titulo.lowercase()}") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Cores.Violeta,
                    unfocusedBorderColor = Cores.SuperficieAlta,
                    focusedTextColor = Cores.Texto,
                    unfocusedTextColor = Cores.Texto,
                    cursorColor = Cores.Violeta,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        if (categorias.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 6.dp),
            ) {
                item {
                    ChipCategoria(
                        texto = "Destaques",
                        selecionado = categoriaAtual == null,
                        aoClicar = { escolherCategoria(null) },
                    )
                }
                items(categorias, key = { it.url }) { categoria ->
                    ChipCategoria(
                        texto = categoria.titulo,
                        selecionado = categoriaAtual?.url == categoria.url,
                        aoClicar = { escolherCategoria(categoria) },
                    )
                }
            }
        }

        when {
            erro != null -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                Text(
                    text = erro.orEmpty(),
                    color = Cores.Perigo,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            itens == null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Cores.Violeta)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Carregando ${secao.titulo.lowercase()}…",
                        color = Cores.TextoFraco,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            visiveis.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                Text(
                    text = if (busca.isBlank()) {
                        "Não encontrei títulos nesta seção. " +
                            "Use \"Abrir o painel completo\" na tela inicial."
                    } else {
                        "Nada encontrado para \"$busca\"."
                    },
                    color = Cores.TextoFraco,
                    textAlign = TextAlign.Center,
                )
            }

            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 108.dp),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(visiveis, key = { it.url }) { item ->
                    Cartaz(item) { aoAbrirItem(item) }
                }
            }
        }
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

@Composable
private fun Cartaz(item: Item, aoClicar: () -> Unit) {
    Column(Modifier.clickable(onClick = aoClicar)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .background(Cores.SuperficieAlta),
            contentAlignment = Alignment.Center,
        ) {
            if (item.capaUrl.isBlank()) {
                Icon(
                    Icons.Filled.Movie,
                    contentDescription = null,
                    tint = Cores.TextoFraco,
                    modifier = Modifier.size(28.dp),
                )
            } else {
                AsyncImage(
                    model = item.capaUrl,
                    contentDescription = item.titulo,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = item.titulo,
            style = MaterialTheme.typography.bodySmall,
            color = Cores.Texto,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
