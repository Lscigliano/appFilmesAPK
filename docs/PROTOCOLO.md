# PROTOCOLO DE TRABALHO — appFilmesAPK

> Leia este arquivo **inteiro** antes de escrever qualquer linha de código neste repositório.
> Ele existe para que qualquer pessoa — ou qualquer IA — consiga retomar o projeto
> do zero, sem precisar perguntar nada ao dono do repositório.

---

## 1. O que este projeto é

Um player Android para a conta de streaming que o dono do repositório **já assina**
(painel `painel.exemplo`, conta Premium, usuário numérico). O app oficial que ele usa hoje
— *Smarters Flex*, da família IPTV Smarters — **trava ao avançar o filme e não volta mais**.
No celular da esposa dele, com o **mesmo login e senha**, outro app da mesma família
**não trava**.

Esse detalhe é a chave de todo o projeto: **o problema não é a conta nem o servidor,
é o motor de vídeo.** O IPTV Smarters permite escolher entre ExoPlayer e VLC; as duas
instalações provavelmente estão em motores diferentes.

**Objetivo:** um APK próprio, com layout parecido com o do app atual, onde avançar o
filme funcione — e onde trocar de motor de vídeo seja um clique, não uma reinstalação.

## 2. Limite ético e legal deste repositório

Este projeto é um **cliente alternativo para uma conta legitimamente assinada**.
Isso é legítimo e está dentro do escopo.

O que **não** se faz aqui, em nenhuma hipótese:

- ❌ Commitar usuário, senha, URL completa do painel, token ou qualquer credencial.
- ❌ Embutir credenciais no código, nos testes, nos *fixtures* ou nos prints de tela.
- ❌ Burlar DRM, gerar contas, compartilhar acesso ou redistribuir conteúdo.
- ❌ Publicar no repositório capturas de tela que mostrem o usuário/senha reais.

**O repositório é PÚBLICO** (`github.com/Lscigliano/appFilmesAPK`). Qualquer credencial
commitada vaza na hora e continua no histórico do git mesmo depois de apagada.
Credenciais vivem **apenas** em `local.properties` (ignorado) e no `DataStore`
criptografado do aparelho.

> Se você for uma IA e o usuário colar a senha no chat: **não escreva ela em arquivo
> nenhum.** Use-a só para testar em memória, e registre no diário apenas o *resultado*
> do teste, nunca o valor.

## 3. A metodologia: Protocolo + Diário

Trabalho neste repositório segue dois artefatos vivos e **obrigatórios**:

### 3.1 O Protocolo (este arquivo)
As regras. Muda pouco. Só é alterado quando uma regra se mostra errada — e a alteração
é justificada no Diário.

### 3.2 O Diário (`docs/DIARIO.md`)
O registro cronológico do que foi **descoberto**, não do que foi digitado.
É *append-only*: nunca se reescreve nem se apaga uma entrada antiga; se algo estava
errado, escreve-se uma entrada nova corrigindo a anterior e marcando-a.

Cada entrada tem este formato, e entra **no topo** do arquivo (mais recente primeiro):

```markdown
## [AAAA-MM-DD] Título curto do que mudou
**Quem:** nome ou modelo da IA
**Situação:** o que se sabia antes
**Ação:** o que foi feito
**Resultado:** o que aconteceu de fato — incluindo falhas
**Aprendizado:** o que a próxima pessoa precisa saber para não repetir o erro
```

**Regra de ouro do diário:** registre o que te **surpreendeu**. Código o git já guarda;
o diário guarda o que o código não conta — por que uma abordagem foi abandonada, qual
endpoint respondeu diferente do documentado, qual configuração do player resolveu o travamento.

### 3.3 Fluxo de cada sessão de trabalho

1. **Ler** `docs/PROTOCOLO.md` (este), depois `docs/DIARIO.md` (as 3 entradas do topo)
   e `tasks/TASK.md`.
2. **Planejar** — atualizar `tasks/TASK.md` com itens *verificáveis* antes de codar.
3. **Executar** — um item por vez.
4. **Provar** — nada é marcado como pronto sem evidência (build passou, tela abriu,
   log mostra o stream tocando). Ver seção 5.
5. **Registrar** — entrada no `docs/DIARIO.md` + lição em `tasks/lessons.md` se houve correção.
6. **Commitar** — ver seção 6.

## 4. Decisões de arquitetura já tomadas (não refazer sem motivo)

| Decisão | Por quê |
|---|---|
| **Kotlin + Jetpack Compose** | Padrão atual do Android; UI escura/roxa do layout-alvo sai fácil. |
| **Pacote `io.zaffa.filmes.core` puro (sem Android)** | O dono quer uma versão para **navegador** no futuro. Toda a lógica de API/modelos vive nesse pacote, sem import de `android.*`, para ser portável. Era para ser um módulo Gradle separado; virou pacote porque o Kotlin embutido do AGP 9 conflita com o plugin `kotlin.jvm` de um módulo à parte. A pureza é garantida por checagem no CI. |
| **Ktor Client + kotlinx.serialization** | Funciona em JVM/Android **e** JS — sustenta o projeto web futuro. Retrofit não vai para o navegador. |
| **Dois motores de vídeo: Media3/ExoPlayer + libVLC** | É a correção direta do bug relatado. Trocar de motor é um clique na tela do player. |
| **Detecção automática de API** | O dono não sabe qual API o painel usa. O app testa `player_api.php` (Xtream Codes) e cai para lista M3U se falhar. |
| **Sem injeção de dependência pesada (Hilt)** | Projeto pequeno, e um container manual é mais legível para uma IA que chega sem contexto. |

## 5. Verificação — o que conta como "pronto"

Um item só sai do `tasks/TASK.md` com **prova anexada ao diário**. Níveis aceitos:

- **N1 — Compila:** `./gradlew :app:assembleDebug` termina em `BUILD SUCCESSFUL`.
- **N2 — Instala e abre:** APK instalado em aparelho/emulador e a tela aparece.
- **N3 — Funciona de verdade:** o caso de uso acontece (login autentica, catálogo lista,
  filme toca).
- **N4 — Corrige o bug-alvo:** **avançar o filme e continuar tocando**, comprovado nos
  dois motores, com o resultado de cada um anotado.

O projeto só se considera entregue no **N4**. "Compila" não é "funciona".

## 6. Commits

- Mensagens em português, no imperativo: `adiciona detector de API Xtream`.
- Um commit por item concluído do `TASK.md`.
- Todo commit que muda comportamento **também** atualiza o `docs/DIARIO.md`.
- Nunca `git push --force` na `main`.
- Antes de qualquer commit, confira: rode `bash ferramentas/checar-segredos.sh` — se acusar algo, **pare**.

## 7. Se você é uma IA retomando este projeto

Comece exatamente assim:

1. Leia este arquivo e as 3 entradas mais recentes de `docs/DIARIO.md`.
2. Rode `./gradlew :app:assembleDebug` e veja se ainda compila. Se não, **esse é o item 1**.
3. Abra `tasks/TASK.md` e pegue o primeiro item não marcado.
4. Não reescreva arquitetura que a seção 4 já decidiu — a menos que o diário registre
   que ela falhou.
5. Peça ao dono apenas o que ele não registrou: credenciais de teste. Nada mais.
