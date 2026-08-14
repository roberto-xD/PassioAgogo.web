package ui.theme

/**
 * Emojis que la fuente empaquetada sabe dibujar.
 *
 * **Generado**: esta lista y `emoji_passion.ttf` salen del mismo recorte de Noto Color
 * Emoji. Si cambia una, hay que regenerar la otra — las instrucciones están en
 * `tools/emoji/README.md`.
 *
 * Existe porque los emojis los escribes tú en Supabase (`attribute_presets.emoji`,
 * `guides.emoji`) y la fuente solo lleva un subconjunto: sin esta comprobación, uno que
 * no estuviera incluido se pintaría como un cuadro vacío. Consultándola, simplemente no
 * se dibuja y queda el rótulo, que es quien lleva la información de todas formas.
 */
private val EMOJIS_SOPORTADOS: Set<String> = setOf(
    "\uD83E\uDD73", // 🥳  U+1F973
    "\uD83E\uDD29", // 🤩  U+1F929
    "\uD83D\uDE08", // 😈  U+1F608
    "\uD83D\uDC7D", // 👽  U+1F47D
    "\uD83D\uDC45", // 👅  U+1F445
    "\uD83D\uDC44", // 👄  U+1F444
    "\uD83D\uDC8B", // 💋  U+1F48B
    "\uD83D\uDC42", // 👂  U+1F442
    "\uD83D\uDC43", // 👃  U+1F443
    "\uD83D\uDC40", // 👀  U+1F440
    "\uD83D\uDC60", // 👠  U+1F460
    "\uD83D\uDC57", // 👗  U+1F457
    "\uD83D\uDD76", // 🕶  U+1F576
    "\u2728", // ✨  U+2728
    "\u2600\uFE0F", // ☀️  U+2600 U+FE0F
    "\uD83D\uDD25", // 🔥  U+1F525
    "\uD83D\uDCA6", // 💦  U+1F4A6
    "\u26C4\uFE0F", // ⛄️  U+26C4 U+FE0F
    "\uD83C\uDF51", // 🍑  U+1F351
    "\uD83E\uDD6D", // 🥭  U+1F96D
    "\uD83C\uDF4C", // 🍌  U+1F34C
    "\uD83C\uDF4B\u200D\uD83D\uDFE9", // 🍋‍🟩  U+1F34B U+200D U+1F7E9
    "\uD83C\uDF4E", // 🍎  U+1F34E
    "\uD83C\uDF46", // 🍆  U+1F346
    "\uD83E\uDD51", // 🥑  U+1F951
    "\uD83C\uDF36", // 🌶  U+1F336
    "\uD83C\uDF3D", // 🌽  U+1F33D
    "\uD83C\uDF70", // 🍰  U+1F370
    "\uD83E\uDD5C", // 🥜  U+1F95C
    "\u2615\uFE0F", // ☕️  U+2615 U+FE0F
    "\uD83E\uDD42", // 🥂  U+1F942
    "\uD83C\uDF77", // 🍷  U+1F377
    "\uD83C\uDF7E", // 🍾  U+1F37E
    "\uD83C\uDF7A", // 🍺  U+1F37A
    "\uD83C\uDFB2", // 🎲  U+1F3B2
    "\u23F0", // ⏰  U+23F0
    "\u23F3", // ⏳  U+23F3
    "\uD83D\uDCB0", // 💰  U+1F4B0
    "\uD83E\uDDF2", // 🧲  U+1F9F2
    "\uD83D\uDEC1", // 🛁  U+1F6C1
    "\uD83D\uDD10", // 🔐  U+1F510
    "\uD83D\uDD0E", // 🔎  U+1F50E
    "\uD83D\uDEB0", // 🚰  U+1F6B0
    "\uD83D\uDCDD", // 📝  U+1F4DD
    "\uD83C\uDFB6", // 🎶  U+1F3B6
    "\u00AE", // ®  U+00AE
    "\uD83D\uDD07", // 🔇  U+1F507
    "\uD83D\uDD0A", // 🔊  U+1F50A
    "\uD83D\uDD08", // 🔈  U+1F508
    "\uD83C\uDFF3\u200D\uD83C\uDF08", // 🏳‍🌈  U+1F3F3 U+200D U+1F308
    "\uD83C\uDFF3\u200D\u26A7", // 🏳‍⚧  U+1F3F3 U+200D U+26A7
    "\uD83C\uDDFB\uD83C\uDDEA", // 🇻🇪  U+1F1FB U+1F1EA
    "\uD83C\uDDF2\uD83C\uDDFD", // 🇲🇽  U+1F1F2 U+1F1FD
    "\uD83E\uDDF4", // 🧴  U+1F9F4
    "\uD83D\uDD37", // 🔷  U+1F537
    "\u2699\uFE0F", // ⚙️  U+2699 U+FE0F
    "\u26AB", // ⚫  U+26AB
    "\uD83E\uDE77", // 🩷  U+1FA77
    "\uD83E\uDD0F", // 🤏  U+1F90F
    "\uD83D\uDC4C", // 👌  U+1F44C
    "\uD83D\uDD90\uFE0F", // 🖐️  U+1F590 U+FE0F
    "\uD83D\uDD0B", // 🔋  U+1F50B
    "\uD83D\uDCA7", // 💧  U+1F4A7
    "\uD83C\uDF49", // 🍉  U+1F349
    "\uD83E\uDED2", // 🫒  U+1FAD2
    "\uD83C\uDF6B", // 🍫  U+1F36B
    "\uD83C\uDF53", // 🍓  U+1F353
    "\uD83D\uDCF1", // 📱  U+1F4F1
    "\uD83C\uDF88", // 🎈  U+1F388
    "\uD83D\uDD79\uFE0F", // 🕹️  U+1F579 U+FE0F
    "\uD83E\uDEAB", // 🪫  U+1FAAB
    "\uD83D\uDC8E", // 💎  U+1F48E
    "\uD83C\uDF52", // 🍒  U+1F352
)

/** `true` si [emoji] se puede pintar con la fuente empaquetada. */
fun emojiSoportado(emoji: String): Boolean {
    val limpio = emoji.trim()
    if (limpio.isEmpty()) return false
    // Se prueba tal cual y con el selector de variación: en la base puede estar escrito
    // de las dos formas y ambas deben valer.
    return limpio in EMOJIS_SOPORTADOS ||
        limpio.removeSuffix("\uFE0F") in EMOJIS_SOPORTADOS ||
        (limpio + "\uFE0F") in EMOJIS_SOPORTADOS
}
