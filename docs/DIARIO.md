# DIÁRIO DO PROJETO — appFilmesAPK

> Registro cronológico do que foi **descoberto**. Entrada mais recente no topo.
> *Append-only*: nunca reescreva nem apague uma entrada; corrija com uma entrada nova.
> Formato e regras: ver `docs/PROTOCOLO.md`, seção 3.2.
>
> ⚠️ **Nunca escreva usuário, senha ou URL completa do painel neste arquivo.**

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
