# TASK.md — plano ativo

Atualizado em 2026-10-28. Itens são **verificáveis**: cada um diz como provar
que ficou pronto. Níveis de prova (N1..N4) definidos em `docs/PROTOCOLO.md`, §5.

---

## Fase 1 — Fundação ✅

- [x] Repositório criado, `.gitignore` protegendo credenciais
- [x] Protocolo e diário escritos (`docs/PROTOCOLO.md`, `docs/DIARIO.md`)
- [x] Toolchain confirmado contra documentação oficial (AGP 9.4.1, Gradle 9.8.2,
      JDK 17, compileSdk 36, Compose BOM 2026.06.01)
- [x] Detector de API + diagnóstico de busca (`ferramentas/detectar-api.mjs`)

## Fase 2 — App compilando **(em andamento)**

- [x] **N1 — `./gradlew :app:assembleDebug` termina em BUILD SUCCESSFUL** ✅ 2026-10-28
      *Prova:* colar a última linha do build no diário.
      *Risco conhecido:* o AGP 9.4.1 embute Kotlin 2.2.10 e os plugins do Compose
      e do serialization aqui são 2.4.21. Em tese sobe o KGP para 2.4.21 sem
      conflito, mas isso **não estava confirmado na documentação** — é o primeiro
      lugar a olhar se o build falhar.
- [ ] **N2 — APK instala e a tela de login aparece** em aparelho real
      *Prova:* `adb install` + print da tela no diário (sem mostrar credenciais).

## Fase 3 — Funcionar de verdade (precisa de rede sem bloqueio)

> ⚠️ Nada desta fase pode ser testado na rede corporativa — o filtro Sophos
> bloqueia o painel. Ver diário, 2026-10-28. Faça em casa ou no 4G/5G.

- [ ] **Rodar `node ferramentas/detectar-api.mjs`** e colar o relatório no diário.
      Isso responde de uma vez: qual API, qual porta, e **se o servidor aceita
      busca por posição**.
- [ ] **N3 — login autentica e o catálogo lista filmes com capa**
- [ ] **N3 — um filme abre e toca** nos dois motores
- [ ] **N4 — AVANÇAR O FILME FUNCIONA.** O objetivo do projeto.
      *Prova obrigatória no diário, para cada motor:*
      | Motor | Avançar 30 s | Arrastar p/ metade | Voltar 10 s | Observação |
      |---|---|---|---|---|
      | ExoPlayer | ? | ? | ? | |
      | VLC | ? | ? | ? | |
- [ ] Definir qual motor vira o **padrão** com base na tabela acima, e registrar
      o porquê no diário.

## Fase 4 — Distribuição

- [x] CI publica APK em Releases a cada push na `main` ✅ 2026-10-28
      *Prova:* link do release com o `.apk` anexado.
- [ ] Criar chave de assinatura própria e guardar como secret do GitHub
      (hoje o release sai assinado com a chave de debug — instala, mas não
      permite atualizar por cima se a chave mudar). Ver `docs/DISTRIBUICAO.md`.

## Fase 5 — Depois

- [ ] Séries (temporadas/episódios) e canais ao vivo
- [ ] "Continuar assistindo" — salvar posição por filme
- [ ] Navegação por controle remoto (D-pad) para TV Box
- [ ] Versão para navegador, reaproveitando `io.zaffa.filmes.core`

---

## Revisão da sessão de 2026-10-28

**Feito:** estrutura do repositório, protocolo, diário, detector de API com
diagnóstico de HTTP Range, app completo (login com detecção automática, catálogo,
player com dois motores e troca sem perder posição), CI de build.

**Descoberto:** a rede corporativa bloqueia o painel (filtro Sophos devolvendo 502);
o portal `portal.exemplo` é um painel CORE atrás de Cloudflare e não responde
`player_api.php`; o AGP 9 mudou o suficiente para quebrar builds escritos para
AGP 8; Compose 1.12+ exigiria um SDK que a máquina não tem.

**Pendente e importante:** nada da Fase 3 pôde ser validado contra o servidor real.
O app está escrito para descobrir a API sozinho, mas **isso ainda não foi provado
contra o painel**. É o primeiro trabalho da próxima sessão, em rede liberada.
