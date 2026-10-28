# DIÁRIO DO PROJETO — appFilmesAPK

> Registro cronológico do que foi **descoberto**. Entrada mais recente no topo.
> *Append-only*: nunca reescreva nem apague uma entrada; corrija com uma entrada nova.
> Formato e regras: ver `docs/PROTOCOLO.md`, seção 3.2.
>
> ⚠️ **Nunca escreva usuário, senha ou URL completa do painel neste arquivo.**

---

## [2026-10-28] Fechamento da sessão: de player avulso a app próprio
**Quem:** Claude Opus 5, com o dono testando cada entrega no aparelho

**Onde o dia terminou:** o app faz login na nossa tela, mostra tela inicial e
catálogo próprios, navega por categorias, abre o filme direto no nosso player e
toca TV ao vivo. O travamento ao avançar — motivo original do projeto — foi
resolvido e confirmado pelo dono em 5G e WiFi.

**Qualidade do vídeo (medida, não suposta):** li o cabeçalho MP4 de um título
real. `H.264 + AAC, 1080p, 2,98 Mbps, áudio em português, 2.455 MB para 1h55m`.
**Não é 4K e não existe seleção de qualidade** — o servidor entrega um único
arquivo por título, sem streaming adaptativo. Nenhum app muda isso.

**Descoberta que explicou o catálogo incompleto:** apareciam ~301 títulos de um
acervo maior, e eu culpei a rolagem. A causa era outra: a página de Filmes é uma
**vitrine com 38 carrosséis por categoria**, cada um mostrando só os primeiros
itens. Rolar nunca traria tudo — **o acervo só é alcançável entrando nas
categorias**. Padrão confirmado com três amostras do dono:
`index.php?page=moviecat&id=...`.

**Arquitetura final:**
```
nossa tela de login  →  nossa tela inicial  →  nosso catálogo (capas, busca,
                                                categorias)
                                                     ↓
                            página de detalhe INVISÍVEL (só para o painel
                            emitir o token do vídeo — pedir direto dá 502)
                                                     ↓
                                              nosso player
                                      (ExoPlayer ⇄ VLC, recuperação automática)
```

**Aprendizado da sessão inteira — o que eu faria diferente:**
1. **Medir antes de arquitetar.** Um dia de código assentou sobre "parece Xtream
   Codes". A primeira URL real desmontou tudo em dez minutos.
2. **Medir a coisa certa.** Conferi código HTTP e cabeçalho por horas sem olhar
   `size_download`. Um `206` pode entregar 6 KB de 1 MB prometido.
3. **Não reconstruir requisição de autenticação nunca vista funcionar.** O login
   nativo recusava senha correta porque eu montei a requisição a partir do HTML.
   A solução foi parar de reconstruir: o WebView invisível usa o formulário do
   próprio painel.
4. **Palpite precisa de caminho de falha no mesmo commit.** Documentar que um
   valor é incerto não protege ninguém — a tela preta sem saída provou isso.
5. **O usuário na frente da tela vence a medição remota.** Três correções dele
   derrubaram conclusões minhas: o reCAPTCHA inexistente, o limite de conexões
   que não era a causa, e "avancei para o fim e funcionou" contra cinco testes
   meus dizendo o contrário.

**Em aberto, por ordem de valor para o uso diário:**
- Retomar de onde parou (hoje o filme recomeça do zero)
- Categorias próprias: mover um título para a seção que o dono quiser
- Confirmar os caminhos de Favoritos e Continuar
- Versão para navegador (repositório appFilmesWEB, com a descoberta do CORS
  liberado já registrada)

---


## [2026-10-28] ⚠️ AVANÇAR FUNCIONA NO APARELHO — minha medição estava contaminada
**Quem:** Claude Opus 5, contrariado pelo teste real do dono

**Relato do dono, textual:** *"em um filme que deixei abrindo anteriormente,
quando voltei, estava no início. Então fui para o final, e o player foi na parte
final e consegui assistir a partir dele sem problemas."*

**Por que isso é grave:** horas antes eu havia concluído, com cinco testes, que
*"o servidor só entrega a partir do byte 0"* e que **avançar seria impossível**.
O teste real no aparelho **derruba essa conclusão**. Avançar para o final
funcionou, e a reprodução seguiu normal a partir de lá.

