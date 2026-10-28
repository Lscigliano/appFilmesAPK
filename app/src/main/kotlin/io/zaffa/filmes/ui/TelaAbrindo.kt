package io.zaffa.filmes.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.zaffa.filmes.painel.AbridorDeVideo

/**
 * Tela de espera entre o catálogo e o player.
 *
 * Por baixo dela, [AbridorDeVideo] carrega a página de detalhe escondida só
 * para o painel emitir o token do vídeo. O usuário vê o nome do filme e um
 * aviso do que está acontecendo — **nunca uma tela preta muda**, que foi
 * exatamente a queixa do dono no primeiro teste ("fiquei olhando sem saber se
 * ia abrir").
 */
@Composable
fun TelaAbrindo(
    titulo: String,
    urlDetalhe: String,
    aoCapturar: (url: String, titulo: String) -> Unit,
    aoDesistir: () -> Unit,
    aoVoltar: () -> Unit,
) {
    BackHandler { aoVoltar() }

    AbridorDeVideo(
        urlDetalhe = urlDetalhe,
        aoCapturar = aoCapturar,
        aoDesistir = aoDesistir,
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(Cores.Fundo),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            CircularProgressIndicator(color = Cores.Violeta)

            Spacer(Modifier.height(24.dp))

            Text(
                text = titulo.ifBlank { "Abrindo…" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Cores.Texto,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Preparando o vídeo…",
                style = MaterialTheme.typography.bodyMedium,
                color = Cores.TextoFraco,
            )

            Spacer(Modifier.height(28.dp))

            TextButton(onClick = aoVoltar) {
                Text("Cancelar", color = Cores.VioletaClaro)
            }
        }
    }
}
