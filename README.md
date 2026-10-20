# Sony Remote TV

Controle remoto para a TV Sony Bravia **KDL-42W655A** (e outras Bravia com IP Control), em duas frentes:

| Pasta | O que é |
| --- | --- |
| [`web/`](web) | MVP web (HTML/CSS/JS puros) + `server.py`, uma ponte local que repassa os comandos para a TV por Wi-Fi (IRCC) |
| [`android/`](android) | Camada de controle em Kotlin: infravermelho (SIRC) como padrão e Wi-Fi (IRCC) como fallback |

## Como a TV é controlada

- **Wi-Fi (IRCC):** requisição HTTP SOAP para `http://IP-da-TV/sony/IRCC`, o mesmo princípio do app Video & TV SideView. Funciona com a TV ligada e na mesma rede.
- **Infravermelho (SIRC):** quadro de 12 bits a 40 kHz pelo emissor IR do celular. Liga a TV do standby e não depende de rede.

## Por onde começar

1. Rode o protótipo web (`web/README.md`) e use **Testar conexão** para descobrir como a TV autentica (PSK ou PIN).
2. Com isso definido, evolua o app Android (`android/README.md`).

Nenhum backend na nuvem é necessário: a TV fica na rede local e só um dispositivo dentro dela consegue enviar comandos.