**Hipótese para a contaminação da minha medição — NÃO confirmada:**
limitação de taxa por IP ou por token no servidor. Eu disparei **dezenas** de
requisições com os mesmos tokens, incluindo downloads de 20 MB e três streams
paralelos. O padrão observado encaixa: a **primeira** entrega de um token novo
veio completa (2 MB), e **todas** as seguintes vieram cortadas em ~6 KB,
inclusive a primeira de um token virgem — o que seria explicado se o bloqueio
fosse do **IP**, já saturado pelos testes anteriores.

**O que fica valendo:**
1. ❌ **NÃO** escreva código partindo de "o servidor não suporta avançar". Está
   contrariado pelo uso real.
2. ⚠️ Minha série de medições sobre posições do arquivo deve ser considerada
   **suspeita**, não refutada: ela mediu algo real, mas provavelmente o efeito da
   própria sondagem, não o comportamento normal.
3. ✅ Continua válido e medido: o servidor **recusa o User-Agent do VLC** com
   HTTP 500, e o token não expira em pelo menos 34 minutos.
4. **Como medir direito da próxima vez:** poucas requisições, espaçadas, com
   token recém-capturado, **e de preferência do mesmo aparelho/rede do uso real**.
   Sondagem agressiva mede a defesa do servidor, não o serviço dele.

**Outros dois pontos do mesmo relato, para o radar:**

- **Não retoma de onde parou.** O dono voltou a um filme que já tinha aberto e
  ele começou do início. O app não salva posição por título — é falta de
  funcionalidade, não defeito. Entra como item de trabalho.
- **Tela preta ao abrir um filme novo.** O dono relatou ficar olhando tela preta
  sem saber se ia abrir ("vou aguardar para ver"). O player mostra indicador de
  carregamento, mas evidentemente não comunica o suficiente. Precisa de texto
  explícito ("Carregando…"), nome do título e botão de voltar sempre visível
  enquanto carrega.

**Aprendizado (terceira vez no mesmo dia, e a mais cara):**
**O ambiente de teste mudou o resultado.** Eu media de um PC, em outra rede, com
tokens reusados à exaustão — e tratei o resultado como propriedade do servidor.
Era propriedade da *minha forma de medir*. Quando medição e uso real divergem,
**o uso real ganha**, e a medição é que precisa ser explicada.

---


## [2026-10-28] Tela preta sem saída — um palpite meu prendeu o usuário
**Quem:** Claude Opus 5, reportado pelo dono

**Situação:** A tela inicial nativa abre o painel direto numa seção
(`index.php?page=<nome>`). Os nomes foram **deduzidos** a partir de
`page=moviedetail`, única amostra que tínhamos, e isso ficou documentado em
`Secoes.kt` como "precisa de confirmação".

**Resultado no primeiro uso real:** só o cartão de **TV** funcionou
(`page=livetv` acertou). Os outros quatro levaram a uma **tela preta sem saída** —
o dono relatou "não consigo mais acessar o celular".

**O erro, e ele é de julgamento, não de código:** eu sabia que os caminhos eram
palpite, escrevi isso no comentário, e **mesmo assim deixei a falha do palpite
levar a um beco sem saída**. Documentar um risco não é tratá-lo.

**Correção:** `onReceivedError` e `onReceivedHttpError` no quadro principal agora
desviam para a raiz do painel e **avisam na tela** que a seção não existe. O app
deixa de depender de os palpites estarem certos.

**Aprendizado:**
1. **Palpite nunca pode levar a estado sem saída.** Se um valor é incerto, o
   caminho de falha dele precisa ser tratado no mesmo commit em que ele entra —
   não "documentado para depois".
2. **Fundo escuro esconde falha.** Numa UI escura, "não carregou" e "carregou
   vazio" têm a mesma aparência. Toda tela que pode ficar vazia precisa de um
   estado visível dizendo o que houve.
3. O relato do usuário foi "não consigo acessar o celular" — do ponto de vista
   dele, o app **era** o celular naquele momento. Vale lembrar o tamanho do
   estrago que uma tela sem botão de saída causa.
4. Pendente: os quatro caminhos corretos de seção. Enquanto não chegarem, o app
   funciona pela raiz do painel, sem travar.

---


## [2026-10-28] ~~CAUSA RAIZ ENCONTRADA~~: o servidor só entrega a partir do byte 0
**Quem:** Claude Opus 5, com URLs capturadas pelo dono

