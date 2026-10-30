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
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.vaultos.app.MainActivity
import com.vaultos.app.storage.VaultRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import java.security.MessageDigest
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
    private var lastCaptureHash: String? = null

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
        toolbar.addView(actionButton("Capture", Color.rgb(32, 91, 69)) { captureClipboard(showToast = true) }, weightParams(1f))
        toolbar.addView(actionButton("Paste saved", Color.rgb(39, 78, 66)) { pasteLatestText() }, weightParams(1.15f))
        toolbar.addView(actionButton("Vault", Color.rgb(39, 55, 75)) { openVault() }, weightParams(0.7f))
        toolbar.addView(actionButton("Switch", Color.rgb(55, 60, 69)) { switchToNextInputMethod(false) }, weightParams(0.75f))
        toolbar.addView(actionButton("Hide", Color.rgb(55, 60, 69)) { requestHideSelf(0) }, weightParams(0.6f))
        root.addView(toolbar, LinearLayout.LayoutParams(-1, dp(43)))

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

    private fun languageKeys(): List<String> = when (currentInputMethodSubtype?.locale?.substringBefore('_')?.lowercase()) {
        "pl" -> listOf("ą", "ć", "ę", "ł", "ń", "ó", "ś", "ź", "ż")
        "tr" -> listOf("ğ", "ı", "ü", "ş", "ö", "ç")
        else -> emptyList()
    }

    private fun inputLocale(): Locale {
        val languageTag = currentInputMethodSubtype?.locale.orEmpty().replace('_', '-')
        return Locale.forLanguageTag(languageTag.ifBlank { "en" })
    }

    private fun handleKey(key: String) {
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
        val signature = entries.joinToString("|") { (uri, text) -> uri?.toString() ?: MessageDigest.getInstance("SHA-256").digest(text.orEmpty().toByteArray(Charsets.UTF_8)).joinToString("") { b -> "%02x".format(b) } }
        if (lastCaptureHash == signature) return
        lastCaptureHash = signature
        serviceScope.launch {
            val repository = VaultRepository(applicationContext)
            val result = runCatching {
                entries.forEach { (uri, text) ->
                    if (uri != null) repository.importUri(uri, "Clipboard · Android file")
                    else if (!text.isNullOrEmpty()) repository.saveText(text, "Clipboard · Android")
                }
            }
            if (showToast) {
                val message = if (result.isSuccess) "Saved ${entries.size} clipboard item(s)" else "Could not save clipboard content"
                Toast.makeText(this@VaultKeyboardService, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun pasteLatestText() {
        val connection = currentInputConnection ?: return
        serviceScope.launch {
            val text = runCatching { VaultRepository(applicationContext).latestText() }.getOrNull()
            if (text.isNullOrEmpty()) Toast.makeText(this@VaultKeyboardService, "No saved text found", Toast.LENGTH_SHORT).show()
            else {
                // Avoid one oversized Binder transaction for a very long saved message.
                var inserted = 0
                for (part in text.chunked(8_000)) {
                    if (!connection.commitText(part, 1)) break
                    inserted += part.length
                    yield()
                }
                Toast.makeText(this@VaultKeyboardService, "Inserted $inserted of ${text.length} characters", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openVault() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
    }

    private fun actionButton(label: String, color: Int, click: () -> Unit) = Button(this).apply {
        text = label; textSize = 12f; isAllCaps = false; setTextColor(Color.WHITE)
        setBackgroundTintList(android.content.res.ColorStateList.valueOf(color)); setOnClickListener { click() }
    }

    private fun weightParams(weight: Float) = LinearLayout.LayoutParams(0, dp(40), weight).apply { setMargins(dp(2), 0, dp(2), 0) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val PREFS = "vaultos_preferences"
        const val KEY_AUTO_CAPTURE = "auto_capture_clipboard"
    }
}
