package com.dshx.game.she.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.arktools.adsdk.AdSdkConfig
import com.dshx.game.she.AppState
import com.dshx.game.she.ComplianceManager
import com.dshx.game.she.Config
import com.dshx.game.she.Prefs
import com.dshx.game.she.Sfx
import com.dshx.game.she.TapSdkInitializer
import com.dshx.game.she.ui.theme.LocalGameFont
import com.dshx.game.she.ui.toColor
import com.taptap.sdk.kit.internal.callback.TapTapCallback
import com.taptap.sdk.kit.internal.exception.TapTapException
import com.taptap.sdk.login.Scopes
import com.taptap.sdk.login.TapTapAccount
import com.taptap.sdk.login.TapTapLogin

@Composable
fun PrivacyGate(onAccepted: () -> Unit, onExit: () -> Unit) {
    val C = Config.COLORS
    val ctx = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(C.uiBackdrop.toColor()),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .background(C.panelWhite.toColor(), RoundedCornerShape(18.dp))
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "隐私政策与用户协议",
                fontSize = 17.sp, fontWeight = FontWeight.Bold,
                color = C.textDark.toColor(), fontFamily = LocalGameFont.current
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState())
                    .background(C.chipBg.toColor(), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("欢迎使用「模拟市长：城市经营」。进入前请阅读并同意：", fontSize = 12.sp, color = C.textDark.toColor(), fontFamily = LocalGameFont.current)
                Text("收集信息：设备型号与系统版本、广告标识（OAID/AndroidID）、网络类型、本地存档、崩溃日志。", fontSize = 11.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current)
                Text("用途：提供游戏服务、展示广告维持免费运营、优化与修复问题。", fontSize = 11.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current)
                Text("第三方：优量汇、AdGain、优推、TapTap 登录等 SDK 可能读取 AndroidID / OAID 用于广告与登录鉴权。", fontSize = 11.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current)
                if (AdSdkConfig.privacyPolicyUrl.isNotEmpty()) {
                    Text(
                        "查看完整隐私政策",
                        fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = C.accentGreen.toColor(), fontFamily = LocalGameFont.current,
                        modifier = Modifier.clickable {
                            try {
                                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AdSdkConfig.privacyPolicyUrl)))
                            } catch (_: Throwable) {}
                        }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .background(C.chipBg.toColor(), RoundedCornerShape(22.dp))
                        .border(1.dp, C.border2.toColor(), RoundedCornerShape(22.dp))
                        .clickable { onExit() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("不同意并退出", fontSize = 13.sp, color = C.textDark.toColor(), fontFamily = LocalGameFont.current)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .background(C.accentGreen.toColor(), RoundedCornerShape(22.dp))
                        .clickable {
                            Sfx.play("sfx_click")
                            Prefs.privacyAccepted = true
                            onAccepted()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("同意并继续", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = LocalGameFont.current)
                }
            }
        }
    }
}

@Composable
fun TapLoginGate(onReady: () -> Unit) {
    val C = Config.COLORS
    val ctx = LocalContext.current
    val activity = ctx as? Activity
    var logging by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var waitingCompliance by remember { mutableStateOf(false) }

    fun startCompliance(account: TapTapAccount) {
        waitingCompliance = true
        val act = activity ?: return
        ComplianceManager.register(object : ComplianceManager.Listener {
            override fun onLoginSuccess() {
                AppState.loggedIn = true
                onReady()
            }
            override fun onBlocked(reason: String) {
                waitingCompliance = false
                err = reason
            }
            override fun onSwitchAccount() {
                waitingCompliance = false
                err = "请重新登录"
            }
        })
        val uid = account.openId ?: account.unionId ?: "guest"
        ComplianceManager.startup(act, uid)
    }

    LaunchedEffect(Unit) {
        TapSdkInitializer.ensureInitialized(ctx)
        val cur = try { TapTapLogin.getCurrentTapAccount() } catch (_: Throwable) { null }
        if (cur != null) startCompliance(cur)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(C.uiBackdrop.toColor()),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .background(C.panelWhite.toColor(), RoundedCornerShape(18.dp))
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("登录后进入城市", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = C.textDark.toColor(), fontFamily = LocalGameFont.current)
            Text("使用 TapTap 账号登录，并完成防沉迷认证。", fontSize = 12.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current, textAlign = TextAlign.Center)
            if (logging || waitingCompliance) {
                CircularProgressIndicator(modifier = Modifier.size(36.dp), color = C.accentGreen.toColor(), strokeWidth = 3.dp)
                Text(if (waitingCompliance) "正在完成认证…" else "正在登录…", fontSize = 12.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current)
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(C.accentGreen.toColor(), RoundedCornerShape(24.dp))
                        .clickable {
                            val act = activity ?: return@clickable
                            logging = true
                            err = null
                            try {
                                TapTapLogin.loginWithScopes(
                                    act,
                                    arrayOf(Scopes.SCOPE_PUBLIC_PROFILE),
                                    object : TapTapCallback<TapTapAccount> {
                                        override fun onSuccess(result: TapTapAccount) {
                                            logging = false
                                            startCompliance(result)
                                        }
                                        override fun onCancel() {
                                            logging = false
                                            err = "登录已取消"
                                        }
                                        override fun onFail(exception: TapTapException) {
                                            logging = false
                                            err = "登录失败：" + (exception.message ?: "")
                                        }
                                    }
                                )
                            } catch (e: Exception) {
                                logging = false
                                err = "登录服务不可用，请安装或更新 TapTap"
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("TapTap 登录", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = LocalGameFont.current)
                }
            }
            err?.let {
                Text(it, fontSize = 12.sp, color = C.accentRed.toColor(), fontFamily = LocalGameFont.current, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
fun AdLoadingOverlay(visible: Boolean) {
    if (!visible) return
    val C = Config.COLORS
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x99000000)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .background(C.panelWhite.toColor(), RoundedCornerShape(16.dp))
                    .padding(horizontal = 28.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(40.dp), color = C.accentGreen.toColor(), strokeWidth = 3.dp)
                Text("广告加载中…", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = C.textDark.toColor(), fontFamily = LocalGameFont.current)
            }
        }
    }
}
