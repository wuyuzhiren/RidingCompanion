package com.riding.companion.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.riding.companion.R
import com.riding.companion.audio.VoiceController
import com.riding.companion.data.AppConfig
import com.riding.companion.data.ChatEngine
import com.riding.companion.databinding.FragmentSettingsBinding
import kotlinx.coroutines.launch

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
        binding.btnFetchModels.setOnClickListener { fetchModels() }

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
        when (AppConfig.currentCharacter) {
            2 -> binding.rgCharacter.check(R.id.rbChar2)
            3 -> binding.rgCharacter.check(R.id.rbChar3)
            else -> binding.rgCharacter.check(R.id.rbChar1)
        }
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
        AppConfig.currentCharacter = when (binding.rgCharacter.checkedRadioButtonId) {
            R.id.rbChar2 -> 2
            R.id.rbChar3 -> 3
            else -> 1
        }
        VoiceController.applyTtsRate()
        Toast.makeText(requireContext(), R.string.settings_saved, Toast.LENGTH_SHORT).show()
        if (AppConfig.llmBaseUrl.isNotBlank()) {
            fetchModels()
        }
    }

    /**
     * 自动识别服务商支持的模型列表，填充到下拉框并弹出候选。
     */
    private fun fetchModels() {
        val b = _binding ?: return
        if (b.etBaseUrl.text.toString().trim().isEmpty()) {
            Toast.makeText(requireContext(), R.string.fetch_models_need_url, Toast.LENGTH_SHORT).show()
            return
        }
        b.btnFetchModels.isEnabled = false
        b.btnFetchModels.text = getString(R.string.fetching_models)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val models = ChatEngine.fetchModels()
                if (models.isEmpty()) throw RuntimeException(getString(R.string.fetch_models_empty))
                val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, models)
                b.etModel.setAdapter(adapter)
                b.etModel.setText(models.first())
                b.etModel.showDropDown()
                Toast.makeText(
                    requireContext(),
                    getString(R.string.fetch_models_ok, models.size),
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.fetch_models_fail, e.message ?: "未知错误"),
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                b.btnFetchModels.isEnabled = true
                b.btnFetchModels.text = getString(R.string.settings_fetch_models)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
