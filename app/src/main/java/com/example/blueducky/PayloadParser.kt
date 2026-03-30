package com.example.blueducky

/**
 * Parses DuckyScript text into a list of HID report byte arrays.
 *
 * Each HID keyboard report is 8 bytes:
 *   [0] Modifier byte
 *   [1] Reserved (always 0x00)
 *   [2] Keycode 1
 *   [3..7] Keycodes 2-6 (unused here, always 0x00)
 *
 * A "key press" is represented as two reports:
 *   1. The key-down report (modifier + keycode set)
 *   2. The key-up report (all zeros)
 */
object PayloadParser {

    // --- Modifier Masks ---
    private const val MOD_NONE: Byte    = 0x00
    private const val MOD_LCTRL: Byte   = 0x01
    private const val MOD_LSHIFT: Byte  = 0x02
    private const val MOD_LALT: Byte    = 0x04
    private const val MOD_LGUI: Byte    = 0x08  // Windows/Command key
    private const val MOD_RCTRL: Byte   = 0x10
    private const val MOD_RSHIFT: Byte  = 0x20
    private const val MOD_RALT: Byte    = 0x40
    private const val MOD_RGUI: Byte    = 0x80.toByte()

    // --- Special / Function Keycodes ---
    private val SPECIAL_KEYS: Map<String, Byte> = mapOf(
        "ENTER"          to 0x28.toByte(),
        "RETURN"         to 0x28.toByte(),
        "ESCAPE"         to 0x29.toByte(),
        "ESC"            to 0x29.toByte(),
        "BACKSPACE"      to 0x2A.toByte(),
        "TAB"            to 0x2B.toByte(),
        "SPACE"          to 0x2C.toByte(),
        "CAPSLOCK"       to 0x39.toByte(),
        "F1"             to 0x3A.toByte(),
        "F2"             to 0x3B.toByte(),
        "F3"             to 0x3C.toByte(),
        "F4"             to 0x3D.toByte(),
        "F5"             to 0x3E.toByte(),
        "F6"             to 0x3F.toByte(),
        "F7"             to 0x40.toByte(),
        "F8"             to 0x41.toByte(),
        "F9"             to 0x42.toByte(),
        "F10"            to 0x43.toByte(),
        "F11"            to 0x44.toByte(),
        "F12"            to 0x45.toByte(),
        "PRINTSCREEN"    to 0x46.toByte(),
        "SCROLLLOCK"     to 0x47.toByte(),
        "PAUSE"          to 0x48.toByte(),
        "INSERT"         to 0x49.toByte(),
        "HOME"           to 0x4A.toByte(),
        "PAGEUP"         to 0x4B.toByte(),
        "DELETE"         to 0x4C.toByte(),
        "END"            to 0x4D.toByte(),
        "PAGEDOWN"       to 0x4E.toByte(),
        "RIGHT"          to 0x4F.toByte(),
        "LEFT"           to 0x50.toByte(),
        "DOWN"           to 0x51.toByte(),
        "UP"             to 0x52.toByte(),
        "NUMLOCK"        to 0x53.toByte(),
        "APP"            to 0x65.toByte(),  // Application / Menu key
        "MENU"           to 0x65.toByte(),
    )

    // --- Modifier Keys (used with GUI, CTRL, SHIFT, ALT combos) ---
    private val MODIFIER_KEYS: Map<String, Byte> = mapOf(
        "CTRL"    to MOD_LCTRL,
        "CONTROL" to MOD_LCTRL,
        "SHIFT"   to MOD_LSHIFT,
        "ALT"     to MOD_LALT,
        "GUI"     to MOD_LGUI,
        "WINDOWS" to MOD_LGUI,
        "META"    to MOD_LGUI,
        "COMMAND" to MOD_LGUI,
        "RCTRL"   to MOD_RCTRL,
        "RSHIFT"  to MOD_RSHIFT,
        "RALT"    to MOD_RALT,
        "RGUI"    to MOD_RGUI,
    )

