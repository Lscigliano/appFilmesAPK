# Arquitetura

Como as peças se encaixam, e **por quê**. Se você mudar algo estrutural, mude
aqui também — este arquivo é o mapa que a próxima pessoa vai ler.

---

## A ideia central

O painel (um CORE/Coreplay) continua sendo a **fonte dos dados e do acesso**.
O que o app acrescenta é **tudo o que o usuário vê e o controle da reprodução**.

```
  NOSSO                                      DO PAINEL
  ─────                                      ─────────
  tela de login  ──── envia o formulário ───▶ autenticação (cookies)
  tela inicial
  catálogo       ◀─── lê a página viva ─────  lista de títulos e categorias
       │
       ▼
  tela de espera ──── abre escondido ───────▶ página de detalhe
       │                                       (só ela emite o token do vídeo)
       ▼
  PLAYER         ◀─── URL do vídeo ─────────  arquivo .mp4 ou stream .m3u8
  (ExoPlayer ⇄ VLC)
```

**Por que o painel continua no meio:** só ele emite o token que libera o vídeo.
Pedir o MP4 direto, sem token, devolve **502** — está medido. Não existe caminho
que dispense o painel.

**Por que o usuário não o vê:** as páginas dele rodam em WebViews de 1dp,
invisíveis. O app lê o que precisa e descarta.

## Mapa do código

```
app/src/main/kotlin/io/zaffa/filmes/
├── core/model/Padroes.kt      Endereço do painel e User-Agent (Kotlin puro)
├── painel/                    Tudo que conversa com o painel
│   ├── LoginInvisivel.kt      Preenche e envia o formulário de login
│   ├── Catalogo.kt            JavaScript de extração + modelo Item
│   ├── LeitorCatalogo.kt      Carrega a página e colhe títulos e categorias
│   ├── AbridorDeVideo.kt      Abre o detalhe escondido e captura a URL do vídeo
│   ├── CapturaDeVideo.kt      Reconhece o que é mídia do painel
│   └── TelaPainel.kt          A página do painel, quando precisa aparecer
├── player/                    A razão do projeto existir
│   ├── MotorVideo.kt          Contrato comum aos dois motores
│   ├── MotorExo.kt            ExoPlayer, com recuperação automática
│   └── MotorVlc.kt            libVLC, idem
├── data/Preferencias.kt       Motor escolhido, sessão, credenciais lembradas
├── ui/                        Compose: Tema, Login, Início, Catálogo, Espera, Player
├── FilmesApp.kt               Container de dependências, feito à mão
└── MainActivity.kt            Entrada única, navegação por estado
```

## Decisões e seus motivos

### Ler a página em vez de usar uma API
O painel não expõe API documentada: `player_api.php` devolve 404, e o login é por
formulário. **Tentamos reconstruir as requisições e falhou** — o login nativo
recusava senha correta (ver `docs/DIARIO.md`).

A leitura é ancorada no **padrão da URL** (`page=moviedetail&id=`,
`page=moviecat&id=`), que o painel precisa manter para funcionar, e **nunca em
classes CSS**, que mudam a cada ajuste visual.

*Fragilidade honesta:* se eles mudarem a estrutura das URLs, a leitura para. O app
trata isso avisando na tela e oferecendo o painel completo — nunca uma tela vazia.

### Login pelo formulário do painel, num WebView invisível
A tela é nossa; o mecanismo é o do navegador. **Não reconstrua a requisição de
login** — já custou um ciclo inteiro de depuração.

### Catálogo sem cache
Nada de lista guardada. Toda abertura relê a página viva, então **título novo no
painel é título novo no app**, sem nada para invalidar. O botão "atualizar"
apenas descarta o leitor e relê.

### Categorias são obrigatórias, não enfeite
A página de Filmes é uma **vitrine com 38 carrosséis**, cada um mostrando só os
primeiros itens. Rolar até o fim traz ~300 títulos de um acervo muito maior.
**O acervo completo só existe dentro das categorias.**

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
Seis telas numa `sealed interface Tela`. Elimina uma dependência e uma camada de
indireção que um leitor novo teria de aprender antes de mexer em qualquer coisa.

### Container de dependências à mão, sem Hilt
Dois objetos criados no `Application`. Mais legível para quem chega sem contexto —
que é o cenário previsto neste projeto.

## O que NÃO existe, de propósito

- **Sem servidor nosso, sem analytics, sem telemetria.** O app fala só com o
  painel do usuário. Credencial que não sai do aparelho não vaza.
- **Sem seleção de qualidade.** O servidor entrega **um** arquivo por título
  (~1080p, 3 Mbps). Não há o que escolher.
- **Sem minify no release.** O libVLC carrega classes por JNI e o ganho de
  tamanho não compensa o risco.