> 🛑 **ESTA ENTRADA ESTÁ CONTRARIADA.** O teste real no aparelho mostrou que
> **avançar funciona** — ver a entrada "AVANÇAR FUNCIONA NO APARELHO" no topo do
> diário. A medição abaixo provavelmente capturou limitação de taxa provocada
> pela própria sondagem, não o comportamento normal do servidor.
> **Não tome decisão de arquitetura com base nela.**

**Situação:** Depois de todas as hipóteses caírem, faltava explicar por que o filme
trava ao avançar. Minhas medições anteriores diziam que o servidor suportava
`Range` — **e estavam erradas**: eu conferia o código HTTP e o cabeçalho
`Content-Range`, mas **nunca medi se os bytes chegavam**.

**Ação:** Mapa de posições medindo **bytes efetivamente recebidos**, com token
novo, pedindo 2 MB em cada posição.

**Resultado:**

| Posição | HTTP | Bytes recebidos | |
|---|---|---|---|
| 0% | 206 | **2.000.001** | ✅ completo |
| 1% | 206 | 6.717 | ⚠️ cortado |
| 10% | 206 | 5.803 | ⚠️ cortado |
| 50% | 206 | 6.504 | ⚠️ cortado |
| 90% | 206 | 5.745 | ⚠️ cortado |

Testes complementares, todos negativos:
- **Insistir:** 10 tentativas na mesma posição → **0 completas**, sempre exatamente
  6.504 bytes. Determinístico, não é falha de rede.
- **Aquecer:** ler 20 MB sequenciais e depois pedir um trecho **dentro** do que já
  foi lido → ainda 6.504 bytes.
- **Token virgem:** primeira requisição de um token novo, direto no meio do
  arquivo → 6.940 bytes. Não é "uma entrega por token".
- **Conteúdo:** os bytes são vídeo binário, não página de erro. O servidor envia
  `Content-Length: 1000001`, entrega ~6 KB e **desliga a conexão**.

**CONCLUSÃO:** o servidor **só serve requisições que começam no byte 0**. Avançar
o filme é **impossível neste servidor**, por desenho ou defeito dele. Não é bug do
player, e **nenhuma quantidade de tentativas resolve** — o que invalida a premissa
da recuperação automática para este caso específico (ela ainda ajuda em quedas de
rede comuns).

**Aprendizado — o mais caro do projeto:**
1. **Medir o código HTTP não é medir a entrega.** Um `206 Partial Content` com
   `Content-Range` correto pode entregar 6 KB de 1 MB prometido. Passei o dia
   inteiro com uma medição errada porque usei `-o /dev/null` sem olhar
   `size_download`. **Sempre meça bytes recebidos, não só status.**
2. É exatamente por isso que o player congela **sem erro**: do ponto de vista dele,
   a resposta foi um sucesso. Ele recebe o cabeçalho, lê 6 KB e fica esperando o
   resto para sempre.
3. O player do painel entra em **laço infinito** de tentativas — o dono observou
   requisições falhando continuamente **mesmo com o vídeo pausado**. Nosso app
   precisa de guarda contra isso (implementada: o orçamento de tentativas só é
   devolvido após 10 s de reprodução estável).
4. **O conserto client-side possível é outro:** cache em disco do que foi baixado
   sequencialmente, para permitir voltar atrás e avançar dentro do já baixado, e
   **dizer a verdade na tela** quando o usuário tentar ir além. Ver TASK.md.

---


## [2026-10-28] Limite de conexões NÃO é a causa do travamento — hipótese testada e derrubada
**Quem:** Claude Opus 5

**Situação:** O painel mostra, no perfil do cliente, `MÁXIMO DE CONEXÕES: 1`.
Isso sugeria uma explicação elegante para o travamento: ao avançar, o player abre
uma segunda conexão antes de fechar a primeira; com limite 1, o servidor recusaria
e o vídeo congelaria. Encaixava com tudo — inclusive com "trava no celular do dono
e não no da esposa", já que motores de vídeo diferem nesse comportamento.

**Ação:** Teste desenhado para reproduzir exatamente isso: uma conexão longa
mantida aberta (20 MB a 400 KB/s, **48 segundos**) e, durante ela, três outras
requisições `Range` em posições distantes do arquivo.

**Resultado:** **Todas as quatro responderam `HTTP 206`.** Nenhuma recusa.

| Conexão | Resultado |
|---|---|
| A — longa, 48 s, 20 MB | 206 |
| B1 — durante A | 206 |
| B2 — durante A | 206 |
| B3 — durante A | 206 |

