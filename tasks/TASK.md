# TASK.md — estado do projeto

Atualizado em 2026-10-28, fim da primeira sessão.
Itens são **verificáveis**. Níveis de prova (N1..N4) em `docs/PROTOCOLO.md`, §5.

---

## ✅ Entregue e confirmado no aparelho

- [x] **N1** — compila (`./gradlew :app:assembleDebug`)
- [x] **N2** — instala e abre
- [x] **N3** — login, catálogo e reprodução funcionam
- [x] **N4** — **avançar o filme funciona** ← o objetivo do projeto
      *Confirmado pelo dono em 5G e em WiFi, filme e TV.*
- [x] CI publica APK em Releases a cada push
- [x] Tela de login própria (WebView invisível envia o formulário do painel)
- [x] "Lembrar meus dados" — o app entra sozinho
- [x] Tela inicial própria, cinco atalhos cabendo na tela
- [x] Catálogo próprio: grade de capas, busca, contagem de títulos
- [x] Categorias do painel como chips (padrão `page=moviecat&id=...`)
- [x] Botão de atualizar lista
- [x] Do catálogo **direto para o nosso player**, sem passar pela página do painel
- [x] Player: dois motores trocáveis, recuperação automática, tela cheia deitada
- [x] TV ao vivo e rádio no nosso player (HLS), com indicador **AO VIVO**
- [x] Nome do filme no player, em vez do número do arquivo

## 🔜 Próximos, por valor no uso diário

- [ ] **Retomar de onde parou.** Hoje o filme sempre recomeça do zero — é a falta
      mais sentida. Guardar posição por título e oferecer "continuar de HH:MM".
- [ ] **Categorias próprias.** O dono quer mover um título de "Ação" para
      "Aventura" no app dele. Agora é viável: o catálogo é nosso, basta guardar
      as marcações dele e aplicá-las por cima das do painel.
- [ ] **Confirmar Favoritos e Continuar.** Os caminhos são palpite; se falharem,
      o app cai na raiz do painel e avisa (não trava).
- [ ] Rádio e TV no **nosso catálogo**, não só no nosso player.

## 📋 Fatos medidos — não re-descubra

| Pergunta | Resposta | Como foi medido |
|---|---|---|
| Qualidade do vídeo | **1080p, H.264+AAC, ~3 Mbps** | cabeçalho MP4 de um título real |
| Tem 4K? Dá para escolher? | **Não** para ambos | servidor entrega **um** arquivo por título |
| User-Agent do VLC | **HTTP 500** | o servidor recusa; o UA é mascarado em `Padroes.USER_AGENT` |
| Pedir o MP4 sem token | **502** | só o painel emite token |
| Token expira? | Não em 34 min | monitor a cada 2 min |
| Conexões simultâneas do plano | **1** | tela "Meu perfil" do painel |
| CORS no servidor de vídeo | **`Allow-Origin: *`** | viabiliza o projeto web |
| Endereços das seções | `livetv`, `movies`, `series` ✅ · `favorites`, `watchhistory` ⚠️ | confirmados pelo dono no navegador |
| Categorias | `index.php?page=moviecat&id=...` | três amostras do dono |

## ⚠️ Armadilhas registradas

1. **O catálogo completo não sai da página principal.** Ela é uma vitrine de 38
   carrosséis; o acervo está dentro das categorias.
2. **Medir `206` não é medir entrega.** Confira bytes recebidos, não só status.
3. **Não automatize login reconstruindo a requisição.** Use o formulário do
   painel pelo WebView.
4. **Nenhum palpite pode levar a tela sem saída.** Ver `tasks/lessons.md`.
5. **Não compile dentro do OneDrive.** As travas de arquivo quebram o build;
   `settings.gradle.kts` já desvia a saída para fora.
