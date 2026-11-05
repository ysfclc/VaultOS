package com.vaultos.app.keyboard

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.ScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.vaultos.app.MainActivity
import com.vaultos.app.ClipboardHistoryActivity
import com.vaultos.app.storage.ClipboardHistoryRepository
import com.vaultos.app.data.ClipboardHistoryEntry
import com.vaultos.app.storage.VaultRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.yield
import java.util.Locale

/**
 * Optional VaultOS IME. It is deliberately opt-in: Android grants the selected input
 * method clipboard access, but no IME can read clipboard content while Gboard is active.
 */
class VaultKeyboardService : InputMethodService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var clipboard: ClipboardManager? = null
    private var shifted = false
    private var symbols = false
    private var clipboardMode = false
    private var clipboardQuery = ""
    private var clipboardList: LinearLayout? = null
    private var clipboardSearchView: TextView? = null
    private var clipboardSearchRevision = 0

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
        serviceScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        super.onDestroy()
    }

    override fun onCreateInputView(): View = buildKeyboard()

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        shifted = false
        symbols = false
    }

    private fun buildKeyboard(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(5), dp(5), dp(5), dp(7))
            setBackgroundColor(Color.rgb(22, 27, 34))
        }
        val toolbar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        toolbar.addView(actionButton("Capture", Color.rgb(32, 91, 69)) { captureClipboard(showToast = true) }, weightParams(0.85f))
        toolbar.addView(actionButton(if (clipboardMode) "Keys" else "Clips", Color.rgb(39, 78, 66)) {
            clipboardMode = !clipboardMode
            setInputView(buildKeyboard())
        }, weightParams(0.75f))
        toolbar.addView(actionButton("Paste", Color.rgb(39, 78, 66)) { pasteLatestText() }, weightParams(0.7f))
        toolbar.addView(actionButton("History", Color.rgb(39, 55, 75)) { openClipboardHistory() }, weightParams(0.8f))
        toolbar.addView(actionButton(selectedLanguageCode().uppercase(Locale.ROOT), Color.rgb(55, 60, 69)) { cycleKeyboardLanguage() }, weightParams(0.55f))
        toolbar.addView(actionButton("Switch", Color.rgb(55, 60, 69)) {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }, weightParams(0.7f))
        toolbar.addView(actionButton("Hide", Color.rgb(55, 60, 69)) { requestHideSelf(0) }, weightParams(0.45f))
        root.addView(toolbar, LinearLayout.LayoutParams(-1, dp(43)))
        if (clipboardMode) {
            val searchView = TextView(this).apply {
                setTextColor(Color.WHITE)
                textSize = 12f
                setPadding(dp(9), dp(5), dp(9), dp(5))
                setBackgroundColor(Color.rgb(32, 40, 49))
                text = clipboardSearchLabel()
            }
            clipboardSearchView = searchView
            root.addView(searchView, LinearLayout.LayoutParams(-1, dp(30)))
            val scroll = ScrollView(this).apply { isFillViewport = false }
            val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(2), 0, dp(2)) }
            clipboardList = list
            scroll.addView(list)
            root.addView(scroll, LinearLayout.LayoutParams(-1, dp(88)))
            renderClipboardEntries()
        } else {
            clipboardList = null
            clipboardSearchView = null
        }

        val rows = if (symbols) listOf(
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            listOf("@", "#", "$", "%", "&", "-", "+", "(", ")", "/"),
            listOf("=", "*", "\"", "'", ":", ";", "!", "?", "⌫"),
            listOf("ABC", ",", "space", ".", "↵")
        ) else buildList {
            add(listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"))
            add(listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"))
            add(listOf("⇧", "z", "x", "c", "v", "b", "n", "m", "⌫"))
            languageKeys().takeIf { it.isNotEmpty() }?.let { add(it) }
            add(listOf("123", ",", "space", ".", "↵"))
        }
        rows.forEach { keys ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
            keys.forEach { key ->
                val wide = key in setOf("space", "⇧", "⌫", "↵", "123", "ABC")
                val button = Button(this).apply {
                    text = when { key == "space" -> "space"; else -> key }
                    textSize = if (key == "space") 9f else 16f
                    isAllCaps = false
                    setTextColor(Color.WHITE)
                    setBackgroundTintList(android.content.res.ColorStateList.valueOf(if (wide) Color.rgb(51, 62, 74) else Color.rgb(43, 51, 61)))
                    setPadding(0, 0, 0, 0)
                    setOnClickListener { handleKey(key) }
                }
                row.addView(button, LinearLayout.LayoutParams(0, dp(if (key == "space") 49 else 43), if (key == "space") 4.0f else if (wide) 1.25f else 1f).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) })
            }
            root.addView(row, LinearLayout.LayoutParams(-1, dp(47)))
        }
        return root
    }

    /** The keyboard language is an explicit VaultOS preference, independent of the phone locale. */
    private fun selectedLanguageCode(): String = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(KEY_LANGUAGE, "en")?.lowercase(Locale.ROOT)?.takeIf { it in setOf("en", "pl", "tr") } ?: "en"

    private fun currentInputLocaleTag(): String = when (selectedLanguageCode()) {
        "pl" -> "pl-PL"
        "tr" -> "tr-TR"
        else -> "en-US"
    }

    private fun cycleKeyboardLanguage() {
        val languages = listOf("en", "pl", "tr")
        val current = selectedLanguageCode()
        val next = languages[(languages.indexOf(current) + 1) % languages.size]
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_LANGUAGE, next).apply()
        setInputView(buildKeyboard())
    }

    private fun languageKeys(): List<String> = when (currentInputLocaleTag().substringBefore('_').substringBefore('-').lowercase(Locale.ROOT)) {
        "pl" -> listOf("ą", "ć", "ę", "ł", "ń", "ó", "ś", "ź", "ż")
        "tr" -> listOf("ğ", "ı", "ü", "ş", "ö", "ç")
        else -> emptyList()
    }

    private fun inputLocale(): Locale = Locale.forLanguageTag(currentInputLocaleTag().replace('_', '-'))

    private fun handleKey(key: String) {
        if (clipboardMode) { handleClipboardSearchKey(key); return }
        when (key) {
            "⇧" -> { shifted = !shifted; setInputView(buildKeyboard()) }
            "123" -> { symbols = true; setInputView(buildKeyboard()) }
            "ABC" -> { symbols = false; setInputView(buildKeyboard()) }
            "⌫" -> {
                val connection = currentInputConnection ?: return
                val selected = connection.getSelectedText(0)
                if (!selected.isNullOrEmpty()) connection.commitText("", 1) else connection.deleteSurroundingText(1, 0)
            }
            "space" -> currentInputConnection?.commitText(" ", 1)
            "↵" -> {
                val action = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
                if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) currentInputConnection?.performEditorAction(action)
                else currentInputConnection?.commitText("\n", 1)
            }
            else -> {
                val value = if (shifted) key.uppercase(inputLocale()) else key
                currentInputConnection?.commitText(value, 1)
                if (shifted) { shifted = false; setInputView(buildKeyboard()) }
            }
        }
    }

    private fun clipboardSearchLabel(): String = if (clipboardQuery.isBlank()) "Search clipboard · type with the keys below" else "Search: $clipboardQuery"

    private fun handleClipboardSearchKey(key: String) {
        when (key) {
            "⌫" -> clipboardQuery = clipboardQuery.dropLast(1)
            "space" -> clipboardQuery += " "
            "↵" -> { clipboardMode = false; setInputView(buildKeyboard()); return }
            "⇧" -> shifted = !shifted
            "123" -> { symbols = true; setInputView(buildKeyboard()); return }
            "ABC" -> { symbols = false; setInputView(buildKeyboard()); return }
            else -> clipboardQuery += if (shifted) key.uppercase(inputLocale()) else key
        }
        if (key != "⇧" && shifted) shifted = false
        clipboardSearchView?.text = clipboardSearchLabel()
        renderClipboardEntries()
    }

    private fun renderClipboardEntries() {
        val container = clipboardList ?: return
        val query = clipboardQuery
        val revision = ++clipboardSearchRevision
        serviceScope.launch {
            delay(90)
            if (revision != clipboardSearchRevision || !clipboardMode) return@launch
            val repository = ClipboardHistoryRepository(applicationContext)
            val entries = if (query.isBlank()) repository.observe().first() else repository.search(query).first()
            if (revision != clipboardSearchRevision || !clipboardMode || clipboardList !== container) return@launch
            container.removeAllViews()
            if (entries.isEmpty()) {
                container.addView(TextView(this@VaultKeyboardService).apply {
                    text = if (query.isBlank()) "No saved clips yet" else "No matching clips"
                    setTextColor(Color.LTGRAY); textSize = 12f; gravity = Gravity.CENTER; setPadding(dp(8), dp(8), dp(8), dp(8))
                }, LinearLayout.LayoutParams(-1, dp(44)))
                return@launch
            }
            entries.take(3).forEach { entry ->
                val row = LinearLayout(this@VaultKeyboardService).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setBackgroundColor(Color.rgb(29, 36, 44)) }
                val preview = TextView(this@VaultKeyboardService).apply {
                    text = entry.preview.replace("\n", " ").take(110)
                    setTextColor(Color.WHITE); textSize = 11f; maxLines = 2; setPadding(dp(7), dp(4), dp(4), dp(4))
                    setOnClickListener { pasteClipboardEntry(entry) }
                }
                row.addView(preview, LinearLayout.LayoutParams(0, dp(48), 1f))
                val paste = actionButton(if (entry.contentType == "text") "Paste" else "Open", Color.rgb(32, 91, 69)) {
                    if (entry.contentType == "text") pasteClipboardEntry(entry) else openClipboardHistory()
                }
                row.addView(paste, LinearLayout.LayoutParams(dp(55), dp(38)))
                val pin = actionButton(if (entry.isPinned) "★" else "☆", Color.rgb(55, 60, 69)) {
                    serviceScope.launch { ClipboardHistoryRepository(applicationContext).setPinned(entry.id, !entry.isPinned); renderClipboardEntries() }
                }
                row.addView(pin, LinearLayout.LayoutParams(dp(40), dp(38)))
                container.addView(row, LinearLayout.LayoutParams(-1, dp(50)).apply { bottomMargin = dp(3) })
            }
        }
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
            setInputView(buildKeyboard())
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
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!showToast && !prefs.getBoolean(KEY_AUTO_CAPTURE, true)) return
        val info = currentInputEditorInfo
        val variation = info?.inputType?.and(InputType.TYPE_MASK_VARIATION)
        if (variation != null && variation in setOf(InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD, InputType.TYPE_NUMBER_VARIATION_PASSWORD)) return
        val clip = clipboard?.primaryClip
        if (clip == null || clip.itemCount == 0) {
            if (showToast) Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
            return
        }
        if (android.os.Build.VERSION.SDK_INT >= 33 && runCatching { clip.description.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false) == true }.getOrDefault(false)) return
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
        val intent = Intent(this, ClipboardHistoryActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }
    private fun openVault() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
    }

    private fun actionButton(label: String, color: Int, click: () -> Unit) = Button(this).apply {
        text = label; textSize = 10.5f; isAllCaps = false; setTextColor(Color.WHITE)
        setBackgroundTintList(android.content.res.ColorStateList.valueOf(color)); setOnClickListener { click() }
    }

    private fun weightParams(weight: Float) = LinearLayout.LayoutParams(0, dp(40), weight).apply { setMargins(dp(2), 0, dp(2), 0) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val PREFS = "vaultos_preferences"
        const val KEY_AUTO_CAPTURE = "auto_capture_clipboard"
        const val KEY_LANGUAGE = "keyboard_language"
    }
}
