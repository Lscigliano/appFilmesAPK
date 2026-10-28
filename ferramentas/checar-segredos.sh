#!/usr/bin/env bash
# Verifica se alguma credencial escapou para o repositório.
#
# IMPORTANTE: este script procura por PADRÕES, nunca por credenciais literais.
# Escrever o usuário ou a senha de verdade aqui dentro seria o próprio vazamento
# que ele existe para evitar — e isso quase aconteceu (ver docs/DIARIO.md).
#
# Uso:  bash ferramentas/checar-segredos.sh
# Sai com código 1 se achar problema.

set -uo pipefail
cd "$(dirname "$0")/.."

falhou=0
aviso() { echo "❌ $*"; falhou=1; }

# Valores que são claramente exemplo/documentação, não credencial de verdade.
EXEMPLOS='USUARIO|SENHA|USER|PASS|SEU_|SUA_|exemplo|example|placeholder|TODO|<[^>]+>|\{[^}]+\}|\$[A-Za-z{]'

ARQS=('*.kt' '*.kts' '*.java' '*.xml' '*.json' '*.md' '*.mjs' '*.js' '*.yml' '*.properties')

# ─── 1. Arquivos que nunca podem ser versionados ───
for arquivo in local.properties credenciais.local.json keystore.properties; do
  if git ls-files --error-unmatch "$arquivo" >/dev/null 2>&1; then
    aviso "O arquivo '$arquivo' está versionado. Rode: git rm --cached $arquivo"
  fi
done

for padrao in '*.jks' '*.keystore'; do
  encontrados=$(git ls-files "$padrao" 2>/dev/null)
  [ -n "$encontrados" ] && aviso "Chave de assinatura versionada: $encontrados"
done

# ─── 2. Credencial embutida numa URL de painel ───
# Ex.: player_api.php?username=12345&password=abcde
achados=$(git grep -nIE 'player_api\.php\?username=[^&"'"'"']+&password=[^&"'"'"'[:space:]]+' \
            -- "${ARQS[@]}" 2>/dev/null | grep -vE "$EXEMPLOS")
if [ -n "$achados" ]; then
  echo "$achados"
  aviso "URL com usuário e senha literais encontrada acima."
fi

# ─── 3. Credencial no caminho de uma URL de stream ───
# Ex.: /movie/USUARIO/SENHA/123.mp4
achados=$(git grep -nIE '/(movie|series|live)/[A-Za-z0-9_.-]{3,}/[A-Za-z0-9_.-]{3,}/[0-9]+\.' \
            -- "${ARQS[@]}" 2>/dev/null | grep -vE "$EXEMPLOS")
if [ -n "$achados" ]; then
  echo "$achados"
  aviso "URL de stream com credenciais no caminho encontrada acima."
fi

# ─── 4. Atribuição literal a campo de credencial ───
# Pega  senha = "algo"  /  password: "algo"  — ignora exemplos e referências a
# outras variáveis.
achados=$(git grep -nIE '(senha|password|pass|usuario|username)[[:space:]]*[=:][[:space:]]*"[A-Za-z0-9@._-]{4,}"' \
            -- '*.kt' '*.kts' '*.java' '*.json' '*.properties' 2>/dev/null | grep -vE "$EXEMPLOS")
if [ -n "$achados" ]; then
  echo "$achados"
  aviso "Possível credencial literal atribuída acima."
fi

if [ "$falhou" -eq 0 ]; then
  echo "✅ Nenhuma credencial encontrada no que está versionado."
fi
exit "$falhou"
