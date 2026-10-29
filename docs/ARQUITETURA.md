# Arquitetura

Como as peças se encaixam, e **por quê**. Se você mudar algo estrutural, mude
aqui também — este arquivo é o mapa que a próxima pessoa vai ler.

---

## A ideia central

O app fala **só com a API Xtream do servidor de streaming**. O site do painel
(CorePlay) não aparece em lugar nenhum — pedido do dono: *"quero que abra
apenas o MEU"*.

```
  NOSSO                                         SERVIDOR (Xtream / XUI)
  ─────                                         ───────────────────────
  tela de login   ── player_api.php ──────────▶ auth: 1 / 0
  tela inicial    ── get_*_categories ────────▶ categorias
  catálogo, busca ── get_vod_streams ─────────▶ 43 mil filmes, 9,7 mil séries,
                     get_series / live_streams    3,3 mil canais (uma chamada cada)
  série           ── get_series_info ─────────▶ temporadas e episódios
       │
       ▼
  PLAYER          ── /movie|series|live/... ──▶ 302 → arquivo com token
  (ExoPlayer ⇄ VLC)
```

**Como se chegou aqui:** a primeira versão era cliente Xtream e foi abandonada
porque `player_api.php` dava 404 — **no host do painel**. A página de detalhe
do painel, lida depois, trazia o link do vídeo pronto num **outro host**, e lá
a API responde com o mesmo usuário e senha. Ver `docs/DIARIO.md` (2026-10-29).

## Mapa do código

```
app/src/main/kotlin/io/zaffa/filmes/
├── core/model/Padroes.kt      Endereços (painel e servidor) e User-Agent
├── xtream/                    Tudo que conversa com o servidor
│   ├── ApiXtream.kt           Login, catálogo, episódios e links de vídeo
│   ├── Acervo.kt              Catálogo inteiro em memória + busca global
│   └── Modelos.kt             Tipo, Categoria, Titulo, Episodio, normalizar()
├── player/                    A razão do projeto existir
│   ├── MotorVideo.kt          Contrato comum aos dois motores
│   ├── MotorExo.kt            ExoPlayer, com recuperação automática
│   └── MotorVlc.kt            libVLC, idem
├── data/Preferencias.kt       Motor escolhido, credenciais lembradas
├── ui/                        Compose: Login, Início, Catálogo, Busca, Série, Player
├── FilmesApp.kt               Container de dependências, feito à mão
└── MainActivity.kt            Entrada única, navegação por estado
```

## Decisões e seus motivos

### API Xtream em vez de ler páginas do painel
Ler o painel em WebViews escondidos funcionou, mas era lento (abrir um filme
esperava a página carregar e o play ser clicado) e às vezes caía na página do
CorePlay. A API devolve o catálogo inteiro em segundos e o link do vídeo é
**montado**, não capturado.

⚠️ O protocolo leva usuário e senha **na URL**. Nenhuma URL de `ApiXtream`
pode ir para log ou mensagem de erro; os erros são reescritos em texto nosso,
e `Tela.Player.toString()` omite a URL.

### Busca global sobre o acervo inteiro
A busca procura pelo nome em **todos** os títulos de todos os tipos, nunca só na
categoria aberta. Sem acento, todas as palavras em qualquer ordem. Por isso a
barra de busca do catálogo **abre a busca global** em vez de filtrar a grade.

### Catálogo em memória, sem cache em disco
Carregado ao entrar (~6 MB, três chamadas em paralelo). Título novo no servidor
aparece na próxima abertura ou no botão "atualizar". Nada para invalidar.

### Títulos duplicados viram um só
A API repete o mesmo `stream_id` uma vez por categoria (55 mil linhas, 43 mil
filmes). `ApiXtream.titulos` junta por id e acumula as categorias.

### Dois motores de vídeo
O app oficial trava ao avançar; a família Smarters deixa escolher o motor, e as
instalações que travavam e as que não travavam estavam em motores diferentes.
Aqui a troca é um toque, **sem perder a posição do filme**.

⚠️ **O User-Agent nunca pode conter "VLC"**: o servidor de vídeo responde
**HTTP 500** a ele. Está centralizado em `Padroes.USER_AGENT`, com a medição
junto ao código. Remover isso torna o vídeo impossível de tocar.

### Recuperação automática no player
Falhou depois de um avanço? O player reabre o fluxo na mesma posição, até 5 vezes
por incidente. O contador só é devolvido após **10 segundos de reprodução
estável** — sem essa guarda, tocar meio segundo e falhar de novo viraria laço
infinito, que é o defeito observado no player do próprio painel.

### Navegação por estado, sem Navigation Compose
Uma `sealed interface Tela` e uma pilha simples em `ModeloPrincipal`: "voltar"
sempre cai na tela anterior (do player para a série, a busca ou a categoria).

### Container de dependências à mão, sem Hilt
Dois objetos criados no `Application`. Mais legível para quem chega sem contexto —
que é o cenário previsto neste projeto.

## O que NÃO existe, de propósito

- **Sem servidor nosso, sem analytics, sem telemetria.** O app fala só com o
  servidor da assinatura do usuário. Credencial que não sai do aparelho não vaza.
- **Sem seleção de qualidade.** O servidor entrega **um** arquivo por título
  (~1080p, 3 Mbps). Não há o que escolher.
- **Sem minify no release.** O libVLC carrega classes por JNI e o ganho de
  tamanho não compensa o risco.
