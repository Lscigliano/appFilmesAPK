package io.zaffa.filmes.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.zaffa.filmes.xtream.Acervo
import io.zaffa.filmes.xtream.Tipo
import io.zaffa.filmes.xtream.Titulo

/**
 * Busca pelo nome em **todo** o acervo: filmes, séries e canais, de todas as
 * categorias de uma vez — "como um select no banco", nas palavras do dono.
 *
 * Os chips só restringem o **tipo** (filme, série, TV), nunca o gênero: quem
 * procura "Hobbit" acha o Hobbit, esteja ele em Aventura, Fantasia ou Lançamentos.
 */
@Composable
fun TelaBusca(
    acervo: Acervo,
    texto: String,
    aoMudarTexto: (String) -> Unit,
    aoAbrir: (Titulo) -> Unit,
    aoVoltar: () -> Unit,
) {
    BackHandler { aoVoltar() }

    var filtro by rememberSaveable { mutableStateOf<Tipo?>(null) }

    val foco = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { foco.requestFocus() } }

    val resultados = remember(acervo.prateleiras, texto, filtro) {
        acervo.buscar(texto, filtro?.let { listOf(it) } ?: Tipo.entries)
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
                .padding(start = 4.dp, end = 12.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = aoVoltar) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Cores.Texto)
            }
            OutlinedTextField(
                value = texto,
                onValueChange = aoMudarTexto,
                placeholder = { Text("Nome do filme, série ou canal") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (texto.isNotEmpty()) {
                        IconButton(onClick = { aoMudarTexto("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Limpar")
                        }
                    }
                },
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
                    .weight(1f)
                    .focusRequester(foco),
            )
        }

        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChipTipo("Tudo", filtro == null) { filtro = null }
            ChipTipo("Filmes", filtro == Tipo.FILME) { filtro = Tipo.FILME }
            ChipTipo("Séries", filtro == Tipo.SERIE) { filtro = Tipo.SERIE }
            ChipTipo("TV", filtro == Tipo.CANAL) { filtro = Tipo.CANAL }
        }

        val estado = acervo.estado
        when {
            estado is Acervo.Estado.Falhou && acervo.prateleiras.isEmpty() ->
                Aviso("Não consegui carregar o catálogo: ${estado.mensagem}", Cores.Perigo)

            acervo.prateleiras.isEmpty() -> Aviso("Carregando o catálogo… a busca já começa em instantes.")

            texto.isBlank() -> Aviso(
                "Digite o nome. A busca procura em " +
                    "${acervo.de(Tipo.FILME)?.titulos?.size ?: 0} filmes, " +
                    "${acervo.de(Tipo.SERIE)?.titulos?.size ?: 0} séries e " +
                    "${acervo.de(Tipo.CANAL)?.titulos?.size ?: 0} canais.",
            )

            resultados.isEmpty() -> Aviso("Nada encontrado para \"${texto.trim()}\".")

            else -> {
                Text(
                    text = "${resultados.size} resultados",
                    style = MaterialTheme.typography.bodySmall,
                    color = Cores.TextoFraco,
                    modifier = Modifier.padding(horizontal = 14.dp),
                )
                GradeDeTitulos(
                    titulos = resultados,
                    legenda = { t -> listOfNotNull(t.tipo.rotulo, t.ano).joinToString(" · ") },
                    aoAbrir = aoAbrir,
                )
            }
        }
    }
}

@Composable
private fun ChipTipo(texto: String, selecionado: Boolean, aoClicar: () -> Unit) {
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
        )
    }
}

@Composable
private fun Aviso(texto: String, cor: androidx.compose.ui.graphics.Color = Cores.TextoFraco) {
    Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
        Text(
            text = texto,
            color = cor,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
