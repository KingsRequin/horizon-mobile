package fr.kingsrequin.horizon.link.pad

import fr.kingsrequin.horizon.protocol.ToPc

/** Modificateur tenu pour la touche suivante ; `wire` : son nom dans le protocole. */
enum class Mod(val wire: String) { CTRL("ctrl"), ALT("alt"), SHIFT("shift"), WIN("win") }

/** Ce que le clavier du téléphone et la rangée de touches envoient au PC. */
object Typing {
    /** Caractères au plus d'un message de texte. */
    const val MAX_TEXT = 256

    /** Une touche nommée avec ses modificateurs, toujours dans le même ordre. */
    fun key(name: String, mods: Set<Mod>): ToPc.InputKey = ToPc.InputKey(name, Mod.entries.filter { it in mods }.map { it.wire })

    /**
     * Texte tapé au clavier du téléphone. Avec un modificateur tenu, une lettre ou un chiffre
     * devient un raccourci (Ctrl + C) ; sinon le texte part en Unicode, retour à la ligne et
     * tabulation en touches, en messages de 256 caractères au plus.
     */
    fun typed(text: String, mods: Set<Mod>): List<ToPc> {
        if (mods.isNotEmpty() && text.length == 1) {
            val c = text[0].lowercaseChar()
            if (c in 'a'..'z' || c in '0'..'9') return listOf(key(c.toString(), mods))
        }
        val out = mutableListOf<ToPc>()
        val run = StringBuilder()
        fun flushRun() {
            chunks(run.toString()).forEach { out += ToPc.InputText(it) }
            run.clear()
        }
        for (c in text) {
            when (c) {
                '\n' -> {
                    flushRun()
                    out += ToPc.InputKey("enter")
                }
                '\t' -> {
                    flushRun()
                    out += ToPc.InputKey("tab")
                }
                else -> run.append(c)
            }
        }
        flushRun()
        return out
    }

    /** Morceaux de 256 caractères au plus, sans couper une paire de substitution (émoji). */
    private fun chunks(text: String): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        while (i < text.length) {
            var end = minOf(i + MAX_TEXT, text.length)
            if (end < text.length && text[end - 1].isHighSurrogate()) end--
            out += text.substring(i, end)
            i = end
        }
        return out
    }
}
