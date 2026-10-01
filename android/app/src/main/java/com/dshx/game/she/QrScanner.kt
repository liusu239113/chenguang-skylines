package com.dshx.game.she

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.dshx.game.she.ui.theme.LocalGameFont
import com.dshx.game.she.ui.toColor
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors

/**
 * 扫码导入：只在玩家主动打开时申请相机权限并启动预览。
 * 扫到内容后回调 onResult，然后由调用方决定是否关闭。
 */
@Composable
fun QrScanScreen(
    onResult: (String) -> Unit,
    onClose: () -> Unit,
    hintText: String = "把镜头对准二维码"
) {
    val C = Config.COLORS
    val ctx = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hint by remember(hintText) { mutableStateOf(hintText) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (!ok) hint = "未获得相机权限，无法扫码。可在系统设置里开启后重试。"
    }
    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(Manifest.permission.CAMERA)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE6000000)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(0.92f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "扫码导入城市", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                color = Color.White, fontFamily = LocalGameFont.current
            )
            Text(
                hint, fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f),
                fontFamily = LocalGameFont.current, textAlign = TextAlign.Center
            )
            if (granted) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                        .background(Color.Black, RoundedCornerShape(14.dp))
                        .border(2.dp, C.accentGreen.toColor(), RoundedCornerShape(14.dp))
                ) {
                    CameraPreview(
                        onDecoded = { text ->
                            hint = "已识别一张，请对准下一张"
                            onResult(text)
                        },
                        onFail = { hint = it }
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .background(C.chipBg.toColor(), RoundedCornerShape(21.dp))
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) { Text("取消", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = C.textDark.toColor(), fontFamily = LocalGameFont.current) }
        }
    }
}

/** CameraX 预览 + 逐帧解码。扫到一次就停。 */
@Composable
private fun CameraPreview(onDecoded: (String) -> Unit, onFail: (String) -> Unit) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    var done by remember { mutableStateOf(false) }
    val reader = remember { MultiFormatReader() }

    DisposableEffect(Unit) {
        onDispose { executor.shutdown() }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { c ->
            val previewView = PreviewView(c)
            val providerFuture = ProcessCameraProvider.getInstance(c)
            providerFuture.addListener({
                try {
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analysis.setAnalyzer(executor) { image: ImageProxy ->
                        if (done) {
                            image.close()
                            return@setAnalyzer
                        }
                        val text = decode(image, reader)
                        image.close()
                        if (text != null) {
                            done = true
                            previewView.post { onDecoded(text) }
                        }
                    }
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )
                } catch (t: Throwable) {
                    onFail("相机启动失败：" + (t.message ?: "未知错误"))
                }
            }, ContextCompat.getMainExecutor(c))
            previewView
        }
    )
}

/** 把一帧 YUV 数据交给 ZXing 解码 */
private fun decode(image: ImageProxy, reader: MultiFormatReader): String? {
    return try {
        val buffer = image.planes[0].buffer
        val data = ByteArray(buffer.remaining())
        buffer.get(data)
        val source = PlanarYUVLuminanceSource(
            data, image.width, image.height, 0, 0, image.width, image.height, false
        )
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        val result = reader.decodeWithState(bitmap)
        result.text
    } catch (t: Throwable) {
        null
    } finally {
        reader.reset()
    }
}
