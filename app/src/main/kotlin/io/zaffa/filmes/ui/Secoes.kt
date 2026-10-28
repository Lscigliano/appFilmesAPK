package io.zaffa.filmes.ui

import androidx.compose.ui.graphics.Color

/**
 * As seções da tela inicial.
 *
 * Cada uma abre o painel direto na página correspondente, pulando a navegação
 * do site. É o que dá cara de app: o usuário escolhe "Filmes" e cai no catálogo,
 * sem passar pelo menu do painel.
 *
 * ⚠️ **Os caminhos (`pagina`) ainda precisam de confirmação.** O padrão foi
 * deduzido de `index.php?page=moviedetail`, observado na prática. Se algum cartão
 * cair numa página errada, é só corrigir a string aqui — nada mais depende disso.
 * Ver `tasks/TASK.md`, Fase 6.
 */
enum class Secao(
    val titulo: String,
    val descricao: String,
    val pagina: String,
    val cor: Color,
) {
    TV(
        titulo = "TV ao vivo",
        descricao = "Canais abertos e fechados",
        pagina = "index.php?page=livetv",
        cor = Color(0xFF38BDF8),
    ),
    FILMES(
        titulo = "Filmes",
        descricao = "Catálogo completo",
        pagina = "index.php?page=movies",
        cor = Color(0xFF8B5CF6),
    ),
    SERIES(
        titulo = "Séries",
        descricao = "Temporadas e episódios",
        pagina = "index.php?page=series",
        cor = Color(0xFFEC4899),
    ),
    FAVORITOS(
        titulo = "Favoritos",
        descricao = "Os seus salvos",
        pagina = "index.php?page=favorites",
        cor = Color(0xFF10B981),
    ),
    CONTINUAR(
        titulo = "Continuar",
        descricao = "De onde você parou",
        pagina = "index.php?page=watchhistory",
        cor = Color(0xFFA855F7),
    ),
    ;

    /** URL completa, a partir do endereço do painel configurado. */
    fun url(base: String): String =
        base.trimEnd('/') + "/" + pagina.trimStart('/')
}
