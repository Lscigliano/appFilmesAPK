# CLAUDE.md — regras deste repositório

Você está num projeto que segue **metodologia de protocolo e diário**.
Antes de qualquer coisa, leia nesta ordem:

1. `docs/COMECE-AQUI.md` — como pôr o projeto para compilar.
2. `docs/PROTOCOLO.md` — inteiro. São as regras.
3. `docs/DIARIO.md` — as 3 entradas do topo.
4. `tasks/TASK.md` — estado, fatos já medidos e armadilhas conhecidas.

## As cinco regras que mais quebram este projeto

1. **Nunca comite credenciais.** O repositório é PÚBLICO. **Usuário e senha** não
   entram em código, teste, log, README nem diário. (O *endereço* do painel é
   permitido e vive em `core/model/Padroes.kt` — decisão do dono, ver
   `docs/PROTOCOLO.md` §2.)
   Antes de commitar: rode `bash ferramentas/checar-segredos.sh`.

2. **Não aplique o plugin `org.jetbrains.kotlin.android`.** O AGP 9 traz Kotlin
   embutido; aplicá-lo quebra o build com *"Cannot add extension with name
   'kotlin'"*. Pelo mesmo motivo, não use `kotlinOptions { }` — use
   `kotlin { compilerOptions { } }`, ou deixe herdar de `compileOptions`.

3. **Não suba a Compose BOM sem ler o comentário em `gradle/libs.versions.toml`.**
   Compose 1.12.0+ exige `compileSdk 37`. A BOM fixada aqui (2026.06.01) é a
   última que roda em `compileSdk 36`.

4. **Nada de `android.*` dentro de `io.zaffa.filmes.core`.** Esse pacote é Kotlin
   puro para ser reaproveitado na versão web. O CI falha se você importar.

5. **"Compila" não é "funciona".** Ver `docs/PROTOCOLO.md`, seção 5: o projeto só
   se considera entregue quando **avançar o filme funciona**, comprovado nos dois
   motores de vídeo.

## Ao terminar qualquer mudança de comportamento

- Acrescente uma entrada no **topo** de `docs/DIARIO.md` (formato na seção 3.2 do
  protocolo). Registre o que te **surpreendeu**, não o que você digitou.
- Se o usuário te corrigiu, acrescente a lição em `tasks/lessons.md`.
- Marque o item em `tasks/TASK.md`.
