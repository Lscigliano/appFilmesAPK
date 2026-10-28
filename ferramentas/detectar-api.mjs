#!/usr/bin/env node
/**
 * detectar-api.mjs — descobre a API do painel E diagnostica o travamento ao avançar.
 *
 * ⚠️ RODE NUMA REDE LIBERADA (casa ou 4G/5G). Em rede corporativa o filtro devolve
 * 502 com página de bloqueio e tudo falha — ver docs/DIARIO.md, 2026-10-28.
 *
 * Uso:
 *   node ferramentas/detectar-api.mjs
 *       lê credenciais.local.json na raiz do repositório (ignorado pelo git)
 *
 *   node ferramentas/detectar-api.mjs --host meupainel.com --user USUARIO --pass SENHA
 *
 * O relatório sai HIGIENIZADO (usuário, senha e host mascarados), para poder ser
 * colado em docs/DIARIO.md sem vazar credencial.
 *
 * Sem dependências. Precisa de Node 18+.
 */

import { readFileSync } from 'node:fs';
import { argv, exit } from 'node:process';

const UA = 'okhttp/4.12.0';
const TIMEOUT = 20000;

/* ─────────────────────────── entrada ─────────────────────────── */

function lerCredenciais() {
  const a = {};
  for (let i = 2; i < argv.length; i++) {
    const m = /^--([a-z]+)$/.exec(argv[i]);
    if (m) a[m[1]] = argv[++i];
  }
  if (a.host && a.user && a.pass) {
    return { host: a.host, user: a.user, pass: a.pass };
  }
  try {
    const caminho = new URL('../credenciais.local.json', import.meta.url);
    const j = JSON.parse(readFileSync(caminho, 'utf8'));
    return {
      host: String(j.host ?? ''),
      user: String(j.usuario ?? j.user ?? ''),
      pass: String(j.senha ?? j.pass ?? ''),
    };
  } catch {
    console.error(
      [
        '',
        'Faltam credenciais. Crie credenciais.local.json na raiz do repositório',
        '(já está no .gitignore, não vai para o GitHub):',
        '',
        '{',
        '  "host": "meupainel.com",',
        '  "usuario": "SEU_USUARIO",',
        '  "senha": "SUA_SENHA"',
        '}',
        '',
        'Ou passe direto:',
        '  node ferramentas/detectar-api.mjs --host X --user Y --pass Z',
        '',
      ].join('\n'),
    );
    exit(1);
  }
}

const { host, user, pass } = lerCredenciais();

/** Mascara qualquer credencial que escape para a saída. */
function mascarar(texto) {
  let s = String(texto);
  if (user) s = s.split(user).join('<<USUARIO>>');
  if (pass) s = s.split(pass).join('<<SENHA>>');
  if (host) s = s.split(host).join('<<HOST>>');
  return s;
}

const log = (...partes) => console.log(mascarar(partes.join(' ')));

/* ─────────────────────────── http ─────────────────────────── */

async function pedir(url, { cabecalhos = {} } = {}) {
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), TIMEOUT);
  const inicio = Date.now();
  try {
    const r = await fetch(url, {
      redirect: 'follow',
      signal: ctrl.signal,
      headers: { 'User-Agent': UA, ...cabecalhos },
    });
    return { ok: true, status: r.status, resp: r, ms: Date.now() - inicio };
  } catch (e) {
    const erro = e.name === 'AbortError' ? 'timeout' : e.message;
    return { ok: false, erro, ms: Date.now() - inicio };
  } finally {
    clearTimeout(timer);
  }
}

const FILTROS = /sophos|zscaler|bluecoat|fortiguard|forcepoint|websense|access denied|blocked/i;

function ehBloqueioDeRede(corpo) {
  if (!corpo || corpo.length < 200) return false;
  const amostra = corpo.slice(0, 80000);
  return FILTROS.test(amostra) && /<html|<!doctype/i.test(amostra);
}

function urlApi(base, extras = {}) {
  const u = new URL(`${base}/player_api.php`);
  u.searchParams.set('username', user);
  u.searchParams.set('password', pass);
  for (const [k, v] of Object.entries(extras)) u.searchParams.set(k, v);
  return u.toString();
}

/* ─────────────── etapa 1: achar o endpoint ─────────────── */

function candidatos() {
  if (host.includes(':')) return [`http://${host}`, `https://${host}`];
  return [
    `http://${host}`,
    `http://${host}:8080`,
    `https://${host}`,
    `http://${host}:2082`,
    `http://${host}:2086`,
    `http://${host}:25461`,
    `http://${host}:8000`,
  ];
}

