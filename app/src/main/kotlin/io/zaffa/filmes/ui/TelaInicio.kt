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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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

/**
 * Tela inicial — a identidade do app.
 *
 * LAYOUT: os cinco atalhos ficam numa **grade que cabe inteira na tela**, sem
 * rolagem. A primeira versão usava uma faixa horizontal rolável e, no celular,
 * só dois cartões apareciam — os outros ficavam cortados fora da vista, que foi
 * exatamente a reclamação no primeiro uso real.
 *
 * O catálogo continua sendo a página do painel (decisão do dono): assim as
 * capas, categorias e busca seguem funcionando e atualizadas. O que esta tela
 * entrega é **entrar direto onde se quer**, sem passar pelo menu do site.
 */
@Composable
fun TelaInicio(
    aoEscolher: (Secao) -> Unit,
    aoAbrirPainel: () -> Unit,
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

            Spacer(Modifier.height(18.dp))

            // Grade de 2 colunas. Com 5 itens, o último ocupa a linha inteira —
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

            LinhaDeAcesso(
                titulo = "Abrir o painel completo",
                descricao = "Busca, categorias, rádio e tudo o mais",
                aoClicar = aoAbrirPainel,
            )

            Spacer(Modifier.height(8.dp))

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
    Secao.FAVORITOS -> Icons.Filled.Favorite
    Secao.CONTINUAR -> Icons.Filled.History
}
