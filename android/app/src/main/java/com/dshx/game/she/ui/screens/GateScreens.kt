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
import com.dshx.game.she.AppState
import com.dshx.game.she.ComplianceManager
import com.dshx.game.she.Config
import com.dshx.game.she.Prefs
import com.dshx.game.she.PrivacyDocs
import com.dshx.game.she.Sfx
import com.dshx.game.she.TapSdkInitializer
import com.dshx.game.she.ThirdPartySdk
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
    var showDoc by remember { mutableStateOf(false) }
    if (showDoc) {
        PrivacyDocPanel(onClose = { showDoc = false })
        return
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(C.uiBackdrop.toColor()),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .background(C.panelWhite.toColor(), RoundedCornerShape(18.dp))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "隐私政策",
                fontSize = 17.sp, fontWeight = FontWeight.Bold,
                color = C.textDark.toColor(), fontFamily = LocalGameFont.current
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
                    .background(C.chipBg.toColor(), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("欢迎使用「模拟市长：城市经营」。进入前请阅读并同意本《隐私政策》。不同意请退出，我们不会开始收集。", fontSize = 12.sp, color = C.textDark.toColor(), fontFamily = LocalGameFont.current)
                Text("处理目的：提供城市建设游戏、TapTap 登录与防沉迷、激励视频广告、本地存档、崩溃排查。", fontSize = 11.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current)
                Text("处理方式：仅在您点击「同意并继续」之后，通过本应用及下列第三方 SDK 处理信息。同意前不会初始化 TapTap 登录 SDK，也不会读取 AndroidID。", fontSize = 11.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current)
                Text("处理范围：设备型号、系统版本、网络类型、OAID/AndroidID（登录鉴权与广告归因）、本地存档、崩溃日志。不收集通讯录、精确位置、IMEI、已安装应用列表。", fontSize = 11.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current)
                Text(
                    "接入第三方 SDK 情况说明",
                    fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    color = C.textDark.toColor(), fontFamily = LocalGameFont.current
                )
                for (sdk in PrivacyDocs.SDKS) {
                    SdkCard(sdk)
                }
                Text(
                    "查看完整《隐私政策》",
                    fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    color = C.accentGreen.toColor(), fontFamily = LocalGameFont.current,
                    modifier = Modifier.clickable { showDoc = true }
                )
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
private fun SdkCard(sdk: ThirdPartySdk) {
    val C = Config.COLORS
    val ctx = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(C.panelWhite.toColor(), RoundedCornerShape(10.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(sdk.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = C.textDark.toColor(), fontFamily = LocalGameFont.current)
        Text("开发者全称：" + sdk.vendor, fontSize = 10.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current)
        Text("使用目的：" + sdk.purpose, fontSize = 10.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current)
        Text("调用权限：" + sdk.permission, fontSize = 10.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current)
        Text("个人信息类型及方式：" + sdk.collect, fontSize = 10.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current)
        Text("使用场景与频次时机：" + sdk.timing, fontSize = 10.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current)
        Text(
            "隐私政策链接：" + sdk.policyUrl,
            fontSize = 10.sp, color = C.accentGreen.toColor(), fontFamily = LocalGameFont.current,
            modifier = Modifier.clickable {
                try {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(sdk.policyUrl)))
                } catch (_: Throwable) {}
            }
        )
    }
}

@Composable
fun PrivacyDocPanel(onClose: () -> Unit) {
    val C = Config.COLORS
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(C.uiBackdrop.toColor()),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 640.dp)
                .background(C.panelWhite.toColor(), RoundedCornerShape(18.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "《隐私政策》",
                    fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    color = C.textDark.toColor(), fontFamily = LocalGameFont.current,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
                )
                Text(
                    "×", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = C.textMid.toColor(), fontFamily = LocalGameFont.current,
                    modifier = Modifier.align(Alignment.CenterEnd).clickable { onClose() }.padding(4.dp)
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("更新日期：2026年9月13日　生效日期：2026年9月13日", fontSize = 11.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current)
                Text("进入游戏前请完整阅读。不同意请退出。我们不会在您点击「同意并继续」之前初始化 TapTap 登录 SDK 或广告 SDK，也不会在同意前读取 AndroidID。", fontSize = 12.sp, color = C.textDark.toColor(), fontFamily = LocalGameFont.current)
                Text("一、处理目的、方式与范围", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.textDark.toColor(), fontFamily = LocalGameFont.current)
                Text("目的：提供城市建设游戏、TapTap 登录与防沉迷、激励视频广告、本地存档、崩溃排查。", fontSize = 11.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current)
                Text("方式：仅在您同意后，通过本应用及下列第三方 SDK 处理信息。", fontSize = 11.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current)
                Text("范围：设备型号、系统版本、网络类型、OAID/AndroidID、本地存档、崩溃日志。不收集通讯录、精确位置、IMEI、已安装应用列表。", fontSize = 11.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current)
                Text("二、我们自行处理的信息", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.textDark.toColor(), fontFamily = LocalGameFont.current)
                Text("本地存档与音量设置仅保存在本机。不收集真实姓名、身份证号、银行账号。", fontSize = 11.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current)
                Text("三、接入第三方 SDK 情况说明", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.textDark.toColor(), fontFamily = LocalGameFont.current)
                for (sdk in PrivacyDocs.SDKS) {
                    SdkCard(sdk)
                }
                Text("四、您的权利", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.textDark.toColor(), fontFamily = LocalGameFont.current)
                Text("可在游戏【设置】再次查阅本政策。不同意请卸载本应用。", fontSize = 11.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current)
                Text("五、未成年人", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.textDark.toColor(), fontFamily = LocalGameFont.current)
                Text("本游戏接入 TapTap 合规认证。未完成实名或处于限制时段，将无法进入游戏。", fontSize = 11.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(C.chipBg.toColor(), RoundedCornerShape(20.dp))
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Text("返回", fontSize = 13.sp, color = C.textDark.toColor(), fontFamily = LocalGameFont.current)
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
