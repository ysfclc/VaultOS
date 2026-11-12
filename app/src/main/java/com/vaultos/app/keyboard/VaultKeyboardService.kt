package com.vaultos.app.keyboard

import android.app.AlertDialog
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SoundEffectConstants
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
import com.vaultos.app.data.VaultDatabase
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
    private val typingMemory by lazy(LazyThreadSafetyMode.NONE) { LocalTypingMemory(applicationContext) }
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
    private var commandsMode = false
    private var commandQuery = ""
    private var commandList: LinearLayout? = null
    private var commandSearchView: TextView? = null
    private var commandSearchRevision = 0
    private var emojiMode = false
    private var keyPreviewPopup: PopupWindow? = null
    private var typedWord = StringBuilder()
    private var lastCommittedWord = ""
    private var previousWordForPrediction = ""
    private var inputGeneration = 0
    private var spellCheckGeneration = 0
    private var currentSuggestions: List<String> = emptyList()
    private var activeSpellCheckWord = ""
    private var lastSpaceAt = 0L

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
        keyPreviewPopup?.dismiss()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onCreateInputView(): View = buildKeyboard()

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        inputGeneration++
        clipboardMode = false
        commandsMode = false
        emojiMode = false
        clipboardQuery = ""
        commandQuery = ""
        shifted = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_AUTO_CAPITALIZE, true) && isSafeForSuggestions(attribute)
        capsLock = false
        lastSpaceAt = 0L
        symbols = false
        typedWord.clear()
        lastCommittedWord = ""
        previousWordForPrediction = ""
        currentSuggestions = emptyList()
        activeSpellCheckWord = ""
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

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        refreshKeyboard()
    }

    override fun onFinishInput() {
        inputGeneration++
        typedWord.clear()
        lastCommittedWord = ""
        previousWordForPrediction = ""
        currentSuggestions = emptyList()
        spellCheckGeneration++
        spellChecker?.close()
        spellChecker = null
        activeSpellCheckWord = ""
        super.onFinishInput()
    }

    override fun onGetSuggestions(results: Array<SuggestionsInfo>) {
        val info = results.firstOrNull() ?: return
        val requestId = info.cookie
        if (requestId != spellCheckGeneration) return
        val looksLikeTypo = info.suggestionsAttributes and SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO != 0
        val suggestions = if (looksLikeTypo) {
            (0 until info.suggestionsCount)
                .mapNotNull { index -> info.getSuggestionAt(index) }
                .filter { it.isNotBlank() }
                .distinct().take(2)
        } else emptyList()
        runOnMain {
            // A newer keystroke may have arrived while this callback was queued.
            if (requestId != spellCheckGeneration) return@runOnMain
            val requestedWord = activeSpellCheckWord
            val activeWord = typedWord.toString().ifBlank { lastCommittedWord }
            if (requestedWord.isNotBlank() && !activeWord.equals(requestedWord, ignoreCase = true)) return@runOnMain
            currentSuggestions = suggestions
            renderSuggestions()
            if (getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_AUTOCORRECT, false) &&
                looksLikeTypo && suggestions.isNotEmpty() && lastCommittedWord.isNotBlank() && typedWord.isEmpty() &&
                inputGeneration == activeSuggestionInputGeneration
            ) {
                val replacement = suggestions.first()
                if (!replacement.equals(lastCommittedWord, ignoreCase = true)) {
                    currentInputConnection?.let { connection ->
                        connection.deleteSurroundingText(lastCommittedWord.length + 1, 0)
                        connection.commitText("$replacement ", 1)
                    }
                    lastCommittedWord = replacement
                    previousWordForPrediction = replacement
                    currentSuggestions = emptyList()
                    renderSuggestions()
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
            setPadding(dp(4), dp(4), dp(4), dp(6))
            setBackgroundColor(if (keyboardThemeDark()) Color.rgb(18, 22, 29) else Color.rgb(232, 237, 243))
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        addToolbarKey(top, "Type", active = !clipboardMode && !commandsMode && !emojiMode, weight = 1.15f) {
            clipboardMode = false; commandsMode = false; emojiMode = false; refreshKeyboard()
        }
        addToolbarKey(top, "Clips", active = clipboardMode, weight = 1.05f) {
            clipboardMode = true; commandsMode = false; emojiMode = false; clipboardQuery = ""; refreshKeyboard()
        }
        addToolbarKey(top, "Vault", active = commandsMode, weight = 0.98f) {
            commandsMode = true; clipboardMode = false; emojiMode = false; commandQuery = ""; refreshKeyboard()
        }
        addToolbarKey(top, selectedLanguageCode().uppercase(Locale.ROOT), active = false, weight = 0.75f) {
            cycleKeyboardLanguage()
        }
        addToolbarKey(top, "Tools", active = false, weight = 0.9f) { showKeyboardTools(itAnchor = top.getChildAt(top.childCount - 1)) }
        root.addView(top, LinearLayout.LayoutParams(-1, dp(46)))

        if (clipboardMode) {
            val searchView = TextView(this).apply {
                setTextColor(if (keyboardThemeDark()) Color.WHITE else Color.rgb(28, 35, 44))
                textSize = 13f
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(5), dp(12), dp(5))
                background = keyBackground(wide = true, toolbar = true)
                text = clipboardSearchLabel()
            }
            clipboardSearchView = searchView
            root.addView(searchView, LinearLayout.LayoutParams(-1, dp(32)).apply { setMargins(0, dp(3), 0, dp(3)) })
            val scroll = ScrollView(this).apply { isFillViewport = false }
            val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(2), 0, dp(2)) }
            clipboardList = list
            scroll.addView(list)
            root.addView(scroll, LinearLayout.LayoutParams(-1, dp(86)))
            renderClipboardEntries()
            addTypingKeys(root)
            return root
        }
        clipboardList = null
        clipboardSearchView = null

        if (commandsMode) {
            val searchView = TextView(this).apply {
                setTextColor(if (keyboardThemeDark()) Color.WHITE else Color.rgb(28, 35, 44))
                textSize = 13f
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(5), dp(12), dp(5))
                background = keyBackground(wide = true, toolbar = true)
                text = commandSearchLabel()
            }
            commandSearchView = searchView
            root.addView(searchView, LinearLayout.LayoutParams(-1, dp(32)).apply { setMargins(0, dp(3), 0, dp(3)) })
            val scroll = ScrollView(this).apply { isFillViewport = false }
            val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(2), 0, dp(2)) }
            commandList = list
            scroll.addView(list)
            root.addView(scroll, LinearLayout.LayoutParams(-1, dp(86)))
            renderCommandEntries()
            addTypingKeys(root)
            return root
        }
        commandList = null
        commandSearchView = null

        if (emojiMode) {
            val emojiRows = listOf(
                listOf("😀", "😂", "🥰", "😍", "😊", "😉", "😎", "🤔"),
                listOf("😭", "😅", "😴", "🥳", "🤯", "😡", "🙏", "👏"),
                listOf("👍", "👎", "❤️", "🔥", "✨", "💯", "🎉", "🤝"),
                listOf("🙂", "🙃", "😇", "🤗", "💪", "☕", "🚀", "🌍")
            )
            emojiRows.forEach { addKeyRow(root, it) }
            val footer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            addToolbarKey(footer, "ABC · Back to typing", active = true, weight = 1f) {
                emojiMode = false; refreshKeyboard()
            }
            root.addView(footer, LinearLayout.LayoutParams(-1, dp(42)))
            return root
        }

        val suggestions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(5), dp(2), dp(5), dp(2))
            setBackgroundColor(if (keyboardThemeDark()) Color.rgb(18, 22, 29) else Color.rgb(232, 237, 243))
        }
        suggestionRow = suggestions
        root.addView(suggestions, LinearLayout.LayoutParams(-1, dp(40)).apply { setMargins(0, dp(2), 0, dp(3)) })
        renderSuggestions()
        addTypingKeys(root)
        return root
    }

    private fun addTypingKeys(root: LinearLayout) {
        if (symbols) {
            addKeyRow(root, listOf("1","2","3","4","5","6","7","8","9","0"))
            addKeyRow(root, listOf("@","#","$","%","&","-","+","(",")","/"))
            addKeyRow(root, listOf("=","*","\"","'",":",";","!","?","⌫"), listOf(1f,1f,1f,1f,1f,1f,1f,1f,1.5f))
            addKeyRow(root, listOf("ABC",",","space",".","↵"), listOf(1.2f,1f,4f,1f,1.3f))
        } else {
            if (numberRowEnabled()) addKeyRow(root, listOf("1","2","3","4","5","6","7","8","9","0"))
            addKeyRow(root, listOf("q","w","e","r","t","y","u","i","o","p"))
            addKeyRow(root, listOf("a","s","d","f","g","h","j","k","l"), listOf(1.15f,1f,1f,1f,1f,1f,1f,1f,1.15f))
            addKeyRow(root, listOf("⇧","z","x","c","v","b","n","m","⌫"), listOf(1.45f,1f,1f,1f,1f,1f,1f,1f,1.45f))
            addKeyRow(root, listOf("?123",",","←","space","→",".","↵"), listOf(1.35f,0.8f,0.85f,3.4f,0.85f,0.8f,1.15f))
        }
    }

    private fun addToolbarKey(
        row: LinearLayout,
        label: String,
        active: Boolean = false,
        weight: Float = 1f,
        action: () -> Unit
    ) {
        val key = TextView(this).apply {
            text = label
            textSize = if (label.length > 8) 10f else 11.5f
            gravity = Gravity.CENTER
            setTextColor(if (keyboardThemeDark()) Color.WHITE else Color.rgb(28, 35, 44))
            isSingleLine = true
            setPadding(dp(2), 0, dp(2), 0)
            background = keyBackground(wide = true, active = active, toolbar = true)
            setOnClickListener { tapFeedback(this); playKeySound(this); action() }
        }
        row.addView(key, LinearLayout.LayoutParams(0, dp(42), weight).apply {
            setMargins(dp(2), dp(2), dp(2), dp(2))
        })
    }

    private fun addKeyRow(root: LinearLayout, keys: List<String>, weights: List<Float> = keys.map { 1f }) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        keys.forEachIndexed { index, key ->
            val wide = key in setOf("space", "⇧", "⌫", "↵", "?123", "123", "ABC", "←", "→")
            val label = when (key) {
                "space" -> selectedLanguageLabel()
                "⇧" -> if (capsLock) "⇪" else "⇧"
                else -> key
            }
            val keyView = TextView(this).apply {
                text = if (!symbols && key.length == 1 && key[0].isLetter()) {
                    if (shifted || capsLock) key.uppercase(inputLocale()) else key
                } else label
                textSize = when {
                    key == "space" -> 10.5f
                    key in setOf("←", "→", "⇧", "⇪", "⌫", "↵") -> 19f
                    key.length > 1 -> 12f
                    key.codePoints().toArray().any { it > 0xFFFF } -> 20f
                    else -> 19f
                }
                gravity = Gravity.CENTER
                setTextColor(keyTextColors())
                isSingleLine = true
                setPadding(dp(1), 0, dp(1), 0)
                background = keyBackground(wide = wide)
                elevation = dp(1).toFloat()
                contentDescription = when (key) {
                    "⌫" -> "Backspace"
                    "↵" -> "Enter"
                    "⇧" -> "Shift"
                    "space" -> "Space, hold and slide to move cursor"
                    else -> key
                }
                setOnClickListener { tapFeedback(this); playKeySound(this); handleKey(key) }
                setOnTouchListener { view, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            view.scaleX = 0.92f
                            view.scaleY = 0.92f
                            view.translationZ = dp(7).toFloat()
                            if (key.length == 1 && key != " ") showKeyPreview(view, text.toString())
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            view.scaleX = 1f
                            view.scaleY = 1f
                            view.translationZ = 0f
                            mainHandler.postDelayed({ if (!view.isPressed) hideKeyPreview() }, 90)
                        }
                    }
                    false
                }
                if (key == "space") {
                    var startX = 0f
                    var lastStep = 0
                    var moved = false
                    setOnTouchListener { view, event ->
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                startX = event.rawX
                                lastStep = 0
                                moved = false
                                view.scaleX = 0.94f
                                view.scaleY = 0.94f
                                view.translationZ = dp(5).toFloat()
                            }
                            MotionEvent.ACTION_MOVE -> {
                                val step = ((event.rawX - startX) / dp(18).toFloat()).toInt()
                                val delta = step - lastStep
                                if (delta != 0) {
                                    repeat(kotlin.math.abs(delta).coerceAtMost(5)) {
                                        sendCursorKey(if (delta > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT)
                                    }
                                    lastStep = step
                                    moved = true
                                }
                                if (moved) return@setOnTouchListener true
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                view.scaleX = 1f
                                view.scaleY = 1f
                                view.translationZ = 0f
                                if (moved) return@setOnTouchListener true
                            }
                        }
                        false
                    }
                }
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
                setMargins(dp(2), dp(3), dp(2), dp(3))
            })
        }
        val side = oneHandedSide()
        val width = if (side == 0) -1 else dp(((resources.displayMetrics.widthPixels / resources.displayMetrics.density) * 0.84f).toInt())
        val params = LinearLayout.LayoutParams(width, dp(keyHeight() + 6))
        params.gravity = when (side) { 1 -> Gravity.START; 2 -> Gravity.END; else -> Gravity.CENTER_HORIZONTAL }
        root.addView(row, params)
    }

    private fun keyBackground(wide: Boolean, active: Boolean = false, toolbar: Boolean = false): RippleDrawable {
        val dark = keyboardThemeDark()
        val normalColor = when {
            active -> if (dark) Color.rgb(39, 73, 63) else Color.rgb(210, 239, 225)
            toolbar -> if (dark) Color.rgb(27, 33, 42) else Color.rgb(224, 230, 237)
            wide -> if (dark) Color.rgb(48, 58, 72) else Color.rgb(211, 219, 228)
            else -> if (dark) Color.rgb(37, 44, 55) else Color.rgb(255, 255, 255)
        }
        val pressedColor = if (dark) Color.rgb(83, 148, 121) else Color.rgb(184, 229, 207)
        val strokeColor = if (dark) Color.rgb(59, 69, 83) else Color.rgb(214, 221, 229)
        fun shape(color: Int) = GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(if (toolbar) 12 else 10).toFloat()
            if (!toolbar) setStroke(dp(1), strokeColor)
        }
        val states = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), shape(pressedColor))
            addState(intArrayOf(), shape(normalColor))
        }
        val mask = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(if (toolbar) 12 else 10).toFloat()
        }
        return RippleDrawable(ColorStateList.valueOf(Color.argb(90, 154, 230, 193)), states, mask)
    }

    private fun keyTextColors(): ColorStateList = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_pressed), intArrayOf()),
        intArrayOf(
            Color.rgb(17, 33, 27),
            if (keyboardThemeDark()) Color.WHITE else Color.rgb(25, 32, 41)
        )
    )

    private fun showKeyPreview(anchor: View, label: String) {
        if (!keyPreviewEnabled()) return
        hideKeyPreview()
        val bubble = TextView(this).apply {
            text = label
            textSize = 34f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(18, 34, 28))
            background = GradientDrawable().apply {
                setColor(Color.rgb(154, 230, 193))
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), Color.WHITE)
            }
            elevation = dp(8).toFloat()
        }
        val popup = PopupWindow(bubble, dp(62), dp(66), false).apply {
            isFocusable = false
            isTouchable = false
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            elevation = dp(8).toFloat()
        }
        keyPreviewPopup = popup
        popup.showAsDropDown(anchor, (anchor.width - dp(62)) / 2, -dp(keyHeight() + 68), Gravity.TOP)
    }

    private fun hideKeyPreview() {
        keyPreviewPopup?.dismiss()
        keyPreviewPopup = null
    }

    private fun playKeySound(view: View) {
        if (getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_SOUND, false)) {
            view.playSoundEffect(SoundEffectConstants.CLICK)
        }
    }

    private fun showKeyboardTools(itAnchor: View) {
        val dark = keyboardThemeDark()
        val menu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setBackgroundColor(if (dark) Color.rgb(20, 26, 34) else Color.rgb(247, 249, 252))
        }
        var popup: PopupWindow? = null
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)

        fun section(label: String) {
            menu.addView(TextView(this).apply {
                text = label
                textSize = 10f
                letterSpacing = 0.12f
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(8), dp(8), dp(3))
                setTextColor(if (dark) Color.rgb(145, 166, 184) else Color.rgb(91, 108, 126))
            }, LinearLayout.LayoutParams(-1, dp(30)))
        }

        fun item(label: String, action: () -> Unit) {
            val row = TextView(this).apply {
                text = label
                textSize = 13.5f
                gravity = Gravity.CENTER_VERTICAL
                minHeight = dp(48)
                setPadding(dp(12), dp(7), dp(12), dp(7))
                setTextColor(if (dark) Color.rgb(240, 245, 250) else Color.rgb(27, 38, 51))
                background = keyBackground(wide = true, toolbar = true)
                setOnClickListener {
                    popup?.dismiss()
                    tapFeedback(this)
                    action()
                }
            }
            menu.addView(row, LinearLayout.LayoutParams(-1, dp(48)).apply {
                bottomMargin = dp(3)
            })
        }

        section("CAPTURE & VAULT")
        item("Capture current clipboard") { captureClipboard(showToast = true) }
        item("Paste latest saved text") { pasteLatestText() }
        item("Open clipboard history") { openClipboardHistory() }

        section("PERSONAL TYPING")
        item(if (typingMemory.isLearningEnabled()) "Pause private word learning" else "Enable private word learning") {
            val memory = typingMemory
            memory.setLearningEnabled(!memory.isLearningEnabled())
            refreshKeyboard()
        }
        item("Clear personal typing data") {
            AlertDialog.Builder(this)
                .setTitle("Clear personal typing data?")
                .setMessage("This removes saved words and local word-pair counts from this device. It does not affect clipboard history or Vault files.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear") { _, _ ->
                    typingMemory.clear()
                    Toast.makeText(this, "Personal typing data cleared", Toast.LENGTH_SHORT).show()
                    refreshKeyboard()
                }
                .show()
        }

        section("TYPING BEHAVIOUR")
        item(if (prefs.getBoolean(KEY_AUTO_CAPITALIZE, true)) "Turn sentence capitalization off" else "Turn sentence capitalization on") {
            prefs.edit().putBoolean(KEY_AUTO_CAPITALIZE, !prefs.getBoolean(KEY_AUTO_CAPITALIZE, true)).apply()
            refreshKeyboard()
        }
        item(if (prefs.getBoolean(KEY_SMART_PUNCTUATION, true)) "Turn double-space period off" else "Turn double-space period on") {
            prefs.edit().putBoolean(KEY_SMART_PUNCTUATION, !prefs.getBoolean(KEY_SMART_PUNCTUATION, true)).apply()
            refreshKeyboard()
        }
        item(if (autoCorrectEnabled()) "Turn autocorrect off" else "Turn autocorrect on") { toggleAutoCorrect() }

        section("KEYBOARD LOOK & FEEL")
        item(if (numberRowEnabled()) "Hide number row" else "Show number row") {
            prefs.edit().putBoolean(KEY_NUMBER_ROW, !numberRowEnabled()).apply()
            refreshKeyboard()
        }
        item("Key size · ${keyHeight()} dp · change") { cycleKeyHeight() }
        item(if (keyboardThemeDark()) "Switch to light theme" else "Switch to dark theme") {
            prefs.edit().putBoolean(KEY_THEME_DARK, !keyboardThemeDark()).apply()
            refreshKeyboard()
        }
        item("One-hand mode · ${oneHandedLabel()} · change") { cycleOneHandedMode() }
        item(if (keyPreviewEnabled()) "Turn key preview off" else "Turn key preview on") {
            prefs.edit().putBoolean(KEY_KEY_PREVIEW, !keyPreviewEnabled()).apply()
            refreshKeyboard()
        }
        item(if (hapticsEnabled()) "Turn vibration off" else "Turn vibration on") { toggleHaptics() }
        item(if (keySoundsEnabled()) "Turn key sounds off" else "Turn key sounds on") {
            prefs.edit().putBoolean(KEY_SOUND, !keySoundsEnabled()).apply()
            refreshKeyboard()
        }

        section("ANDROID")
        item("Switch keyboard") {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }
        item("Hide keyboard") { requestHideSelf(0) }

        val scroll = ScrollView(this).apply {
            isFillViewport = false
            addView(menu)
        }
        val popupWidth = minOf(dp(320), (resources.displayMetrics.widthPixels - dp(24)).coerceAtLeast(dp(220)))
        val popupHeight = minOf(dp(460), (resources.displayMetrics.heightPixels - dp(120)).coerceAtLeast(dp(280)))
        popup = PopupWindow(scroll, popupWidth, popupHeight, true).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            elevation = dp(14).toFloat()
            showAsDropDown(itAnchor, itAnchor.width - popupWidth, 0, Gravity.TOP)
        }
    }

        private fun oneHandedSide(): Int = getSharedPreferences(PREFS, MODE_PRIVATE).getInt(KEY_ONE_HANDED, 0).coerceIn(0, 2)
    private fun oneHandedLabel(): String = when (oneHandedSide()) { 1 -> "Left"; 2 -> "Right"; else -> "Off" }
    private fun numberRowEnabled(): Boolean = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_NUMBER_ROW, false)
    private fun keyPreviewEnabled(): Boolean = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_KEY_PREVIEW, true)
    private fun keySoundsEnabled(): Boolean = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_SOUND, false)
    private fun keyboardThemeDark(): Boolean = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_THEME_DARK, true)

    private fun cycleOneHandedMode() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(KEY_ONE_HANDED, (oneHandedSide() + 1) % 3).apply()
        refreshKeyboard()
    }

    private fun commandSearchLabel(): String =
        if (commandQuery.isBlank()) "Find in Vault · type below" else "Vault search: $commandQuery"

    private fun handleCommandSearchKey(key: String) {
        when (key) {
            "⌫" -> commandQuery = commandQuery.dropLast(1)
            "space" -> commandQuery += " "
            "↵" -> { renderCommandEntries(); return }
            "⇧" -> { shifted = !shifted; refreshKeyboard(); return }
            "?123", "123" -> { symbols = true; refreshKeyboard(); return }
            "ABC" -> { symbols = false; refreshKeyboard(); return }
            "←", "→" -> return
            else -> commandQuery += if (shifted || capsLock) key.uppercase(inputLocale()) else key
        }
        if (key != "⇧" && shifted && !capsLock) shifted = false
        commandSearchView?.text = commandSearchLabel()
        renderCommandEntries()
    }

    private fun renderCommandEntries() {
        val container = commandList ?: return
        val query = commandQuery
        val revision = ++commandSearchRevision
        serviceScope.launch {
            delay(90)
            if (revision != commandSearchRevision || !commandsMode) return@launch
            val dao = VaultDatabase.get(applicationContext).dao()
            val entries = if (query.isBlank()) dao.observeAll().first() else dao.search(query).first()
            if (revision != commandSearchRevision || !commandsMode || commandList !== container) return@launch
            container.removeAllViews()
            if (entries.isEmpty()) {
                container.addView(TextView(this@VaultKeyboardService).apply {
                    text = if (query.isBlank()) "Your Vault is empty" else "No matching items"
                    setTextColor(if (keyboardThemeDark()) Color.LTGRAY else Color.DKGRAY)
                    textSize = 12f
                    gravity = Gravity.CENTER
                }, LinearLayout.LayoutParams(-1, dp(42)))
                return@launch
            }
            entries.take(3).forEach { entry ->
                val item = TextView(this@VaultKeyboardService).apply {
                    text = "${entry.name}  ·  ${entry.category}"
                    textSize = 12f
                    maxLines = 2
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(10), dp(4), dp(10), dp(4))
                    setTextColor(if (keyboardThemeDark()) Color.WHITE else Color.rgb(25, 32, 41))
                    background = keyBackground(wide = true, toolbar = true)
                    setOnClickListener {
                        tapFeedback(this)
                        if (entry.category in setOf("Text", "Code")) {
                            serviceScope.launch {
                                val text = runCatching { VaultRepository(applicationContext).readFullText(entry) }.getOrNull()
                                if (!text.isNullOrEmpty()) {
                                    insertText(text)
                                    commandsMode = false
                                    commandQuery = ""
                                    refreshKeyboard()
                                } else Toast.makeText(this@VaultKeyboardService, "Full text is unavailable", Toast.LENGTH_SHORT).show()
                            }
                        } else openVault()
                    }
                }
                container.addView(item, LinearLayout.LayoutParams(-1, dp(42)).apply { bottomMargin = dp(3) })
            }
        }
    }

    private fun showAlternates(anchor: View, alternatives: List<String>) {
        if (alternatives.isEmpty()) return
        hideKeyPreview()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(5), dp(5), dp(5), dp(5))
            setBackgroundColor(if (keyboardThemeDark()) Color.rgb(31, 39, 49) else Color.rgb(235, 240, 246))
        }
        var popup: PopupWindow? = null
        alternatives.distinct().forEach { value ->
            val option = TextView(this).apply {
                text = value
                textSize = 22f
                gravity = Gravity.CENTER
                setTextColor(if (keyboardThemeDark()) Color.WHITE else Color.rgb(25, 32, 41))
                background = keyBackground(wide = true)
                setPadding(dp(12), dp(8), dp(12), dp(8))
                setOnClickListener {
                    tapFeedback(this)
                    playKeySound(this)
                    currentInputConnection?.commitText(if (shifted || capsLock) value.uppercase(inputLocale()) else value, 1)
                    popup?.dismiss()
                    if (shifted && !capsLock) { shifted = false; refreshKeyboard() }
                }
            }
            content.addView(option, LinearLayout.LayoutParams(-2, dp(52)).apply { setMargins(dp(2), 0, dp(2), 0) })
        }
        popup = PopupWindow(content, -2, -2, true).apply {
            isOutsideTouchable = true
            elevation = dp(12).toFloat()
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            showAsDropDown(anchor, -dp(18), -dp(keyHeight() + 58), Gravity.TOP)
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
        if (commandsMode) { handleCommandSearchKey(key); return }
        if (emojiMode) {
            if (key == "⌫") currentInputConnection?.deleteSurroundingText(1, 0)
            else if (key !in setOf("space", "↵")) currentInputConnection?.commitText(key, 1)
            emojiMode = false
            refreshKeyboard()
            return
        }
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
                lastCommittedWord = ""
                requestWordSuggestions(typedWord.toString())
                renderSuggestions()
            }
            "←" -> sendCursorKey(KeyEvent.KEYCODE_DPAD_LEFT)
            "→" -> sendCursorKey(KeyEvent.KEYCODE_DPAD_RIGHT)
            "space" -> handleSmartSpace()
            "↵" -> {
                val action = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
                if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) currentInputConnection?.performEditorAction(action)
                else currentInputConnection?.commitText("\n", 1)
                if (typedWord.isNotBlank()) {
                    learnWord(typedWord.toString(), previousWordForPrediction)
                }
                typedWord.clear()
                lastCommittedWord = ""
                previousWordForPrediction = ""
                currentSuggestions = emptyList()
                shifted = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_AUTO_CAPITALIZE, true)
                lastSpaceAt = 0L
                renderSuggestions()
                refreshKeyboard()
            }
            else -> {
                val value = if (shifted || capsLock) key.uppercase(inputLocale()) else key
                currentInputConnection?.commitText(value, 1)
                if (value.length == 1 && value[0].isLetter()) {
                    typedWord.append(value)
                    lastCommittedWord = ""
                    requestWordSuggestions(typedWord.toString())
                } else {
                    if (typedWord.isNotBlank()) {
                        learnWord(typedWord.toString(), previousWordForPrediction)
                        previousWordForPrediction = typedWord.toString()
                    }
                    typedWord.clear()
                    lastCommittedWord = ""
                    activeSpellCheckWord = ""
                    currentSuggestions = emptyList()
                }
                if (value in setOf(".", "?", "!")) {
                    shifted = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_AUTO_CAPITALIZE, true)
                    lastSpaceAt = 0L
                    refreshKeyboard()
                } else if (shifted && !capsLock) {
                    shifted = false
                    refreshKeyboard()
                }
                renderSuggestions()
            }
        }
    }

    private fun requestWordSuggestions(word: String) {
        spellCheckGeneration++
        val requestId = spellCheckGeneration
        currentSuggestions = emptyList()
        if (word.length < 2 || !isSafeForSuggestions(currentInputEditorInfo) || spellChecker == null) {
            activeSpellCheckWord = ""
            renderSuggestions()
            return
        }
        activeSpellCheckWord = word
        spellChecker?.getSuggestions(arrayOf(TextInfo(word, requestId, requestId)), 3, false)
    }

    private fun learnWord(word: String, previousWord: String = "") {
        // Never persist text from password, email, URL, numeric, autocomplete or other
        // fields where suggestions are disabled, even when a word ends at punctuation.
        if (!isSafeForSuggestions(currentInputEditorInfo)) return
        typingMemory.learn(selectedLanguageCode(), word, previousWord)
    }

    private fun handleSmartSpace() {
        val now = android.os.SystemClock.uptimeMillis()
        if (getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_SMART_PUNCTUATION, true) &&
            typedWord.isEmpty() && lastCommittedWord.isNotBlank() &&
            lastSpaceAt > 0L && now - lastSpaceAt in 1L..500L
        ) {
            currentInputConnection?.deleteSurroundingText(1, 0)
            currentInputConnection?.commitText(". ", 1)
            typedWord.clear()
            lastCommittedWord = ""
            previousWordForPrediction = ""
            currentSuggestions = emptyList()
            activeSpellCheckWord = ""
            spellCheckGeneration++
            lastSpaceAt = 0L
            shifted = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_AUTO_CAPITALIZE, true)
            refreshKeyboard()
            return
        }
        commitSpace()
        lastSpaceAt = now
    }

    private fun commitSpace() {
        val word = typedWord.toString()
        if (word.isNotBlank() && isSafeForSuggestions(currentInputEditorInfo)) {
            learnWord(word, previousWordForPrediction)
            lastCommittedWord = word
            previousWordForPrediction = word
            activeSuggestionInputGeneration = inputGeneration
            requestWordSuggestions(word)
        } else {
            lastCommittedWord = ""
            activeSpellCheckWord = ""
            currentSuggestions = emptyList()
            spellCheckGeneration++
        }
        currentInputConnection?.commitText(" ", 1)
        typedWord.clear()
        renderSuggestions()
    }

    private fun applySuggestion(value: String) {
        val composingWord = typedWord.toString()
        val committedWord = composingWord.isEmpty() && lastCommittedWord.isNotBlank()
        val word = composingWord.ifBlank { lastCommittedWord }
        if (word.isBlank()) return
        currentInputConnection?.let { connection ->
            connection.deleteSurroundingText(word.length + if (committedWord) 1 else 0, 0)
            connection.commitText("$value ", 1)
        }
        learnWord(value, previousWordForPrediction)
        typedWord.clear()
        lastCommittedWord = value
        previousWordForPrediction = value
        activeSpellCheckWord = ""
        spellCheckGeneration++
        currentSuggestions = emptyList()
        renderSuggestions()
    }

    private fun renderSuggestions() {
        val row = suggestionRow ?: return
        row.removeAllViews()
        if (!isSafeForSuggestions(currentInputEditorInfo)) {
            val hint = TextView(this).apply {
                text = selectedLanguageLabel()
                textSize = 11f
                gravity = Gravity.CENTER
                setTextColor(if (keyboardThemeDark()) Color.rgb(115, 127, 143) else Color.rgb(130, 141, 154))
            }
            row.addView(hint, LinearLayout.LayoutParams(-1, -1))
            return
        }
        val typed = typedWord.toString()
        val memory = typingMemory
        val language = selectedLanguageCode()
        val learnedVocabulary = memory.dictionaryWords(language)
        val learnedCompletions = if (typed.isNotBlank()) memory.completions(language, typed, 5) else emptyList()
        val localCorrections = if (typed.isNotBlank() && currentSuggestions.isEmpty()) {
            KeyboardSuggestionEngine.nearbyCorrections(language, typed, additionalWords = learnedVocabulary)
        } else emptyList()
        val correcting = currentSuggestions.isNotEmpty() || localCorrections.isNotEmpty()
        // Read only a short, transient window from the active safe text field. This keeps
        // predictions aligned with the real cursor after edits, pastes or cursor movement.
        val contextualWord = contextWordBeforeCursor().ifBlank { previousWordForPrediction }
        val candidates = when {
            typed.isNotBlank() -> (listOf(typed) + currentSuggestions + localCorrections +
                learnedCompletions + KeyboardSuggestionEngine.completePrefix(language, typed, additionalWords = learnedCompletions)).distinct().take(3)
            correcting -> (listOf(lastCommittedWord) + currentSuggestions).distinct().take(3)
            contextualWord.isNotBlank() ->
                (memory.nextWords(language, contextualWord) +
                    KeyboardSuggestionEngine.nextWordSuggestions(language, contextualWord)).distinct().take(3)
            else -> emptyList()
        }

        if (candidates.isEmpty()) {
            val hint = TextView(this).apply {
                text = selectedLanguageLabel()
                textSize = 11f
                letterSpacing = 0.04f
                gravity = Gravity.CENTER
                setTextColor(if (keyboardThemeDark()) Color.rgb(143, 155, 171) else Color.rgb(112, 124, 139))
            }
            row.addView(hint, LinearLayout.LayoutParams(-1, -1))
            return
        }

        candidates.forEachIndexed { index, candidate ->
            if (index > 0) {
                row.addView(View(this).apply {
                    setBackgroundColor(if (keyboardThemeDark()) Color.rgb(53, 62, 74) else Color.rgb(205, 213, 222))
                }, LinearLayout.LayoutParams(dp(1), dp(20)).apply {
                    gravity = Gravity.CENTER_VERTICAL
                })
            }
            val isCurrent = (typed.isNotBlank() || correcting) && candidate.equals(typed.ifBlank { lastCommittedWord }, ignoreCase = true)
            val item = TextView(this).apply {
                text = candidate
                textSize = if (isCurrent) 13f else 13.5f
                typeface = if (isCurrent) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(
                    when {
                        isCurrent && keyboardThemeDark() -> Color.rgb(177, 244, 211)
                        isCurrent -> Color.rgb(27, 105, 75)
                        keyboardThemeDark() -> Color.rgb(226, 232, 240)
                        else -> Color.rgb(45, 55, 68)
                    }
                )
                setPadding(dp(5), 0, dp(5), 0)
                if (isCurrent) background = suggestionPillBackground()
                setOnLongClickListener {
                    if (typed.isNotBlank() && candidate.equals(typed, ignoreCase = true)) {
                        LocalTypingMemory(this@VaultKeyboardService).addToDictionary(selectedLanguageCode(), typed)
                        Toast.makeText(this@VaultKeyboardService, "Added to personal dictionary", Toast.LENGTH_SHORT).show()
                        true
                    } else false
                }
                setOnClickListener {
                    when {
                        typed.isNotBlank() -> {
                            if (candidate.equals(typed, ignoreCase = true)) commitSpace() else applySuggestion(candidate)
                        }
                        correcting -> {
                            if (!candidate.equals(lastCommittedWord, ignoreCase = true)) applySuggestion(candidate)
                        }
                        else -> applyNextWordSuggestion(candidate, contextualWord)
                    }
                }
            }
            row.addView(item, LinearLayout.LayoutParams(0, -1, 1f))
        }
    }

    private fun suggestionPillBackground(): android.graphics.drawable.Drawable {
        val dark = keyboardThemeDark()
        return GradientDrawable().apply {
            setColor(if (dark) Color.rgb(37, 62, 57) else Color.rgb(210, 239, 225))
            cornerRadius = dp(9).toFloat()
        }
    }

    private fun contextWordBeforeCursor(): String {
        if (!isSafeForSuggestions(currentInputEditorInfo)) return ""
        val before = currentInputConnection?.getTextBeforeCursor(80, 0)?.toString().orEmpty()
        val normalized = before.trimEnd()
        return Regex("""[\p{L}][\p{L}'’\-]*$""")
            .find(normalized)?.value
            ?.lowercase(inputLocale())
            .orEmpty()
    }

    private fun applyNextWordSuggestion(value: String, contextWord: String) {
        learnWord(value, contextWord)
        currentInputConnection?.commitText("$value ", 1)
        typedWord.clear()
        lastCommittedWord = value
        previousWordForPrediction = value
        activeSpellCheckWord = ""
        currentSuggestions = emptyList()
        spellCheckGeneration++
        renderSuggestions()
    }

    private fun selectedLanguageLabel(): String = when (selectedLanguageCode()) {
        "pl" -> "Polski"; "tr" -> "Türkçe"; else -> "English"
    }

    private fun sendCursorKey(code: Int) {
        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        typedWord.clear()
        lastCommittedWord = ""
        previousWordForPrediction = ""
        activeSpellCheckWord = ""
        currentSuggestions = emptyList()
        spellCheckGeneration++
        renderSuggestions()
    }

    private fun isSafeForSuggestions(info: EditorInfo?): Boolean {
        if (info == null) return false
        val inputType = info.inputType
        val inputClass = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        if (inputClass != InputType.TYPE_CLASS_TEXT) return false
        if (inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0) return false
        if (inputType and InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE != 0) return false
        if (variation in setOf(
                InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
                InputType.TYPE_TEXT_VARIATION_URI,
                InputType.TYPE_TEXT_VARIATION_FILTER
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
        activeSpellCheckWord = ""
        currentSuggestions = emptyList()
        activeSuggestionInputGeneration = -1
        spellCheckGeneration++
        if (isSafeForSuggestions(currentInputEditorInfo)) {
            val manager = getSystemService(Context.TEXT_SERVICES_MANAGER_SERVICE) as? TextServicesManager
            spellChecker = runCatching { manager?.newSpellCheckerSession(null, inputLocale(), this, true) }.getOrNull()
            val wordToCheck = typedWord.toString().ifBlank { lastCommittedWord }
            if (wordToCheck.isNotBlank()) requestWordSuggestions(wordToCheck)
        }
        refreshKeyboard()
    }

    private fun keyHeight(): Int = getSharedPreferences(PREFS, MODE_PRIVATE).getInt(KEY_HEIGHT, 50).coerceIn(44, 60)
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
        val sizes = listOf(46, 50, 54, 58)
        val current = keyHeight()
        val next = sizes.firstOrNull { it > current } ?: sizes.first()
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(KEY_HEIGHT, next).apply()
        refreshKeyboard()
    }

    private fun tapFeedback(view: View) {
        if (hapticsEnabled()) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private fun refreshKeyboard() {
        hideKeyPreview()
        setInputView(buildKeyboard())
    }

    private fun clipboardSearchLabel(): String =
        if (clipboardQuery.isBlank()) "Find in clipboard · type below" else "Search: $clipboardQuery"

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
                    setBackgroundColor(if (keyboardThemeDark()) Color.rgb(29, 36, 44) else Color.rgb(238, 242, 247))
                }
                val preview = TextView(this@VaultKeyboardService).apply {
                    text = entry.preview.replace("\n", " ").take(100)
                    setTextColor(if (keyboardThemeDark()) Color.WHITE else Color.rgb(30, 39, 51)); textSize = 11.5f; maxLines = 2
                    setPadding(dp(7), dp(4), dp(4), dp(4))
                    setOnClickListener { pasteClipboardEntry(entry) }
                }
                row.addView(preview, LinearLayout.LayoutParams(0, dp(52), 1f))
                row.addView(toolbarButton(if (entry.contentType == "text") "Paste" else "Open") {
                    if (entry.contentType == "text") pasteClipboardEntry(entry) else openClipboardHistory()
                }, LinearLayout.LayoutParams(dp(58), dp(44)))
                row.addView(toolbarButton(if (entry.isPinned) "★" else "☆") {
                    serviceScope.launch {
                        ClipboardHistoryRepository(applicationContext).setPinned(entry.id, !entry.isPinned)
                        renderClipboardEntries()
                    }
                }, LinearLayout.LayoutParams(dp(42), dp(44)))
                container.addView(row, LinearLayout.LayoutParams(-1, dp(54)).apply { bottomMargin = dp(3) })
            }
        }
    }

    private fun toolbarButton(label: String, action: () -> Unit) = TextView(this).apply {
        text = label; gravity = Gravity.CENTER; textSize = 11f
        setTextColor(if (keyboardThemeDark()) Color.WHITE else Color.rgb(28, 38, 50))
        background = keyBackground(wide = true)
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
        const val KEY_NUMBER_ROW = "keyboard_number_row"
        const val KEY_KEY_PREVIEW = "keyboard_key_preview"
        const val KEY_SOUND = "keyboard_key_sound"
        const val KEY_THEME_DARK = "keyboard_theme_dark"
        const val KEY_ONE_HANDED = "keyboard_one_handed"
        const val KEY_AUTO_CAPITALIZE = "keyboard_auto_capitalize"
        const val KEY_SMART_PUNCTUATION = "keyboard_smart_punctuation"
    }
}
