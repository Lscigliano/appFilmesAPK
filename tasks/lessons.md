# Lições acumuladas

Regras que nasceram de erro real neste projeto. Acrescente ao final, com data.
Uma lição só entra aqui se evitar que o erro **se repita**.

---

## 2026-10-28 — Não confunda bloqueio de rede com servidor fora do ar

**O erro:** interpretar HTTP 502 como "o painel está fora do ar" e começar a
depurar o servidor.

**O que era de verdade:** a rede corporativa (filtro Sophos) devolvia a própria
página de bloqueio com status 502 e 74 KB de HTML.

**Regra:** antes de culpar o servidor, verifique se o corpo da resposta é HTML
com assinatura de filtro (`sophos`, `zscaler`, `fortiguard`, `blocked`…).
Já existe a função `ehPaginaDeBloqueio()` em `core/api/ClienteXtream.kt` — use-a
em qualquer novo caminho de rede, e nunca mostre "erro desconhecido" nesse caso.

---

## 2026-10-28 — O endereço que aparece no app não é o host da API

**O erro:** supor que o `painel.exemplo` mostrado na tela da conta é onde fica a API.

**O que era de verdade:** o portal web é outro (`portal.exemplo`, um painel CORE atrás
de Cloudflare), e ele devolve 404 em `player_api.php`. O host da API de streaming
é um terceiro endereço.

**Regra:** nunca fixe host nem porta no código. O endereço é dado pelo usuário e
o resto é **descoberto em tempo de execução** (`core/api/DetectorApi.kt`).

---

## 2026-10-28 — No Windows, `local.properties` precisa de barra escapada

**O erro:** gerar `local.properties` com `sdk.dir=C:\Users\...` (barras invertidas
simples). O build falhou com um `IOException` enterrado em 190 linhas de stack
trace: *"A sintaxe do nome do arquivo... está incorreta"*.

**O que era de verdade:** em arquivo `.properties`, a barra invertida é caractere
de **escape**. `\U` não é escape válido e o caminho chega corrompido no AGP.

**Regra:** escreva o caminho com barras normais e escape só os dois-pontos:
`sdk.dir=C\:/Users/.../Android/Sdk`. Vale para `keystore.properties` também.

---

## 2026-10-28 — Verifique o toolchain na documentação oficial antes de escrever build files

**O erro:** quase escrever `build.gradle.kts` no formato AGP 8, por hábito.

**O que era de verdade:** o AGP 9 liga a "new DSL" por padrão, traz Kotlin
embutido (aplicar `org.jetbrains.kotlin.android` quebra o build) e removeu
`kotlinOptions`. Além disso, o Compose estável atual exige `compileSdk 37`, que
não estava instalado na máquina.

**Regra:** antes de fixar qualquer versão, consulte as release notes oficiais e os
metadados Maven — e **anote no `libs.versions.toml` por que** aquela versão foi
escolhida, especialmente quando ela está travada por um limite externo.

---

## 2026-10-28 — Um verificador de segredos nunca pode conter o segredo

**O erro:** escrever o passo de CI "procurar credenciais vazadas" com o usuário e
os hosts **literais** dentro do padrão de busca. O arquivo do workflow iria para
um repositório público levando justamente o dado que deveria proteger.

**O que salvou:** rodar a checagem sobre o conteúdo em *stage* **antes** do
primeiro commit. Ela acusou a si mesma.

**Regra:** verificação de segredo procura **padrão**, nunca valor:
URL com `username=`&`password=`, caminho `/movie/x/y/123.mp4`, atribuição literal
a campo de senha, e arquivos proibidos versionados. Está em
`ferramentas/checar-segredos.sh` — use-o, não escreva outro.

**Regra 2:** rode a checagem **antes** do primeiro commit de qualquer repositório
novo. Depois de publicado, o valor permanece no histórico do git mesmo apagado.

---

## 2026-10-28 — Verificação automática não pode acusar a própria documentação

**O erro (duas vezes no mesmo dia):** escrever checagens de CI com `grep` por um
texto que **a própria documentação do repositório contém**.
1. O verificador de segredos trazia a credencial literal no padrão de busca.
2. A checagem "o plugin `kotlin.android` não voltou" quebrou o CI porque o
   comentário em `build.gradle.kts` que **avisa para não usar o plugin** cita o
   nome dele.

**Regra:** uma checagem procura o **fato**, não a **palavra**. Busque a forma
aplicada (`id("...")`, `alias(...)`, atribuição no `.toml`) e exclua linhas de
comentário. Depois de escrever qualquer checagem, **rode-a localmente** antes de
commitar — as duas falhas teriam sido pegas em 5 segundos.

---

## 2026-10-28 — Esperar a medição antes de anunciar a causa (2ª ocorrência no mesmo dia)

**O padrão:** encontrar um indício forte, anunciá-lo ao usuário como explicação, e
só então medir — descobrindo que estava errado.

Aconteceu duas vezes:
1. Campo `recaptcha_response` no HTML → anunciei "o login tem reCAPTCHA" e usei
   isso para justificar arquitetura. Não havia script nem sitekey: estava desligado.
2. Painel mostrando `MÁXIMO DE CONEXÕES: 1` → anunciei que era "provavelmente a
   causa raiz" do travamento ao avançar. Teste seguinte: conexão longa de 48 s
   segurada, mais três simultâneas — **todas 206**. O servidor de vídeo não impõe
   limite algum. O limite é do portal, não do CDN.

**Por que importa:** o dono toma decisões com base no que eu afirmo. Uma causa
anunciada errada faz ele parar de procurar a verdadeira — e no caso 1, quase
mudou a arquitetura do app.

**Regra:** indício forte não vira afirmação. Diga "tenho uma hipótese, vou medir",
meça, e **só então** afirme. Se a medição for demorada, apresente como hipótese
explicitamente rotulada, com o teste que a confirmaria.

**Regra 2:** uma hipótese que explica *tudo* elegantemente é justamente a que mais
precisa de teste. As duas pareciam encaixar perfeitamente. Nenhuma sobreviveu.

---

## 2026-10-28 — Palpite documentado ainda é palpite: trate o caminho de falha

**O erro:** deduzir os endereços das seções do painel, escrever no código que
precisavam de confirmação, e mesmo assim deixar o app ir para uma **tela preta
sem saída** quando a dedução falhasse. Quatro dos cinco palpites estavam errados.

**Impacto real:** o dono relatou "a tela ficou preta, não consigo mais acessar o
celular". Para ele, naquele momento, o app era o celular.

**Regra:** valor incerto exige tratamento do caminho de falha **no mesmo commit**.
Comentário avisando não é tratamento. Pergunte: "se este palpite estiver errado,
o usuário consegue sair?" Se a resposta for não, não entregue.

**Regra 2:** em UI escura, "falhou" e "vazio" são visualmente idênticos. Toda tela
que pode ficar sem conteúdo precisa de estado explícito na tela.