**Conclusão:** o **servidor de vídeo não impõe limite de conexões**. O
`MÁXIMO DE CONEXÕES: 1` é contabilizado pelo **portal**, para sessões do painel,
não pelo CDN que entrega o MP4. Portanto **não explica o travamento ao avançar**.

**Aprendizado:**
1. **A causa raiz continua desconhecida.** Todas as hipóteses testadas caíram:
   servidor sem suporte a `Range`, token expirando, limite de conexões. A única
   que sobrevive é o User-Agent do VLC devolvendo 500 — mas ela não explicaria o
   travamento de quem usa ExoPlayer.
2. **O limite de 1 conexão é real e importa**, só que para outra pergunta: o dono
   perguntou se três pessoas podem assistir ao mesmo tempo. A resposta é **não** —
   mas por limite do plano no portal, não por isto travar o avanço.
3. Registrado em `tasks/lessons.md`: esta foi a **segunda** vez no mesmo dia em que
   anunciei uma causa antes de medir. A regra agora é explícita — hipótese se
   rotula como hipótese até o teste existir.
4. Consequência prática: **o conserto continua sendo o certo**. Como a causa não
   se deixa capturar, tornar o player resistente à falha (recuperação automática)
   é a resposta adequada — e já se provou no aparelho do dono.

---


## [2026-10-28] ✅ N4 ATINGIDO — o filme avança sem travar, nas duas redes
**Quem:** Claude Opus 5, testado pelo dono no aparelho real

**Situação:** O projeto existe por um motivo só: o app oficial trava ao avançar o
filme e não volta mais. Todos os níveis anteriores (N1 compila, N2 instala e abre,
N3 toca) já tinham sido atingidos, mas **N4 — avançar o filme funciona** — seguia
em aberto, e era o único que importava.

**Ação:** Dono instalou o APK `build-d889fad` e testou filme e TV.

**Resultado:** **Funcionou.** Filme e TV, no **5G** e no **WiFi corporativo**.
O travamento ao avançar não ocorreu.

**O que resolveu — e a honestidade sobre isso:**
A correção entregue foi **recuperação automática** nos dois motores: ao falhar
depois de um avanço, o player reabre o fluxo na mesma posição em vez de congelar
(até 5 tentativas, contador zerando a cada retomada). Junto veio o User-Agent
mascarado, obrigatório porque o servidor devolve HTTP 500 ao UA do VLC.

**Não dá para afirmar qual das duas correções resolveu.** As duas foram ao APK
juntas, e nunca consegui reproduzir o travamento a partir desta máquina — de cá o
servidor sempre respondeu 206 em qualquer posição. Quem quiser isolar a causa
precisa gerar uma versão com só uma das correções e testar no aparelho do dono.
Registro isso explicitamente para ninguém escrever depois que "a causa era X".

**Aprendizado:**
1. **Consertar o efeito é uma resposta legítima quando a causa não se reproduz.**
   Passei o dia tentando achar a causa raiz por medição remota e todas as
   hipóteses caíram. O que entregou valor foi tornar o player resistente à falha,
   qualquer que fosse ela.
2. **O aparelho do usuário é o único ambiente que conta.** Nenhuma das medições
   daqui — três redes, onze portas, 34 minutos de monitoramento — reproduziu o
   problema que o dono via em segundos.
3. Fechado este capítulo, o próximo pedido do dono é **interface própria** (app e
   web), porque hoje ele vê a página web do painel dentro do app. Isso reabre a
   necessidade de mapear a API do painel — ver `tasks/TASK.md`, Fase 6.

---

## [2026-10-28] Token não expira: hipótese encerrada após 34 minutos de medição
**Quem:** Claude Opus 5

**Situação:** Entre as causas possíveis do travamento ao avançar, uma era o token
da URL do vídeo expirar durante a reprodução — o que faria a nova conexão aberta
pelo avanço ser recusada.

**Ação:** Monitor automático repetindo a mesma requisição `Range` com o **mesmo
token**, a cada 2 minutos, por 34 minutos.

**Resultado:** `HTTP 206` em **todas** as 18 medições, da primeira (+0min) à última
(+34min). Tempo de resposta estável.

**Aprendizado:**
1. **Expiração de token está descartada** como causa do travamento. Não gaste
   tempo nessa direção de novo — está medido.
2. Como o filme tem ~2h25 e o token sobrevive a pelo menos 34 minutos sem sinal de
   degradação, nem o cenário "expira no meio do filme" se sustenta. Se alguém
   quiser fechar de vez, basta estender o monitor; o script está no histórico.
