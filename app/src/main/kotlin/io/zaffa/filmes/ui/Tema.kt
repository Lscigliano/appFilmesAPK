package io.zaffa.filmes.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Paleta tirada da interface que o dono já usa (roxo profundo + violeta),
 * para o app não parecer "outra coisa" depois da troca.
 *
 * O tema é sempre escuro: é um app de assistir filme, quase sempre no escuro,
 * e um fundo claro piscando entre um filme e outro incomoda.
 */
object Cores {
    val Fundo = Color(0xFF0B0618)
    val Superficie = Color(0xFF160B2B)
    val SuperficieAlta = Color(0xFF221240)
    val Violeta = Color(0xFF8B5CF6)
    val VioletaClaro = Color(0xFFA78BFA)
    val Ativo = Color(0xFF22C55E)
    val Perigo = Color(0xFFEF4444)
    val Texto = Color(0xFFF5F3FF)
    val TextoFraco = Color(0xFFA9A0C4)
}

private val esquema = darkColorScheme(
    primary = Cores.Violeta,
    onPrimary = Color.White,
    secondary = Cores.VioletaClaro,
    background = Cores.Fundo,
    onBackground = Cores.Texto,
    surface = Cores.Superficie,
    onSurface = Cores.Texto,
    surfaceVariant = Cores.SuperficieAlta,
    onSurfaceVariant = Cores.TextoFraco,
    error = Cores.Perigo,
)

@Composable
fun TemaFilmes(
    @Suppress("UNUSED_PARAMETER") escuro: Boolean = isSystemInDarkTheme(),
    conteudo: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = esquema, content = conteudo)
}
