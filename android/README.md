# SonyRemote Android

Controle remoto para a TV Sony Bravia **KDL-42W655A** em Kotlin. Usa infravermelho (SIRC) como padrão e Wi-Fi (IRCC) como fallback.

Referência de layout e protótipo funcional: pasta [web](../web).

## Estado atual

Este repositório contém a **camada de controle** (ainda sem tela). Os arquivos estão em `app/src/main/java/app/sonyremote/tv/`:

| Arquivo | Função |
| --- | --- |
| `SonyKey.kt` | Teclas, com o código IRCC (Wi-Fi) e o comando SIRC (IR) |
| `SonyIrRemote.kt` | Gera e transmite o quadro SIRC de 12 bits pelo emissor IR do celular |
| `SonyTvClient.kt` | Envia comandos IRCC por HTTP (OkHttp) |
| `TvTransport.kt` | Interface comum, `IrTransport`, `WifiTransport` e `TvRemote` (IR primeiro, Wi-Fi como fallback) |

Ainda não há `build.gradle`, `MainActivity` nem tela em Compose: o próximo passo é criar o projeto no Android Studio (pacote `app.sonyremote`) e copiar estes fontes.

## Configuração do projeto

`app/build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
```

`AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.TRANSMIT_IR" />
<uses-feature android:name="android.hardware.consumerir" android:required="false" />

<application android:usesCleartextTraffic="true" ...>
```

## Uso

```kotlin
val remote = TvRemote(
    primary = IrTransport(SonyIrRemote(context)),
    fallback = WifiTransport(SonyTvClient(tvIp = "192.168.0.50", preSharedKey = "0000")),
)
scope.launch { remote.send(SonyKey.VOLUME_UP) }
```

## Observações

- IR exige um celular com emissor infravermelho (`ConsumerIrManager`). Sem ele, o fallback para Wi-Fi assume.
- IR não retorna confirmação de entrega, então o fallback só dispara quando o celular não tem emissor ou a tecla não tem comando SIRC.
- Os comandos SIRC das teclas de navegação foram derivados do payload IRCC e precisam ser validados na TV real.
- A TV de 2013 pode exigir registro com PIN em vez de PSK. Use o "Testar conexão" do projeto web para descobrir.