async function acharXtream() {
  for (const base of candidatos()) {
    const r = await pedir(urlApi(base));
    if (!r.ok) {
      log(`  ${base}  ->  falhou (${r.erro}, ${r.ms}ms)`);
      continue;
    }
    const corpo = await r.resp.text();
    if (ehBloqueioDeRede(corpo)) {
      log(`  ${base}  ->  HTTP ${r.status}  PÁGINA DE BLOQUEIO DA SUA REDE (não é o servidor)`);
      continue;
    }
    try {
      const j = JSON.parse(corpo);
      if (j && j.user_info) {
        log(`  ${base}  ->  HTTP ${r.status}  ✅ Xtream Codes confirmado (${r.ms}ms)`);
        return { base, info: j };
      }
      log(`  ${base}  ->  HTTP ${r.status}  JSON sem user_info`);
    } catch {
      log(`  ${base}  ->  HTTP ${r.status}  resposta não-JSON (${corpo.length} bytes)`);
    }
  }
  return null;
}

/* ─────────── etapa 4: diagnóstico do travamento ─────────── */

/**
 * O CORAÇÃO DESTE SCRIPT.
 *
 * Avançar um filme = o player pede um trecho do MEIO do arquivo com o cabeçalho
 * `Range`. Se o servidor ignora e devolve 200 com o arquivo do início, o
 * ExoPlayer acha que recebeu o trecho pedido, mas recebeu outra coisa — e
 * congela. É a causa nº 1 de "avancei e não volta mais".
 */
async function diagnosticarBusca(urlFilme) {
  const inicial = await pedir(urlFilme, { cabecalhos: { Range: 'bytes=0-1023' } });
  if (!inicial.ok) {
    log(`  ❌ nem o primeiro trecho baixou: ${inicial.erro}`);
    return;
  }

  const aceitaRange = inicial.resp.headers.get('accept-ranges');
  const contentRange = inicial.resp.headers.get('content-range');
  const tamanho = Number(
    (contentRange && contentRange.split('/')[1]) ||
      inicial.resp.headers.get('content-length') ||
      0,
  );

  log(`  tipo de conteúdo  : ${inicial.resp.headers.get('content-type')}`);
  log(`  accept-ranges     : ${aceitaRange ?? '(ausente)'}`);
  log(`  tamanho do arquivo: ${tamanho ? (tamanho / 1048576).toFixed(1) + ' MB' : 'desconhecido'}`);
  log(
    `  trecho inicial    : HTTP ${inicial.status} ` +
      (inicial.status === 206 ? '✅ 206 Partial Content' : '⚠️ esperava 206'),
  );
  await inicial.resp.arrayBuffer().catch(() => {});

  if (!tamanho) {
    log('  ⚠️ sem tamanho conhecido — não dá para testar o meio do arquivo.');
    return;
  }

  const meio = Math.floor(tamanho / 2);
  const noMeio = await pedir(urlFilme, {
    cabecalhos: { Range: `bytes=${meio}-${meio + 1023}` },
  });
  if (!noMeio.ok) {
    log(`  ❌ avançar para a metade falhou: ${noMeio.erro}`);
    return;
  }

  const crMeio = noMeio.resp.headers.get('content-range');
  const buscaOk = noMeio.status === 206 && Boolean(crMeio);
  log(
    `  avançar p/ metade : HTTP ${noMeio.status} content-range=${crMeio ?? '(ausente)'} ` +
      `${buscaOk ? '✅' : '❌'} (${noMeio.ms}ms)`,
  );
  await noMeio.resp.arrayBuffer().catch(() => {});

  log('');
  if (buscaOk) {
    log('  ➜ VEREDITO: o servidor SUPORTA busca por posição.');
    log('    O travamento é CONFIGURAÇÃO DO PLAYER, não do servidor.');
    log('    Ação: manter o ExoPlayer como padrão (já ajustado em MotorExo.kt)');
    log('    e deixar o VLC como alternativa.');
  } else {
    log('  ➜ VEREDITO: o servidor IGNORA o cabeçalho Range.');
    log('    É exatamente isso que trava o filme ao avançar no ExoPlayer.');
    log('    Ação: tornar o VLC o motor PADRÃO (Sessao.kt / Motor.EXOPLAYER).');
  }
}

/* ─────────────────────────── principal ─────────────────────────── */

