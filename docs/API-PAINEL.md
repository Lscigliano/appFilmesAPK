# A API do painel

Contrato que o app fala. Serve de referência para a futura versão web, que deve
reaproveitar o pacote `io.zaffa.filmes.core`.

> ⚠️ **Nada aqui foi confirmado contra o servidor real ainda.** A rede corporativa
> bloqueia o painel (ver `docs/DIARIO.md`, 2026-10-28). O que está escrito abaixo é
> o protocolo Xtream Codes padrão, que é o que a família Smarters usa. Ao rodar
> `ferramentas/detectar-api.mjs` numa rede liberada, **corrija este arquivo com o
> que o servidor realmente responder** e registre a diferença no diário.

## Descoberta do endereço

O usuário informa só o host. O app testa, em ordem:

```
http://HOST          http://HOST:8080      https://HOST
http://HOST:2082     http://HOST:2086      http://HOST:25461      http://HOST:8000
```

Aceita o primeiro que **autenticar de verdade** — responder HTTP 200 não basta,
porque páginas de erro e de bloqueio de rede também respondem 200.

Implementação: `core/api/DetectorApi.kt`.

## Autenticação

```
GET {base}/player_api.php?username=USUARIO&password=SENHA
```

Resposta esperada:

```json
{
  "user_info": {
    "auth": 1,
    "status": "Active",
    "exp_date": "1750000000",
    "active_cons": "0",
    "max_connections": "1",
    "allowed_output_formats": ["m3u8", "ts", "rtmp"]
  },
  "server_info": { "port": "8080", "https_port": "8443" }
}
```

Regras aplicadas pelo app:

| Condição | Tratamento |
|---|---|
| `auth == 0` | Usuário ou senha incorretos |
| `status != "Active"` | Conta expirada/desativada |
| `active_cons >= max_connections` | **Aviso de limite de telas** — causa real de travamento no meio do filme |
| corpo é HTML com assinatura de filtro | Rede intermediária bloqueando (não é o servidor) |

## Catálogo de filmes

```
GET {base}/player_api.php?...&action=get_vod_categories
GET {base}/player_api.php?...&action=get_vod_streams[&category_id=N]
GET {base}/player_api.php?...&action=get_vod_info&vod_id=N
```

Campos de interesse em cada filme: `stream_id`, `name`, `stream_icon`,
`container_extension`, `rating`, `added`, `category_id`.

**Cuidado com `container_extension`.** Às vezes só vem no `get_vod_info`, e a URL
montada com a extensão errada devolve 404 — o filme simplesmente "não abre". Por
isso o app busca o detalhe antes de reproduzir
(`ModeloPrincipal.abrirFilme`).

## URL de reprodução

```
{base}/movie/{USUARIO}/{SENHA}/{stream_id}.{container_extension}
```

> 🔒 **As credenciais vão no caminho da URL.** Essa string nunca pode ir para log,
> telemetria ou mensagem de erro. `MotorExo` mostra só o código do erro do
> ExoPlayer justamente por isso.

## Por que a leitura de JSON é tolerante

Painéis Xtream devolvem o mesmo campo ora como número (`"stream_id": 123`), ora
como texto (`"stream_id": "123"`), ora ausente, ora `false` no lugar de `null`.
Com classes `@Serializable`, **um item estranho derruba o catálogo inteiro**.

`core/api/Json.kt` lê campo a campo, aceita número ou texto, e ignora o que não
entende. Este painel é um **CORE/Coreplay**, que pode nem seguir o Xtream à risca
— mais um motivo para não confiar em formato fixo.

## O que ainda falta mapear

- [ ] Séries: `get_series`, `get_series_info` (temporadas e episódios)
- [ ] Canais ao vivo: `get_live_streams`, e se o servidor entrega `.ts` ou `.m3u8`
- [ ] EPG: `get_short_epg`
- [ ] Confirmar se este painel responde `player_api.php` ou precisa de outro caminho