3. Das hipóteses iniciais, **só uma sobreviveu a toda a medição**: o servidor
   devolve HTTP 500 ao User-Agent do VLC. Todas as outras — servidor sem suporte a
   `Range`, limite de conexões, token expirando — foram descartadas por teste.
4. Como nenhuma causa remota explica o sintoma, a correção foi feita sobre o
   **efeito**: recuperação automática nos dois motores. É um conserto honesto
   (reabre e continua) e não depende de descobrir a causa raiz, que pode ser
   específica do aparelho ou da rede do dono.

---


## [2026-10-28] A arquitetura inteira estava errada — e o dono derrubou minha conclusão
**Quem:** Claude Opus 5, com o dono coletando evidência no navegador

**Situação:** O app estava escrito como cliente **Xtream Codes**, com login, catálogo
e detecção de endpoint próprios. Nada disso tinha sido validado contra o servidor.

**Ação:** O dono abriu um filme no navegador, logado, e capturou no DevTools a URL
que o player usa.

**Resultado — a URL respondeu quase tudo:**
```
http://<ip-do-servidor-de-video>/vauth/<numero>.mp4?token=<token-longo>
```
Medições feitas sobre ela:

| Teste | Resultado | Conclusão |
|---|---|---|
| `Range` no início / meio / 3-4 / fim | **206 em ~0,27s** em todos | o servidor **suporta avançar perfeitamente** |
| 2 conexões simultâneas | ambas 206 | não é limite de telas |
| Token adulterado | 502 | o token é validado |
| Token após 9 min | 206 | não expira rápido |
| **User-Agent `VLC/3.0.20 LibVLC/3.0.20`** | **500** | **o servidor recusa o VLC** |
| `Access-Control-Allow-Origin` | `*` | o projeto web é viável |
| Pedir o MP4 **sem** token | **502** | só o painel emite token |

**O que isso derrubou:**
1. **Não é Xtream Codes.** Prova dupla: `player_api.php` dá 404, e o ID do filme é
   texto (`oev_z4A`), não `stream_id` numérico. **Todo o cliente Xtream foi removido**
   — estava bem escrito, para o protocolo errado.
2. **O endereço que o app oficial exibe está morto** e nunca foi o host da API.
3. **Nenhuma das minhas hipóteses sobre o travamento se sustentou**, exceto uma: o
   servidor devolve **HTTP 500** para o User-Agent do VLC. Como o app oficial deixa
   escolher o motor, isso explica o sintoma relatado desde o início — trava no
   celular do dono, funciona no da esposa, mesma conta.
4. O painel **mistura fontes**: trailer do YouTube embutido em alguns títulos, MP4
   próprio em outros. Um player que só espera MP4 fica girando para sempre nos
   trailers — sintoma que o dono viu e eu quase diagnostiquei como bug nosso.

**Correção que o dono me fez, e que eu deveria ter evitado:** afirmei que o login do
painel "tem reCAPTCHA", baseado **apenas no nome de um campo escondido**
(`recaptcha_response`). Ele respondeu que nunca viu desafio nenhum. Conferi o HTML:
**não há script do Google nem sitekey** — o recurso existe no software do painel e
está **desligado**. Eu havia transformado um indício fraco em fato declarado, e usado
isso para justificar uma decisão de arquitetura.

**Nova arquitetura (híbrida):** o app carrega a página do painel num WebView — login,
catálogo e regras de reprodução são dele. O app **intercepta a URL do vídeo com token**
e desvia para o nosso player, com ExoPlayer e libVLC trocáveis. Motivos, agora
baseados em medição e não em suposição:
- só o painel emite o token (pedido direto = 502);
- só o painel sabe quando é trailer do YouTube e quando é MP4;
- o catálogo nunca desatualiza, e mudanças de layout não quebram o app.

**Aprendizado:**
1. **Medir antes de arquitetar.** Um dia inteiro de código assentou sobre "parece
   Xtream". A primeira URL real desmontou tudo em dez minutos. O diário já marcava a
   suposição como não confirmada — o erro não foi supor, foi *construir* sobre a
   suposição em vez de medir primeiro.
2. **Não transformar indício em fato na fala.** "O formulário tem um campo de
   reCAPTCHA" ≠ "o login tem reCAPTCHA". A diferença mudou uma decisão de projeto.
