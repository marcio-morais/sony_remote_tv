package app.sonyremote.tv

/**
 * Remote keys.
 *
 * [irccCode] is the base64 payload of the Wi-Fi (IRCC) protocol.
 * [sircCommand] is the 7-bit command of the 12-bit Sony SIRC infrared protocol
 * (device address 1 = TV). Keys without one use extended IR codes, so they go over Wi-Fi only.
 *
 * For device-1 keys the SIRC command is the byte right before the "Aw==" suffix of the
 * IRCC payload (for example POWER ends in 0x15 = 21).
 */
enum class SonyKey(val irccCode: String, val sircCommand: Int? = null) {
    POWER("AAAAAQAAAAEAAAAVAw==", 21),
    VOLUME_UP("AAAAAQAAAAEAAAASAw==", 18),
    VOLUME_DOWN("AAAAAQAAAAEAAAATAw==", 19),
    MUTE("AAAAAQAAAAEAAAAUAw==", 20),
    CHANNEL_UP("AAAAAQAAAAEAAAAQAw==", 16),
    CHANNEL_DOWN("AAAAAQAAAAEAAAARAw==", 17),
    INPUT("AAAAAQAAAAEAAAAlAw==", 37),
    UP("AAAAAQAAAAEAAAB0Aw==", 116),
    DOWN("AAAAAQAAAAEAAAB1Aw==", 117),
    LEFT("AAAAAQAAAAEAAAA0Aw==", 52),
    RIGHT("AAAAAQAAAAEAAAAzAw==", 51),
    CONFIRM("AAAAAQAAAAEAAABlAw==", 101),
    HOME("AAAAAQAAAAEAAABgAw==", 96),
    INFO("AAAAAQAAAAEAAAA6Aw==", 58),
    EXIT("AAAAAQAAAAEAAABjAw==", 99),
    JUMP("AAAAAQAAAAEAAAA7Aw==", 59),

    // Digits: SIRC command is the digit minus one (0 is 9)
    NUM1("AAAAAQAAAAEAAAAAAw==", 0),
    NUM2("AAAAAQAAAAEAAAABAw==", 1),
    NUM3("AAAAAQAAAAEAAAACAw==", 2),
    NUM4("AAAAAQAAAAEAAAADAw==", 3),
    NUM5("AAAAAQAAAAEAAAAEAw==", 4),
    NUM6("AAAAAQAAAAEAAAAFAw==", 5),
    NUM7("AAAAAQAAAAEAAAAGAw==", 6),
    NUM8("AAAAAQAAAAEAAAAHAw==", 7),
    NUM9("AAAAAQAAAAEAAAAIAw==", 8),
    NUM0("AAAAAQAAAAEAAAAJAw==", 9),

    // Extended codes: Wi-Fi only
    BACK("AAAAAgAAAJcAAAAjAw=="),
    OPTIONS("AAAAAgAAAJcAAAA2Aw=="),
    SEN("AAAAAgAAABoAAAB9Aw=="),
    RED("AAAAAgAAAJcAAAAlAw=="),
    GREEN("AAAAAgAAAJcAAAAmAw=="),
    YELLOW("AAAAAgAAAJcAAAAnAw=="),
    BLUE("AAAAAgAAAJcAAAAkAw=="),
    PLAY("AAAAAgAAAJcAAAAaAw=="),
    PAUSE("AAAAAgAAAJcAAAAZAw=="),
    STOP("AAAAAgAAAJcAAAAYAw=="),
    REWIND("AAAAAgAAAJcAAAAbAw=="),
    FORWARD("AAAAAgAAAJcAAAAcAw=="),
}
