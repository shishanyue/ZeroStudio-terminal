/*
 * 终端界面核心显示逻辑。
 * 承载了从环境安装检查、资源下载、指令初始化到正常交互的全流程 UI 表现。
 *
 * 工作流程线路图：
 * 1. 状态分发：根据 ViewModel 的 state 决定显示加载、下载、安装还是操作界面。
 * 2. 终端注入：将 Android 原生 TerminalView 嵌入 Compose，并注入跨平台 Client 回调。
 * 3. 交互优化：解决键盘弹出、右键菜单、高斯模糊遮挡及会话切换崩溃问题。
 *
 * @author android_zero
 */
package com.rustywarfare.modstudio.app.terminal.compose

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import com.rustywarfare.modstudio.shared.termux.extrakeys.ExtraKeysConstants
import com.rustywarfare.modstudio.shared.termux.extrakeys.ExtraKeysInfo
import com.rustywarfare.modstudio.shared.termux.extrakeys.ExtraKeysView
import com.rustywarfare.modstudio.shared.termux.extrakeys.SpecialButton
import com.rustywarfare.modstudio.shared.termux.terminal.TermuxTerminalViewClientBase
import com.rustywarfare.modstudio.shared.termux.terminal.io.TerminalExtraKeys
import com.rustywarfare.modstudio.terminal.TerminalSession
import com.rustywarfare.modstudio.view.TerminalView

/** 虚拟按键布局配置 */
const val VIRTUAL_KEYS_JSON = """[[
    "ESC",
    {"key":"<","popup":""},
    {"key":">","popup":""},
    {"key":"BACKSLASH","popup":""},
    {"key":"=","popup":""},
    {"key":"^","popup":""},
    {"key":"$","popup":""},
    {"key":"(","popup":")"},
    {"key":"{","popup":"}"},
    {"key":"[","popup":"]"},
    "ENTER"
],[
    "TAB",
    {"key":"&","popup":""},
    {"key":";","popup":""},
    {"key":"/","popup":"~"},
    {"key":"%","popup":""},
    {"key":"*","popup":""},
    "HOME","UP","END","PGUP"
],[
    "CTRL","FN","ALT",
    {"key":"|","popup":""},
    {"key":"-","popup":"+"},
    {"key":"QUOTE","popup":""},
    "LEFT","DOWN","RIGHT","PGDN"
]]"""

// ==================== 主入口界面 ====================

/**
 * 正常运行模式下的终端屏幕
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(viewModel: TerminalViewModel, fragment: Fragment) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        // 顶部会话切换栏
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            // 修复 Issue 5: 对 Index 实行安全闭环检查，防止 IndexOutOfBoundsException
            val safeIndex = if (viewModel.sessions.isEmpty()) 0 else viewModel.currentIndex.coerceIn(0, maxOf(0, viewModel.sessions.lastIndex))

            ScrollableTabRow(
                selectedTabIndex = safeIndex,
                edgePadding = 8.dp,
                containerColor = Color.Transparent,
                modifier = Modifier.weight(1f)
            ) {
                if (viewModel.sessions.isEmpty()) {
                    Tab(selected = true, onClick = {}, text = { Text("No Session") })
                } else {
                    viewModel.sessions.forEachIndexed { index, session ->
                        Tab(
                            selected = safeIndex == index,
                            onClick = { viewModel.currentIndex = index },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(session.mSessionName ?: "Session ${index + 1}")
                                    Spacer(Modifier.width(8.dp))
                                    IconButton(
                                        onClick = { viewModel.closeSession(index) },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        )
                    }
                }
            }

            // 更多操作菜单
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("New Session") },
                        onClick = { showMenu = false; viewModel.addSession(context) }
                    )
                    DropdownMenuItem(
                        text = { Text("Close All Sessions") },
                        onClick = { showMenu = false; viewModel.closeAllSessions() }
                    )
                    Divider()
                    DropdownMenuItem(
                        text = { Text("Settings") },
                        onClick = {
                            showMenu = false
                            fragment.parentFragmentManager.beginTransaction()
                                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                                .replace((fragment.requireView().parent as ViewGroup).id, TerminalSettingsFragment())
                                .addToBackStack(null)
                                .commit()
                        }
                    )
                }
            }
        }

        // 终端内容呈现区
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (viewModel.sessions.isNotEmpty()) {
                val currentSession = viewModel.sessions.getOrNull(viewModel.currentIndex.coerceIn(0, viewModel.sessions.lastIndex))
                if (currentSession != null) {
                    TerminalWorkspace(currentSession)
                }
            } else {
                EmptySessionPlaceholder { viewModel.addSession(context) }
            }
        }
    }
}

// ==================== 状态机辅助界面 ====================

/**
 * 加载中界面
 */
