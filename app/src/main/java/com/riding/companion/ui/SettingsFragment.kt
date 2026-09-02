package com.riding.companion.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.riding.companion.R
import com.riding.companion.audio.VoiceController
import com.riding.companion.data.AppConfig
import com.riding.companion.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadConfig()
        binding.btnSave.setOnClickListener { saveConfig() }

        val vName = try {
            requireContext().packageManager
                .getPackageInfo(requireContext().packageName, 0).versionName
        } catch (_: Exception) {
            "?"
        }
        binding.tvCurrentVersion.text = getString(R.string.update_current, vName)
        binding.btnCheckUpdate.setOnClickListener {
            val act = activity as? AppCompatActivity ?: return@setOnClickListener
            UpdateManager.checkForUpdate(act, manual = true)
        }
    }

    private fun loadConfig() {
        binding.etBaseUrl.setText(AppConfig.llmBaseUrl)
        binding.etApiKey.setText(AppConfig.llmApiKey)
        binding.etModel.setText(AppConfig.llmModel)
        binding.etSystemPrompt.setText(AppConfig.systemPrompt)
        binding.etTemp.setText(AppConfig.temperature.toString())
        binding.sbTtsRate.progress = ((AppConfig.ttsRate - 0.5f) / 1.5f * 30).toInt().coerceIn(0, 30)
        binding.sbAutoVolume.progress = AppConfig.cyclingAutoVolume.coerceIn(0, 100)
        binding.sbDuck.progress = AppConfig.duckLevel.coerceIn(0, 100)
        binding.swLocalCmd.isChecked = AppConfig.localCommandMatching
        binding.swBeep.isChecked = AppConfig.cyclingBeep
    }

    private fun saveConfig() {
        AppConfig.llmBaseUrl = binding.etBaseUrl.text.toString().trim()
        AppConfig.llmApiKey = binding.etApiKey.text.toString().trim()
        AppConfig.llmModel = binding.etModel.text.toString().trim()
        AppConfig.systemPrompt = binding.etSystemPrompt.text.toString().trim()
        AppConfig.temperature =
            (binding.etTemp.text.toString().toFloatOrNull() ?: 0.8f).coerceIn(0f, 2f)
        AppConfig.ttsRate = 0.5f + binding.sbTtsRate.progress / 30f * 1.5f
        AppConfig.cyclingAutoVolume = binding.sbAutoVolume.progress
        AppConfig.duckLevel = binding.sbDuck.progress
        AppConfig.localCommandMatching = binding.swLocalCmd.isChecked
        AppConfig.cyclingBeep = binding.swBeep.isChecked
        VoiceController.applyTtsRate()
        Toast.makeText(requireContext(), R.string.settings_saved, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
