package com.lagradost.cloudstream3.desktop.player

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Validates the player-ui web resources (HTML, CSS, JavaScript) at build time.
 * Prevents syntax regressions, unmatched brackets, or missing IPC exports from breaking the runtime WebView2 player.
 */
class PlayerUiValidationTest {

    private fun loadResource(relativePath: String): String {
        // Try file system first (running from project root or module dir)
        val candidates = listOf(
            File("desktop-app/src/main/resources/player-ui/$relativePath"),
            File("src/main/resources/player-ui/$relativePath"),
            File("../desktop-app/src/main/resources/player-ui/$relativePath")
        )
        for (f in candidates) {
            if (f.exists() && f.isFile) {
                return f.readText(Charsets.UTF_8)
            }
        }
        // Fallback to classpath
        val stream = javaClass.getResourceAsStream("/player-ui/$relativePath")
        assertNotNull(stream, "Resource /player-ui/$relativePath not found on filesystem or classpath")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    @Test
    fun testPlayerHtmlIntegrity() {
        val html = loadResource("player.html")
        assertTrue(html.isNotBlank(), "player.html must not be empty")

        // Mandatory placeholders for runtime injection
        assertTrue(html.contains("/* CSS_INJECT */"), "player.html must contain /* CSS_INJECT */ placeholder")
        assertTrue(html.contains("/* JS_INJECT */"), "player.html must contain /* JS_INJECT */ placeholder")

        // Error telemetry trap in <head>
        assertTrue(html.contains("window.addEventListener('error'"), "player.html must include <head> error telemetry trap")
        assertTrue(html.contains("client_error"), "player.html error trap must post client_error to WebView2 host")

        // Mandatory DOM anchors
        assertTrue(html.contains("id=\"linkProbingOverlay\""), "player.html must declare #linkProbingOverlay")
        assertTrue(html.contains("id=\"linkProbingContent\""), "player.html must declare #linkProbingContent")
        assertTrue(html.contains("id=\"linkProbingList\""), "player.html must declare #linkProbingList")
    }

    @Test
    fun testPlayerCssIntegrity() {
        val css = loadResource("player.css")
        assertTrue(css.isNotBlank(), "player.css must not be empty")
        assertTrue(css.contains("#linkProbingOverlay"), "player.css must style #linkProbingOverlay")
    }

    @Test
    fun testPlayerJsExportsAndContracts() {
        val js = loadResource("player.js")
        assertTrue(js.isNotBlank(), "player.js must not be empty")

        // Critical global exports used by Kotlin and C++
        assertTrue(js.contains("window.__dismissProbingOverlay"), "player.js must export window.__dismissProbingOverlay")
        assertTrue(js.contains("window.playerUpdate"), "player.js must export window.playerUpdate")

        // Critical message types handled by player.js
        assertTrue(js.contains("'dismiss_probing'"), "player.js must handle 'dismiss_probing' message type")
        assertTrue(js.contains("'metadata_update'"), "player.js must handle 'metadata_update' message type")
        assertTrue(js.contains("'state_update'"), "player.js must handle 'state_update' message type")
    }

    @Test
    fun testPlayerJsBracketAndScopeBalancing() {
        val js = loadResource("player.js")
        val errors = validateJavaScriptBrackets(js, "player.js")
        assertTrue(errors.isEmpty(), "player.js bracket balance errors:\n" + errors.joinToString("\n"))
    }

    @Test
    fun testPlayerJsNodeSyntaxCheckIfAvailable() {
        val nodeAvailable = runCatching {
            val process = ProcessBuilder("node", "-v").start()
            process.waitFor() == 0
        }.getOrDefault(false)

        if (nodeAvailable) {
            val candidateFiles = listOf(
                File("desktop-app/src/main/resources/player-ui/player.js"),
                File("src/main/resources/player-ui/player.js"),
                File("../desktop-app/src/main/resources/player-ui/player.js")
            )
            val file = candidateFiles.firstOrNull { it.exists() }
            assertNotNull(file, "player.js not found for node check")
            val pb = ProcessBuilder("node", "--check", file.absolutePath)
            pb.redirectErrorStream(true)
            val proc = pb.start()
            val output = proc.inputStream.bufferedReader().readText()
            val exitCode = proc.waitFor()
            kotlin.test.assertEquals(0, exitCode, "node --check player.js failed:\n$output")
        }
    }

    private data class Bracket(val char: Char, val line: Int, val col: Int)

    private fun validateJavaScriptBrackets(source: String, filename: String): List<String> {
        val errors = mutableListOf<String>()
        val stack = ArrayDeque<Bracket>()

        var line = 1
        var col = 0
        var i = 0
        val len = source.length

        var prevNonWs = ';'
        var prevWord = ""
        var curWord = StringBuilder()

        val regexPreceders = setOf('(', '[', '{', ',', ';', ':', '?', '!', '=', '&', '|', '^', '~', '+', '-', '*', '%', '<', '>')
        val regexKeywords = setOf("return", "typeof", "instanceof", "case", "throw", "void", "delete", "do", "else", "in", "of")

        while (i < len) {
            val c = source[i]
            if (c == '\n') {
                if (curWord.isNotEmpty()) {
                    prevWord = curWord.toString()
                    curWord.clear()
                }
                line++
                col = 0
                i++
                continue
            }
            col++

            // If we are currently inside a template literal string (backtick mode)
            if (stack.lastOrNull()?.char == '`') {
                if (c == '\\') {
                    i += 2
                    col++
                    continue
                }
                if (c == '`') {
                    // Template literal closed
                    stack.removeLast()
                    prevNonWs = '`'
                    prevWord = ""
                    i++
                    continue
                }
                if (c == '$' && i + 1 < len && source[i + 1] == '{') {
                    // Start of interpolation expression inside template literal
                    val interpLine = line
                    val interpCol = col
                    i += 2
                    col++
                    stack.addLast(Bracket('$', interpLine, interpCol))
                    prevNonWs = '{'
                    prevWord = ""
                    continue
                }
                // Any other character inside template literal is literal text
                i++
                continue
            }

            // Normal JavaScript code mode:

            // Handle comments and regex literals starting with '/'
            if (c == '/') {
                if (i + 1 < len && source[i + 1] == '/') {
                    // Line comment
                    i += 2
                    while (i < len && source[i] != '\n') {
                        i++
                    }
                    continue
                }
                if (i + 1 < len && source[i + 1] == '*') {
                    // Block comment
                    i += 2
                    while (i + 1 < len && !(source[i] == '*' && source[i + 1] == '/')) {
                        if (source[i] == '\n') {
                            line++
                            col = 0
                        } else {
                            col++
                        }
                        i++
                    }
                    i += 2
                    col += 2
                    continue
                }

                // Check if this '/' is a regular expression literal
                val isRegex = prevNonWs in regexPreceders || prevWord in regexKeywords
                if (isRegex) {
                    i++ // consume opening '/'
                    col++
                    var inCharClass = false
                    while (i < len) {
                        val rc = source[i]
                        if (rc == '\\') {
                            i += 2
                            col += 2
                            continue
                        }
                        if (rc == '[') {
                            inCharClass = true
                        } else if (rc == ']') {
                            inCharClass = false
                        } else if (rc == '/' && !inCharClass) {
                            i++ // consume closing '/'
                            col++
                            // consume regex flags (g, i, m, s, u, y)
                            while (i < len && source[i].isLetter()) {
                                i++
                                col++
                            }
                            break
                        }
                        if (rc == '\n') {
                            line++
                            col = 0
                            i++
                            break
                        }
                        i++
                        col++
                    }
                    prevNonWs = '/'
                    prevWord = ""
                    continue
                }
            }

            // Skip string literals: single or double quote
            if (c == '\'' || c == '"') {
                val quote = c
                i++
                while (i < len) {
                    val sc = source[i]
                    if (sc == '\\') {
                        i += 2
                        col += 2
                        continue
                    }
                    if (sc == quote) {
                        i++
                        col++
                        break
                    }
                    if (sc == '\n') {
                        line++
                        col = 0
                    } else {
                        col++
                    }
                    i++
                }
                prevNonWs = quote
                prevWord = ""
                continue
            }

            // Start of template literal
            if (c == '`') {
                stack.addLast(Bracket('`', line, col))
                prevNonWs = '`'
                prevWord = ""
                i++
                continue
            }

            // Word token tracking for keyword detection
            if (c.isLetterOrDigit() || c == '_' || c == '$') {
                curWord.append(c)
                prevNonWs = c
            } else {
                if (curWord.isNotEmpty()) {
                    prevWord = curWord.toString()
                    curWord.clear()
                }
                if (!c.isWhitespace()) {
                    prevNonWs = c
                }
            }

            // Bracket matching
            when (c) {
                '{', '(', '[' -> {
                    stack.addLast(Bracket(c, line, col))
                }
                '}' -> {
                    if (stack.isEmpty()) {
                        errors.add("$filename:$line:$col - Unexpected closing brace '}' with no matching opening brace")
                    } else {
                        val top = stack.removeLast()
                        // If top was '$', it matches this '}', returning us to template literal mode
                        if (top.char != '{' && top.char != '$') {
                            errors.add("$filename:$line:$col - Mismatched '}' for opening '${top.char}' at line ${top.line}:${top.col}")
                        }
                    }
                }
                ')' -> {
                    if (stack.isEmpty()) {
                        errors.add("$filename:$line:$col - Unexpected closing parenthesis ')' with no matching opening parenthesis")
                    } else {
                        val top = stack.removeLast()
                        if (top.char != '(') {
                            errors.add("$filename:$line:$col - Mismatched ')' for opening '${top.char}' at line ${top.line}:${top.col}")
                        }
                    }
                }
                ']' -> {
                    if (stack.isEmpty()) {
                        errors.add("$filename:$line:$col - Unexpected closing bracket ']' with no matching opening bracket")
                    } else {
                        val top = stack.removeLast()
                        if (top.char != '[') {
                            errors.add("$filename:$line:$col - Mismatched ']' for opening '${top.char}' at line ${top.line}:${top.col}")
                        }
                    }
                }
            }
            i++
        }

        while (stack.isNotEmpty()) {
            val unclosed = stack.removeLast()
            errors.add("$filename:${unclosed.line}:${unclosed.col} - Unclosed bracket '${unclosed.char}'")
        }

        return errors
    }
}
