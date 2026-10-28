# Distribuição do APK

Objetivo: **qualquer pessoa baixa, instala e assiste.** Sem Android Studio, sem
Java, sem linha de comando.

## Como sai o APK hoje

A cada push na `main`, o GitHub Actions compila e publica o `.apk` em
**Releases**. O link fica em `https://github.com/Lscigliano/appFilmesAPK/releases`.

Quem for instalar:

1. Baixa o `.apk` pelo navegador do celular.
2. O Android pede permissão para "instalar apps desconhecidos" — é normal para
   app fora da Play Store; basta autorizar o navegador.
3. Instala e abre.

## Assinatura — o ponto que precisa de atenção

Hoje o APK sai assinado com a **chave de debug**, gerada na hora pelo CI.
Ele instala normalmente, mas tem uma limitação real:

> Se a chave mudar entre uma versão e outra, o Android **recusa a atualização**
> por cima ("app não instalado"), e o usuário precisa desinstalar antes.

Para resolver de vez, crie uma chave própria **uma única vez**:

```bash
keytool -genkey -v -keystore filmes.jks -keyalg RSA -keysize 2048 \
        -validity 10000 -alias filmes
```

Guarde o arquivo `filmes.jks` **fora do repositório** (ele está no `.gitignore`).
Depois, para compilar assinado na sua máquina, crie `keystore.properties` na raiz:

```properties
storeFile=filmes.jks
storePassword=SUA_SENHA
keyAlias=filmes
keyPassword=SUA_SENHA
```

O `app/build.gradle.kts` detecta esse arquivo sozinho e passa a assinar o release
com ele. Sem o arquivo, cai na chave de debug — nada quebra.

Para o CI assinar também, guarde o keystore em base64 como secret do repositório
(`Settings → Secrets → Actions`) e acrescente o passo de decodificação no
workflow. Enquanto isso não for feito, cada release pode exigir desinstalar a
versão anterior.

> ⚠️ **Nunca** comite `filmes.jks` nem `keystore.properties`. Quem tem a chave
> pode publicar um app se passando por este.

## Versão

`versionCode` e `versionName` ficam em `app/build.gradle.kts`. Suba o
`versionCode` em **toda** release, senão o Android trata como a mesma versão e não
atualiza.