3. **O usuário que está vendo a tela sabe coisas que nenhuma medição remota dá.**
   Duas correções dele — "não vejo reCAPTCHA" e "esse é o trailer, não o filme" —
   valeram mais que horas de sondagem de rede.
4. O User-Agent deixou de ser detalhe e virou **requisito documentado** em
   `core/model/Padroes.kt`, com a tabela da medição junto ao código.

---


## [2026-10-28] Primeiro teste em aparelho real — N2 atingido, e o usuário achou dois defeitos
**Quem:** Claude Opus 5, com o dono testando no celular

**Situação:** APK publicado no Releases. Primeira instalação real, fora da máquina
de desenvolvimento.

**Resultado:** **Instalou, abriu, a tela de login funcionou e a detecção rodou** —
nível **N2 atingido**. A mensagem de erro apareceu em português, como projetada.

**Dois defeitos que só o uso real revelou:**

1. **O usuário digitou `https pfsv.io`** (com espaço, sem `://`). O normalizador
   só removia os prefixos `http://` e `https://`, então tentou encontrar um host
   chamado literalmente "https pfsv.io". Falha minha de desenho: supus que as
   pessoas digitariam uma URL bem formada ou nada. Corrigido para tolerar esquema
   solto, espaços internos, maiúsculas, caminho e barra no fim; entrada que não
   pode ser host agora dá "Endereço inválido", não "servidor não encontrado".

2. **"Não foi possível encontrar o servidor" não dizia nada.** O usuário não tinha
   como agir, e eu não tinha como depurar à distância. Agora a falha carrega o
   resumo do que foi tentado, porta a porta ("porta 8080 — sem resposta",
   "porta 80 — respondeu, mas não é painel"). Transforma reclamação em diagnóstico.

**Decisão do dono:** o endereço do servidor passa a vir **pré-preenchido** e
editável (`core/model/Padroes.kt`). Motivo: "igual qualquer outro programa de
usuário" — abrir o app e já poder digitar só usuário e senha. Isso abriu exceção
na regra do protocolo §2, que proibia commitar o endereço do painel; a regra foi
atualizada com a justificativa. **Usuário e senha continuam proibidos.**

**Aprendizado:**
1. **Mensagem de erro sem o "o quê" é inútil em app distribuído.** Quem instala
   não tem logcat, e quem desenvolve não tem o aparelho. A mensagem É o canal de
   diagnóstico — trate-a como tal desde o começo.
2. **Normalizar entrada do usuário com tolerância, não com rigor.** O primeiro
   usuário real já digitou de um jeito que eu não previ. Rejeitar é o último
   recurso, não o primeiro.
3. Em aberto e crítico: **não se sabe em qual rede o celular estava** durante o
   teste. O dono relatou que o app oficial não funciona no 5G dele, só no WiFi.
   Sem essa informação, o resultado não conclui nada sobre o servidor.

---


## [2026-10-28] CI verde e APK publicado — quatro armadilhas no caminho
**Quem:** Claude Opus 5

**Situação:** O build passava na máquina local, mas o CI falhou quatro vezes
seguidas, cada vez num passo diferente.

**As quatro falhas, em ordem:**
1. A checagem "o plugin kotlin.android não voltou" acusou **o próprio comentário**
   em `build.gradle.kts` que avisa para não usar o plugin. Corrigido para procurar
   o plugin sendo *aplicado* (`id(...)`, `alias(...)`), ignorando comentários.
2. `android-actions/setup-android@v3` quebra sozinha: tenta instalar o pacote
   `tools`, que o Google removeu ("Failed to find package 'tools'"). Removida — o
   runner já traz o SDK.
3. O `sdkmanager` do runner **também** não enxerga a `platforms;android-37` —
   mesma limitação da máquina local (entende SDK XML até v3, o pacote está na v4).
   Resolvido do mesmo jeito: baixar `platform-37.0_r02.zip` de `dl.google.com` e
   escrever o `source.properties` à mão.
4. `./gradlew: Permission denied` — o Windows não grava o bit de execução, então o
   runner Linux recebia o arquivo sem permissão. `git update-index --chmod=+x`.

**Resultado:** build de 5m01s, verde, com release publicado automaticamente:
`arm64-v8a` 77 MB e `armeabi-v7a` 63 MB.

**Aprendizado:**
1. **"Passa na minha máquina" não diz nada sobre o CI.** Os quatro problemas eram
   de ambiente, não de código — e nenhum apareceria localmente.
