package io.zaffa.filmes.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.zaffa.filmes.xtream.Acervo

/**
 * Tela inicial — a identidade do app.
 *
 * LAYOUT: os atalhos ficam numa **grade que cabe inteira na tela**, sem
 * rolagem. A primeira versão usava uma faixa horizontal rolável e, no celular,
 * só dois cartões apareciam — os outros ficavam cortados fora da vista, que foi
 * exatamente a reclamação no primeiro uso real.
 *
 * Como no painel: o usuário escolhe TV, Filmes ou Séries e, dentro, as
 * categorias. A barra de busca no topo procura pelo nome em **todo** o
 * acervo, sem depender de seção ou gênero.
 */
@Composable
fun TelaInicio(
    aoEscolher: (Secao) -> Unit,
    aoBuscar: () -> Unit,
    acervo: Acervo,
    aoTentarDeNovo: () -> Unit,
    aoSair: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                // Degradê no lugar de imagem: mesmo clima da referência, sem
                // peso no APK.
                Brush.verticalGradient(
                    listOf(Color(0xFF130B26), Cores.Fundo, Color(0xFF07040F)),
                ),
            ),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(20.dp))

            Text(
                text = "O que vamos assistir?",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = Cores.Texto,
            )
            Text(
                text = "TV ao vivo, filmes e séries num lugar só.",
                style = MaterialTheme.typography.bodyMedium,
                color = Cores.TextoFraco,
            )

            Spacer(Modifier.height(14.dp))

            // Parece um campo de texto, mas só abre a busca geral: digitar
            // aqui exigiria o índice pronto antes mesmo de o usuário decidir.
            BarraDeBusca(texto = "Buscar em todos os filmes e séries", aoClicar = aoBuscar)

            Spacer(Modifier.height(14.dp))

            // Grade de 2 colunas. Com 3 itens, o último ocupa a linha inteira —
            // fica equilibrado e não sobra buraco.
            val linhas = Secao.entries.chunked(2)
            linhas.forEachIndexed { indice, linha ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    linha.forEach { secao ->
                        CartaoSecao(
                            secao = secao,
                            modifier = Modifier.weight(1f),
                            aoClicar = { aoEscolher(secao) },
                        )
                    }
                }
                if (indice != linhas.lastIndex) Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(20.dp))

            when (val estado = acervo.estado) {
                Acervo.Estado.Carregando, Acervo.Estado.Vazio -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 12.dp),
                ) {
                    CircularProgressIndicator(
                        color = Cores.Violeta,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Carregando o catálogo…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Cores.TextoFraco,
                    )
                }

                // Falha precisa ser vista e ter saída: catálogo vazio sem aviso
                // parece app quebrado (tasks/lessons.md).
                is Acervo.Estado.Falhou -> {
                    LinhaDeAcesso(
                        titulo = "Não consegui carregar o catálogo",
                        descricao = estado.mensagem + " Toque para tentar de novo.",
                        icone = Icons.Filled.Refresh,
                        aoClicar = aoTentarDeNovo,
                    )
                    Spacer(Modifier.height(8.dp))
                }

                Acervo.Estado.Pronto -> Unit
            }

            LinhaDeAcesso(
                titulo = "Sair da conta",
                descricao = "Encerra a sessão neste aparelho",
                icone = Icons.Filled.Logout,
                aoClicar = aoSair,
            )

            Spacer(Modifier.weight(1f))

            Text(
                text = "Travou ao avançar? Toque no nome do motor, no topo do player.",
                style = MaterialTheme.typography.bodySmall,
                color = Cores.TextoFraco,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
    }
}

@Composable
private fun CartaoSecao(
    secao: Secao,
    modifier: Modifier = Modifier,
    aoClicar: () -> Unit,
) {
    Card(
        onClick = aoClicar,
        modifier = modifier.height(92.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.5.dp, secao.cor.copy(alpha = 0.85f)),
        colors = CardDefaults.cardColors(containerColor = secao.cor.copy(alpha = 0.10f)),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Icon(
                imageVector = iconeDe(secao),
                contentDescription = null,
                tint = secao.cor,
                modifier = Modifier.size(26.dp),
            )
            Column {
                Text(
                    text = secao.titulo,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Cores.Texto,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = secao.descricao,
                    style = MaterialTheme.typography.bodySmall,
                    color = Cores.TextoFraco,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun LinhaDeAcesso(
    titulo: String,
    descricao: String,
    icone: ImageVector? = null,
    aoClicar: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Cores.Superficie)
            .clickable(onClick = aoClicar)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icone != null) {
            Icon(icone, contentDescription = null, tint = Cores.TextoFraco)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = titulo,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = Cores.Texto,
                maxLines = 1,
            )
            Text(
                text = descricao,
                style = MaterialTheme.typography.bodySmall,
                color = Cores.TextoFraco,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Cores.TextoFraco,
        )
    }
}

private fun iconeDe(secao: Secao): ImageVector = when (secao) {
    Secao.TV -> Icons.Filled.Tv
    Secao.FILMES -> Icons.Filled.Movie
    Secao.SERIES -> Icons.Filled.VideoLibrary
}