@Composable
fun LoadingScreen(message: String) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(message, color = Color.White, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * 下载进度界面
 */
@Composable
fun DownloadingScreen(state: TerminalState.Downloading) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color.Black).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Terminal, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(24.dp))
        Text("Downloading ${state.fileName}", color = Color.White, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { state.progress },
            modifier = Modifier.fillMaxWidth().height(8.dp).background(Color.DarkGray),
            color = MaterialTheme.colorScheme.primary,
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
        )
        Spacer(Modifier.height(8.dp))
        Text("${state.downloadedMb} MB / ${state.totalMb} MB", color = Color.LightGray, style = MaterialTheme.typography.labelSmall)
    }
}

/**
 * 解压环境界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(session: TerminalSession) {
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        TopAppBar(
            title = { Text("Extracting RootFS...", color = Color.White) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E1E1E))
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            TerminalWorkspace(session, isInteractive = false)
        }
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.secondary)
    }
}



/**
 * 错误提示界面
 */
@Composable
fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF1A1A1A)).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Installation Error", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(8.dp))
        Text(message, color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
            Text("Retry Installation")
        }
    }
}

// ==================== 终端核心组件 ====================

/**
 * 终端工作区：封装了 AndroidView 承载的原生终端
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TerminalWorkspace(session: TerminalSession, isInteractive: Boolean = true) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val context = LocalContext.current
    var terminalViewRef by remember { mutableStateOf<TerminalView?>(null) }
    var extraKeysViewRef by remember { mutableStateOf<ExtraKeysView?>(null) }
    val density = LocalDensity.current.density

    val fontSize by TerminalSettings.fontSize.collectAsState()
    val keepScreenOn by TerminalSettings.keepScreenOn.collectAsState()
    val cursorStyle by TerminalSettings.cursorStyle.collectAsState()
    val bellVibrate by TerminalSettings.bellVibrate.collectAsState()

    val clipboard = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                TerminalView(ctx, null).apply {
                    isFocusable = true
                    isFocusableInTouchMode = true

                    val client = object : TermuxTerminalViewClientBase() {
                        override fun onSingleTapUp(e: MotionEvent) {
                            if (!isInteractive) return
                            this@apply.requestFocus()
                            val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                            imm.showSoftInput(this@apply, InputMethodManager.SHOW_IMPLICIT)
                        }

                        override fun onLongPress(event: MotionEvent): Boolean {
                            if (!isInteractive) return false
                            this@apply.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            this@apply.showContextMenu()
                            return true
                        }

                        override fun readControlKey(): Boolean = extraKeysViewRef?.readSpecialButton(SpecialButton.CTRL, true) ?: false
                        override fun readAltKey(): Boolean = extraKeysViewRef?.readSpecialButton(SpecialButton.ALT, true) ?: false
                        override fun readShiftKey(): Boolean = extraKeysViewRef?.readSpecialButton(SpecialButton.SHIFT, true) ?: false
                        override fun readFnKey(): Boolean = extraKeysViewRef?.readSpecialButton(SpecialButton.FN, true) ?: false

                        override fun onScale(scale: Float): Float {
                            if (scale !in 0.9f..1.1f) {
                                val currentFont = TerminalSettings.fontSize.value
                                val newFontSize = if (scale > 1f) currentFont + 1 else currentFont - 1
                                TerminalSettings.updateFontSize(newFontSize.coerceIn(8, 32))
                                return 1.0f
                            }
                            return scale
                        }
                    }

                    session.updateTerminalSessionClient(object : com.rustywarfare.modstudio.terminal.TerminalSessionClient {
                        override fun onTextChanged(changedSession: TerminalSession) = onScreenUpdated()
                        override fun onTitleChanged(changedSession: TerminalSession) {}
                        override fun onSessionFinished(finishedSession: TerminalSession) {}
                        override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
                            clipboard.setPrimaryClip(ClipData.newPlainText("Terminal", text))
                        }
                        override fun onPasteTextFromClipboard(session: TerminalSession?) {
                            val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                            if (text.isNotEmpty()) this@apply.mEmulator?.paste(text)
                        }
                        override fun onBell(session: TerminalSession) {
                            if (bellVibrate) {
                                val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    v.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                                } else { v.vibrate(50) }
                            }
                        }
                        override fun onColorsChanged(session: TerminalSession) {}
                        override fun onTerminalCursorStateChange(state: Boolean) {}
                        override fun setTerminalShellPid(session: TerminalSession, pid: Int) {}
                        override fun getTerminalCursorStyle(): Int = cursorStyle
                        override fun logError(t: String, m: String) {}
                        override fun logWarn(t: String, m: String) {}
                        override fun logInfo(t: String, m: String) {}
                        override fun logDebug(t: String, m: String) {}
                        override fun logVerbose(t: String, m: String) {}
                        override fun logStackTraceWithMessage(t: String, m: String, e: Exception) {}
                        override fun logStackTrace(t: String, e: Exception) {}
                    })

                    setTerminalViewClient(client)
                    attachSession(session)
                    terminalViewRef = this
                }
            },
            modifier = Modifier.fillMaxSize().background(Color.Black),
            update = { view ->
                if (view.mTermSession != session) view.attachSession(session)
                view.setTextSize((fontSize * density).toInt())
                view.keepScreenOn = keepScreenOn
                view.onScreenUpdated()
            }
        )

        // 底部工具栏逻辑
        if (isInteractive) {
            Box(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(58.dp)
            ) {
                Box(Modifier.matchParentSize().background(Color(0x991E1E1E)))
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    if (page == 0) {
                        AndroidView(
                            factory = { ctx ->
                                ExtraKeysView(ctx, null).apply {
                                    val info = ExtraKeysInfo(VIRTUAL_KEYS_JSON, "default", ExtraKeysConstants.CONTROL_CHARS_ALIASES)
                                    buttonTextColor = android.graphics.Color.WHITE
                                    buttonBackgroundColor = android.graphics.Color.TRANSPARENT
                                    reload(info, 58f * density)
                                    extraKeysViewClient = TerminalExtraKeys(terminalViewRef!!)
                                    extraKeysViewRef = this
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        QuickInputRow(terminalViewRef)
                    }
                }
            }
        }
    }
}

/**
 * 快捷输入行组件
 */
@Composable
private fun QuickInputRow(terminalView: TerminalView?) {
    var textInput by remember { mutableStateOf("") }
    AndroidView(
        factory = { ctx ->
            EditText(ctx).apply {
                maxLines = 1; isSingleLine = true
                imeOptions = EditorInfo.IME_ACTION_DONE
                setTextColor(android.graphics.Color.WHITE)
                hint = "Command input..."
                background = null
                doOnTextChanged { text, _, _, _ -> textInput = text.toString() }
                setOnEditorActionListener { _, actionId, _ ->
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        terminalView?.let { tv ->
                            if (textInput.isEmpty()) {
                                tv.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                                tv.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
                            } else {
                                tv.mTermSession?.write(textInput + "\r")
                                setText("")
                            }
                        }
                        true
                    } else false
                }
            }
        },
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        update = { it.setText(textInput) }
    )
}

/**
 * 空状态提示
 */
@Composable
private fun EmptySessionPlaceholder(onAdd: () -> Unit) {
    Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
        Text("No active terminal sessions", color = Color.Gray)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onAdd) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("New Terminal Session")
        }
    }
}
