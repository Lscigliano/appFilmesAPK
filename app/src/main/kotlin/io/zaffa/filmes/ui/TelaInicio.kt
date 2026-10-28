package io.zaffa.filmes.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
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
 * É a única tela 100% nossa na navegação. O catálogo continua sendo a página do
 * painel (decisão do dono): assim as capas, categorias e busca continuam
 * funcionando e atualizadas, sem nós reimplementarmos nada que muda sem aviso.
 *
 * O que esta tela entrega é **entrar direto onde se quer**: tocar em "Filmes"
 * abre o catálogo de filmes, sem passar pelo menu do site.
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
                // Fundo em degradê, lembrando o céu estrelado da referência,
                // sem precisar de imagem (que pesaria no APK).
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF130B26),
                        Cores.Fundo,
                        Color(0xFF07040F),
                    ),
                ),
            ),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                // Sem o inset de baixo, o texto final fica escondido sob a barra
                // de navegação em aparelhos com os três botões — `enableEdgeToEdge`
                // faz o conteúdo ir até a borda da tela.
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(32.dp))

            Text(
                text = "O que vamos assistir?",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                color = Cores.Texto,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "TV ao vivo. Filmes. Séries. Tudo num lugar só.",
                style = MaterialTheme.typography.bodyLarge,
                color = Cores.TextoFraco,
                modifier = Modifier.padding(horizontal = 20.dp),
            )

            Spacer(Modifier.height(24.dp))

            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(Secao.entries) { secao ->
                    CartaoSecao(secao) { aoEscolher(secao) }
                }
            }

            Spacer(Modifier.height(36.dp))

            Text(
                text = "Acesso rápido",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Cores.Texto,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(12.dp))

            LinhaDeAcesso(
                titulo = "Abrir o painel completo",
                descricao = "Busca, categorias e tudo o mais",
                aoClicar = aoAbrirPainel,
            )

            Spacer(Modifier.height(10.dp))

            LinhaDeAcesso(
                titulo = "Sair da conta",
                descricao = "Encerra a sessão neste aparelho",
                aoClicar = aoSair,
            )

            Spacer(Modifier.height(40.dp))

            Text(
                text = "Se um filme travar ao avançar, troque o motor de vídeo " +
                    "no canto superior direito do player.",
                style = MaterialTheme.typography.bodySmall,
                color = Cores.TextoFraco,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CartaoSecao(secao: Secao, aoClicar: () -> Unit) {
    Card(
        onClick = aoClicar,
        modifier = Modifier
            .width(172.dp)
            .height(112.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, secao.cor.copy(alpha = 0.85f)),
        colors = CardDefaults.cardColors(
            containerColor = secao.cor.copy(alpha = 0.10f),
        ),
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = iconeDe(secao),
                contentDescription = null,
                tint = secao.cor,
                modifier = Modifier.size(34.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
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
                    maxLines = 2,
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
}

@Composable
private fun LinhaDeAcesso(titulo: String, descricao: String, aoClicar: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Cores.Superficie)
            .clickable(onClick = aoClicar)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = titulo,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = Cores.Texto,
            )
            Text(
                text = descricao,
                style = MaterialTheme.typography.bodySmall,
                color = Cores.TextoFraco,
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
