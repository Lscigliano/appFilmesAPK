package io.zaffa.filmes.painel

import org.json.JSONArray

/** Um título do catálogo, lido da página do painel. */
data class Item(
    val titulo: String,
    val capaUrl: String,
    /** Endereço da página de detalhe, de onde o vídeo é aberto. */
    val url: String,
)

/**
 * Lê o catálogo direto da página do painel.
 *
 * POR QUE RASPAR A PÁGINA EM VEZ DE USAR UMA API:
 * o painel não expõe API documentada — `player_api.php` devolve 404, e o login
 * é por formulário. Reconstruir requisições a partir do que se vê no HTML já
 * falhou uma vez neste projeto (ver docs/DIARIO.md, login nativo). Aqui a página
 * é carregada pelo WebView, **já autenticada**, e nós apenas lemos o que ela
 * mostra.
 *
 * Vantagem direta para o dono: **títulos novos aparecem sozinhos**, porque a
 * leitura é sempre da página viva. Não há lista nossa para atualizar.
 *
 * Fragilidade honesta: se o painel mudar a estrutura do HTML, a extração para
 * de achar os itens. Por isso o seletor é o mais genérico possível — procura
 * links que levam a uma página de detalhe, em vez de depender de nomes de
 * classes CSS, que mudam com qualquer redesenho.
 */
object Catalogo {

    /**
     * JavaScript que colhe os títulos da página.
     *
     * Ancorado no **padrão da URL** (`page=...detail&id=...`), que é estável
     * porque o painel precisa dele para funcionar — e não em classes CSS, que
     * mudam a cada ajuste visual.
     */
    const val JS_EXTRAIR: String = """
        (function () {
          var vistos = {};
          var itens = [];

          var links = document.querySelectorAll('a[href*="detail"]');
          for (var i = 0; i < links.length; i++) {
            var a = links[i];
            var href = a.href || '';
            if (href.indexOf('id=') === -1) continue;
            if (vistos[href]) continue;

            var img = a.querySelector('img');
            // Capas costumam ser carregadas sob demanda: o endereço real fica
            // em data-src até a imagem entrar na tela.
            var capa = '';
            if (img) {
              capa = img.getAttribute('data-src')
                  || img.getAttribute('data-original')
                  || img.src
                  || '';
            }

            var titulo = (a.getAttribute('title') || '').trim();
            if (!titulo && img) titulo = (img.getAttribute('alt') || '').trim();
            if (!titulo) titulo = (a.innerText || '').trim().split('\n')[0];
            if (!titulo) continue;

            vistos[href] = true;
            itens.push({ titulo: titulo, capa: capa, url: href });
          }
          return JSON.stringify(itens);
        })();
    """

    /** Converte o JSON devolvido pelo [JS_EXTRAIR] em objetos. */
    fun lerJson(bruto: String?): List<Item> {
        val texto = bruto?.trim().orEmpty()
        if (texto.isBlank() || texto == "null") return emptyList()

        // evaluateJavascript devolve a string JSON **escapada**, entre aspas.
        val json = if (texto.startsWith("\"")) {
            runCatching { org.json.JSONTokener(texto).nextValue() as? String }.getOrNull().orEmpty()
        } else {
            texto
        }
        if (json.isBlank()) return emptyList()

        return runCatching {
            val lista = JSONArray(json)
            (0 until lista.length()).mapNotNull { i ->
                val o = lista.optJSONObject(i) ?: return@mapNotNull null
                val titulo = o.optString("titulo").trim()
                val url = o.optString("url").trim()
                if (titulo.isBlank() || url.isBlank()) {
                    null
                } else {
                    Item(titulo = titulo, capaUrl = o.optString("capa").trim(), url = url)
                }
            }
        }.getOrDefault(emptyList())
    }

    /**
     * JavaScript que rola a página até o fim.
     *
     * Catálogos grandes carregam por rolagem infinita; sem isto só os primeiros
     * títulos existiriam no DOM e a nossa lista viria curta.
     */
    const val JS_ROLAR: String = """
        (function () {
          window.scrollTo(0, document.body.scrollHeight);
          return document.querySelectorAll('a[href*="detail"]').length;
        })();
    """
}
