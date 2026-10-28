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

1. Baixe o arquivo `.apk` mais recente em **[Releases](../../releases)**.
2. No celular, abra o arquivo baixado. O Android vai pedir para permitir
   "instalar de fontes desconhecidas" — autorize para o seu navegador/gerenciador
   de arquivos.
3. Abra o app, preencha **servidor**, **usuário** e **senha** da sua assinatura.
4. Pronto. Se um filme travar ao avançar, toque no nome do motor no canto superior
   direito do player para trocar — o filme continua de onde parou.

> Seus dados de acesso ficam **somente no seu aparelho**. Este projeto não tem
> servidor, não coleta nada e não envia nada para lugar nenhum.

## O que está pronto

- [x] Login com **detecção automática do servidor** (testa portas e protocolos)
- [x] Catálogo de filmes com capas, categorias e busca
- [x] Player com **dois motores** (ExoPlayer e VLC) e troca em um toque
- [x] Mensagens de erro que dizem a causa real (rede bloqueando, limite de telas,
      conta expirada) em vez de "erro desconhecido"
- [ ] Séries e canais ao vivo
- [ ] Continuar assistindo de onde parou, entre sessões
- [ ] Versão para navegador

Estado detalhado: [`tasks/TASK.md`](tasks/TASK.md).

## Para quem for mexer no código (pessoa ou IA)

**Leia primeiro, nesta ordem:**

1. [`docs/PROTOCOLO.md`](docs/PROTOCOLO.md) — as regras do projeto. Obrigatório.
2. [`docs/DIARIO.md`](docs/DIARIO.md) — as 3 entradas do topo. É onde está o que
   o código não conta.
3. [`tasks/TASK.md`](tasks/TASK.md) — o próximo item a fazer.

Compilar:

```bash
./gradlew :app:assembleDebug
# APK em app/build/outputs/apk/debug/
```

Requisitos: JDK 17 e Android SDK com `platforms;android-36` e `build-tools;36.0.0`.
As versões exatas do toolchain e **por que** são essas estão em
[`gradle/libs.versions.toml`](gradle/libs.versions.toml) — leia os comentários
antes de atualizar qualquer coisa.

## Aviso

Cliente alternativo para uma assinatura legítima do próprio autor. Não contorna
proteção de conteúdo, não gera acesso e não redistribui nada. Credenciais nunca
entram neste repositório — veja a seção 2 do protocolo.