    // SWEDISH keyboard layout: char -> (modifier, keycode)
    // modifier = 0x02 means SHIFT required, 0x40 means AltGr required
    private val CHAR_MAP: Map<Char, Pair<Byte, Byte>> = buildMap {
        // Lowercase letters a-z (no modifier, keycodes 0x04-0x1D)
        for (c in 'a'..'z') {
            put(c, Pair(MOD_NONE, (0x04 + (c - 'a')).toByte()))
        }
        // Uppercase letters A-Z (SHIFT + lowercase keycode)
        for (c in 'A'..'Z') {
            put(c, Pair(MOD_LSHIFT, (0x04 + (c - 'A')).toByte()))
        }
        
        // Swedish specific letters
        put('å', Pair(MOD_NONE, 0x2F.toByte())); put('Å', Pair(MOD_LSHIFT, 0x2F.toByte()))
        put('ä', Pair(MOD_NONE, 0x34.toByte())); put('Ä', Pair(MOD_LSHIFT, 0x34.toByte()))
        put('ö', Pair(MOD_NONE, 0x33.toByte())); put('Ö', Pair(MOD_LSHIFT, 0x33.toByte()))

        // Digits 1-9 (keycodes 0x1E-0x26), 0 = 0x27
        // Swedish shift layout: !"#¤%&/()=
        put('1', Pair(MOD_NONE, 0x1E.toByte())); put('!', Pair(MOD_LSHIFT, 0x1E.toByte()))
        put('2', Pair(MOD_NONE, 0x1F.toByte())); put('"', Pair(MOD_LSHIFT, 0x1F.toByte())); put('@', Pair(MOD_RALT, 0x1F.toByte()))
        put('3', Pair(MOD_NONE, 0x20.toByte())); put('#', Pair(MOD_LSHIFT, 0x20.toByte())); put('£', Pair(MOD_RALT, 0x20.toByte()))
        put('4', Pair(MOD_NONE, 0x21.toByte())); put('¤', Pair(MOD_LSHIFT, 0x21.toByte())); put('$', Pair(MOD_RALT, 0x21.toByte()))
        put('5', Pair(MOD_NONE, 0x22.toByte())); put('%', Pair(MOD_LSHIFT, 0x22.toByte())); put('€', Pair(MOD_RALT, 0x22.toByte()))
        put('6', Pair(MOD_NONE, 0x23.toByte())); put('&', Pair(MOD_LSHIFT, 0x23.toByte()))
        put('7', Pair(MOD_NONE, 0x24.toByte())); put('/', Pair(MOD_LSHIFT, 0x24.toByte())); put('{', Pair(MOD_RALT, 0x24.toByte()))
        put('8', Pair(MOD_NONE, 0x25.toByte())); put('(', Pair(MOD_LSHIFT, 0x25.toByte())); put('[', Pair(MOD_RALT, 0x25.toByte()))
        put('9', Pair(MOD_NONE, 0x26.toByte())); put(')', Pair(MOD_LSHIFT, 0x26.toByte())); put(']', Pair(MOD_RALT, 0x26.toByte()))
        put('0', Pair(MOD_NONE, 0x27.toByte())); put('=', Pair(MOD_LSHIFT, 0x27.toByte())); put('}', Pair(MOD_RALT, 0x27.toByte()))

        // Punctuation & other symbols
        put(' ',  Pair(MOD_NONE,   0x2C.toByte()))
        put('\n', Pair(MOD_NONE,   0x28.toByte()))  // Enter
        put('\t', Pair(MOD_NONE,   0x2B.toByte()))  // Tab

        // Key right of 0: + ? \
        put('+',  Pair(MOD_NONE,   0x2D.toByte())); put('?', Pair(MOD_LSHIFT, 0x2D.toByte())); put('\\', Pair(MOD_RALT, 0x2D.toByte()))
        
        // Key right of +: ´ `
        put('´',  Pair(MOD_NONE,   0x2E.toByte())); put('`', Pair(MOD_LSHIFT, 0x2E.toByte()))
        
        // Key right of å: ¨ ^ ~
        put('¨',  Pair(MOD_NONE,   0x30.toByte())); put('^', Pair(MOD_LSHIFT, 0x30.toByte())); put('~', Pair(MOD_RALT, 0x30.toByte()))

        // Key right of ä: ' *
        put('\'', Pair(MOD_NONE,   0x31.toByte())); put('*', Pair(MOD_LSHIFT, 0x31.toByte()))
        
        // Key right of M: , ;
        put(',',  Pair(MOD_NONE,   0x36.toByte())); put(';', Pair(MOD_LSHIFT, 0x36.toByte()))
        // Key right of ,: . :
        put('.',  Pair(MOD_NONE,   0x37.toByte())); put(':', Pair(MOD_LSHIFT, 0x37.toByte()))
        // Key right of .: - _
        put('-',  Pair(MOD_NONE,   0x38.toByte())); put('_', Pair(MOD_LSHIFT, 0x38.toByte()))

        // Key left of 1: § ½
        put('§',  Pair(MOD_NONE,   0x35.toByte())); put('½', Pair(MOD_LSHIFT, 0x35.toByte()))
        
        // Key left of Z: < > |
        put('<',  Pair(MOD_NONE,   0x64.toByte())); put('>', Pair(MOD_LSHIFT, 0x64.toByte())); put('|', Pair(MOD_RALT, 0x64.toByte()))
    }

    /** Represents a single action derived from a parsed line */
    sealed class Action {
        data class KeyPress(val modifier: Byte, val keycode: Byte) : Action()
        data class Delay(val milliseconds: Long) : Action()
        data class DefaultDelay(val milliseconds: Long) : Action()
    }

