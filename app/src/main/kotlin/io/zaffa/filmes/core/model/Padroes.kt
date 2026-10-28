package io.zaffa.filmes.core.model

/**
 * Valores que o app já vem preenchendo, para o usuário final não precisar
 * descobrir nada: abre o app, digita usuário e senha, assiste.
 *
 * Decisão do dono do projeto: o endereço do servidor vem pronto, mas **continua
 * editável** na tela de login — quem tiver outro painel é só trocar.
 *
 * NOTA DE PRIVACIDADE: aqui mora apenas o *endereço do painel*, que é público
 * (é o mesmo que o app oficial mostra na tela da conta). **Usuário e senha nunca
 * entram neste arquivo nem em nenhum outro do repositório** — eles vivem só no
 * armazenamento privado do aparelho. Ver docs/PROTOCOLO.md, seção 2.
 */
object Padroes {

    /**
     * Endereço que aparece pré-preenchido na tela de login.
     *
     * ⚠️ Este endereço **ainda não foi confirmado como o host da API**: ele é o
     * que o app oficial exibe na tela da conta, mas não respondeu em nenhuma das
     * redes testadas (ver docs/DIARIO.md). Quando o endereço real for descoberto
     * — lendo-o dentro do app oficial —, troque aqui e pronto: todo mundo que
     * instalar o APK passa a abrir já no servidor certo.
     *
     * Deixar vazio ("") faz o campo abrir em branco.
     */
    const val SERVIDOR: String = "pfsv.io"
}
