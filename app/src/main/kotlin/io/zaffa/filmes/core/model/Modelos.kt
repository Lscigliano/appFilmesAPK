package io.zaffa.filmes.core.model

/**
 * Modelos do domínio.
 *
 * REGRA DESTE PACOTE (`io.zaffa.filmes.core`): nenhum arquivo aqui pode importar
 * `android.*` nem `androidx.*`. O pacote é Kotlin puro justamente para poder ser
 * reaproveitado na versão para navegador. Há uma checagem automática disso no CI
 * (.github/workflows/build-apk.yml). Ver docs/ARQUITETURA.md.
 */

/** Credenciais do painel. Nunca serializar isto para log, arquivo ou analytics. */
data class Credenciais(
    val host: String,
    val usuario: String,
    val senha: String,
) {
    /** Representação segura para log — nunca expõe a senha. */
    override fun toString(): String = "Credenciais(host=$host, usuario=***, senha=***)"
}

/** Qual protocolo o painel falou, descoberto em tempo de execução. */
enum class TipoApi { XTREAM, M3U, DESCONHECIDA }

/** Endpoint que respondeu de fato, com o esquema e a porta que funcionaram. */
data class EndpointDescoberto(
    val baseUrl: String,
    val tipo: TipoApi,
)

data class ContaInfo(
    val status: String,
    val expiraEm: String?,
    val conexoesAtivas: Int,
    val conexoesMaximas: Int,
    val formatosPermitidos: List<String>,
) {
    /**
     * Causa clássica de travamento: ao avançar o filme o player abre uma nova
     * conexão antes de fechar a antiga. Se a conta já está no limite, o servidor
     * recusa e o vídeo congela. O app avisa o usuário quando isto acontece.
     */
    val noLimiteDeConexoes: Boolean
        get() = conexoesMaximas in 1..conexoesAtivas
}

data class Categoria(
    val id: String,
    val nome: String,
)

data class Filme(
    val id: String,
    val nome: String,
    val capaUrl: String?,
    val extensao: String,
    val nota: String?,
    val adicionadoEm: String?,
    val categoriaId: String?,
)

data class DetalheFilme(
    val filme: Filme,
    val sinopse: String?,
    val duracao: String?,
    val lancamento: String?,
    val genero: String?,
    val elenco: String?,
)

/** Resultado de operação que pode falhar com causa conhecida. */
sealed interface Resultado<out T> {
    data class Ok<T>(val valor: T) : Resultado<T>
    data class Erro(val causa: Falha) : Resultado<Nothing>
}

/**
 * Falhas que o app sabe explicar ao usuário em português claro.
 *
 * `RedeIntermediariaBloqueou` existe por causa de uma descoberta real: a rede
 * corporativa devolvia HTTP 502 com uma página do filtro Sophos, e isso parecia
 * "servidor fora do ar". Ver docs/DIARIO.md, entrada de 2026-10-28.
 */
sealed interface Falha {
    val mensagem: String

    data object CredenciaisInvalidas : Falha {
        override val mensagem = "Usuário ou senha incorretos."
    }

    data object ContaExpirada : Falha {
        override val mensagem = "Esta conta está expirada ou desativada no painel."
    }

    data object LimiteDeConexoes : Falha {
        override val mensagem =
            "O limite de telas simultâneas da sua conta foi atingido. " +
                "Feche o filme em outro aparelho e tente de novo."
    }

    data object RedeIntermediariaBloqueou : Falha {
        override val mensagem =
            "A rede que você está usando está bloqueando o servidor " +
                "(filtro de internet corporativa ou do provedor). " +
                "Tente pelos dados do celular (4G/5G)."
    }

    data object EnderecoInvalido : Falha {
        override val mensagem =
            "Endereço inválido. Digite apenas o endereço do servidor, " +
                "sem \"http\" e sem espaços. Exemplo: meupainel.com"
    }

    data object NaoEUmPainel : Falha {
        override val mensagem =
            "Esse endereço respondeu, mas não é um painel de streaming. " +
                "Confira o endereço com quem lhe vendeu o acesso."
    }

    /**
     * Carrega o resumo do que foi tentado. Sem isso a mensagem vira
     * "não encontrei" e ninguém consegue agir — nem o usuário, nem quem for
     * depurar depois.
     */
    data class NenhumEndpointRespondeu(
        val tentativas: Int,
        val resumo: String,
    ) : Falha {
        override val mensagem =
            "Não encontrei o servidor neste endereço.\n\n" +
                "Testei $tentativas portas e nenhuma respondeu:\n$resumo\n\n" +
                "Verifique se digitou o endereço certo e se esta rede não está " +
                "bloqueando o servidor (teste em outra rede)."
    }

    data class SemConexao(val detalhe: String) : Falha {
        override val mensagem = "Sem conexão com o servidor."
    }

    data class Inesperada(val detalhe: String) : Falha {
        override val mensagem = "Erro inesperado: $detalhe"
    }
}
