# COMECE AQUI

Guia para colocar o projeto para compilar numa máquina nova, do zero.
Se alguma coisa aqui não funcionar, **conserte este arquivo** junto com o problema.

---

## 1. O que você precisa instalado

| Ferramenta | Versão | Observação |
|---|---|---|
| JDK | **17** | O AGP 9.4 exige 17 no mínimo. JDK 21 não foi testado aqui. |
| Android SDK | com **platforms;android-37** e **build-tools;36.0.0** | ver passo 3 — o 37 dá trabalho |
| Git | qualquer | |

**Não precisa de Android Studio.** Tudo roda pelo Gradle wrapper (`./gradlew`).

## 2. Onde colocar o repositório

**Fora de pastas sincronizadas** (OneDrive, Dropbox, Google Drive).

O OneDrive mantém arquivos abertos enquanto sincroniza, e o build quebra com
`AccessDeniedException` no meio das tarefas de merge — aconteceu três vezes neste
projeto. Se você **precisar** deixar dentro do OneDrive, o
`settings.gradle.kts` já detecta e manda a saída do build para a pasta temporária
do sistema. Mas o simples é não deixar.

Sugestão: `C:\dev\appFilmesAPK`.

## 3. Instalar a platform android-37 (o passo chato)

O projeto usa `compileSdk = 37`, porque **o Compose atual exige isso** — tentamos
ficar em 36 com uma BOM mais antiga e o build recusa, dizendo em claro
*"update to at least 37"*.

**O problema:** o `sdkmanager` que vem com instalações mais antigas do SDK
**não enxerga** a API 37. Ele responde `Failed to find package 'platforms;android-37'`
porque entende o catálogo até a versão 3, e esse pacote está na 4.

**Tente primeiro o caminho normal:**

```bash
sdkmanager "platforms;android-37" "build-tools;36.0.0"
```

**Se der "Failed to find package", instale à mão:**

```bash
# 1. baixe a platform direto do repositório do Google
curl -L -o p37.zip https://dl.google.com/android/repository/platform-37.0_r02.zip
unzip p37.zip -d /tmp/p37

# 2. mova para dentro do SDK, com o nome android-37
mv /tmp/p37/android-37.0 "$ANDROID_HOME/platforms/android-37"

# 3. o zip vem SEM source.properties, e sem ele o AGP IGNORA a pasta.
#    Crie à mão:
cat > "$ANDROID_HOME/platforms/android-37/source.properties" <<'EOF'
Pkg.Desc=Android SDK Platform 37
Pkg.UserSrc=false
Platform.Version=17
Pkg.Revision=2
AndroidVersion.ApiLevel=37
Layoutlib.Api=15
Layoutlib.Revision=1
Platform.MinToolsRev=22
EOF
```

O passo 3 é o que mais confunde: sem o `source.properties`, o Gradle diz que a
platform não existe, mesmo com a pasta no lugar certo.

> O CI faz exatamente isso em `.github/workflows/build-apk.yml` — se este guia
> ficar desatualizado, copie de lá, que lá é testado a cada push.

## 4. Apontar o SDK (`local.properties`)

Esse arquivo **não vem no git** (contém caminho da sua máquina). Crie na raiz:

```properties
sdk.dir=C\:/Users/SEU_USUARIO/AppData/Local/Android/Sdk
```

**Atenção ao escape.** Em arquivo `.properties`, a barra invertida é caractere de
escape — `sdk.dir=C:\Users\...` chega corrompido no AGP e o build falha com um
`IOException` enterrado em 190 linhas de stack trace. Use barras normais e escape
só os dois-pontos, como no exemplo. No Linux/macOS é só o caminho direto.

## 5. Compilar

```bash
./gradlew :app:assembleDebug
```

O APK sai em `app/build/outputs/apk/debug/` — ou, se você estiver dentro do
OneDrive, na pasta temporária que o build anuncia na primeira linha.

São dois APKs, um por arquitetura de processador (`arm64-v8a` e `armeabi-v7a`).
Para celular atual, use o **arm64-v8a**.

## 6. Antes de commitar

```bash
bash ferramentas/checar-segredos.sh
```

O repositório é **público**. Esse script procura credenciais por padrão (URL com
usuário e senha, caminho de stream, atribuição literal a campo de senha) e
arquivos que nunca podem ser versionados.

## 7. Para testar de verdade

O app precisa de uma conta do painel. **As credenciais não estão no repositório,
e não devem estar** — peça ao dono. Elas são digitadas na tela de login do app e
ficam só no aparelho.

Para testar no celular: ative a **Depuração USB**, ligue o cabo e

```bash
adb install -r caminho/do/app-arm64-v8a-debug.apk
```

---

## Depois de compilar, leia nesta ordem

1. [`docs/PROTOCOLO.md`](PROTOCOLO.md) — as regras do projeto
2. [`docs/DIARIO.md`](DIARIO.md) — as entradas do topo; é onde está o que o código
   não conta
3. [`docs/ARQUITETURA.md`](ARQUITETURA.md) — como as peças se encaixam e por quê
4. [`tasks/TASK.md`](../tasks/TASK.md) — estado atual, fatos já medidos e
   armadilhas conhecidas
5. [`tasks/lessons.md`](../tasks/lessons.md) — erros já cometidos, para não repetir