2. A limitação do `sdkmanager` com a API 37 **não era da máquina do dono**; é da
   ferramenta. Vale para qualquer lugar onde este projeto for compilado, e por
   isso a solução virou passo do workflow, não gambiarra local.
3. Toda checagem automática precisa ser rodada antes de commitar. Duas das quatro
   falhas eram checagens minhas acusando conteúdo legítimo do próprio repositório.

---


## [2026-10-28] N1 alcançado — e o APK nasceu com 221 MB
**Quem:** Claude Opus 5

**Situação:** Depois de instalar a plataforma 37 à mão, o build finalmente passou:
`BUILD SUCCESSFUL in 2m 36s`. Primeiro nível de prova (N1) atingido.

**Resultado inesperado:** o APK saiu com **221 MB**. Causa: `libvlc-all` embute as
bibliotecas nativas das quatro arquiteturas (arm64-v8a, armeabi-v7a, x86, x86_64),
~55 MB cada. Tecnicamente "funciona", mas ninguém baixa 221 MB no celular — ou
seja, falharia no objetivo real do projeto, que é qualquer pessoa instalar e
assistir.

**Ação:** `splits { abi { ... } }` em `app/build.gradle.kts`, gerando um APK por
arquitetura e descartando x86/x86_64 (que só existem em emulador).

**Resultado:** `arm64-v8a` com **71 MB** e `armeabi-v7a` com **64 MB**.

**Aprendizado:**
1. Qualquer projeto que use libVLC **precisa** de ABI splits desde o começo; sem
   isso o APK fica inutilizável na prática.
2. `arm64-v8a` cobre praticamente todo celular moderno; `armeabi-v7a` fica como
   alternativa para aparelho antigo e TV Box barata. Não vale gerar x86.
3. Lembrete do protocolo §5: "compila" não é "funciona". Um APK de 221 MB compila
   e mesmo assim falha o objetivo. O nível N1 não deve ser confundido com entrega.

---

## [2026-10-28] O próprio verificador de segredos quase vazou a credencial
**Quem:** Claude Opus 5

**Situação:** O CI ganhou um passo "Procurar credenciais vazadas" para impedir que
usuário/senha/host fossem commitados.

**Ação:** Esse passo foi escrito com os valores **literais** no padrão de busca
(`grep -E '(USUARIO|host1|host2)'`). Antes do primeiro commit, rodou-se a própria
checagem sobre o conteúdo em *stage*.

**Resultado:** A checagem acusou — e o que ela acusou foi **ela mesma**. O arquivo
do workflow, que iria para um repositório público, continha o usuário real. Os
hosts reais também estavam espalhados pela documentação.

**Aprendizado:**
1. **Um verificador de segredos nunca pode conter o segredo.** Ele procura por
   *padrão* (URL com `username=`&`password=`, caminho `/movie/x/y/123.mp4`,
   atribuição literal a campo de senha), nunca por valor. Agora está em
   `ferramentas/checar-segredos.sh`, usado no CI e antes de cada commit.
2. Hosts reais saíram da documentação e viraram `painel.exemplo` /
   `portal.exemplo`. A lição técnica de cada entrada continua legível sem eles.
3. Rodar a checagem **antes** do primeiro commit salvou o repositório: depois de
   publicado, o valor fica no histórico do git mesmo que seja apagado.

---

## [2026-10-28] Compose obriga compileSdk 37 — e o SDK da máquina não alcançava
**Quem:** Claude Opus 5

**Situação:** Para evitar instalar SDK novo, tentou-se fixar a Compose BOM
2026.06.01 (Compose 1.11.4), na expectativa de que só a 1.12+ exigisse SDK 37.

**Ação:** Build com `compileSdk = 36`.

