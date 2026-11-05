package com.vaultos.app.keyboard

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.textservice.SentenceSuggestionsInfo
import android.view.textservice.SpellCheckerSession
import android.view.textservice.SuggestionsInfo
import android.view.textservice.TextInfo
import android.view.textservice.TextServicesManager
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.vaultos.app.ClipboardHistoryActivity
import com.vaultos.app.MainActivity
import com.vaultos.app.data.ClipboardHistoryEntry
import com.vaultos.app.storage.ClipboardHistoryRepository
import com.vaultos.app.storage.VaultRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import java.util.Locale

/**
 * Local-first VaultOS keyboard. The keyboard is intentionally independent from the
 * phone's current locale and keeps Gboard available through Android's IME picker.
 */
class VaultKeyboardService : InputMethodService(), SpellCheckerSession.SpellCheckerSessionListener {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var clipboard: ClipboardManager? = null
    private var spellChecker: SpellCheckerSession? = null
    private var shifted = false
    private var capsLock = false
    private var symbols = false
    private var clipboardMode = false
    private var clipboardQuery = ""
    private var clipboardList: LinearLayout? = null
    private var clipboardSearchView: TextView? = null
    private var suggestionRow: LinearLayout? = null
    private var clipboardSearchRevision = 0
    private var typedWord = StringBuilder()
    private var lastCommittedWord = ""
    private var inputGeneration = 0
    private var spellCheckGeneration = 0
    private var currentSuggestions: List<String> = emptyList()

    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        if (getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_AUTO_CAPTURE, true)) {
            captureClipboard(showToast = false)
        }
    }

    override fun onCreate() {
        super.onCreate()
        clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard?.addPrimaryClipChangedListener(clipboardListener)
    }

    override fun onDestroy() {
        clipboard?.removePrimaryClipChangedListener(clipboardListener)
        spellChecker?.close()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onCreateInputView(): View = buildKeyboard()

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        inputGeneration++
        shifted = false
        capsLock = false
        symbols = false
        typedWord.clear()
        lastCommittedWord = ""
        currentSuggestions = emptyList()
        spellCheckGeneration++
        spellChecker?.close()
        spellChecker = null
        if (isSafeForSuggestions(attribute)) {
            val manager = getSystemService(Context.TEXT_SERVICES_MANAGER_SERVICE) as? TextServicesManager
            spellChecker = runCatching {
                manager?.newSpellCheckerSession(null, inputLocale(), this, true)
            }.getOrNull()
        }
    }

    override fun onFinishInput() {
        inputGeneration++
        typedWord.clear()
        lastCommittedWord = ""
        currentSuggestions = emptyList()
        spellCheckGeneration++
        spellChecker?.close()
        spellChecker = null
        super.onFinishInput()
    }

    override fun onGetSuggestions(results: Array<SuggestionsInfo>) {
        val suggestions = results.firstOrNull()?.let { info ->
            (0 until info.suggestionsCount).mapNotNull { index -> info.getSuggestionAt(index) }
        }.orEmpty().distinct().take(3)
        runOnMain {
            currentSuggestions = suggestions
            renderSuggestions()
            if (getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_AUTOCORRECT, false) &&
                suggestions.isNotEmpty() && lastCommittedWord.isNotBlank() && typedWord.isEmpty() &&
                inputGeneration == activeSuggestionInputGeneration
            ) {
                val replacement = suggestions.first()
                if (!replacement.equals(lastCommittedWord, ignoreCase = true)) {
                    currentInputConnection?.let { connection ->
                        connection.deleteSurroundingText(lastCommittedWord.length + 1, 0)
                        connection.commitText("$replacement ", 1)
                    }
                }
            }
        }
    }

    override fun onGetSentenceSuggestions(results: Array<SentenceSuggestionsInfo>) {
        // Sentence-level results are not requested; word suggestions are used instead.
    }

    private var activeSuggestionInputGeneration = 0

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post { block() }
    }

    private fun buildKeyboard(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(5))
            setBackgroundColor(Color.rgb(19, 24, 31))
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        addToolbarKey(top, "Capture", Color.rgb(31, 92, 69)) { captureClipboard(showToast = true) }
        addToolbarKey(top, if (clipboardMode) "Keys" else "Clips", Color.rgb(37, 83, 67)) {
            clipboardMode = !clipboardMode
            if (!clipboardMode) clipboardQuery = ""
            setInputView(buildKeyboard())
        }
        addToolbarKey(top, "Paste", Color.rgb(38, 69, 91)) { pasteLatestText() }
        addToolbarKey(top, "History", Color.rgb(42, 55, 73)) { openClipboardHistory() }
        root.addView(top, LinearLayout.LayoutParams(-1, dp(34)))

        val tools = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        addToolbarKey(tools, selectedLanguageCode().uppercase(Locale.ROOT), Color.rgb(52, 61, 74)) { cycleKeyboardLanguage() }
        addToolbarKey(tools, if (hapticsEnabled()) "Vib on" else "Vib off", Color.rgb(52, 61, 74)) { toggleHaptics() }
        addToolbarKey(tools, "Size", Color.rgb(52, 61, 74)) { cycleKeyHeight() }
        addToolbarKey(tools, if (autoCorrectEnabled()) "Auto on" else "Auto off", Color.rgb(52, 61, 74)) { toggleAutoCorrect() }
        addToolbarKey(tools, "Switch", Color.rgb(52, 61, 74)) {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }
        addToolbarKey(tools, "Hide", Color.rgb(52, 61, 74)) { requestHideSelf(0) }
        root.addView(tools, LinearLayout.LayoutParams(-1, dp(34)))

        if (clipboardMode) {
            val searchView = TextView(this).apply {
                setTextColor(Color.WHITE)
                textSize = 12f
                setPadding(dp(9), dp(5), dp(9), dp(5))
                setBackgroundColor(Color.rgb(32, 40, 49))
                text = clipboardSearchLabel()
            }
            clipboardSearchView = searchView
            root.addView(searchView, LinearLayout.LayoutParams(-1, dp(28)))
            val scroll = ScrollView(this).apply { isFillViewport = false }
            val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(2), 0, dp(2)) }
            clipboardList = list
            scroll.addView(list)
            root.addView(scroll, LinearLayout.LayoutParams(-1, dp(94)))
            renderClipboardEntries()
            addKeyRow(root, listOf("123", "ABC", "space", "⌫", "↵"), listOf(1.1f, 1.1f, 3.5f, 1.2f, 1.2f))
            return root
        }
        clipboardList = null
        clipboardSearchView = null

        val suggestions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(27, 34, 43))
        }
        suggestionRow = suggestions
        root.addView(suggestions, LinearLayout.LayoutParams(-1, dp(31)))
        renderSuggestions()

        if (symbols) {
            addKeyRow(root, listOf("1","2","3","4","5","6","7","8","9","0"))
            addKeyRow(root, listOf("@","#","$","%","&","-","+","(",")","/"))
            addKeyRow(root, listOf("=","*","\"","'",":",";","!","?","⌫"), listOf(1f,1f,1f,1f,1f,1f,1f,1f,1.5f))
            addKeyRow(root, listOf("ABC",",","space",".","↵"), listOf(1.2f,1f,4f,1f,1.3f))
        } else {
            addKeyRow(root, listOf("q","w","e","r","t","y","u","i","o","p"))
            addKeyRow(root, listOf("a","s","d","f","g","h","j","k","l"), listOf(1.15f,1f,1f,1f,1f,1f,1f,1f,1.15f))
            addKeyRow(root, listOf("⇧","z","x","c","v","b","n","m","⌫"), listOf(1.45f,1f,1f,1f,1f,1f,1f,1f,1.45f))
            addKeyRow(root, listOf("?123",",","←","space","→",".","↵"), listOf(1.35f,0.8f,0.85f,3.4f,0.85f,0.8f,1.15f))
        }
        return root
    }

    private fun addToolbarKey(row: LinearLayout, label: String, color: Int, action: () -> Unit) {
        val key = TextView(this).apply {
            text = label
            textSize = 10f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            isSingleLine = true
            setBackgroundColor(color)
            setOnClickListener { tapFeedback(this); action() }
        }
        row.addView(key, LinearLayout.LayoutParams(0, dp(30), 1f).apply {
            setMargins(dp(2), dp(1), dp(2), dp(1))
        })
    }

    private fun addKeyRow(root: LinearLayout, keys: List<String>, weights: List<Float> = keys.map { 1f }) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        keys.forEachIndexed { index, key ->
            val wide = key in setOf("space", "⇧", "⌫", "↵", "?123", "123", "ABC", "←", "→")
            val label = when (key) {
                "space" -> "space"
                "⇧" -> if (capsLock) "⇪" else "⇧"
                "?123" -> "?123"
                else -> key
            }
            val keyView = TextView(this).apply {
                text = if (!symbols && key.length == 1 && key[0].isLetter()) {
                    if (shifted || capsLock) key.uppercase(inputLocale()) else key
                } else label
                textSize = when (key) { "space" -> 10f; "←", "→", "⇧", "⇪", "⌫", "↵" -> 17f; else -> 16f }
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                isSingleLine = true
                setPadding(dp(1), 0, dp(1), 0)
                setBackgroundColor(if (wide) Color.rgb(54, 67, 82) else Color.rgb(39, 48, 59))
                setOnClickListener { tapFeedback(this); handleKey(key) }
                if (key.length == 1 && key[0].isLetter()) {
                    setOnLongClickListener {
                        showAlternates(this, alternativesFor(key))
                        true
                    }
                } else if (key in setOf(",", ".", "?", "!", "'", "\"", "-")) {
                    setOnLongClickListener {
                        showAlternates(this, punctuationAlternates(key))
                        true
                    }
                }
            }
            row.addView(keyView, LinearLayout.LayoutParams(0, dp(keyHeight()), weights.getOrElse(index) { 1f }).apply {
                setMargins(dp(2), dp(2), dp(2), dp(2))
            })
        }
        root.addView(row, LinearLayout.LayoutParams(-1, dp(keyHeight() + 4)))
    }

    private fun showAlternates(anchor: View, alternatives: List<String>) {
        if (alternatives.isEmpty()) return
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            setBackgroundColor(Color.rgb(63, 76, 91))
        }
        var popup: PopupWindow? = null
        alternatives.distinct().forEach { value ->
            val option = TextView(this).apply {
                text = value
                textSize = 20f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setPadding(dp(12), dp(8), dp(12), dp(8))
                setOnClickListener {
                    tapFeedback(this)
                    currentInputConnection?.commitText(if (shifted || capsLock) value.uppercase(inputLocale()) else value, 1)
                    popup?.dismiss()
                    if (shifted && !capsLock) { shifted = false; refreshKeyboard() }
                }
            }
            content.addView(option, LinearLayout.LayoutParams(-2, dp(48)))
        }
        popup = PopupWindow(content, -2, -2, true).apply {
            isOutsideTouchable = true
            elevation = dp(8).toFloat()
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            showAsDropDown(anchor, 0, -dp(keyHeight() + 58), Gravity.TOP)
        }
    }

    private fun alternativesFor(key: String): List<String> {
        val base = key.lowercase(Locale.ROOT)
        val locale = selectedLanguageCode()
        val language = when (locale) {
            "pl" -> when (base) {
                "a" -> listOf("ą"); "c" -> listOf("ć"); "e" -> listOf("ę")
                "l" -> listOf("ł"); "n" -> listOf("ń"); "o" -> listOf("ó")
                "s" -> listOf("ś"); "z" -> listOf("ź", "ż"); else -> emptyList()
            }
            "tr" -> when (base) {
                "g" -> listOf("ğ"); "i" -> listOf("ı", "İ"); "s" -> listOf("ş")
                "u" -> listOf("ü"); "o" -> listOf("ö"); "c" -> listOf("ç"); else -> emptyList()
            }
            else -> emptyList()
        }
        val number = when (base) {
            "q" -> "1"; "w" -> "2"; "e" -> "3"; "r" -> "4"; "t" -> "5"
            "y" -> "6"; "u" -> "7"; "i" -> "8"; "o" -> "9"; "p" -> "0"; else -> ""
        }
        return language + listOfNotNull(number.takeIf { it.isNotEmpty() })
    }

    private fun punctuationAlternates(key: String): List<String> = when (key) {
        "," -> listOf(";", "،", "…")
        "." -> listOf("?", "!", "。")
        "?" -> listOf("¿", "؟")
        "!" -> listOf("¡")
        "'" -> listOf("’", "`")
        "\"" -> listOf("“", "”")
        "-" -> listOf("_", "—", "–")
        else -> emptyList()
    }

    private fun handleKey(key: String) {
        if (clipboardMode) { handleClipboardSearchKey(key); return }
        when (key) {
            "⇧" -> {
                when {
                    capsLock -> { capsLock = false; shifted = false }
                    shifted -> { capsLock = true }
                    else -> shifted = true
                }
                refreshKeyboard()
            }
            "?123", "123" -> { symbols = true; refreshKeyboard() }
            "ABC" -> { symbols = false; refreshKeyboard() }
            "⌫" -> {
                val connection = currentInputConnection ?: return
                val selected = connection.getSelectedText(0)
                if (!selected.isNullOrEmpty()) connection.commitText("", 1)
                else connection.deleteSurroundingText(1, 0)
                if (typedWord.isNotEmpty()) typedWord.deleteCharAt(typedWord.length - 1)
                currentSuggestions = emptyList()
                renderSuggestions()
            }
            "←" -> sendCursorKey(KeyEvent.KEYCODE_DPAD_LEFT)
            "→" -> sendCursorKey(KeyEvent.KEYCODE_DPAD_RIGHT)
            "space" -> commitSpace()
            "↵" -> {
                val action = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
                if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) currentInputConnection?.performEditorAction(action)
                else currentInputConnection?.commitText("\n", 1)
                typedWord.clear()
                currentSuggestions = emptyList()
                renderSuggestions()
            }
            else -> {
                val value = if (shifted || capsLock) key.uppercase(inputLocale()) else key
                currentInputConnection?.commitText(value, 1)
                if (value.length == 1 && value[0].isLetter()) typedWord.append(value)
                else typedWord.clear()
                currentSuggestions = emptyList()
                renderSuggestions()
                if (shifted && !capsLock) { shifted = false; refreshKeyboard() }
            }
        }
    }

    private fun commitSpace() {
        val word = typedWord.toString()
        if (word.isNotBlank() && isSafeForSuggestions(currentInputEditorInfo)) {
            lastCommittedWord = word
            activeSuggestionInputGeneration = inputGeneration
            spellCheckGeneration++
            spellChecker?.getSuggestions(arrayOf(TextInfo(word)), 3, false)
        } else {
            lastCommittedWord = ""
            currentSuggestions = emptyList()
        }
        currentInputConnection?.commitText(" ", 1)
        typedWord.clear()
        renderSuggestions()
    }

    private fun applySuggestion(value: String) {
        val word = lastCommittedWord
        if (word.isBlank()) return
        currentInputConnection?.let { connection ->
            connection.deleteSurroundingText(word.length + 1, 0)
            connection.commitText("$value ", 1)
        }
        lastCommittedWord = value
        currentSuggestions = emptyList()
        renderSuggestions()
    }

    private fun renderSuggestions() {
        val row = suggestionRow ?: return
        row.removeAllViews()
        val showCorrections = typedWord.isEmpty() && lastCommittedWord.isNotBlank() && currentSuggestions.isNotEmpty()
        val candidates = if (showCorrections) currentSuggestions else listOf(typedWord.toString().ifBlank { selectedLanguageLabel() })
        candidates.take(3).forEach { candidate ->
            val item = TextView(this).apply {
                text = candidate
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(if (!showCorrections) Color.LTGRAY else Color.WHITE)
                setPadding(dp(8), 0, dp(8), 0)
                setOnClickListener { if (showCorrections) applySuggestion(candidate) }
            }
            row.addView(item, LinearLayout.LayoutParams(0, -1, 1f))
        }
    }

    private fun selectedLanguageLabel(): String = when (selectedLanguageCode()) {
        "pl" -> "Polski"; "tr" -> "Türkçe"; else -> "English"
    }

    private fun sendCursorKey(code: Int) {
        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        typedWord.clear()
        lastCommittedWord = ""
        currentSuggestions = emptyList()
        renderSuggestions()
    }

    private fun isSafeForSuggestions(info: EditorInfo?): Boolean {
        if (info == null) return false
        val inputType = info.inputType
        val inputClass = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        if (inputClass != InputType.TYPE_CLASS_TEXT) return false
        if (inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0) return false
        if (variation in setOf(
                InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
                InputType.TYPE_TEXT_VARIATION_URI
            )
        ) return false
        return true
    }

    private fun inputLocale(): Locale = when (selectedLanguageCode()) {
        "pl" -> Locale.forLanguageTag("pl-PL")
        "tr" -> Locale.forLanguageTag("tr-TR")
        else -> Locale.US
    }

    private fun selectedLanguageCode(): String = getSharedPreferences(PREFS, MODE_PRIVATE)
        .getString(KEY_LANGUAGE, "en")?.lowercase(Locale.ROOT)?.takeIf { it in setOf("en", "pl", "tr") } ?: "en"

    private fun cycleKeyboardLanguage() {
        val languages = listOf("en", "pl", "tr")
        val next = languages[(languages.indexOf(selectedLanguageCode()) + 1) % languages.size]
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_LANGUAGE, next).apply()
        spellChecker?.close()
        spellChecker = null
        if (isSafeForSuggestions(currentInputEditorInfo)) {
            val manager = getSystemService(Context.TEXT_SERVICES_MANAGER_SERVICE) as? TextServicesManager
            spellChecker = runCatching { manager?.newSpellCheckerSession(null, inputLocale(), this, true) }.getOrNull()
        }
        refreshKeyboard()
    }

    private fun keyHeight(): Int = getSharedPreferences(PREFS, MODE_PRIVATE).getInt(KEY_HEIGHT, 43).coerceIn(38, 54)
    private fun hapticsEnabled(): Boolean = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_HAPTICS, true)
    private fun autoCorrectEnabled(): Boolean = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_AUTOCORRECT, false)

    private fun toggleHaptics() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_HAPTICS, !hapticsEnabled()).apply()
        refreshKeyboard()
    }

    private fun toggleAutoCorrect() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_AUTOCORRECT, !autoCorrectEnabled()).apply()
        refreshKeyboard()
    }

    private fun cycleKeyHeight() {
        val sizes = listOf(40, 44, 48, 52)
        val current = keyHeight()
        val next = sizes.firstOrNull { it > current } ?: sizes.first()
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(KEY_HEIGHT, next).apply()
        refreshKeyboard()
    }

    private fun tapFeedback(view: View) {
        if (hapticsEnabled()) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private fun refreshKeyboard() {
        setInputView(buildKeyboard())
    }

    private fun clipboardSearchLabel(): String =
        if (clipboardQuery.isBlank()) "Search clipboard · type below" else "Search: $clipboardQuery"

    private fun handleClipboardSearchKey(key: String) {
        when (key) {
            "⌫" -> clipboardQuery = clipboardQuery.dropLast(1)
            "space" -> clipboardQuery += " "
            "↵" -> { clipboardMode = false; clipboardQuery = ""; refreshKeyboard(); return }
            "⇧" -> shifted = !shifted
            "?123", "123" -> { symbols = true; refreshKeyboard(); return }
            "ABC" -> { symbols = false; refreshKeyboard(); return }
            "←", "→" -> return
            else -> clipboardQuery += if (shifted || capsLock) key.uppercase(inputLocale()) else key
        }
        if (key != "⇧" && shifted && !capsLock) shifted = false
        clipboardSearchView?.text = clipboardSearchLabel()
        renderClipboardEntries()
    }

    private fun renderClipboardEntries() {
        val container = clipboardList ?: return
        val query = clipboardQuery
        val revision = ++clipboardSearchRevision
        serviceScope.launch {
            delay(80)
            if (revision != clipboardSearchRevision || !clipboardMode) return@launch
            val repository = ClipboardHistoryRepository(applicationContext)
            val entries = if (query.isBlank()) repository.observe().first() else repository.search(query).first()
            if (revision != clipboardSearchRevision || !clipboardMode || clipboardList !== container) return@launch
            container.removeAllViews()
            if (entries.isEmpty()) {
                container.addView(TextView(this@VaultKeyboardService).apply {
                    text = if (query.isBlank()) "No saved clips yet" else "No matching clips"
                    setTextColor(Color.LTGRAY); textSize = 12f; gravity = Gravity.CENTER
                }, LinearLayout.LayoutParams(-1, dp(44)))
                return@launch
            }
            entries.take(3).forEach { entry ->
                val row = LinearLayout(this@VaultKeyboardService).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setBackgroundColor(Color.rgb(29, 36, 44))
                }
                val preview = TextView(this@VaultKeyboardService).apply {
                    text = entry.preview.replace("\n", " ").take(100)
                    setTextColor(Color.WHITE); textSize = 11f; maxLines = 2
                    setPadding(dp(7), dp(4), dp(4), dp(4))
                    setOnClickListener { pasteClipboardEntry(entry) }
                }
                row.addView(preview, LinearLayout.LayoutParams(0, dp(46), 1f))
                row.addView(toolbarButton(if (entry.contentType == "text") "Paste" else "Open") {
                    if (entry.contentType == "text") pasteClipboardEntry(entry) else openClipboardHistory()
                }, LinearLayout.LayoutParams(dp(54), dp(36)))
                row.addView(toolbarButton(if (entry.isPinned) "★" else "☆") {
                    serviceScope.launch {
                        ClipboardHistoryRepository(applicationContext).setPinned(entry.id, !entry.isPinned)
                        renderClipboardEntries()
                    }
                }, LinearLayout.LayoutParams(dp(38), dp(36)))
                container.addView(row, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(3) })
            }
        }
    }

    private fun toolbarButton(label: String, action: () -> Unit) = TextView(this).apply {
        text = label; gravity = Gravity.CENTER; textSize = 11f; setTextColor(Color.WHITE)
        setBackgroundColor(Color.rgb(48, 62, 76))
        setOnClickListener { tapFeedback(this); action() }
    }

    private fun pasteClipboardEntry(entry: ClipboardHistoryEntry) {
        serviceScope.launch {
            val text = runCatching { ClipboardHistoryRepository(applicationContext).readText(entry) }.getOrNull()
            if (text.isNullOrEmpty()) {
                Toast.makeText(this@VaultKeyboardService, "This saved clip is unavailable", Toast.LENGTH_SHORT).show()
                return@launch
            }
            insertText(text)
            clipboardMode = false
            clipboardQuery = ""
            refreshKeyboard()
        }
    }

    private suspend fun insertText(text: String): Int {
        val connection = currentInputConnection ?: return 0
        var inserted = 0
        for (part in text.chunked(8_000)) {
            if (!connection.commitText(part, 1)) break
            inserted += part.length
            yield()
        }
        return inserted
    }

    private fun captureClipboard(showToast: Boolean) {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (!showToast && !prefs.getBoolean(KEY_AUTO_CAPTURE, true)) return
        val info = currentInputEditorInfo
        val variation = info?.inputType?.and(InputType.TYPE_MASK_VARIATION)
        if (variation != null && variation in setOf(
                InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
                InputType.TYPE_NUMBER_VARIATION_PASSWORD
            )
        ) return
        val clip = clipboard?.primaryClip
        if (clip == null || clip.itemCount == 0) {
            if (showToast) Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            runCatching { clip.description.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false) == true }.getOrDefault(false)
        ) return
        val label = clip.description.label?.toString().orEmpty()
        if (label.startsWith("VaultOS", ignoreCase = true)) return
        val entries = (0 until clip.itemCount).mapNotNull { index ->
            val item = clip.getItemAt(index)
            val uri = item.uri
            val text = if (uri == null) item.text?.toString() ?: runCatching { item.coerceToText(this)?.toString() }.getOrNull() else null
            if (uri == null && text.isNullOrEmpty()) null else uri to text
        }
        if (entries.isEmpty()) {
            if (showToast) Toast.makeText(this, "This clipboard item cannot be imported", Toast.LENGTH_SHORT).show()
            return
        }
        val mimeTypes = (0 until clip.description.mimeTypeCount).map { clip.description.getMimeType(it) }
        val foregroundPackage = info?.packageName
        serviceScope.launch {
            val repository = ClipboardHistoryRepository(applicationContext)
            val outcomes = entries.map { (uri, text) ->
                runCatching {
                    if (uri != null) repository.captureUri(uri, foregroundPackage, label, mimeTypes)
                    else if (!text.isNullOrEmpty()) repository.captureText(text, foregroundPackage, label, mimeTypes)
                    else null
                }
            }
            if (showToast) {
                val saved = outcomes.count { it.isSuccess && it.getOrNull() != null }
                val failed = outcomes.size - saved
                val message = if (failed == 0) "Saved $saved clipboard item(s)" else "Saved $saved · failed $failed"
                Toast.makeText(this@VaultKeyboardService, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun pasteLatestText() {
        serviceScope.launch {
            val text = runCatching { VaultRepository(applicationContext).latestText() }.getOrNull()
            if (text.isNullOrEmpty()) Toast.makeText(this@VaultKeyboardService, "No saved text found", Toast.LENGTH_SHORT).show()
            else {
                val inserted = insertText(text)
                Toast.makeText(this@VaultKeyboardService, "Inserted $inserted of ${text.length} characters", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openClipboardHistory() {
        startActivity(Intent(this, ClipboardHistoryActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun openVault() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val PREFS = "vaultos_preferences"
        const val KEY_AUTO_CAPTURE = "auto_capture_clipboard"
        const val KEY_LANGUAGE = "keyboard_language"
        const val KEY_HAPTICS = "keyboard_haptics"
        const val KEY_HEIGHT = "keyboard_key_height"
        const val KEY_AUTOCORRECT = "keyboard_autocorrect"
    }
}
