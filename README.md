# appFilmesAPK

Player Android para uma conta de streaming própria, feito porque o app oficial
(*Smarters Flex*) **trava ao avançar o filme e não volta mais**.

No celular da esposa do autor, com o **mesmo login e senha**, outro app da mesma
família não trava. Essa diferença é a pista: os apps dessa família deixam escolher
o motor de vídeo, e as duas instalações estão em motores diferentes.

Então este app faz do motor de vídeo um **botão dentro do player** — e troca de
motor **sem perder a posição do filme**.

---

## Para quem só quer assistir

Não precisa de Android Studio, nem de Java, nem de nada instalado.

1. Abra **[Releases](../../releases)** e baixe o arquivo mais recente terminado
   em **`arm64-v8a.apk`** — é o certo para praticamente todo celular atual.
   (Se ele recusar a instalar, baixe a versão `armeabi-v7a.apk`.)
2. No celular, abra o arquivo baixado. O Android vai pedir para permitir
   "instalar de fontes desconhecidas" — autorize para o seu navegador/gerenciador
   de arquivos.
3. Abra o app, preencha **servidor**, **usuário** e **senha** da sua assinatura.
4. Pronto. Se um filme travar ao avançar, toque no nome do motor no canto superior
   direito do player para trocar — o filme continua de onde parou.

> Seus dados de acesso ficam **somente no seu aparelho**. Este projeto não tem
> servidor, não coleta nada e não envia nada para lugar nenhum.

## O que está pronto

- [x] **Tela de login própria** — o app entra sozinho nas próximas vezes
- [x] **Tela inicial própria**, com atalhos para TV, Filmes, Séries, Favoritos e Continuar
- [x] **Catálogo próprio**: grade de capas, busca e as categorias do painel
- [x] **Do catálogo direto para o player**, sem passar pela página do painel
- [x] **Player com dois motores** (ExoPlayer e VLC), trocáveis sem perder a posição
- [x] **Recuperação automática**: falhou ao avançar, ele reabre e continua
- [x] **TV ao vivo e rádio** no nosso player, com indicador AO VIVO
- [ ] Retomar de onde parou
- [ ] Categorias próprias (mover um título para a seção que você quiser)
- [ ] Versão para navegador

Estado detalhado: [`tasks/TASK.md`](tasks/TASK.md).

## Para quem for mexer no código (pessoa ou IA)

**Leia primeiro, nesta ordem:**

1. [`docs/COMECE-AQUI.md`](docs/COMECE-AQUI.md) — como pôr para compilar. Comece por aqui.
2. [`docs/PROTOCOLO.md`](docs/PROTOCOLO.md) — as regras do projeto. Obrigatório.
3. [`docs/DIARIO.md`](docs/DIARIO.md) — as 3 entradas do topo. É onde está o que
   o código não conta.
4. [`tasks/TASK.md`](tasks/TASK.md) — estado, fatos medidos e armadilhas.

Compilar:

```bash
./gradlew :app:assembleDebug
# APK em app/build/outputs/apk/debug/
```

**Requisitos e o passo a passo completo estão em [`docs/COMECE-AQUI.md`](docs/COMECE-AQUI.md)** —
leia antes de tentar compilar. Resumo: JDK 17 e Android SDK com `platforms;android-37`
(que o `sdkmanager` talvez não consiga instalar sozinho) e `build-tools;36.0.0`.
As versões exatas do toolchain e **por que** são essas estão em
[`gradle/libs.versions.toml`](gradle/libs.versions.toml) — leia os comentários
antes de atualizar qualquer coisa.

## Aviso

Cliente alternativo para uma assinatura legítima do próprio autor. Não contorna
proteção de conteúdo, não gera acesso e não redistribui nada. Credenciais nunca
entram neste repositório — veja a seção 2 do protocolo.
