# DIÁRIO DO PROJETO — appFilmesAPK

> Registro cronológico do que foi **descoberto**. Entrada mais recente no topo.
> *Append-only*: nunca reescreva nem apague uma entrada; corrija com uma entrada nova.
> Formato e regras: ver `docs/PROTOCOLO.md`, seção 3.2.
>
> ⚠️ **Nunca escreva usuário, senha ou URL completa do painel neste arquivo.**

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
