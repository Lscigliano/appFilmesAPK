package io.zaffa.filmes.xtream

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.zaffa.filmes.data.Credenciais
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** Categorias e títulos de um tipo, já carregados. */
class Prateleira(
    val categorias: List<Categoria>,
    val titulos: List<Titulo>,
) {
    /** Só as categorias que têm algo dentro, na ordem do servidor. */
    val categoriasComTitulos: List<Categoria> = run {
        val usadas = titulos.flatMapTo(HashSet()) { it.categorias }
        categorias.filter { it.id in usadas }
    }

    fun daCategoria(id: String?): List<Titulo> =
        if (id == null) titulos else titulos.filter { id in it.categorias }

    fun nomeDaCategoria(id: String): String? = categorias.firstOrNull { it.id == id }?.nome
}

/**
 * O acervo inteiro do servidor, na memória: filmes, séries e canais.
 *
 * É o que torna a busca **global**: ela procura em todos os títulos de todos os
 * tipos, independentemente da categoria aberta — o pedido do dono foi "achar
 * qualquer filme pelo nome na lista inteira, como um select no banco".
 *
 * Carregado uma vez por abertura do app (~6 MB, poucos segundos). Sem cache em
 * disco, de propósito: título novo no servidor aparece na próxima abertura, ou
 * no botão "atualizar", sem nada para invalidar.
 */
class Acervo(private val api: ApiXtream) {

    sealed interface Estado {
        data object Vazio : Estado
        data object Carregando : Estado
        data object Pronto : Estado
        data class Falhou(val mensagem: String) : Estado
    }

    var estado by mutableStateOf<Estado>(Estado.Vazio)
        private set

    var prateleiras by mutableStateOf<Map<Tipo, Prateleira>>(emptyMap())
        private set

    fun de(tipo: Tipo): Prateleira? = prateleiras[tipo]

    suspend fun carregar(c: Credenciais, forcar: Boolean = false) {
        if (!forcar && (estado == Estado.Pronto || estado == Estado.Carregando)) return
        estado = Estado.Carregando
        estado = try {
            // Os três tipos em paralelo: são três chamadas leves de API, não
            // fluxos de vídeo — o limite de 1 conexão da conta não se aplica.
            prateleiras = coroutineScope {
                Tipo.entries
                    .map { tipo ->
                        tipo to async { Prateleira(api.categorias(c, tipo), api.titulos(c, tipo)) }
                    }
                    .associate { (tipo, tarefa) -> tipo to tarefa.await() }
            }
            Estado.Pronto
        } catch (e: FalhaApi) {
            Estado.Falhou(e.message.orEmpty())
        }
    }

    fun esquecer() {
        prateleiras = emptyMap()
        estado = Estado.Vazio
    }

    /**
     * Busca pelo nome em **todo** o acervo.
     *
     * Todas as palavras, em qualquer ordem, sem acento: "hobbit smaug" acha
     * "O Hobbit: A Desolação de Smaug". Quem começa com o texto digitado vem
     * primeiro; o resto segue a ordem do servidor (mais recentes antes).
     */
    fun buscar(texto: String, tipos: Collection<Tipo> = Tipo.entries): List<Titulo> {
        val consulta = normalizar(texto)
        val palavras = consulta.split(' ').filter { it.isNotBlank() }
        if (palavras.isEmpty()) return emptyList()
        return tipos
            .flatMap { prateleiras[it]?.titulos.orEmpty() }
            .filter { t -> palavras.all { t.chave.contains(it) } }
            .sortedBy { if (it.chave.startsWith(consulta)) 0 else 1 }
    }
}
