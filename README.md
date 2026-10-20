# Sony Remote (Android)

App Android em Kotlin e Jetpack Compose para controlar a TV Sony Bravia **KDL-42W655A**. Funciona sozinho no celular, sem servidor: envia os comandos por Wi-Fi (IRCC) ou, se o celular tiver emissor infravermelho, por IR (SIRC).

O protótipo web que serviu de referência de layout fica em [sony_remote_tv_web](https://github.com/marcio-morais/sony_remote_tv_web).

## Instalar no celular

Cada push na `main` gera um APK pelo GitHub Actions. No celular, abra o link abaixo, baixe e instale (o Android vai pedir para permitir a instalação de apps desta fonte):

```
https://github.com/marcio-morais/sony_remote_tv/releases/latest/download/SonyRemote.apk
```

Se o repositório for renomeado, o GitHub redireciona o link antigo, mas vale atualizar o endereço acima.

Também dá para baixar o APK em **Actions > Build APK > SonyRemote-apk** na página do repositório.

## O que o app faz

Duas abas, que também mudam com um arrasto de dedo: **Controle** (Entrada, Home, SEN, Liga, setas e OK, Mudo, volume, canal, Opções, Voltar, Sair e Jump) e **Números** (0 a 9, teclas coloridas e mídia). Segurar setas, volume ou canal repete o comando. A engrenagem abre a conexão: busca automática da TV na rede (SSDP e varredura da faixa local), IP, chave PSK, o modo de envio (Automático, Infravermelho ou Wi-Fi) e o histórico dos últimos comandos. O nome da TV aparece no topo.

No modo Automático o app usa IR quando o celular tem emissor e a tecla tem código SIRC, e Wi-Fi no resto.

## Configurar a TV

Configurações > Rede > Controle IP, com autenticação "Normal e Pre-Shared Key" e a mesma chave informada no app (padrão `0000`). Os nomes dos menus variam entre gerações.

## Estrutura

```
app/src/main/java/app/sonyremote/tv/
  SonyKey.kt            teclas com código IRCC (Wi-Fi) e SIRC (IR)
  SonyTvClient.kt       envio IRCC por HTTP (OkHttp)
  SonyIrRemote.kt       quadro SIRC de 12 bits no emissor IR
  TvRemote.kt           escolhe IR ou Wi-Fi conforme o modo
  TvDiscovery.kt        busca da TV (SSDP + varredura /24)
  WifiNetworkHelper.kt  fixa o tráfego na rede Wi-Fi (mesmo com dados móveis ligados)
  Settings.kt           IP, nome, PSK e modo salvos no DataStore
  ui/                   ViewModel e telas em Compose
.github/workflows/build.yml   gera o APK a cada push
```

## Compilar localmente

Abra a pasta no Android Studio, ou rode `./gradlew assembleDebug` (JDK 17). O APK sai em `app/build/outputs/apk/debug/`.

## Pendências

- Os códigos de Guia, SEN, Opções, Sair, Jump, números, cores e mídia ainda precisam ser validados na TV real.
- Os comandos IR das teclas de navegação foram derivados do código IRCC e não foram testados.
- O APK é assinado com a chave de debug; para publicar na Play Store seria preciso uma chave própria.
