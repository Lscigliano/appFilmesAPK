package io.zaffa.filmes.player

import android.content.Context
import android.view.View
import kotlinx.coroutines.flow.StateFlow

/**
 * Os dois motores de vídeo disponíveis.
 *
 * ESTE É O MOTIVO DO PROJETO EXISTIR. O app oficial (Smarters Flex) trava ao
 * avançar o filme; no celular da esposa do dono, com o MESMO login, não trava.
 * A família Smarters deixa escolher o motor de vídeo — então as duas instalações
 * quase certamente estão em motores diferentes.
 *
 * Aqui a troca é um botão dentro do player, sem reinstalar nada e sem perder a
 * posição do filme.
 */
enum class Motor(val rotulo: String, val explicacao: String) {
    EXOPLAYER(
        rotulo = "ExoPlayer",
        explicacao = "Motor do Android. Mais leve e suave, mas exige que o servidor " +
            "aceite busca por posição (HTTP Range).",
    ),
    VLC(
        rotulo = "VLC",
        explicacao = "Motor do VLC. Mais tolerante com servidores problemáticos — " +
            "use este se o filme travar ao avançar.",
    ),
    ;

    fun outro(): Motor = if (this == EXOPLAYER) VLC else EXOPLAYER
}

/** Estado observável do player, igual para os dois motores. */
data class EstadoPlayer(
    val tocando: Boolean = false,
    val carregando: Boolean = true,
    val posicaoMs: Long = 0L,
    val duracaoMs: Long = 0L,
    val erro: String? = null,
    val terminou: Boolean = false,
) {
    val duracaoConhecida: Boolean get() = duracaoMs > 0L
}

/**
 * Contrato mínimo que os dois motores cumprem.
 *
 * Mantido pequeno de propósito: tudo que a tela do player precisa e nada além,
 * para que trocar de motor no meio do filme seja só recriar o objeto e chamar
 * [abrir] com a posição salva.
 */
interface MotorVideo {

    val estado: StateFlow<EstadoPlayer>

    /** View de vídeo deste motor, para embutir na tela via `AndroidView`. */
    fun criarView(context: Context): View

    /**
     * Abre a mídia.
     *
     * @param posicaoInicialMs onde começar. É o que preserva o ponto do filme
     *        quando o usuário troca de motor no meio da reprodução.
     */
    fun abrir(url: String, posicaoInicialMs: Long = 0L)

    fun tocar()

    fun pausar()

    /** Busca por posição — exatamente a operação que trava no app oficial. */
    fun buscar(posicaoMs: Long)

    /** Chamado pela tela a cada tick para atualizar a barra de progresso. */
    fun atualizarProgresso()

    fun liberar()
}
