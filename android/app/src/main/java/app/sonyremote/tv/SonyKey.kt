package app.sonyremote.tv

/**
 * Remote keys.
 *
 * [irccCode] is the base64 payload of the Wi-Fi (IRCC) protocol.
 * [sircCommand] is the 7-bit command of the 12-bit Sony SIRC infrared protocol
 * (device address 1 = TV). Keys without a SIRC command are Wi-Fi only.
 *
 * The SIRC values for navigation keys are derived from the IRCC payload and
 * still need to be validated on the real KDL-42W655A.
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
    BACK("AAAAAgAAAJcAAAAjAw=="),
}