**Resultado:** Falhou, com mensagem explícita: *":app is currently compiled against
android-36 / Recommended action: update to at least 37"*. Ou seja, **a suposição
estava errada** — nesta linha do Compose não há saída que rode em 36.
Pior: o `sdkmanager` instalado não enxerga a plataforma 37 ("entende SDK XML até
versão 3, encontrou versão 4").

**Ação corretiva:** Baixar `platform-37.0_r02.zip` direto do repositório oficial
(`dl.google.com/android/repository/`), extrair em `platforms/android-37` e criar
à mão o `source.properties` (o zip vem sem ele), com `AndroidVersion.ApiLevel=37`.
O build.prop confirma: `ro.system.build.version.sdk=37`, Android 17.

**Aprendizado:** quando o `sdkmanager` for velho demais para um pacote, dá para
instalar a plataforma manualmente — mas **o `source.properties` precisa ser criado
à mão**, senão o AGP não reconhece a pasta e a mensagem de erro não ajuda em nada.

---

## [2026-10-28] O endereço que o app oficial mostra não responde em rede nenhuma
**Quem:** Claude Opus 5

**Situação:** A tela de conta do app oficial mostra um domínio como endereço do
painel. Era o candidato natural a host da API.

**Ação:** Testado esse domínio em **três redes diferentes** — corporativa cabeada,
5G da operadora e WiFi — em 11 portas (80, 443, 8080, 8000, 2052, 2082, 2086,
2095, 8880, 8443, 25461), em IPv4 e IPv6, e também pelo IP direto com cabeçalho
`Host:` forçado.

**Resultado:** DNS resolve normalmente (IPv4 + IPv6, hospedagem DataPacket), mas
**nenhuma porta responde e o ping expira** em todas as redes. Na rede corporativa
a resposta era a página de bloqueio do Sophos; nas outras, simplesmente silêncio.
Já o portal web do painel responde normal (HTTP 302 / 200) — e o dono confirma que
o app oficial **funciona** nessa mesma rede WiFi.

**Aprendizado:**
1. **O domínio mostrado na tela da conta é um rótulo, não o host da API.** O app
   oficial busca os filmes em outro endereço. Confirma a decisão de arquitetura de
   não ter host fixo no código.
2. Tentou-se descobrir o host entrando no portal web por código: **impossível, o
   login tem reCAPTCHA.** Não insista por esse caminho.
3. **O caminho que falta é ler o endereço do servidor dentro do app oficial**, no
   celular (Configurações → informações da conta, ou a tela de login). A porta é
   parte essencial da informação.
4. Teste de rede só vale com a origem anotada: o mesmo domínio dá 502 (bloqueio),
   000 (silêncio) ou funciona, dependendo de onde se testa. Sempre registre de
   qual rede o teste saiu.

---


## [2026-10-28] A rede corporativa bloqueia o painel — descoberta da API fica para casa
**Quem:** Claude Opus 5 (sessão inicial, máquina corporativa Zaffari)

**Situação:** O dono não sabia qual API o painel usa. A tela do app mostra um usuário
numérico de 8 dígitos e o host `painel.exemplo`, o que sugere fortemente **Xtream Codes**
(padrão da família IPTV Smarters). Era preciso confirmar na prática.

**Ação:** Tentativa de sondar o endpoint `player_api.php` direto da máquina de trabalho,
com as credenciais fornecidas em memória (não gravadas em disco no repositório).
Testadas as variantes: `http`, `https`, e as portas 80, 8080, 2082 e 25461. Também `get.php`.

**Resultado:** Nenhuma variante respondeu. A porta 80 devolveu **HTTP 502 com 74 KB de
HTML**, e o corpo da resposta contém a assinatura **`Sophos`** e a palavra `blocked` —
ou seja, é a **página de bloqueio do filtro web corporativo**, não um erro do servidor
de streaming. As demais portas deram timeout (código 000), comportamento típico de
firewall descartando o pacote. DNS resolve normalmente (`89.222.120.167` / IPv6
`2a02:6ea0:cc58::6`), então o bloqueio é por categoria de conteúdo, na camada de proxy.

**Aprendizado:**
1. **Não é possível validar a API a partir da rede corporativa.** Qualquer tentativa
   futura daqui vai dar 502/timeout e isso **não** significa que a conta ou o servidor
   estão com problema. Não perca tempo depurando o servidor a partir daqui.
2. A confirmação da API precisa ser feita **na rede de casa**. Para isso existe o script
   `ferramentas/detectar-api.mjs` — roda com Node puro, sem dependências, e imprime um
   relatório já higienizado para colar neste diário.
3. Como consequência de projeto, a detecção de API **não pode ser uma suposição fixa no
   código**: o app precisa detectar em tempo de execução (Xtream → fallback M3U) e
   mostrar ao usuário o que encontrou. Isso virou requisito, não enfeite.
4. Um 502 com corpo HTML grande, em qualquer requisição deste projeto, deve ser tratado
   como "rede intermediária interferindo", e a mensagem de erro do app deve dizer isso
   em vez de "servidor fora do ar" — senão o usuário final vai culpar a assinatura.
