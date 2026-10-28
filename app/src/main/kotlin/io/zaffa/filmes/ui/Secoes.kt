package io.zaffa.filmes.ui

import androidx.compose.ui.graphics.Color

/**
 * As seções da tela inicial.
 *
 * Cada uma abre o painel direto na página correspondente, pulando a navegação
 * do site. É o que dá cara de app: o usuário escolhe "Filmes" e cai no catálogo,
 * sem passar pelo menu do painel.
 *
 * Os caminhos foram deduzidos de `index.php?page=moviedetail` e depois
 * **confirmados pelo dono no navegador**, um a um. Os dois últimos seguem sem
 * confirmação e estão marcados abaixo.
 *
 * Se algum cair numa página inexistente, `TelaPainel` desvia para a raiz do
 * painel e avisa na tela — nenhum palpite daqui pode prender o usuário numa tela
 * sem saída. Essa proteção existe porque já falhou uma vez: ver docs/DIARIO.md.
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
        // ✅ confirmado: funcionou no primeiro teste real
        pagina = "index.php?page=livetv",
        cor = Color(0xFF38BDF8),
    ),
    FILMES(
        titulo = "Filmes",
        descricao = "Catálogo completo",
        // ✅ confirmado pelo dono no navegador (2026-10-28)
        pagina = "index.php?page=movies",
        cor = Color(0xFF8B5CF6),
    ),
    SERIES(
        titulo = "Séries",
        descricao = "Temporadas e episódios",
        // ✅ confirmado pelo dono no navegador (2026-10-28)
        pagina = "index.php?page=series",
        cor = Color(0xFFEC4899),
    ),
    FAVORITOS(
        titulo = "Favoritos",
        descricao = "Os seus salvos",
        // ⚠️ NÃO confirmado — se falhar, cai na raiz do painel
        pagina = "index.php?page=favorites",
        cor = Color(0xFF10B981),
    ),
    CONTINUAR(
        titulo = "Continuar",
        descricao = "De onde você parou",
        // ⚠️ NÃO confirmado — se falhar, cai na raiz do painel
        pagina = "index.php?page=watchhistory",
        cor = Color(0xFFA855F7),
    ),
    ;

    /** URL completa, a partir do endereço do painel configurado. */
    fun url(base: String): String =
        base.trimEnd('/') + "/" + pagina.trimStart('/')
}