    /**
     * Parse a full DuckyScript string into a flat ordered list of [Action]s.
     * Callers can then iterate and execute each action.
     */
    fun parse(script: String): List<Action> {
        val actions = mutableListOf<Action>()
        var defaultDelay = 0L

        val lines = script.lines()
        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("REM")) continue  // skip blanks & comments

            val parts = line.split(" ", limit = 2)
            val command = parts[0].uppercase()
            val arg = if (parts.size > 1) parts[1] else ""

            when (command) {
                "DEFAULTDELAY", "DEFAULT_DELAY" -> {
                    defaultDelay = arg.toLongOrNull() ?: 0L
                }
                "DELAY" -> {
                    val ms = arg.toLongOrNull() ?: 0L
                    actions.add(Action.Delay(ms))
                }
                "STRING" -> {
                    // Each character becomes a key press
                    for (ch in arg) {
                        val mapping = CHAR_MAP[ch]
                        if (mapping != null) {
                            actions.add(Action.KeyPress(mapping.first, mapping.second))
                        }
                        // Inject default delay between each char if set
                        if (defaultDelay > 0) actions.add(Action.Delay(defaultDelay))
                    }
                }
                "STRINGLN" -> {
                    // Same as STRING but appends ENTER
                    for (ch in arg) {
                        val mapping = CHAR_MAP[ch]
                        if (mapping != null) {
                            actions.add(Action.KeyPress(mapping.first, mapping.second))
                        }
                        if (defaultDelay > 0) actions.add(Action.Delay(defaultDelay))
                    }
                    actions.add(Action.KeyPress(MOD_NONE, SPECIAL_KEYS["ENTER"]!!))
                }
                else -> {
                    // Could be a combo like "GUI r", "CTRL ALT DELETE", or a single special key
                    actions.addAll(parseKeyCombo(line))
                }
            }

            // Apply default delay after every non-DELAY/non-STRING line
            if (defaultDelay > 0 && command !in listOf("DELAY", "DEFAULTDELAY", "DEFAULT_DELAY", "STRING", "STRINGLN")) {
                actions.add(Action.Delay(defaultDelay))
            }
        }

        return actions
    }

    /**
     * Parse a key combination line such as:
     *   "CTRL ALT DELETE"
     *   "GUI r"
     *   "SHIFT F10"
     *   "ENTER"
     */
    private fun parseKeyCombo(line: String): List<Action> {
        val tokens = line.trim().split(" ").map { it.uppercase() }
        var modifier: Byte = MOD_NONE
        var keycode: Byte = 0x00

        for (token in tokens) {
            when {
                MODIFIER_KEYS.containsKey(token) -> {
                    modifier = (modifier.toInt() or MODIFIER_KEYS[token]!!.toInt()).toByte()
                }
                SPECIAL_KEYS.containsKey(token) -> {
                    keycode = SPECIAL_KEYS[token]!!
                }
                token.length == 1 -> {
                    // Single character used as a key (e.g. "GUI r")
                    val mapping = CHAR_MAP[token[0].lowercaseChar()]
                    if (mapping != null) keycode = mapping.second
                }
            }
        }

        return if (keycode != 0x00.toByte() || modifier != MOD_NONE) {
            listOf(Action.KeyPress(modifier, keycode))
        } else {
            emptyList()
        }
    }

    /**
     * Convert a KeyPress action into two raw 8-byte HID reports:
     *   report[0] = key-down (modifier + keycode)
     *   report[1] = key-up   (all zeros)
     */
    fun toHidReports(keyPress: Action.KeyPress): List<ByteArray> {
        val keyDown = ByteArray(8).also {
            it[0] = keyPress.modifier
            it[1] = 0x00
            it[2] = keyPress.keycode
        }
        val keyUp = ByteArray(8)  // all zeros = key release
        return listOf(keyDown, keyUp)
    }

    /**
     * Helper to wrap raw text into valid DuckyScript.
     * If the text already looks like DuckyScript, it returns it unmodified.
     */
    fun rawTextToDuckyScript(raw: String): String {
        val duckyCommands = listOf("STRING ", "STRINGLN ", "DELAY ", "ENTER", "GUI ", "CTRL ", "ALT ", "SHIFT ", "REM ", "DEFAULTDELAY ")
        val isLikelyScript = raw.lines().any { line -> 
            val upper = line.trimStart().uppercase()
            duckyCommands.any { upper.startsWith(it) || upper == it.trim() }
        }
        
        // If it already contains DuckyScript commands, don't double convert it!
        if (isLikelyScript) return raw 

        val sb = java.lang.StringBuilder()
        val lines = raw.lines()
        for ((i, line) in lines.withIndex()) {
            val processedLine = line.replace("\t", "    ") // Optional: expand tabs to spaces if preferred, but we support \t in STRING now. Let's keep \t.
            if (line.isNotEmpty()) {
                sb.append("STRING ").append(line).append("\n")
            }
            if (i < lines.size - 1) {
                sb.append("ENTER\n")
            }
        }
        return sb.toString().trimEnd()
    }
}