log('═══ DETECTOR DE API + DIAGNÓSTICO DE BUSCA ═══');
log(`host: <<HOST>>   usuário: <<USUARIO>>`);
log('');
log('— etapa 1: procurando endpoint Xtream —');

const achado = await acharXtream();

if (!achado) {
  log('');
  log('Nenhum endpoint Xtream respondeu. Pode ser:');
  log('  a) rede ainda bloqueando o painel — teste no 4G/5G;');
  log('  b) o painel serve lista M3U. Testando get.php…');

  const u = new URL(`http://${host}/get.php`);
  u.searchParams.set('username', user);
  u.searchParams.set('password', pass);
  u.searchParams.set('type', 'm3u_plus');
  u.searchParams.set('output', 'ts');

  const r = await pedir(u.toString());
  if (r.ok) {
    const corpo = await r.resp.text();
    if (corpo.trimStart().startsWith('#EXTM3U')) {
      const entradas = (corpo.match(/#EXTINF/g) ?? []).length;
      log(`  ✅ Lista M3U confirmada — ${entradas} entradas.`);
      log('     Registre no diário: API = M3U (sem capa/sinopse).');
    } else if (ehBloqueioDeRede(corpo)) {
      log('  ❌ página de bloqueio da rede de novo. Rode em casa ou no 4G/5G.');
    } else {
      log(`  ❌ get.php devolveu HTTP ${r.status}, ${corpo.length} bytes, não-M3U.`);
    }
  } else {
    log(`  ❌ get.php falhou: ${r.erro}`);
  }
  exit(2);
}

const { base, info } = achado;
const ui = info.user_info ?? {};
const si = info.server_info ?? {};

log('');
log('— etapa 2: a conta —');
log(`  status          : ${ui.status}`);
log(`  é teste?        : ${ui.is_trial === '1' ? 'sim' : 'não'}`);
log(`  conexões        : ${ui.active_cons} ativas de ${ui.max_connections} permitidas`);
log(
  `  expira em       : ${
    ui.exp_date ? new Date(Number(ui.exp_date) * 1000).toISOString().slice(0, 10) : 'sem data'
  }`,
);
log(`  formatos        : ${(ui.allowed_output_formats ?? []).join(', ')}`);
log(`  porta informada : ${si.port} (https ${si.https_port})`);

if (Number(ui.active_cons) >= Number(ui.max_connections) && Number(ui.max_connections) > 0) {
  log('');
  log('  🚨 O LIMITE DE TELAS SIMULTÂNEAS JÁ ESTÁ ESTOURADO.');
  log('     Isso sozinho explica travamento: ao avançar, o player abre uma nova');
  log('     conexão antes de fechar a antiga, o servidor recusa e o vídeo congela.');
  log('     Anote no diário — isso MUDA qual é a correção certa.');
}

log('');
log('— etapa 3: o catálogo —');

let primeiroFilme = null;
const cats = await pedir(urlApi(base, { action: 'get_vod_categories' }));
if (cats.ok) {
  const lista = await cats.resp.json().catch(() => []);
  log(`  categorias de filmes: ${Array.isArray(lista) ? lista.length : 0}`);
}
const filmes = await pedir(urlApi(base, { action: 'get_vod_streams' }));
if (filmes.ok) {
  const lista = await filmes.resp.json().catch(() => []);
  log(`  filmes no catálogo  : ${Array.isArray(lista) ? lista.length : 0}`);
  if (Array.isArray(lista)) {
    primeiroFilme = lista.find((f) => f && f.stream_id);
    if (primeiroFilme) {
      log(`  campos por filme    : ${Object.keys(primeiroFilme).join(', ')}`);
      log(`  container_extension : ${primeiroFilme.container_extension}`);
    }
  }
}

if (primeiroFilme) {
  const ext = primeiroFilme.container_extension || 'mp4';
  const urlFilme = `${base}/movie/${encodeURIComponent(user)}/${encodeURIComponent(pass)}/${primeiroFilme.stream_id}.${ext}`;
  log('');
  log('— etapa 4: DIAGNÓSTICO DO TRAVAMENTO AO AVANÇAR —');
  log(`  testando um filme real do catálogo (.${ext})`);
  await diagnosticarBusca(urlFilme);
} else {
  log('');
  log('⚠️ não consegui pegar um filme do catálogo para testar a busca.');
}

log('');
log('═══ FIM — cole este relatório em docs/DIARIO.md ═══');
