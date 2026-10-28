package io.zaffa.filmes.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import io.zaffa.filmes.player.Motor
import io.zaffa.filmes.player.MotorExo
import io.zaffa.filmes.player.MotorVideo
import io.zaffa.filmes.player.MotorVlc
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Tela de reprodução.
 *
 * O ponto central: **trocar de motor no meio do filme sem perder a posição.**
 * A posição corrente fica guardada fora do motor (`posicaoPreservada`); quando o
 * usuário troca, o motor antigo é liberado, o novo é criado e abre exatamente
 * de onde parou. É a diferença entre "o app travou" e "resolvi em um toque".
 */
@Composable
fun TelaPlayer(modelo: ModeloPrincipal, titulo: String, url: String) {
    val contexto = LocalContext.current
    val motorEscolhido by modelo.motor.collectAsStateWithLifecycle()

    // Sobrevive à troca de motor de propósito: é o que preserva o ponto do filme.
    val posicaoPreservada = remember { mutableLongStateOf(0L) }

    val motor: MotorVideo = remember(motorEscolhido) {
        when (motorEscolhido) {
            Motor.EXOPLAYER -> MotorExo(contexto)
            Motor.VLC -> MotorVlc(contexto)
        }
    }

    DisposableEffect(motor) {
        onDispose { motor.liberar() }
    }

    LaunchedEffect(motor, url) {
        motor.abrir(url, posicaoPreservada.longValue)
    }

    val estado by motor.estado.collectAsStateWithLifecycle()

    // Mantém a barra de progresso viva e atualiza a posição preservada.
    LaunchedEffect(motor) {
        while (true) {
            motor.atualizarProgresso()
            val pos = motor.estado.value.posicaoMs
            if (pos > 0L) posicaoPreservada.longValue = pos
            delay(500)
        }
    }

    // Tela cheia: em pé, um vídeo 16:9 vira uma faixa fina com tarjas enormes.
    // Deitado ele ocupa a tela toda. O usuário escolhe — não forçamos rotação.
    val atividade = contexto as? Activity

    // Abre DEITADO por padrão. Em pé, um vídeo 16:9 ocupa uma faixa fina com
    // tarjas pretas enormes em cima e embaixo — foi o que o dono viu no primeiro
    // teste. Deitado ele usa a tela inteira, que é como se assiste filme.
    // O botão no topo volta para o modo retrato, caso alguém prefira.
    var deitado by remember { mutableStateOf(true) }

    LaunchedEffect(deitado) {
        atividade?.requestedOrientation = if (deitado) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // Sair do player devolve a rotação ao aparelho, senão o resto do
            // app ficaria preso na horizontal.
            atividade?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    var controlesVisiveis by remember { mutableStateOf(true) }
    var arrastando by remember { mutableStateOf(false) }
    var valorArrasto by remember { mutableLongStateOf(0L) }

    // Some com os controles sozinho, como qualquer player.
    LaunchedEffect(controlesVisiveis, estado.tocando) {
        if (controlesVisiveis && estado.tocando) {
            delay(4_000)
            controlesVisiveis = false
        }
    }

    BackHandler { modelo.voltarAoPainel() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { controlesVisiveis = !controlesVisiveis },
    ) {
        AndroidView(
            factory = { ctx -> motor.criarView(ctx) },
            modifier = Modifier.fillMaxSize(),
        )

        if (estado.carregando && estado.erro == null) {
            CircularProgressIndicator(
                color = Cores.Violeta,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        estado.erro?.let { mensagem ->
            PainelDeErro(
                mensagem = mensagem,
                motorAtual = motorEscolhido,
                aoTrocarMotor = modelo::trocarMotor,
                aoVoltar = modelo::voltarAoPainel,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        if (controlesVisiveis) {
            Controles(
                titulo = titulo,
                motorAtual = motorEscolhido,
                tocando = estado.tocando,
                posicaoMs = if (arrastando) valorArrasto else estado.posicaoMs,
                duracaoMs = estado.duracaoMs,
                aoVoltar = modelo::voltarAoPainel,
                aoAlternar = { if (estado.tocando) motor.pausar() else motor.tocar() },
                aoTrocarMotor = modelo::trocarMotor,
                deitado = deitado,
                aoGirar = { deitado = !deitado },
                aoRetroceder = { motor.buscar((estado.posicaoMs - 10_000).coerceAtLeast(0L)) },
                aoAvancar = { motor.buscar(estado.posicaoMs + 30_000) },
                aoArrastar = { arrastando = true; valorArrasto = it },
                aoSoltar = {
                    arrastando = false
                    motor.buscar(valorArrasto)
                },
            )
        }
    }
}

@Composable
private fun PainelDeErro(
    mensagem: String,
    motorAtual: Motor,
    aoTrocarMotor: () -> Unit,
    aoVoltar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(Cores.Superficie.copy(alpha = 0.96f))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = mensagem,
            color = Cores.Texto,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = aoTrocarMotor) {
            Icon(Icons.Filled.SwapHoriz, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Usar motor ${motorAtual.outro().rotulo}")
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = motorAtual.outro().explicacao,
            color = Cores.TextoFraco,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = aoVoltar) { Text("Voltar ao catálogo") }
    }
}

@Composable
private fun Controles(
    titulo: String,
    motorAtual: Motor,
    tocando: Boolean,
    posicaoMs: Long,
    duracaoMs: Long,
    aoVoltar: () -> Unit,
    aoAlternar: () -> Unit,
    aoTrocarMotor: () -> Unit,
    deitado: Boolean,
    aoGirar: () -> Unit,
    aoRetroceder: () -> Unit,
    aoAvancar: () -> Unit,
    aoArrastar: (Long) -> Unit,
    aoSoltar: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {

        // Barra de cima: título + qual motor está em uso (sempre visível, porque
        // é a informação que resolve o problema do usuário).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = aoVoltar) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Cores.Texto)
            }
            Text(
                text = titulo,
                color = Cores.Texto,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = aoGirar) {
                Icon(
                    imageVector = if (deitado) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                    contentDescription = if (deitado) "Sair da tela cheia" else "Tela cheia",
                    tint = Cores.Texto,
                )
            }
            AssistChip(
                onClick = aoTrocarMotor,
                label = { Text(motorAtual.rotulo) },
                leadingIcon = { Icon(Icons.Filled.SwapHoriz, contentDescription = null) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = Cores.Violeta.copy(alpha = 0.25f),
                    labelColor = Cores.Texto,
                    leadingIconContentColor = Cores.VioletaClaro,
                ),
            )
        }

        Spacer(Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = aoRetroceder) {
                Icon(Icons.Filled.Replay10, contentDescription = "Voltar 10s", tint = Cores.Texto)
            }
            Spacer(Modifier.width(24.dp))
            IconButton(onClick = aoAlternar) {
                Icon(
                    imageVector = if (tocando) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (tocando) "Pausar" else "Tocar",
                    tint = Cores.Texto,
                )
            }
            Spacer(Modifier.width(24.dp))
            IconButton(onClick = aoAvancar) {
                Icon(Icons.Filled.Forward30, contentDescription = "Avançar 30s", tint = Cores.Texto)
            }
        }

        Spacer(Modifier.weight(1f))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            if (duracaoMs > 0L) {
                Slider(
                    value = posicaoMs.toFloat().coerceIn(0f, duracaoMs.toFloat()),
                    onValueChange = { aoArrastar(it.toLong()) },
                    onValueChangeFinished = aoSoltar,
                    valueRange = 0f..duracaoMs.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = Cores.Violeta,
                        activeTrackColor = Cores.Violeta,
                        inactiveTrackColor = Cores.TextoFraco.copy(alpha = 0.4f),
                    ),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatarTempo(posicaoMs), color = Cores.TextoFraco, style = MaterialTheme.typography.bodySmall)
                    Text(formatarTempo(duracaoMs), color = Cores.TextoFraco, style = MaterialTheme.typography.bodySmall)
                }
            } else {
                Text(
                    "Duração ainda desconhecida — aguarde o início da reprodução.",
                    color = Cores.TextoFraco,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.height(28.dp),
                )
            }
        }
    }
}

private fun formatarTempo(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSegundos = ms / 1000
    val horas = totalSegundos / 3600
    val minutos = (totalSegundos % 3600) / 60
    val segundos = totalSegundos % 60
    return if (horas > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", horas, minutos, segundos)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", minutos, segundos)
    }
}
