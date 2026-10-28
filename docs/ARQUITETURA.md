# Arquitetura

Mapa do código, e **por que** cada parte é como é. Se você vier mudar algo
estrutural, mude aqui também.

## Visão geral

```
app/src/main/kotlin/io/zaffa/filmes/
├── core/                    ← KOTLIN PURO. Nada de android.* aqui.
│   ├── model/Modelos.kt     Domínio: Filme, Credenciais, Falha…
│   └── api/
│       ├── Json.kt          Leitura tolerante de JSON
│       ├── ClienteXtream.kt Protocolo Xtream Codes
│       └── DetectorApi.kt   Descobre o endpoint em tempo de execução
├── player/                  ← A razão do projeto existir
│   ├── MotorVideo.kt        Contrato comum aos dois motores
│   ├── MotorExo.kt          ExoPlayer, ajustado contra o bug de seek
│   └── MotorVlc.kt          libVLC, tolerante a servidor problemático
├── data/Sessao.kt           Credenciais e preferências, só no aparelho
├── ui/                      Compose: Tema, Login, Catálogo, Player
├── FilmesApp.kt             Container de dependências, feito à mão
└── MainActivity.kt          Entrada única
```

## As decisões, e o motivo

### Um módulo só, com `core` isolado por pacote

O plano original era um módulo Gradle `core` separado, Kotlin puro, para a futura
versão web. Foi **abandonado durante a implementação**: o AGP 9 traz Kotlin
embutido, e misturar isso com o plugin `kotlin.jvm` de um módulo separado é uma
fonte de conflito de versão que não vale o custo num projeto deste tamanho.

A portabilidade foi preservada por **disciplina verificada**: o pacote
`io.zaffa.filmes.core` não pode importar `android.*` nem `androidx.*`, e o CI
falha se alguém importar. Quando a versão web existir, esse pacote é copiado
inteiro.

### Leitura de JSON campo a campo, sem `@Serializable`

Painéis Xtream mandam o mesmo campo ora como número, ora como texto, ora ausente,
ora como `false` no lugar de `null`. Com classes anotadas, **um item estranho
derruba o catálogo inteiro**. `Json.kt` lê campo a campo e ignora o que não
entende. Custa mais código e sobrevive a painel mal-comportado — e este painel é
um CORE, que pode nem seguir o Xtream à risca.

### Descoberta de endpoint em vez de host fixo

O endereço que o usuário vê no app (`painel.exemplo`) não é necessariamente o host da
API; o portal web é outro (`portal.exemplo`). `DetectorApi` testa esquema e portas em
ordem de probabilidade e só aceita o endereço que **autenticar de verdade** —
responder HTTP 200 não basta, porque páginas de bloqueio e de erro também
respondem 200.

### Dois motores de vídeo, trocáveis em um toque

O núcleo do problema. Resumo técnico:

| | ExoPlayer | libVLC |
|---|---|---|
| Exige `HTTP Range` correto | **sim** | não |
| Lida com MKV sem índice | mal | bem |
| Consumo de bateria | menor | maior |
| Suavidade | melhor | boa |

Se o servidor **ignora o cabeçalho Range** — devolve o arquivo do início quando o
player pede um trecho do meio — o ExoPlayer confia na resposta e congela. É a
causa nº 1 de "avancei e não volta mais". O VLC detecta a inconsistência e reabre
o fluxo.

Como não dava para testar o servidor real (rede bloqueada), o app **não escolhe
por você**: entrega os dois e deixa a troca a um toque, preservando a posição do
filme. `ferramentas/detectar-api.mjs` responde qual é o certo, quando rodar numa
rede liberada.

### Navegação por estado, sem Navigation Compose

São três telas. Uma `sealed interface Tela` no `ModeloPrincipal` resolve, e
elimina uma dependência e uma camada de indireção que um leitor novo teria que
aprender antes de mexer em qualquer coisa.

### Container de dependências à mão, sem Hilt

Três objetos (`HttpClient`, `Sessao`, `DetectorApi`) criados no `Application`.
Legível para quem chega sem contexto — que é o cenário previsto neste projeto.

## O que NÃO existe de propósito

- **Sem analytics, sem crash reporting, sem servidor próprio.** O app fala só com
  o painel do usuário. Credencial que não sai do aparelho não vaza.
- **Sem cache de catálogo em disco** (ainda). Enquanto o formato da API não for
  confirmado contra o servidor real, cachear um formato errado só atrapalha.
- **Sem minify no release.** O libVLC carrega classes por JNI e o ganho de
  tamanho não compensa o risco. As regras ProGuard já estão escritas para quando
  valer a pena.
