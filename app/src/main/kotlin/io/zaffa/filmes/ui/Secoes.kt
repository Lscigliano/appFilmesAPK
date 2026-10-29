package io.zaffa.filmes.ui

import androidx.compose.ui.graphics.Color
import io.zaffa.filmes.xtream.Tipo

/**
 * As seções da tela inicial — as mesmas três do painel: canais, filmes e
 * séries. Cada uma abre o nosso catálogo, com as categorias do servidor.
 */
enum class Secao(
    val titulo: String,
    val descricao: String,
    val tipo: Tipo,
    val cor: Color,
) {
    TV(
        titulo = "TV ao vivo",
        descricao = "Canais e rádios",
        tipo = Tipo.CANAL,
        cor = Color(0xFF38BDF8),
    ),
    FILMES(
        titulo = "Filmes",
        descricao = "Catálogo completo",
        tipo = Tipo.FILME,
        cor = Color(0xFF8B5CF6),
    ),
    SERIES(
        titulo = "Séries",
        descricao = "Temporadas e episódios",
        tipo = Tipo.SERIE,
        cor = Color(0xFFEC4899),
    ),
}
