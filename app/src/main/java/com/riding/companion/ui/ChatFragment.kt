package com.riding.companion.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.riding.companion.R
import com.riding.companion.audio.VoiceController
import com.riding.companion.cycling.BeepHelper
import com.riding.companion.cycling.CommandRouter
import com.riding.companion.cycling.CyclingService
import com.riding.companion.data.AppConfig
import com.riding.companion.data.ChatEngine
import com.riding.companion.databinding.FragmentChatBinding
import kotlinx.coroutines.launch
import kotlin.math.sin

class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!
    private val adapter = ChatAdapter()
    private val history = mutableListOf<ChatEngine.Msg>()

    private var breathingAnim: android.animation.AnimatorSet? = null
    private var glowAnim: android.animation.ObjectAnimator? = null
    private var mouthAnim: android.animation.ValueAnimator? = null
    private var mouthValue = 0f

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
            adapter.add(ChatItem(false, "你好！我是骑行小智。点下方麦克风跟我说话；开启骑行模式后，音乐和音量指令会在本地直执，风噪下更稳定。"))
        }
        startBreathing()
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

    private fun setSpeaking(speaking: Boolean) {
        val b = _binding ?: return
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
                    b.avatarClosed.alpha = 1f - mouthValue
                    b.avatarOpen.alpha = mouthValue
                }
                start()
            }
        } else {
            glowAnim?.cancel(); glowAnim = null
            b.avatarGlow.animate().alpha(0f).setDuration(300).start()
            mouthAnim?.cancel(); mouthAnim = null
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
        _binding?.let { it.cyclingSwitch.isChecked = AppConfig.cyclingMode }
    }

    override fun onDestroyView() {
        breathingAnim?.cancel(); glowAnim?.cancel(); mouthAnim?.cancel()
        breathingAnim = null; glowAnim = null; mouthAnim = null
        super.onDestroyView()
        _binding = null
    }
}