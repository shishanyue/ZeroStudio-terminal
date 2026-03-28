/**
 * 终端后台持久化服务
 * 
 * 功能：
 * 1. 承载终端会话（TerminalSession）的生命周期，确保 Activity 销毁后 Linux 环境依然运行。
 * 2. 提供前台通知（Foreground Service），防止系统在内存紧张时回收终端进程。
 * 3. 管理底层 PTY 会话的创建与强行销毁。
 * 
 * 工作流程线路图：
 * [Service Start] -> [onCreate: 注册通知频道] -> [startForeground]
 *       |--> [onBind: 返回 Binder 接口]
 *       |--> [createSession: 调用 PRoot 生成会话] -> [加入列表] -> [更新通知]
 *       |--> [Session Exit] -> [onSessionFinished 回调] -> [从列表移除] -> [checkStopSelf]
 * [Service Stop] -> [onDestroy: killAllSessions] -> [释放所有系统资源]
 * 
 * @author android_zero
 * @change 1. 实现动态通知更新，实时显示活跃会话数。
 * @change 2. 增强 onDestroy 时的进程清理能力，防止资源泄露。
 * @change 3. 规范化 KDoc 注释，明确方法用途与上下文。
 */
package com.rustywarfare.modstudio.app.terminal.compose

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.rustywarfare.modstudio.R
import com.rustywarfare.modstudio.app.terminal.proot.PRootEnvironment
import com.rustywarfare.modstudio.shared.termux.terminal.TermuxTerminalSessionClientBase
import com.rustywarfare.modstudio.terminal.TerminalSession
import java.util.concurrent.CopyOnWriteArrayList

class TerminalSessionService : Service() {

    private val binder = LocalBinder()
    private val channelId = "terminal_service_channel"
    private val notificationId = 1337

    /** 活跃会话的线程安全列表 */
    val sessions = CopyOnWriteArrayList<TerminalSession>()

    inner class LocalBinder : Binder() {
        fun getService(): TerminalSessionService = this@TerminalSessionService
    }

    override fun onCreate() {
        super.onCreate()
        setupNotificationChannel()
        updateNotification()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 使用 START_NOT_STICKY，因为如果 Service 被系统杀死，PTY 文件描述符会失效，
        // 自动重启 Service 无法恢复之前的 Linux 运行状态。
        return START_NOT_STICKY
    }

    /**
     * 配置 Android O+ 所需的通知渠道
     */
    private fun setupNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Terminal Service"
            val descriptionText = "Keep Linux environment running in background"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * 更新前台通知内容
     * 根据当前活跃的会话数量动态显示文案
     */
    private fun updateNotification() {
        val intent = Intent(this, TerminalActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val sessionCount = sessions.size
        val contentText = if (sessionCount > 0) {
            "Active sessions: $sessionCount"
        } else {
            "Linux environment is ready"
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("ModStudio Terminal")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher) // 确保此图标存在
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(notificationId, notification)
    }

    /**
     * 创建并注册一个新的 Linux 终端会话
     * 
     * @param context 环境上下文
     * @param sessionId 会话唯一标识名
     * @param onFinished 当 Linux 进程退出时的逻辑回调
     */
    fun createSession(
        context: Context,
        sessionId: String,
        onFinished: (TerminalSession) -> Unit
    ): TerminalSession {
        val client = object : TermuxTerminalSessionClientBase() {
            override fun onSessionFinished(finishedSession: TerminalSession) {
                sessions.remove(finishedSession)
                onFinished(finishedSession)
                updateNotification()
                checkStopSelf()
            }
        }

        // 通过环境工具类构建物理会话
        val session = PRootEnvironment.createSession(context, sessionId, client)
        sessions.add(session)
        updateNotification()
        return session
    }

    /**
     * 强行销毁指定的会话进程
     */
    fun killSession(session: TerminalSession) {
        if (session.isRunning) {
            session.finishIfRunning()
        }
        sessions.remove(session)
        updateNotification()
        checkStopSelf()
    }

    /**
     * 清理所有活跃进程
     */
    fun killAllSessions() {
        for (session in sessions) {
            session.finishIfRunning()
        }
        sessions.clear()
        checkStopSelf()
    }

    /**
     * 状态检查：如果没有任务在运行，则释放 Service 自身
     */
    private fun checkStopSelf() {
        if (sessions.isEmpty()) {
            stopForeground(true)
            stopSelf()
        }
    }

    override fun onDestroy() {
        killAllSessions()
        super.onDestroy()
    }
}
