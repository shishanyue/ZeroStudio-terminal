/*
 * Terminal UI 容器。
 * 负责分发安装、初始化指令执行及就绪后的 UI。
 * 
 * @author android_zero
 */
package com.rustywarfare.modstudio.app.terminal.compose

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels

class TerminalFragment : Fragment() {

    private val viewModel: TerminalViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // 自动触发检查与安装流程
        if (viewModel.state is TerminalState.Checking) {
            viewModel.checkAndSetup(requireContext())
        }

        return ComposeView(requireContext()).apply {
            setContent {
                /*
                 * 副作用监听：
                 * 当环境安装且指令全部执行成功（SetupFinished）后，自动关闭 Activity 回到主界面。
                 */
                LaunchedEffect(viewModel.state) {
                    if (viewModel.state is TerminalState.SetupFinished) {
                        requireActivity().finish()
                    }
                }

                MaterialTheme(colorScheme = darkColorScheme()) {
                    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                        when (val state = viewModel.state) {
                            is TerminalState.Checking -> LoadingScreen("Checking Ubuntu Environment...")

                            is TerminalState.Downloading -> DownloadingScreen(state)

                            // 阶段一：显示解压过程日志
                            is TerminalState.SettingUp -> SetupScreen(state.setupSession)

                            // 正常使用状态
                            is TerminalState.Ready -> TerminalScreen(viewModel, this@TerminalFragment)

                            // 临时过渡状态
                            is TerminalState.SetupFinished -> LoadingScreen("Initialization complete! Returning...")

                            is TerminalState.Error -> ErrorScreen(state.message) { viewModel.checkAndSetup(requireContext()) }
                        }
                    }
                }
            }
        }
    }
}
