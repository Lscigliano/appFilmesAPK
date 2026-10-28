# Como o painel entrega as coisas

O que foi **confirmado na prática**. Nada aqui é suposição — cada linha tem
origem numa medição ou numa amostra que o dono capturou no navegador.

> ⚠️ A versão anterior deste arquivo descrevia o protocolo **Xtream Codes**.
> **Estava errada.** Este painel não é Xtream: `player_api.php` devolve 404, e o
> ID de título é texto (`oev_z4A`), não número sequencial. Todo o cliente Xtream
> foi removido do projeto. Ver `docs/DIARIO.md`.

---

## Endereços

| O quê | Endereço | Confirmação |
|---|---|---|
| Painel | `http://vouver.me/` | em uso |
| Login | `index.php?page=login` | POST → 302 → homepage |
| Página inicial | `index.php?page=homepage` | barra do navegador |
| Filmes | `index.php?page=movies` | dono, no navegador |
| Séries | `index.php?page=series` | dono, no navegador |
| TV ao vivo | `index.php?page=livetv` | funcionou no app |
| Categoria | `index.php?page=moviecat&id=<ID>` | três amostras do dono |
| Detalhe do título | `index.php?page=moviedetail&id=<ID>` | barra do navegador |
| Favoritos | `index.php?page=favorites` | ⚠️ **não confirmado** |
| Continuar | `index.php?page=watchhistory` | ⚠️ **não confirmado** |

Os dois últimos são palpite. Se falharem, o app cai na raiz do painel e avisa na
tela — nenhum palpite pode prender o usuário.

## Login

Formulário em `index.php?page=login`, campos:

| Campo | O que é |
|---|---|
| `username` | usuário |
| `sifre` | senha (o painel é de base turca) |
| `csrf_token` | gerado por sessão, está no HTML |
| `beni_hatirla` | "lembrar-me" |
| `recaptcha_response` | **existe no formulário mas o reCAPTCHA está DESLIGADO** — a página não carrega script nem sitekey do Google |

Sucesso devolve **302** para `page=homepage`. Falha devolve a própria página de
login de volta, com status 200 — por isso **não dá para detectar pelo código HTTP**.

> 🛑 **Não reconstrua esta requisição em código.** Já foi tentado e falhou: o
> painel recusava credenciais corretas. O app usa o formulário dele, preenchido
> por JavaScript num WebView invisível (`painel/LoginInvisivel.kt`).

## Vídeo

```
http://<ip-do-servidor>/vauth/<numero>.mp4?token=<token-longo>
```

| Fato | Valor | Como foi medido |
|---|---|---|
| Quem emite o token | **só o painel** | pedir sem token devolve **502** |
| Validade do token | ≥ 34 min | monitor a cada 2 min |
| Formato | MP4 progressivo, H.264 + AAC | cabeçalho do arquivo |
| Qualidade | **~1080p, 2,98 Mbps** | idem |
| Seleção de qualidade | **não existe** | um arquivo por título |
| `Accept-Ranges` | sim, 206 Partial Content | |
| `Access-Control-Allow-Origin` | **`*`** | viabiliza o projeto web |
| User-Agent do VLC | **HTTP 500** | o servidor recusa |

**TV e rádio** usam HLS (`.m3u8`), não arquivo fechado. O app captura **só o
manifesto**, nunca os segmentos `.ts` — eles chegam às centenas por minuto.

## Cuidados que já custaram caro

1. **O título pode ser um trailer do YouTube.** O painel embute YouTube em
   alguns itens, por cima da página do filme, com um botão "Fechar". Um player
   que só espera MP4 fica girando para sempre neles.
2. **A página de Filmes é uma vitrine, não uma lista.** 38 carrosséis, cada um
   com os primeiros itens. O acervo completo só sai pelas categorias.
3. **Medir status HTTP não é medir entrega.** Um `206` com `Content-Range`
   correto pode entregar 6 KB de 1 MB prometido. **Confira os bytes recebidos.**
4. **O endereço que o app oficial exibe na tela da conta está morto.** Não é o
   host da API — testado em 3 redes e 11 portas.

## Conta

O plano tem **máximo de 1 conexão simultânea** (visto em *Meu perfil*). Só uma
pessoa assiste por vez; isso é do plano, não do app.
