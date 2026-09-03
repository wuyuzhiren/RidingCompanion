package com.riding.companion.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.webkit.WebViewAssetLoader
import com.riding.companion.R
import com.riding.companion.audio.VoiceController
import com.riding.companion.cycling.BeepHelper
import com.riding.companion.cycling.CommandRouter
import com.riding.companion.cycling.CyclingService
import com.riding.companion.data.AppConfig
import com.riding.companion.data.ChatEngine
import com.riding.companion.databinding.FragmentChatBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin
import kotlin.random.Random

class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!
    private val adapter = ChatAdapter()
    private val history = mutableListOf<ChatEngine.Msg>()

    private var breathingAnim: android.animation.AnimatorSet? = null
    private var glowAnim: android.animation.ObjectAnimator? = null
    private var mouthAnim: android.animation.ValueAnimator? = null
    private var mouthValue = 0f
    private var isSpeaking = false
    private var blinkJob: Job? = null

    // Live2D 形象
    private var live2dActive = false
    private var live2dWebView: WebView? = null

    // 当前角色四帧资源：闭嘴(待机)/张嘴(说话)/眨眼/害羞反应
    private var curClosedRes = R.drawable.avatar1_closed
    private var curOpenRes = R.drawable.avatar1_open
    private var curBlinkRes = R.drawable.avatar1_blink
    private var curReactRes = R.drawable.avatar1_react

    private val recordLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                VoiceController.startListening()
            } else {
                _binding?.let { it.statusText.text = "需要录音权限才能对话" }
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.chatList.layoutManager = LinearLayoutManager(requireContext())
        binding.chatList.adapter = adapter
        if (adapter.items.isEmpty()) {
            adapter.add(ChatItem(false, "你好！我是骑行小智。点下方麦克风跟我说话，或在输入框打字；点我也会有反应哦～"))
        }
        startBreathing()
        applyCharacter()
        initLive2d()
        setupTouchInteraction()
        binding.cyclingSwitch.isChecked = AppConfig.cyclingMode
        binding.cyclingSwitch.setOnCheckedChangeListener { _, checked ->
            AppConfig.cyclingMode = checked
            if (checked) {
                CyclingService.start(requireContext())
                setStatus("骑行模式已开启：本地指令直执 + 提示音反馈，音乐指令无需网络")
            } else {
                CyclingService.stop(requireContext())
                setStatus(getString(R.string.status_idle))
            }
        }
        binding.micButton.setOnClickListener {
            if (VoiceController.isListening()) {
                VoiceController.stopListening()
            } else {
                VoiceController.cancelListening()
                if (!hasRecordPermission()) {
                    recordLauncher.launch(Manifest.permission.RECORD_AUDIO)
                } else {
                    VoiceController.startListening()
                }
            }
        }
        binding.btnSend.setOnClickListener {
            val text = binding.etInput.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener
            binding.etInput.setText("")
            handleUserInput(text)
        }
        VoiceController.onResult = { text -> onVoiceResult(text) }
        VoiceController.onError = { err -> setStatus(err) }
        VoiceController.onListeningChange = { listening ->
            if (listening) {
                setStatus(getString(R.string.status_listening))
                setListening(true)
            } else {
                setStatus(if (AppConfig.cyclingMode) "已进入骑行模式，等待语音指令…" else getString(R.string.status_idle))
                setListening(false)
            }
        }
        VoiceController.onSpeakingChange = { speaking ->
            if (speaking) setStatus(getString(R.string.status_speaking))
            setSpeaking(speaking)
        }
    }

    private fun applyCharacter() {
        val c = AppConfig.currentCharacter
        live2dActive = (c == 0)
        val b = _binding ?: return
        if (live2dActive) {
            b.live2dView.visibility = View.VISIBLE
            b.avatarClosed.visibility = View.INVISIBLE
            b.avatarOpen.visibility = View.INVISIBLE
            b.avatarName.setText(R.string.char_live_name)
            return
        }
        b.live2dView.visibility = View.GONE
        b.avatarClosed.visibility = View.VISIBLE
        b.avatarOpen.visibility = View.VISIBLE
        curClosedRes = when (c) { 2 -> R.drawable.avatar2_closed; 3 -> R.drawable.avatar3_closed; else -> R.drawable.avatar1_closed }
        curOpenRes = when (c) { 2 -> R.drawable.avatar2_open; 3 -> R.drawable.avatar3_open; else -> R.drawable.avatar1_open }
        curBlinkRes = when (c) { 2 -> R.drawable.avatar2_blink; 3 -> R.drawable.avatar3_blink; else -> R.drawable.avatar1_blink }
        curReactRes = when (c) { 2 -> R.drawable.avatar2_react; 3 -> R.drawable.avatar3_react; else -> R.drawable.avatar1_react }
        b.avatarClosed.setImageResource(curClosedRes)
        b.avatarOpen.setImageResource(curOpenRes)
        b.avatarClosed.alpha = 1f
        b.avatarOpen.alpha = 0f
        mouthValue = 0f
        val name = when (c) { 2 -> R.string.char2_name; 3 -> R.string.char3_name; else -> R.string.char1_name }
        b.avatarName.setText(name)
        startBlinkLoop()
    }

    /** 初始化 Live2D WebView（WebViewAssetLoader 提供 https 访问 assets，保证 fetch 正常） */
    private fun initLive2d() {
        val wv = binding.live2dView
        live2dWebView = wv
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.settings.allowFileAccess = false
        wv.settings.mediaPlaybackRequiresUserGesture = false
        wv.setBackgroundColor(Color.TRANSPARENT)
        wv.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(requireContext()))
            .build()
        wv.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                loader.shouldInterceptRequest(request.url)
        }
        wv.loadUrl("https://appassets.androidplatform.net/assets/live2d/index.html")
    }

    private fun driveLive2dMouth(v: Float) {
        live2dWebView?.evaluateJavascript("window.Live2D_setMouth($v)", null)
    }

    private fun driveLive2dSpeaking(b: Boolean) {
        live2dWebView?.evaluateJavascript("window.Live2D_setSpeaking($b)", null)
    }

    private fun startBreathing() {
        val sx = android.animation.ObjectAnimator.ofFloat(binding.avatarContainer, "scaleX", 1.0f, 1.03f).apply {
            duration = 3600; repeatMode = android.animation.ValueAnimator.REVERSE
            repeatCount = android.animation.ValueAnimator.INFINITE; interpolator = AccelerateDecelerateInterpolator()
        }
        val sy = android.animation.ObjectAnimator.ofFloat(binding.avatarContainer, "scaleY", 1.0f, 1.03f).apply {
            duration = 3600; repeatMode = android.animation.ValueAnimator.REVERSE
            repeatCount = android.animation.ValueAnimator.INFINITE; interpolator = AccelerateDecelerateInterpolator()
        }
        breathingAnim = android.animation.AnimatorSet().apply { playTogether(sx, sy); start() }
    }

    /** 待机眨眼：随机间隔 2.5~4 秒眨眼一次（说话时跳过） */
    private fun startBlinkLoop() {
        blinkJob?.cancel()
        blinkJob = viewLifecycleOwner.lifecycleScope.launch {
            while (true) {
                delay(Random.nextLong(2500, 4200))
                if (isSpeaking) continue
                val b = _binding ?: continue
                b.avatarClosed.setImageResource(curBlinkRes)
                b.avatarClosed.postDelayed({
                    if (!isSpeaking) _binding?.avatarClosed?.setImageResource(curClosedRes)
                }, 160)
            }
        }
    }

    /** 点角色不同部位 → 害羞/惊讶反应帧 + 聊天气泡 */
    private fun setupTouchInteraction() {
        val gd = GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                handleTouch(e)
                return true
            }
        })
        binding.avatarContainer.setOnTouchListener { _, event ->
            // Live2D 模式由 WebView 内部 hit 检测处理点击互动
            if (live2dActive) false else gd.onTouchEvent(event)
        }
    }

    private fun handleTouch(e: MotionEvent) {
        val b = _binding ?: return
        val h = b.avatarContainer.height.toFloat()
        val relY = e.y / h
        val zone = when {
            relY < 0.40f -> "head"
            relY < 0.72f -> "face"
            else -> "body"
        }
        val c = AppConfig.currentCharacter
        val reply = when (c) {
            2 -> when (zone) {
                "head" -> "嘿嘿，别弄乱我头发啦～"
                "face" -> "嗯？我脸上有什么吗？"
                else -> "怎么啦，有事找我？"
            }
            3 -> when (zone) {
                "head" -> "嗯，别闹。"
                "face" -> "这样盯着看，我会害羞的。"
                else -> "说吧，找我有事？"
            }
            else -> when (zone) {
                "head" -> "诶？！别摸头啦…"
                "face" -> "呜…脸会红的…"
                else -> "呀！吓我一跳…"
            }
        }
        b.avatarClosed.setImageResource(curReactRes)
        b.avatarClosed.animate().alpha(1f).setDuration(80).start()
        b.avatarClosed.postDelayed({
            if (!isSpeaking) _binding?.avatarClosed?.setImageResource(curClosedRes)
        }, 1300)
        adapter.add(ChatItem(false, "（你轻轻碰了碰她）$reply"))
        binding.chatList.scrollToPosition(adapter.items.size - 1)
    }

    private fun setSpeaking(speaking: Boolean) {
        val b = _binding ?: return
        isSpeaking = speaking
        if (speaking) {
            glowAnim?.cancel()
            glowAnim = android.animation.ObjectAnimator.ofFloat(b.avatarGlow, "alpha", 0f, 0.7f).apply {
                duration = 900; repeatMode = android.animation.ValueAnimator.REVERSE
                repeatCount = android.animation.ValueAnimator.INFINITE; interpolator = AccelerateDecelerateInterpolator(); start()
            }
            mouthAnim?.cancel()
            mouthAnim = android.animation.ValueAnimator.ofFloat(0f, 100f).apply {
                duration = 5000; repeatCount = android.animation.ValueAnimator.INFINITE
                addUpdateListener { anim ->
                    val t = anim.animatedValue as Float
                    val v = 0.5f + 0.42f * sin(t * 0.9f).toFloat() + 0.28f * sin(t * 2.1f + 1.5f).toFloat()
                    mouthValue = v.coerceIn(0f, 1f)
                    if (live2dActive) {
                        driveLive2dMouth(mouthValue)
                        driveLive2dSpeaking(true)
                    } else {
                        b.avatarClosed.alpha = 1f - mouthValue
                        b.avatarOpen.alpha = mouthValue
                    }
                    // 说话时轻微点头
                    b.avatarContainer.rotation = (sin(t * 0.7f) * 3f).toFloat()
                }
                start()
            }
        } else {
            glowAnim?.cancel(); glowAnim = null
            b.avatarGlow.animate().alpha(0f).setDuration(300).start()
            mouthAnim?.cancel(); mouthAnim = null
            if (live2dActive) {
                driveLive2dMouth(0f)
                driveLive2dSpeaking(false)
            } else {
                val sv = mouthValue
                android.animation.ValueAnimator.ofFloat(sv, 0f).apply {
                    duration = 250
                    addUpdateListener { anim ->
                        mouthValue = anim.animatedValue as Float
                        b.avatarClosed.alpha = 1f - mouthValue
                        b.avatarOpen.alpha = mouthValue
                    }
                    start()
                }
                // 说完回到待机闭嘴帧
                b.avatarClosed.postDelayed({ _binding?.avatarClosed?.setImageResource(curClosedRes) }, 260)
            }
            b.avatarContainer.animate().rotation(0f).setDuration(250).start()
        }
    }

    private fun setListening(listening: Boolean) {
        val b = _binding ?: return
        if (listening) {
            glowAnim?.cancel(); glowAnim = null
            b.avatarGlow.animate().alpha(0.35f).setDuration(400).setInterpolator(DecelerateInterpolator()).start()
        } else {
            b.avatarGlow.animate().alpha(0f).setDuration(300).start()
        }
    }

    private fun hasRecordPermission(): Boolean =
        ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun setStatus(s: String) { binding.statusText.text = s }

    private fun onVoiceResult(text: String) {
        handleUserInput(text)
    }

    private fun handleUserInput(text: String) {
        adapter.add(ChatItem(true, text))
        history.add(ChatEngine.Msg("user", text))
        if (history.size > 20) history.removeAt(0)
        if (AppConfig.cyclingMode && AppConfig.localCommandMatching) {
            val cmd = CommandRouter.match(text)
            if (cmd != null) {
                CommandRouter.execute(requireContext(), cmd)
                if (AppConfig.cyclingBeep) BeepHelper.beep(requireContext())
                val reply = "✓ 已执行：${cmd.label}"
                adapter.add(ChatItem(false, reply))
                history.add(ChatEngine.Msg("assistant", reply))
                setStatus("已执行：${cmd.label}")
                return
            }
        }
        if (AppConfig.llmBaseUrl.isBlank()) {
            val reply = "我还没接入大模型，请先到「设置」页配置接口地址和 Key；也可以开启骑行模式试试本地语音指令。"
            adapter.add(ChatItem(false, reply))
            history.add(ChatEngine.Msg("assistant", reply))
            setStatus(getString(R.string.status_no_llm))
            VoiceController.speak(reply)
            return
        }
        sendToLLM()
    }

    private fun sendToLLM() {
        adapter.add(ChatItem(false, "…"))
        setStatus(getString(R.string.status_thinking))
        val msgs = mutableListOf<ChatEngine.Msg>()
        if (AppConfig.systemPrompt.isNotBlank()) msgs.add(ChatEngine.Msg("system", AppConfig.systemPrompt))
        msgs.addAll(history)
        binding.micButton.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            var reply = ""
            try {
                reply = ChatEngine.streamChat(msgs) { delta ->
                    reply += delta
                    adapter.updateLast(reply)
                    binding.chatList.scrollToPosition(adapter.items.size - 1)
                }
                adapter.updateLast(reply)
                history.add(ChatEngine.Msg("assistant", reply))
                binding.micButton.isEnabled = true
                VoiceController.speak(reply)
            } catch (e: Exception) {
                binding.micButton.isEnabled = true
                val err = "出错了：${e.message ?: "未知错误"}"
                adapter.updateLast(err)
                setStatus(err)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        _binding?.let {
            it.cyclingSwitch.isChecked = AppConfig.cyclingMode
            applyCharacter()
        }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            _binding?.let {
                it.cyclingSwitch.isChecked = AppConfig.cyclingMode
                applyCharacter()
            }
        }
    }

    override fun onDestroyView() {
        blinkJob?.cancel(); blinkJob = null
        breathingAnim?.cancel(); glowAnim?.cancel(); mouthAnim?.cancel()
        breathingAnim = null; glowAnim = null; mouthAnim = null
        _binding?.live2dView?.removeAllViews()
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        live2dWebView?.destroy()
        live2dWebView = null
        super.onDestroy()
    }
}
