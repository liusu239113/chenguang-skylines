package com.dshx.game.she.world

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.dshx.game.she.Ambience
import com.dshx.game.she.Config
import com.dshx.game.she.GameData
import com.dshx.game.she.RGBA
import com.dshx.game.she.Sfx
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// ============================================================================
// MapRenderView — 俯视格子地图（Android Canvas 2D 渲染 + 相机 + 手势）
//   与 scripts/world/MapView.lua 1:1 对应
//   渲染顺序：地形色块 → 分区底色 → 道路 → 建筑(微立体) → 标签 → 选中框
//   相机：screen = (world - cam) * cell
//   手势：拖拽平移 / 双指捏合 / 点击选格 / 工具笔刷拖拽
//   坐标系：逻辑像素(dp)，onDraw 内统一 scale(density)
// ============================================================================

data class Tool(
    val kind: String,               // road | zone | bulldoze | service | pipe | cable | bus | spec
    val roadKind: String? = null,
    val zoneKey: String? = null,
    val id: String? = null
)

class MapRenderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // -----------------------------------------------------------------------
    // 状态
    // -----------------------------------------------------------------------
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private var typeface: Typeface? = null

    private var camX = 0f
    private var camY = 0f
    private var camScale = 1f

    private var viewW = 360f
    private var viewH = 640f
    private var topInset = 70f
    private var bottomInset = 88f
    private var cameraNeedsFit = true

    var selectedX: Int = -1
    var selectedY: Int = -1
    private var hasSelection = false

    private var hoverX: Int = -1
    private var hoverY: Int = -1
    private var hasHover = false

    private var toastMsg: String? = null
    private var toastT = 0f

    var tool: Tool? = null
    var onTileChanged: (() -> Unit)? = null

    /** 覆盖热力图："" 关闭 | power/water/garbage/health/education/safety */
    var overlay: String = ""

    /** 夜晚因子 0=白天 1=深夜（由 GameData.timeOfDay 计算） */
    private var nightLevel = 0f
    private var rainPhase = 0f
    private val rainDrops = Array(160) { RainDrop() }

    private class RainDrop {
        var x = 0f
        var y = 0f
        var vy = 0f
        var len = 0f
        var thick = 0f
        var splash = 0f
        var splashX = 0f
        var splashY = 0f
        fun reset(w: Float, h: Float, scatter: Boolean) {
            x = (Math.random() * (w + 40.0) - 20.0).toFloat()
            y = if (scatter) (Math.random() * h).toFloat() else -8f - (Math.random() * 80.0).toFloat()
            vy = 380f + (Math.random() * 220.0).toFloat()
            len = 9f + (Math.random() * 10.0).toFloat()
            thick = 1.1f + (Math.random() * 0.8).toFloat()
            splash = 0f
        }
    }

    private val cell: Float get() = Config.MAP.baseCell * camScale

    // 拖拽状态
    private var dragActive = false
    private var dragMode: String? = null      // "tool" | "pan"
    private var dragMoved = false
    private var dragSx = 0f
    private var dragSy = 0f
    private var dragCx = 0f
    private var dragCy = 0f
    private var dragLastTile: String? = null

    // 指针
    private var ptrDown = false
    private var ptrX = 0f
    private var ptrY = 0f
    private var activePointerId = -1

    // 捏合
    private var pinchActive = false
    private var pinchDist0 = 0f
    private var pinchScale0 = 1f

    private val density: Float get() = resources.displayMetrics.density

    /** 体块左右挤出方向：false=朝右露右墙，true=朝左露左墙 */
    private var boxFlip = false

    // -----------------------------------------------------------------------
    // 初始化
    // -----------------------------------------------------------------------
    fun initView() {
        typeface = try {
            Typeface.createFromAsset(context.assets, "fonts/ZCOOLKuaiLe-Regular.ttf")
        } catch (t: Throwable) {
            null
        }
    }

    fun setViewport(wDp: Float, hDp: Float, top: Float, bottom: Float) {
        viewW = wDp
        viewH = hDp
        topInset = top
        bottomInset = bottom
        if (cameraNeedsFit) fitCameraIfNeeded() else clampCamera()
    }

    fun resetCamera() {
        cameraNeedsFit = true
        camScale = 1.55f
        val w = World.current
        setPlayCenterTile((w?.spawnX ?: 8).toFloat(), (w?.spawnY ?: 8).toFloat())
    }

    fun fitCameraIfNeeded() {
        if (!cameraNeedsFit) {
            clampCamera()
            return
        }
        camScale = 1.55f
        val w = World.current
        setPlayCenterTile((w?.spawnX ?: 8).toFloat(), (w?.spawnY ?: 8).toFloat())
        cameraNeedsFit = false
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewW = w / density
        viewH = h / density
        clampCamera()
    }

    // -----------------------------------------------------------------------
    // 坐标换算
    // -----------------------------------------------------------------------
    private fun screenToWorldX(sx: Float) = sx / cell + camX
    private fun screenToWorldY(sy: Float) = sy / cell + camY
    private fun worldToScreenX(wx: Float) = (wx - camX) * cell
    private fun worldToScreenY(wy: Float) = (wy - camY) * cell

    private fun tileAtX(sx: Float) = floor(screenToWorldX(sx)).toInt() + 1
    private fun tileAtY(sy: Float) = floor(screenToWorldY(sy)).toInt() + 1

    private fun camExtentW() = viewW / cell
    private fun camExtentH() = viewH / cell

    private fun clampCamera() {
        val w = World.current ?: return
        val ew = camExtentW()
        val eh = camExtentH()
        val playTop = topInset / cell
        val playBot = bottomInset / cell
        val extra = 4f
        // 把顶行拖到 HUD 下面的可点区：camY 必须能更负
        val minX = -ew * 0.28f
        val maxX = max(minX, w.cols.toFloat() - ew * 0.72f)
        val minY = -playTop - extra
        val maxY = max(minY, w.rows.toFloat() - (eh - playBot) + extra)
        camX = clamp(camX, minX, maxX)
        camY = clamp(camY, minY, maxY)
    }

    private fun zoomAt(factor: Float, anchorX: Float, anchorY: Float) {
        val oldCell = cell
        var newCell = oldCell * factor
        newCell = clamp(newCell, Config.MAP.baseCell * Config.MAP.minScale,
            Config.MAP.baseCell * Config.MAP.maxScale)
        if (newCell == oldCell) return
        val wx = screenToWorldX(anchorX)
        val wy = screenToWorldY(anchorY)
        camScale = newCell / Config.MAP.baseCell
        val c = cell
        camX = wx - anchorX / c
        camY = wy - anchorY / c
        clampCamera()
    }

    fun zoomCentered(factor: Float) = zoomAt(factor, viewW / 2f, viewH / 2f)

    private fun setCenterTile(tx: Float, ty: Float) {
        setPlayCenterTile(tx, ty)
    }

    private fun setPlayCenterTile(tx: Float, ty: Float) {
        val playH = (viewH - topInset - bottomInset) / cell
        camX = tx - camExtentW() / 2f
        camY = ty - (topInset / cell) - playH / 2f
        clampCamera()
    }

    private fun clamp(v: Float, lo: Float, hi: Float): Float = max(lo, min(hi, v))

    fun selectTile(tx: Int, ty: Int) {
        val w = World.current ?: return
        selectedX = tx.coerceIn(1, w.cols)
        selectedY = ty.coerceIn(1, w.rows)
        hasSelection = true
        onTileChanged?.invoke()
    }

    fun trySelectVehicle(mx: Float, my: Float): Boolean {
        val wx = screenToWorldX(mx)
        val wy = screenToWorldY(my)
        val car = Traffic.hitTest(wx, wy)
        if (car != null) {
            Traffic.selected = car
            Traffic.selectedTrain = null
            Traffic.selectedPlane = null
            CitySystems.selected = null
            selectedX = car.x
            selectedY = car.y
            hasSelection = true
            Sfx.play("sfx_horn", 0.95f)
            onTileChanged?.invoke()
            return true
        }
        val train = Traffic.hitTrain(wx, wy)
        if (train != null) {
            Traffic.selectedTrain = train
            Traffic.selected = null
            Traffic.selectedPlane = null
            CitySystems.selected = null
            selectedX = train.x
            selectedY = train.y
            hasSelection = true
            Sfx.play("sfx_engine", 0.7f)
            onTileChanged?.invoke()
            return true
        }
        val plane = Traffic.hitPlane(wx, wy)
        if (plane != null) {
            Traffic.selectedPlane = plane
            Traffic.selected = null
            Traffic.selectedTrain = null
            CitySystems.selected = null
            selectedX = plane.ax
            selectedY = plane.ay
            hasSelection = true
            Sfx.play("sfx_engine", 0.45f)
            onTileChanged?.invoke()
            return true
        }
        val svc = CitySystems.hitTest(wx, wy)
        if (svc != null) {
            CitySystems.selected = svc
            Traffic.selected = null
            Traffic.selectedTrain = null
            Traffic.selectedPlane = null
            selectedX = svc.x
            selectedY = svc.y
            hasSelection = true
            Sfx.play("sfx_horn", 0.7f)
            onTileChanged?.invoke()
            return true
        }
        Traffic.clearSelection()
        return false
    }

    fun clearSelection() {
        hasSelection = false
        selectedX = -1
        selectedY = -1
        Traffic.clearSelection()
        onTileChanged?.invoke()
    }

    fun setToast(msg: String) {
        toastMsg = msg
        toastT = 0f
    }

    // -----------------------------------------------------------------------
    // 工具
    // -----------------------------------------------------------------------
    fun applyTool(tx: Int, ty: Int): Boolean {
        val t = tool ?: return false
        if (!World.inBounds(tx, ty)) return false
        when (t.kind) {
            "road" -> {
                val (ok, msg) = GameData.placeRoad(tx, ty, t.roadKind ?: "local")
                if (ok) Sfx.play("sfx_click", 0.5f) else if (msg != null) setToast(msg)
                return ok
            }
            "zone" -> {
                val (ok, msg) = GameData.paintZone(tx, ty, t.zoneKey ?: "residential")
                if (ok) Sfx.play("sfx_click", 0.35f) else if (msg != null) setToast(msg)
                return ok
            }
            "bulldoze" -> {
                val ok = GameData.bulldoze(tx, ty)
                if (ok) Sfx.play("sfx_demolish", 0.6f)
                return ok
            }
            "service" -> {
                val (ok, msg) = GameData.paintServiceDraft(t.id ?: return false, tx, ty)
                if (ok) Sfx.play("sfx_click", 0.35f) else if (msg != null) setToast(msg)
                return ok
            }
            "spec" -> {
                val (ok, msg) = GameData.paintSpec(tx, ty, t.id ?: "retail")
                if (ok) Sfx.play("sfx_click", 0.35f) else if (msg != null) setToast(msg)
                return ok
            }
            "bus" -> {
                val (ok, msg) = GameData.tapBusStop(tx, ty)
                if (ok) Sfx.play("sfx_click", 0.5f)
                if (msg != null) setToast(msg)
                return ok
            }
            "tree" -> {
                val (ok, msg) = GameData.plantTree(tx, ty)
                if (ok) Sfx.play("sfx_build", 0.4f) else if (msg != null) setToast(msg)
                return ok
            }
            "raise" -> {
                val (ok, msg) = GameData.raiseLand(tx, ty)
                if (ok) Sfx.play("sfx_click", 0.3f) else if (msg != null) setToast(msg)
                return ok
            }
            "lower" -> {
                val (ok, msg) = GameData.lowerLand(tx, ty)
                if (ok) Sfx.play("sfx_click", 0.3f) else if (msg != null) setToast(msg)
                return ok
            }
        }
        return false
    }

    fun toolValidAt(tx: Int, ty: Int): Boolean {
        val t = tool ?: return false
        if (!World.inBounds(tx, ty)) return false
        return when (t.kind) {
            "road" -> World.canRoad(tx, ty, t.roadKind ?: "local").first
            "zone" -> {
                val tile = World.tile(tx, ty)
                tile != null && tile.road == null && tile.building == null && tile.terrain != "water" &&
                    World.isUnlocked(tx, ty)
            }
            "bulldoze" -> {
                val tile = World.tile(tx, ty)
                tile != null && (tile.building != null || tile.road != null || tile.metro || tile.rail)
            }
            "service" -> World.canPlaceService(t.id ?: return false, tx, ty).first
            "spec" -> {
                val def = Config.specOf(t.id ?: "")
                val tile = World.tile(tx, ty)
                tile != null && def != null && tile.zone == def.zone
            }
            "bus" -> Transit.isStopCell(tx, ty)
            "tree" -> {
                val tile = World.tile(tx, ty)
                tile != null && tile.terrain != "water" && tile.road == null && tile.building == null
            }
            "raise", "lower" -> {
                val tile = World.tile(tx, ty)
                tile != null && tile.road == null && tile.building == null
            }
            else -> false
        }
    }

    private val dirs = arrayOf(intArrayOf(1, 0), intArrayOf(0, 1), intArrayOf(-1, 0), intArrayOf(0, -1))

    // -----------------------------------------------------------------------
    // 输入
    // -----------------------------------------------------------------------
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val d = density
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0)
                ptrDown = true
                ptrX = event.x / d
                ptrY = event.y / d
                onPress()
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2) {
                    dragActive = false
                    dragMode = null
                    pinchActive = true
                    pinchDist0 = dist(event, 0, 1)
                    pinchScale0 = camScale
                    ptrX = event.x / d
                    ptrY = event.y / d
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2) {
                    val dist = dist(event, 0, 1)
                    if (pinchActive && pinchDist0 > 0) {
                        val mx = (event.getX(0) + event.getX(1)) * 0.5f / d
                        val my = (event.getY(0) + event.getY(1)) * 0.5f / d
                        val target = clamp(
                            pinchScale0 * (dist / pinchDist0),
                            Config.MAP.minScale, Config.MAP.maxScale
                        )
                        zoomAt(target / camScale, mx, my)
                    }
                    return true
                }
                val idx = event.findPointerIndex(activePointerId)
                if (idx >= 0) {
                    ptrX = event.getX(idx) / d
                    ptrY = event.getY(idx) / d
                }
                onMove()
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.pointerCount <= 2) pinchActive = false
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (pinchActive && event.pointerCount >= 2) {
                    pinchActive = false
                    return true
                }
                val idx = event.findPointerIndex(activePointerId)
                if (idx >= 0) {
                    ptrX = event.getX(idx) / d
                    ptrY = event.getY(idx) / d
                }
                ptrDown = false
                activePointerId = -1
                pinchActive = false
                onRelease()
            }
        }
        return true
    }

    private fun dist(e: MotionEvent, i: Int, j: Int): Float {
        val dx = e.getX(i) - e.getX(j)
        val dy = e.getY(i) - e.getY(j)
        return sqrt(dx * dx + dy * dy)
    }

    private fun tileUnderCursor(mx: Float, my: Float): Pair<Int, Int>? {
        if (my <= topInset || my >= viewH - bottomInset) return null
        val tx = tileAtX(mx)
        val ty = tileAtY(my)
        val w = World.current ?: return null
        if (tx < 1 || tx > w.cols || ty < 1 || ty > w.rows) return null
        return tx to ty
    }

    private fun onPress() {
        val w = World.current ?: return
        val inMap = ptrY > topInset && ptrY < viewH - bottomInset
        if (!inMap) return
        val tile = tileUnderCursor(ptrX, ptrY)
        if (tool != null) {
            dragActive = true
            dragMode = "tool"
            dragMoved = true
            dragLastTile = if (tile != null) "${tile.first},${tile.second}" else null
            if (tile != null) applyTool(tile.first, tile.second)
        } else {
            dragActive = true
            dragMode = "pan"
            dragMoved = false
            dragSx = ptrX
            dragSy = ptrY
            dragCx = camX
            dragCy = camY
        }
    }

    private fun onMove() {
        if (dragActive && ptrDown) {
            if (dragMode == "tool") {
                val tile = tileUnderCursor(ptrX, ptrY)
                if (tile != null) {
                    val key = "${tile.first},${tile.second}"
                    if (key != dragLastTile) {
                        dragLastTile = key
                        applyTool(tile.first, tile.second)
                    }
                }
            } else if (dragMode == "pan") {
                val dx = ptrX - dragSx
                val dy = ptrY - dragSy
                if (abs(dx) > 3 || abs(dy) > 3) dragMoved = true
                if (dragMoved) {
                    val c = cell
                    camX = dragCx - dx / c
                    camY = dragCy - dy / c
                    clampCamera()
                }
            }
        }
        // 悬停
        if (ptrX > 0 && ptrY > topInset && ptrY < viewH - bottomInset) {
            val tx = tileAtX(ptrX)
            val ty = tileAtY(ptrY)
            if (World.inBounds(tx, ty)) {
                hoverX = tx; hoverY = ty; hasHover = true
            } else {
                hasHover = false
            }
        } else {
            hasHover = false
        }
    }

    private fun onRelease() {
        val tile = tileUnderCursor(ptrX, ptrY)
        if (dragActive && dragMode == "pan" && !dragMoved) {
            if (!trySelectVehicle(ptrX, ptrY) && tile != null && World.inBounds(tile.first, tile.second)) {
                selectTile(tile.first, tile.second)
            }
        }
        if (dragActive && dragMode == "tool") {
            onTileChanged?.invoke()
        }
        dragActive = false
        dragMode = null
        dragMoved = false
        dragLastTile = null
    }

    private var ambientAcc = 0f

    /** 每帧：toast 计时 + 车辆 + 环境音 */
    fun update(dt: Float) {
        if (toastMsg != null) {
            toastT += dt
            if (toastT > 3.2f) {
                toastMsg = null
                toastT = 0f
            }
        }
        rainPhase += dt * 2.4f
        if (rainPhase > 1000f) rainPhase -= 1000f
        if (GameData.weather == 1) {
            for (d in rainDrops) {
                if (d.vy <= 1f) d.reset(viewW, viewH, true)
                d.y += d.vy * dt
                if (d.splash > 0f) {
                    d.splash -= dt * 7f
                    if (d.splash <= 0f) d.reset(viewW, viewH, false)
                } else if (d.y > viewH - bottomInset * 0.2f) {
                    d.splash = 1f
                    d.splashX = d.x
                    d.splashY = d.y
                    d.vy = 0f
                }
            }
        } else {
            for (d in rainDrops) d.vy = 0f
        }
        ambientAcc += dt
        if (ambientAcc > 2.4f && camScale > 1.15f) {
            ambientAcc = 0f
            val tx = tileAtX(viewW * 0.5f)
            val ty = tileAtY(viewH * 0.5f)
            val t = World.tile(tx, ty)
            when {
                t?.building?.zone == "industrial" -> Sfx.play("sfx_engine", 0.18f)
                t?.road == "highway" && Traffic.visitorsToday > 0 -> Sfx.play("sfx_engine", 0.14f)
                t?.road != null && Traffic.localMoving > 0 -> Sfx.play("sfx_engine", 0.10f)
            }
        }
    }

    private fun hash01(x: Int, y: Int, salt: Int = 0): Float {
        var n = x * 374761393 + y * 668265263 + salt * 1274126177
        n = (n xor (n ushr 13)) * 1274126177
        return ((n ushr 16) and 0x7fff) / 32767f
    }

    // -----------------------------------------------------------------------
    // 绘制辅助
    // -----------------------------------------------------------------------
    private fun fillColor(c: RGBA, alpha: Int = c.a) {
        paint.color = c.argb(alpha)
        paint.style = Paint.Style.FILL
    }

    private fun strokeColor(c: RGBA, alpha: Int = c.a, width: Float) {
        paint.color = c.argb(alpha)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width
    }

    private fun fillRect(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, c: RGBA, alpha: Int = c.a) {
        fillColor(c, alpha)
        canvas.drawRect(x, y, x + w, y + h, paint)
    }

    private fun fillRoundRect(
        canvas: Canvas, x: Float, y: Float, w: Float, h: Float, r: Float, c: RGBA, alpha: Int = c.a
    ) {
        fillColor(c, alpha)
        canvas.drawRoundRect(x, y, x + w, y + h, r, r, paint)
    }

    private fun strokeRoundRect(
        canvas: Canvas, x: Float, y: Float, w: Float, h: Float, r: Float,
        c: RGBA, alpha: Int = c.a, width: Float
    ) {
        strokeColor(c, alpha, width)
        canvas.drawRoundRect(x, y, x + w, y + h, r, r, paint)
    }

    private fun fillCircle(canvas: Canvas, x: Float, y: Float, r: Float, c: RGBA, alpha: Int = c.a) {
        fillColor(c, alpha)
        canvas.drawCircle(x, y, r, paint)
    }

    private fun fillOval(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, c: RGBA, alpha: Int = c.a) {
        fillColor(c, alpha)
        canvas.drawOval(x, y, x + w, y + h, paint)
    }

    private fun strokeCircle(
        canvas: Canvas, x: Float, y: Float, r: Float, c: RGBA, alpha: Int = c.a, width: Float
    ) {
        strokeColor(c, alpha, width)
        canvas.drawCircle(x, y, r, paint)
    }

    private fun fillPath(canvas: Canvas, p: Path, c: RGBA, alpha: Int = c.a) {
        fillColor(c, alpha)
        canvas.drawPath(p, paint)
    }

    private fun strokePath(canvas: Canvas, p: Path, c: RGBA, alpha: Int = c.a, width: Float) {
        strokeColor(c, alpha, width)
        canvas.drawPath(p, paint)
    }

    private fun addSeg(out: MutableList<Float>, x0: Float, y0: Float, x1: Float, y1: Float) {
        out.add(x0); out.add(y0); out.add(x1); out.add(y1)
    }

    private fun fillLines(
        canvas: Canvas, pts: FloatArray, c: RGBA, alpha: Int = c.a, width: Float,
        dash: DashPathEffect? = null
    ) {
        strokeColor(c, alpha, width)
        paint.strokeCap = Paint.Cap.BUTT
        paint.pathEffect = dash
        canvas.drawLines(pts, paint)
        paint.pathEffect = null
    }

    private enum class TAlign { CENTER, LEFT, RIGHT }

    private fun drawText(
        canvas: Canvas, sx: Float, sy: Float, size: Float, c: RGBA,
        text: String, align: TAlign, alpha: Int = 255
    ) {
        paint.typeface = typeface
        paint.textSize = size
        paint.textAlign = when (align) {
            TAlign.CENTER -> Paint.Align.CENTER
            TAlign.LEFT -> Paint.Align.LEFT
            TAlign.RIGHT -> Paint.Align.RIGHT
        }
        paint.style = Paint.Style.FILL
        paint.color = c.argb(alpha)
        val fm = paint.fontMetrics
        val baseline = sy - (fm.ascent + fm.descent) / 2f
        canvas.drawText(text, sx, baseline, paint)
    }

    private fun measure(text: String, size: Float): Float {
        paint.typeface = typeface
        paint.textSize = size
        return paint.measureText(text)
    }

    private fun drawStreetNameOnRoad(canvas: Canvas, tx: Int, ty: Int, name: String, vertical: Boolean) {
        if (name.isEmpty()) return
        val sx = worldToScreenX((tx - 1).toFloat())
        val sy = worldToScreenY((ty - 1).toFloat())
        val save = canvas.save()
        canvas.clipRect(sx + 1f, sy + 1f, sx + cell - 1f, sy + cell - 1f)
        val cx = sx + cell * 0.5f
        val cy = sy + cell * 0.5f
        val fs = min(cell * 0.28f, 8.5f)
        val ink = RGBA(248, 248, 242)
        if (vertical) {
            val chars = name.toList()
            val chH = min(fs * 0.92f, (cell - 4f) / max(1, chars.size))
            var y = cy - (chars.size - 1) * chH * 0.5f
            for (ch in chars) {
                drawText(canvas, cx, y, fs, ink, ch.toString(), TAlign.CENTER, 210)
                y += chH
            }
        } else {
            var shown = name
            while (shown.length > 1 && measure(shown, fs) > cell - 4f) {
                shown = shown.dropLast(1)
            }
            drawText(canvas, cx, cy, fs, ink, shown, TAlign.CENTER, 210)
        }
        canvas.restoreToCount(save)
    }

    // -----------------------------------------------------------------------
    // render
    // -----------------------------------------------------------------------
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.scale(density, density)
        try {
            renderInternal(canvas)
        } finally {
            canvas.restore()
        }
    }

    private fun renderInternal(canvas: Canvas) {
        val w = World.current ?: return
        val C = Config.COLORS

        // 昼夜：天空色 + 夜晚因子
        val tod = GameData.timeOfDay
        nightLevel = nightFactor(tod)
        fillRect(canvas, 0f, 0f, viewW, viewH, skyColor(tod))

        val cell = this.cell
        val x0 = Config.clamp(tileAtX(0f), 1, w.cols)
        val x1 = Config.clamp(tileAtX(viewW), 1, w.cols)
        val y0 = Config.clamp(tileAtY(0f), 1, w.rows)
        val y1 = Config.clamp(tileAtY(viewH), 1, w.rows)

        // ---- 1) 底色 ----
        for (ty in y0..y1) {
            for (tx in x0..x1) {
                val t = w.grid[ty - 1][tx - 1]
                val sx = worldToScreenX((tx - 1).toFloat())
                val sy = worldToScreenY((ty - 1).toFloat())
                fillRect(canvas, sx, sy, cell + 0.5f, cell + 0.5f, zoneBaseColor(t, tx, ty))
                val spec = Config.specOf(t.spec)
                if (spec != null && t.road == null) {
                    fillRect(canvas, sx, sy, cell + 0.5f, cell + 0.5f, spec.color.withAlpha(70))
                    fillRect(canvas, sx, sy, cell * 0.22f, cell * 0.22f, spec.color.withAlpha(210))
                }
                if (!World.isUnlocked(tx, ty) && t.road != "highway") {
                    fillRect(canvas, sx, sy, cell + 0.5f, cell + 0.5f, RGBA(28, 36, 42, 150))
                }
            }
        }

        // ---- 1.4) 地铁隧 / 铁轨 ----
        val showNet = overlay in listOf("metro", "rail") ||
            tool?.kind in listOf("metro", "rail") ||
            (tool?.kind == "road" && (tool?.roadKind == "metro" || tool?.roadKind == "rail"))
        if (showNet) {
            for (ty in y0..y1) {
                for (tx in x0..x1) {
                    val t = w.grid[ty - 1][tx - 1]
                    val sx = worldToScreenX((tx - 1).toFloat())
                    val sy = worldToScreenY((ty - 1).toFloat())
                    if ((overlay == "metro" || tool?.roadKind == "metro" || tool?.kind == "metro") && t.metro) {
                        fillRect(canvas, sx + cell * 0.1f, sy + cell * 0.42f, cell * 0.8f, cell * 0.16f, RGBA(40, 80, 160, 190))
                    }
                    if ((overlay == "rail" || tool?.roadKind == "rail" || tool?.kind == "rail") && t.rail) {
                        fillRect(canvas, sx + cell * 0.12f, sy + cell * 0.38f, cell * 0.76f, cell * 0.24f, RGBA(70, 70, 78, 210))
                    }
                }
            }
        }

        // ---- 1.5) 地物装饰：树冠 / 山形 / 水纹 ----
        if (cell >= 6) {
            for (ty in y0..y1) {
                for (tx in x0..x1) {
                    val t = w.grid[ty - 1][tx - 1]
                    if (t.building != null || t.road != null) continue
                    val sx = worldToScreenX((tx - 1).toFloat())
                    val sy = worldToScreenY((ty - 1).toFloat())
                    when {
                        t.terrain == "forest" -> drawTreeBlocks(canvas, sx, sy, tx, ty)
                        t.terrain == "hill" && cell >= 8 -> drawHillBlocks(canvas, sx, sy, tx, ty)
                        t.terrain == "water" && cell >= 8 -> drawWaterRipple(canvas, sx, sy, tx, ty)
                        t.terrain != "water" && hash01(tx, ty, 9) > 0.86f -> {
                            fillRoundRect(
                                canvas, sx + cell * 0.38f, sy + cell * 0.62f,
                                cell * 0.12f, cell * 0.10f, 1.5f, RGBA(90, 140, 80)
                            )
                        }
                    }
                }
            }
        }

        // ---- 2) 分区格缝浅线 ----
        if (cell >= 8) {
            path.reset()
            var any = false
            for (ty in y0..y1) {
                for (tx in x0..x1) {
                    val t = w.grid[ty - 1][tx - 1]
                    if (t.zone != "none" && t.road == null && t.building == null) {
                        val sx = worldToScreenX((tx - 1).toFloat())
                        val sy = worldToScreenY((ty - 1).toFloat())
                        path.addRect(sx, sy, sx + cell, sy + cell, Path.Direction.CW)
                        any = true
                    }
                }
            }
            if (any) strokePath(canvas, path, RGBA(255, 255, 255, 22), 255, 0.5f)
        }

        // ---- 3) 道路标线：两车道中虚线 / 四车道双黄+两侧白虚线 / 高速双黄 ----
        if (cell >= 8) {
            val localDash = mutableListOf<Float>()
            val aveYellowA = mutableListOf<Float>()
            val aveYellowB = mutableListOf<Float>()
            val aveWhite = mutableListOf<Float>()
            val hwyYellowA = mutableListOf<Float>()
            val hwyYellowB = mutableListOf<Float>()
            val hwyWhite = mutableListOf<Float>()
            val railPt = mutableListOf<Float>()
            val cross = mutableListOf<Triple<Float, Float, Int>>()
            for (ty in y0..y1) {
                for (tx in x0..x1) {
                    val t = w.grid[ty - 1][tx - 1]
                    val sx = worldToScreenX((tx - 1).toFloat())
                    val sy = worldToScreenY((ty - 1).toFloat())
                    val cx = sx + cell * 0.5f
                    val cy = sy + cell * 0.5f
                    if (t.rail) {
                        val ru = w.grid.getOrNull(ty - 2)?.get(tx - 1)?.rail == true
                        val rd = w.grid.getOrNull(ty)?.get(tx - 1)?.rail == true
                        val rl = w.grid[ty - 1].getOrNull(tx - 2)?.rail == true
                        val rr = w.grid[ty - 1].getOrNull(tx)?.rail == true
                        if (rl || rr) {
                            railPt.add(sx); railPt.add(cy)
                            railPt.add(sx + cell); railPt.add(cy)
                        }
                        if (ru || rd) {
                            railPt.add(cx); railPt.add(sy)
                            railPt.add(cx); railPt.add(sy + cell)
                        }
                    }
                    val kind = t.road ?: continue
                    val up = w.grid.getOrNull(ty - 2)?.get(tx - 1)?.road != null
                    val down = w.grid.getOrNull(ty)?.get(tx - 1)?.road != null
                    val left = w.grid[ty - 1].getOrNull(tx - 2)?.road != null
                    val right = w.grid[ty - 1].getOrNull(tx)?.road != null
                    val horiz = left || right
                    val vert = up || down
                    val crossroad = horiz && vert
                    if (!crossroad) {
                        when (kind) {
                            "highway" -> {
                                val gap = cell * 0.06f
                                val lane = cell * 0.22f
                                if (horiz) {
                                    addSeg(hwyYellowA, sx, cy - gap, sx + cell, cy - gap)
                                    addSeg(hwyYellowB, sx, cy + gap, sx + cell, cy + gap)
                                    addSeg(hwyWhite, sx, cy - lane, sx + cell, cy - lane)
                                    addSeg(hwyWhite, sx, cy + lane, sx + cell, cy + lane)
                                }
                                if (vert) {
                                    addSeg(hwyYellowA, cx - gap, sy, cx - gap, sy + cell)
                                    addSeg(hwyYellowB, cx + gap, sy, cx + gap, sy + cell)
                                    addSeg(hwyWhite, cx - lane, sy, cx - lane, sy + cell)
                                    addSeg(hwyWhite, cx + lane, sy, cx + lane, sy + cell)
                                }
                            }
                            "avenue" -> {
                                val gap = cell * 0.045f
                                val lane = cell * 0.24f
                                if (horiz) {
                                    addSeg(aveYellowA, sx, cy - gap, sx + cell, cy - gap)
                                    addSeg(aveYellowB, sx, cy + gap, sx + cell, cy + gap)
                                    addSeg(aveWhite, sx, cy - lane, sx + cell, cy - lane)
                                    addSeg(aveWhite, sx, cy + lane, sx + cell, cy + lane)
                                }
                                if (vert) {
                                    addSeg(aveYellowA, cx - gap, sy, cx - gap, sy + cell)
                                    addSeg(aveYellowB, cx + gap, sy, cx + gap, sy + cell)
                                    addSeg(aveWhite, cx - lane, sy, cx - lane, sy + cell)
                                    addSeg(aveWhite, cx + lane, sy, cx + lane, sy + cell)
                                }
                            }
                            "local" -> {
                                if (horiz) addSeg(localDash, sx, cy, sx + cell, cy)
                                if (vert) addSeg(localDash, cx, sy, cx, sy + cell)
                            }
                        }
                    }
                    if (crossroad) {
                        cross.add(Triple(cx, cy, if (kind == "avenue" || kind == "highway") 1 else 0))
                    }
                }
            }
            val dash = DashPathEffect(floatArrayOf(max(3.5f, cell * 0.18f), max(2.4f, cell * 0.12f)), 0f)
            if (localDash.isNotEmpty()) {
                fillLines(canvas, localDash.toFloatArray(), RGBA(236, 236, 240, 210), 255, max(1f, cell * 0.035f), dash)
            }
            if (aveYellowA.isNotEmpty()) {
                fillLines(canvas, aveYellowA.toFloatArray(), RGBA(236, 196, 70, 230), 255, max(1.1f, cell * 0.04f))
                fillLines(canvas, aveYellowB.toFloatArray(), RGBA(236, 196, 70, 230), 255, max(1.1f, cell * 0.04f))
            }
            if (aveWhite.isNotEmpty()) {
                fillLines(canvas, aveWhite.toFloatArray(), RGBA(240, 240, 245, 210), 255, max(0.9f, cell * 0.03f), dash)
            }
            if (hwyYellowA.isNotEmpty()) {
                fillLines(canvas, hwyYellowA.toFloatArray(), RGBA(236, 196, 70, 240), 255, max(1.3f, cell * 0.045f))
                fillLines(canvas, hwyYellowB.toFloatArray(), RGBA(236, 196, 70, 240), 255, max(1.3f, cell * 0.045f))
            }
            if (hwyWhite.isNotEmpty()) {
                fillLines(canvas, hwyWhite.toFloatArray(), RGBA(245, 245, 248, 220), 255, max(1f, cell * 0.032f), dash)
            }
            if (railPt.isNotEmpty()) {
                fillLines(canvas, railPt.toFloatArray(), RGBA(48, 48, 52, 240), 255, max(2.2f, cell * 0.16f))
                fillLines(canvas, railPt.toFloatArray(), RGBA(210, 210, 214, 220), 255, max(0.8f, cell * 0.04f))
            }
            if (typeface != null && cell >= 12f) {
                for (line in w.roadLines) {
                    val n = min(line.segX.size, line.segY.size)
                    if (n <= 0) continue
                    val idx = if (n <= 2) 0 else n / 3
                    val mx = line.segX[idx]
                    val my = line.segY[idx]
                    if (mx < x0 || mx > x1 || my < y0 || my > y1) continue
                    if (World.tile(mx, my)?.road == null) continue
                    drawStreetNameOnRoad(canvas, mx, my, line.name, line.dir == "v")
                }
            }
            if (cross.isNotEmpty()) {
                val cycle = (Growth.simTime * 1.2).toInt() % 4
                val red = cycle < 2
                val col = if (red) RGBA(210, 60, 50, 255) else RGBA(70, 180, 90, 255)
                val box = max(2.4f, cell * 0.11f)
                for ((cx, cy, _) in cross) {
                    // 路口信号灯做成小方盒，避免被看成路上行人圆点
                    fillRect(canvas, cx + cell * 0.16f, cy - box, box * 0.62f, box * 1.55f, RGBA(36, 36, 40, 235))
                    fillRect(
                        canvas,
                        cx + cell * 0.16f + box * 0.12f,
                        cy - box * 0.72f,
                        box * 0.38f,
                        box * 0.38f,
                        col
                    )
                }
            }
            // 路灯：夜晚只在路边角落发微光，不在车道中央画圆点
            if (nightLevel > 0.3f) {
                for (ty in y0..y1) {
                    for (tx in x0..x1) {
                        if (w.grid[ty - 1][tx - 1].road != null && (tx + ty) % 2 == 0) {
                            val lx = worldToScreenX(tx - 1f)
                            val ly = worldToScreenY(ty - 1f)
                            val glow = max(1.6f, cell * 0.06f)
                            fillRect(
                                canvas, lx + cell * 0.06f, ly + cell * 0.08f,
                                glow, glow * 1.6f,
                                RGBA(255, 230, 150, (nightLevel * 90).toInt())
                            )
                        }
                    }
                }
            }
            // 电线杆（沿道路，白天可见）
            if (cell >= 12) {
                strokeColor(RGBA(90, 85, 75, 160), 255, max(1f, cell * 0.02f))
                for (ty in y0..y1) {
                    for (tx in x0..x1) {
                        if (w.grid[ty - 1][tx - 1].road == null || (tx + ty) % 2 == 1) continue
                        val px = worldToScreenX(tx - 1f) + cell * 0.14f
                        val py = worldToScreenY(ty - 1f)
                        canvas.drawLine(px, py + cell * 0.2f, px, py + cell * 0.5f, paint)
                    }
                }
            }
        }

        // ---- 3.5) 车辆（一户一车 / 高速游客，右行车道） ----
        if (cell >= 8) {
            for (c in Traffic.cars) {
                if (c.parked) continue
                val v = dirs[c.dir]
                var sx = worldToScreenX(c.x - 1 + v[0] * c.prog + 0.5f)
                var sy = worldToScreenY(c.y - 1 + v[1] * c.prog + 0.5f)
                val kind = World.tile(c.x, c.y)?.road ?: "local"
                val inner = cell * when (kind) {
                    "highway" -> 0.12f
                    "avenue" -> 0.13f
                    else -> 0.16f
                }
                val outer = cell * when (kind) {
                    "highway" -> 0.30f
                    "avenue" -> 0.32f
                    else -> 0.16f
                }
                val off = if (c.lane == 1) outer else inner
                when (c.dir) {
                    0 -> sy += off
                    2 -> sy -= off
                    1 -> sx -= off
                    3 -> sx += off
                }
                if (sx > -cell && sy > -cell && sx < viewW + cell && sy < viewH + cell) {
                    drawVehicleBox(
                        canvas, sx, sy, c.dir, c.color,
                        longBody = c.kind == "freight",
                        selected = Traffic.selected === c
                    )
                }
            }
        }

        // ---- 3.6) 公交车 + 草稿线路 ----
        if (cell >= 8) {
            val draftCols = listOf(RGBA(70, 140, 210), RGBA(210, 90, 70), RGBA(80, 170, 110), RGBA(180, 120, 40))
            fun drawStopLine(stops: List<BusStopRef>, col: RGBA) {
                if (stops.size < 2) return
                val pts = mutableListOf<Float>()
                for (i in 0 until stops.size - 1) {
                    pts.add(worldToScreenX(stops[i].x - 0.5f))
                    pts.add(worldToScreenY(stops[i].y - 0.5f))
                    pts.add(worldToScreenX(stops[i + 1].x - 0.5f))
                    pts.add(worldToScreenY(stops[i + 1].y - 0.5f))
                }
                fillLines(canvas, pts.toFloatArray(), col, 220, max(1.5f, cell * 0.08f))
            }
            for ((idx, line) in Transit.lines.withIndex()) {
                drawStopLine(line.stops, draftCols[idx % draftCols.size])
            }
            if (Transit.draft.isNotEmpty()) {
                drawStopLine(Transit.draft, RGBA(255, 90, 70, 220))
                for (s in Transit.draft) {
                    fillCircle(
                        canvas, worldToScreenX(s.x - 0.5f), worldToScreenY(s.y - 0.5f),
                        cell * 0.18f, RGBA(255, 90, 70)
                    )
                }
            }
            for (v in Transit.vehicles) {
                val (wx, wy) = Transit.vehicleCell(v)
                val sx = worldToScreenX(wx)
                val sy = worldToScreenY(wy)
                drawVehicleBox(canvas, sx, sy, Transit.heading(v), RGBA(40, 90, 170), longBody = true, selected = false)
            }
            for (ev in CitySystems.cars) {
                val (wx, wy) = CitySystems.screenCell(ev)
                val sx = worldToScreenX(wx)
                val sy = worldToScreenY(wy)
                val col = when (ev.kind) {
                    "fire" -> RGBA(220, 70, 50)
                    "ambulance" -> RGBA(240, 240, 245)
                    "police" -> RGBA(50, 80, 180)
                    "garbage" -> RGBA(90, 118, 86)
                    else -> RGBA(40, 40, 40)
                }
                drawVehicleBox(canvas, sx, sy, CitySystems.heading(ev), col, longBody = ev.kind == "garbage" || ev.kind == "hearse", selected = CitySystems.selected === ev)
            }
            for (tr in Traffic.trains) {
                val sx = worldToScreenX(tr.x - 1 + dirs[tr.dir][0] * tr.prog + 0.5f)
                val sy = worldToScreenY(tr.y - 1 + dirs[tr.dir][1] * tr.prog + 0.5f)
                drawVehicleBox(canvas, sx, sy, tr.dir, RGBA(36, 52, 78), longBody = true, selected = Traffic.selectedTrain === tr)
            }
            for (pl in Traffic.planes) {
                val sx = worldToScreenX(pl.x - 0.5f)
                val sy = worldToScreenY(pl.y - 0.5f) - pl.alt * cell * 0.18f
                val body = RGBA(230, 232, 238)
                fillRoundRect(canvas, sx - cell * 0.38f, sy - cell * 0.08f, cell * 0.76f, cell * 0.16f, 3f, body.shade(0.72))
                fillRoundRect(canvas, sx - cell * 0.38f, sy - cell * 0.16f, cell * 0.76f, cell * 0.14f, 3f, body)
                fillRect(canvas, sx - cell * 0.08f, sy - cell * 0.30f, cell * 0.16f, cell * 0.52f, RGBA(210, 214, 222))
                fillCircle(canvas, sx, sy, cell * 0.06f, RGBA(70, 90, 130))
                if (Traffic.selectedPlane === pl) {
                    strokeRoundRect(canvas, sx - cell * 0.42f, sy - cell * 0.32f, cell * 0.84f, cell * 0.64f, 4f, RGBA(220, 80, 50), 255, 1.4f)
                }
            }
        }

        // 市民通勤只体现在车辆上，不画路上行人圆点。

        // ---- 4) 建筑（格内斜二测三面体块，从后往前画） ----
        for (ty in y0..y1) {
            for (tx in x0..x1) {
                val t = w.grid[ty - 1][tx - 1]
                val bl = t.building ?: continue
                val isService = bl.isService
                val anchor = (!isService) || (bl.ax == tx && bl.ay == ty)
                if (!anchor) continue
                boxFlip = ((tx * 3 + ty * 5) and 1) == 1
                val sx = worldToScreenX((if (isService) bl.ax else tx) - 1f)
                val sy = worldToScreenY((if (isService) bl.ay else ty) - 1f)
                val bw = cell * bl.w
                val bh = cell * bl.h
                val base: RGBA
                val hFactor: Float
                if (isService) {
                    if (bl.service == "park" || bl.service == "plaza") {
                        base = C.bService; hFactor = 0.72f
                    } else {
                        base = C.bCivic
                        hFactor = when (bl.service) {
                            "water_tower" -> 2.35f
                            "tv_tower" -> 3.4f
                            "stadium" -> 0.85f
                            "university" -> 1.85f
                            "coal_plant", "nuclear_plant" -> 1.75f
                            "wind_farm" -> 2.2f
                            "clinic" -> 1.25f
                            "hospital" -> 1.85f
                            "school", "middle_school" -> 1.35f
                            "fire_station", "police" -> 1.25f
                            else -> 1.25f
                        }
                    }
                } else {
                    base = if (bl.abandoned) C.bAbandoned else when (bl.zone) {
                        "residential" -> C.bResidential
                        "commercial" -> C.bCommercial
                        "industrial" -> C.bIndustrial
                        "office" -> C.bOffice
                        else -> C.bResidential
                    }
                    hFactor = when (bl.zone) {
                        "residential" -> when (bl.level) {
                            1 -> 1.35f
                            2 -> 2.05f
                            else -> 3.05f
                        }
                        "commercial" -> when (bl.level) {
                            1 -> 1.05f
                            2 -> 1.55f
                            else -> 2.15f
                        }
                        "industrial" -> when (bl.level) {
                            1 -> 0.95f
                            2 -> 1.35f
                            else -> 1.75f
                        }
                        "office" -> when (bl.level) {
                            1 -> 1.85f
                            2 -> 2.55f
                            else -> 3.35f
                        }
                        else -> 1.2f
                    }
                }
                // 生长动画：新建建筑从 30% 弹到 100%
                var anim = 1f
                if (!isService) {
                    val age = Growth.simTime - bl.born
                    if (age < 0.6) {
                        val k = max(0f, (age / 0.6).toFloat())
                        anim = 0.25f + 0.75f * (k * k * (3 - 2 * k))
                    }
                }
                val hpx = clamp(cell * 1.12f * hFactor * anim, cell * 0.48f, cell * 5.4f)
                val shrink = (anim - 1) * bw * 0.5f
                drawBuilding(
                    canvas,
                    sx - shrink,
                    sy - shrink * (bh / bw),
                    bw + shrink * 2,
                    bh + shrink * 2 * (bh / bw),
                    hpx, base, bl, tx, ty
                )
            }
        }

        // 专精角标画在楼顶前，刷完立刻看得见
        if (cell >= 8) {
            for (ty in y0..y1) {
                for (tx in x0..x1) {
                    val spec = Config.specOf(w.grid[ty - 1][tx - 1].spec) ?: continue
                    val sx = worldToScreenX((tx - 1).toFloat())
                    val sy = worldToScreenY((ty - 1).toFloat())
                    fillRect(canvas, sx + 1f, sy + 1f, cell * 0.28f, cell * 0.28f, spec.color.withAlpha(230))
                }
            }
        }

        // ---- 4.5) 覆盖热力图（电力/供水/垃圾/医疗/教育/安全/交通/地价） ----
        if (overlay == "traffic") {
            // 拥堵热力图（道路流量红黄绿）
            for (ty in y0..y1) {
                for (tx in x0..x1) {
                    val t = w.grid[ty - 1][tx - 1]
                    if (t.road == null) continue
                    val flow = Traffic.flowAt(tx, ty)
                    val col = when {
                        flow > 40 -> RGBA(220, 80, 70, 150)
                        flow > 12 -> RGBA(230, 190, 70, 150)
                        else -> RGBA(90, 200, 120, 120)
                    }
                    fillRect(
                        canvas, worldToScreenX(tx - 1f), worldToScreenY(ty - 1f),
                        cell, cell, col
                    )
                }
            }
        } else if (overlay == "landvalue") {
            // 地价热力图（滨水/绿地抬升，工业拉低）
            for (ty in y0..y1) {
                for (tx in x0..x1) {
                    val t = w.grid[ty - 1][tx - 1]
                    if (t.road != null || t.building != null) continue
                    var v = 0
                    for (dy in -2..2) for (dx in -2..2) {
                        val nt = World.tile(tx + dx, ty + dy) ?: continue
                        if (nt.terrain == "water") v += 3
                        if (nt.building?.isService == true) {
                            val cfg = World.serviceConfig(nt.building!!.service)
                            if (cfg?.category == Config.ServiceCat.AMENITY) v += 2
                        }
                        if (nt.building?.zone == "industrial") v -= 2
                    }
                    val col = when {
                        v > 6 -> RGBA(90, 200, 120, 100)
                        v > 0 -> RGBA(200, 200, 120, 90)
                        else -> RGBA(220, 90, 80, 90)
                    }
                    fillRect(
                        canvas, worldToScreenX(tx - 1f), worldToScreenY(ty - 1f),
                        cell, cell, col
                    )
                }
            }
        } else if (overlay == "spec") {
            for (ty in y0..y1) {
                for (tx in x0..x1) {
                    val t = w.grid[ty - 1][tx - 1]
                    val spec = Config.specOf(t.spec) ?: continue
                    fillRect(
                        canvas, worldToScreenX(tx - 1f), worldToScreenY(ty - 1f),
                        cell, cell, spec.color.withAlpha(120)
                    )
                }
            }
        } else if (overlay.isNotEmpty() && overlay !in listOf("metro", "rail")) {
            val hue = overlayHue(overlay)
            val green = hue.withAlpha(95)
            val red = RGBA(210, 70, 60, 70)
            for (ty in y0..y1) {
                for (tx in x0..x1) {
                    val t = w.grid[ty - 1][tx - 1]
                    val sx = worldToScreenX(tx - 1f)
                    val sy = worldToScreenY(ty - 1f)
                    val b = t.building
                    if (b != null && b.isService) {
                        val cfg = World.serviceConfig(b.service)
                        if (cfg != null && cfg.category == overlay && b.ax == tx && b.ay == ty) {
                            fillRect(canvas, sx, sy, cell * b.w, cell * b.h, hue.withAlpha(160))
                        }
                    } else {
                        val fac = World.coveringFacility(tx, ty, overlay)
                        if (fac != null) {
                            fillRect(canvas, sx, sy, cell, cell, green)
                        } else if (b != null && !b.isService) {
                            fillRect(canvas, sx, sy, cell, cell, red)
                        }
                    }
                }
            }
            for (e in World.allBuildings()) {
                if (!e.b.isService) continue
                val cfg = World.serviceConfig(e.b.service) ?: continue
                if (cfg.category != overlay) continue
                val (ccx, ccy) = World.coverCenter(e)
                val cx = worldToScreenX(ccx)
                val cy = worldToScreenY(ccy)
                val r = cell * (World.coverRadius(cfg) + 0.5f)
                fillCircle(canvas, cx, cy, r, hue.withAlpha(22))
                strokeCircle(canvas, cx, cy, r, hue.withAlpha(210), 255, 2.0f)
                val capTxt = when {
                    cfg.powerCap > 0 -> cfg.name + " 电" + cfg.powerCap + " 半径" + World.coverRadius(cfg)
                    cfg.waterCap > 0 -> cfg.name + " 水" + cfg.waterCap + " 半径" + World.coverRadius(cfg)
                    else -> cfg.name + " 半径" + World.coverRadius(cfg) + "格"
                }
                if (typeface != null && cell >= 9) {
                    drawText(canvas, cx, cy - r - 8f, max(10f, cell * 0.38f), hue.shade(0.45), capTxt, TAlign.CENTER, 235)
                }
            }
        }

        // ---- 5) 标签 ----
        if (typeface != null) {
            // 路名已画在路面层；建筑名贴前墙。这里只画预置 POI
            // 预置 POI 标签
            for (lb in w.labels) {
                val sx = worldToScreenX(lb.x - 0.5f)
                val sy = worldToScreenY(lb.y - 0.5f)
                if (sx > -60 && sx < viewW + 60 && sy > topInset - 30 && sy < viewH + 30) {
                    val pad = cell * 0.18f
                    val fs = cell * 0.5f
                    val tw = measure(lb.text, fs)
                    fillRoundRect(
                        canvas,
                        sx - tw / 2 - pad, sy - fs / 2 - pad * 0.7f,
                        tw + pad * 2, fs + pad * 1.4f, 2f,
                        if (lb.kind == "red") C.accentRed else C.accentBlue
                    )
                    drawText(canvas, sx, sy, fs, RGBA(255, 255, 255), lb.text, TAlign.CENTER, 255)
                }
            }
        }

        // ---- 5.5) 划区/设施草稿预览（确认前不扣费） ----
        val sid = GameData.serviceDraftId
        if (sid != null && GameData.serviceDraftX > 0) {
            val sc = World.serviceConfig(sid)
            if (sc != null) {
                val sx = worldToScreenX(GameData.serviceDraftX - 1f)
                val sy = worldToScreenY(GameData.serviceDraftY - 1f)
                val gw = cell * sc.sizeW
                val gh = cell * sc.sizeH
                val cx = worldToScreenX(GameData.serviceDraftX - 1 + sc.sizeW / 2f)
                val cy = worldToScreenY(GameData.serviceDraftY - 1 + sc.sizeH / 2f)
                val cr = World.coverRadius(sc).toFloat()
                fillCircle(canvas, cx, cy, cell * (cr + 0.5f), RGBA(96, 200, 140, 26))
                strokeCircle(canvas, cx, cy, cell * (cr + 0.5f), RGBA(96, 200, 140, 90), 255, 1f)
                fillRect(canvas, sx, sy, gw, gh, C.ghostOk)
                strokeColor(RGBA(110, 220, 140, 230), 255, 1.8f)
                canvas.drawRect(sx, sy, sx + gw, sy + gh, paint)
            }
        }
        if (GameData.zoneDraft.isNotEmpty()) {
            val zc = when (GameData.zoneDraftKey) {
                "residential" -> C.zoneResidential
                "commercial" -> C.zoneCommercial
                "industrial" -> C.zoneIndustrial
                "office" -> C.zoneOffice
                else -> C.ghostOk
            }
            for (k in GameData.zoneDraft) {
                val zx = GameData.unpackTileX(k)
                val zy = GameData.unpackTileY(k)
                val sx = worldToScreenX(zx - 1f)
                val sy = worldToScreenY(zy - 1f)
                fillRect(canvas, sx, sy, cell, cell, zc.withAlpha(110))
                strokeColor(zc.shade(0.55), 200, 1.4f)
                canvas.drawRect(sx, sy, sx + cell, sy + cell, paint)
            }
        }

        // ---- 6) 悬停 / 选中 / 工具幽灵 ----
        if (tool != null && hasHover) {
            val gx = worldToScreenX(hoverX - 1f)
            val gy = worldToScreenY(hoverY - 1f)
            val ok = toolValidAt(hoverX, hoverY)
            var gw = cell
            var gh = cell
            if (tool?.kind == "service") {
                val sc = World.serviceConfig(tool?.id)
                if (sc != null) {
                    gw = cell * sc.sizeW
                    gh = cell * sc.sizeH
                    val cx = worldToScreenX(hoverX - 1 + sc.sizeW / 2f)
                    val cy = worldToScreenY(hoverY - 1 + sc.sizeH / 2f)
                    val cr = World.coverRadius(sc).toFloat()
                    fillCircle(canvas, cx, cy, cell * (cr + 0.5f), RGBA(96, 200, 140, 26))
                    strokeCircle(canvas, cx, cy, cell * (cr + 0.5f), RGBA(96, 200, 140, 90), 255, 1f)
                    val capTxt = when {
                        sc.powerCap > 0 -> "电容量 " + sc.powerCap + " · 半径 " + World.coverRadius(sc)
                        sc.waterCap > 0 -> "水容量 " + sc.waterCap + " · 半径 " + World.coverRadius(sc)
                        else -> "半径 " + World.coverRadius(sc) + " 格"
                    }
                    if (typeface != null) {
                        drawText(canvas, cx, cy + cell * (cr + 0.5f) + 10f, 11f, RGBA(40, 90, 60), capTxt, TAlign.CENTER, 230)
                    }
                }
            }
            fillRect(canvas, gx, gy, gw, gh, if (ok) C.ghostOk else C.ghostBad)
            strokeColor(if (ok) RGBA(110, 220, 140, 230) else RGBA(220, 90, 80, 230), 255, 1.5f)
            canvas.drawRect(gx, gy, gx + gw, gy + gh, paint)
        } else if (hasHover) {
            var hx = worldToScreenX(hoverX - 1f)
            var hy = worldToScreenY(hoverY - 1f)
            val t = World.tile(hoverX, hoverY)
            val cellW = cell * (t?.building?.w ?: 1)
            val cellH = cell * (t?.building?.h ?: 1)
            if (t?.building != null) {
                hx += (t.building!!.ax - hoverX) * cell
                hy += (t.building!!.ay - hoverY) * cell
            }
            strokeColor(C.hoverStroke, 255, 1.5f)
            canvas.drawRect(hx, hy, hx + cellW, hy + cellH, paint)
        }
        if (hasSelection) {
            val t = World.tile(selectedX, selectedY)
            var sxp = worldToScreenX(selectedX - 1f)
            var syp = worldToScreenY(selectedY - 1f)
            var sw = cell
            var sh = cell
            if (t?.building != null) {
                sxp += (t.building!!.ax - selectedX) * cell
                syp += (t.building!!.ay - selectedY) * cell
                sw = cell * t.building!!.w
                sh = cell * t.building!!.h
            }
            fillRect(canvas, sxp, syp, sw, sh, C.selectFill)
            strokeColor(C.selectStroke, 255, 2f)
            canvas.drawRect(sxp, syp, sxp + sw, syp + sh, paint)
        }

        // ---- 6.5) 夜晚压暗 ----
        if (nightLevel > 0.02f) {
            fillRect(canvas, 0f, 0f, viewW, viewH, RGBA(18, 24, 52, (nightLevel * 88).toInt()))
        }
        if (Ambience.lightning > 0.04f) {
            fillRect(canvas, 0f, 0f, viewW, viewH, RGBA(230, 236, 255, (Ambience.lightning * 90).toInt()))
        }

        // ---- 6.6) 天气：屏幕空间粒子雨，落地溅开
        when (GameData.weather) {
            1 -> {
                fillRect(canvas, 0f, 0f, viewW, viewH, RGBA(70, 90, 110, 28))
                paint.strokeCap = Paint.Cap.ROUND
                for (d in rainDrops) {
                    if (d.splash > 0f) {
                        val a = (180 * d.splash).toInt()
                        fillCircle(canvas, d.splashX, d.splashY, 2.2f + (1f - d.splash) * 5f, RGBA(210, 225, 240, a))
                    } else if (d.vy > 1f) {
                        strokeColor(RGBA(210, 225, 240, 190), 255, d.thick)
                        canvas.drawLine(d.x, d.y, d.x, d.y + d.len, paint)
                    }
                }
            }
            2 -> {
                fillRect(canvas, 0f, 0f, viewW, viewH, RGBA(215, 222, 228, 55))
            }
        }

        // ---- 7) Toast ----
        if (toastMsg != null && typeface != null) {
            val alpha = if (toastT > 2.6f) clamp((3.2f - toastT) / 0.6f, 0f, 1f) else 1f
            val fs = 12f
            val tw = measure(toastMsg!!, fs)
            val px = viewW / 2f
            val py = viewH - bottomInset - 30f
            fillRoundRect(
                canvas, px - tw / 2 - 10, py - fs, tw + 20, fs * 2 + 6, 8f,
                RGBA(250, 250, 248, 235), (235 * alpha).toInt()
            )
            strokeRoundRect(
                canvas, px - tw / 2 - 10, py - fs, tw + 20, fs * 2 + 6, 8f,
                C.accentRed, (200 * alpha).toInt(), 1f
            )
            drawText(canvas, px, py + 3, fs, RGBA(176, 66, 66), toastMsg!!, TAlign.CENTER, (255 * alpha).toInt())
        }
    }

    private fun zoneBaseColor(t: Tile, x: Int, y: Int): RGBA {
        val C = Config.COLORS
        if (t.metro) return RGBA(48, 72, 110)
        if (t.rail) return RGBA(72, 72, 78)
        t.road?.let {
            return when (it) {
                "highway" -> C.roadHighway
                "avenue" -> C.roadAvenue
                "dirt" -> C.roadDirt
                else -> C.roadLocal
            }
        }
        when (t.zone) {
            "residential" -> return C.zoneResidential
            "commercial" -> return C.zoneCommercial
            "industrial" -> return C.zoneIndustrial
            "office" -> return C.zoneOffice
        }
        when (t.terrain) {
            "water" -> return if ((x + y) % 2 == 0) C.water else C.waterAlt
            "forest" -> return C.forest
            "hill" -> return C.hill
            "plain" -> return C.plain
        }
        return if ((x + y) % 2 == 0) C.grass else C.grassAlt
    }

    private fun drawBuilding(
        canvas: Canvas, rx0: Float, ry0: Float, rw0: Float, rh0: Float,
        hpx: Float, base: RGBA, bl: Building, tx: Int, ty: Int
    ) {
        val cell = this.cell
        val pad = max(0.8f, cell * 0.05f)
        val bx = rx0 + pad
        val by = ry0 + pad
        val bw = rw0 - pad * 2
        val bh = rh0 - pad * 2
        val seed = tx * 17 + ty * 31 + bl.level * 9
        val variant = seed % 3
        boxFlip = (seed and 1) == 1
        when {
            bl.service == "park" -> {
                fillRoundRect(canvas, bx + 1.2f, by + 2f, bw, bh, 3f, RGBA(28, 34, 30, 40))
                drawSolidBox(canvas, bx + bw * 0.06f, by + bh * 0.58f, bw * 0.88f, bh * 0.28f, hpx * 0.18f, RGBA(210, 198, 140))
                drawSolidBox(canvas, bx + bw * 0.18f, by + bh * 0.42f, cell * 0.10f, cell * 0.12f, hpx * 0.55f, RGBA(92, 70, 48))
                drawSolidBox(canvas, bx + bw * 0.58f, by + bh * 0.32f, cell * 0.09f, cell * 0.11f, hpx * 0.48f, RGBA(92, 70, 48))
                drawSolidBox(canvas, bx + bw * 0.12f, by + bh * 0.16f, bw * 0.28f, bh * 0.28f, hpx * 0.32f, RGBA(46, 108, 58))
                drawSolidBox(canvas, bx + bw * 0.52f, by + bh * 0.08f, bw * 0.24f, bh * 0.24f, hpx * 0.26f, RGBA(62, 128, 70))
            }
            bl.service == "plaza" -> {
                drawSolidBox(canvas, bx, by, bw, bh, hpx * 0.12f, RGBA(188, 186, 176))
                drawSolidBox(canvas, bx + bw * 0.12f, by + bh * 0.12f, bw * 0.76f, bh * 0.76f, hpx * 0.18f, RGBA(214, 210, 196))
                drawSolidBox(canvas, bx + bw * 0.38f, by + bh * 0.38f, bw * 0.24f, bh * 0.24f, hpx * 0.55f, RGBA(170, 90, 80))
                drawSolidBox(canvas, bx + bw * 0.14f, by + bh * 0.16f, cell * 0.16f, cell * 0.16f, hpx * 0.22f, RGBA(70, 130, 80))
                drawSolidBox(canvas, bx + bw * 0.74f, by + bh * 0.70f, cell * 0.16f, cell * 0.16f, hpx * 0.22f, RGBA(70, 130, 80))
            }
            bl.service == "wind_farm" -> {
                drawSolidBox(canvas, bx + bw * 0.32f, by + bh * 0.52f, bw * 0.36f, bh * 0.32f, hpx * 0.28f, RGBA(210, 214, 218))
                val cxp = bx + bw * 0.5f
                val cyp = by - hpx * 0.55f
                strokeColor(RGBA(230, 230, 230), 255, max(1.4f, cell * 0.05f))
                canvas.drawLine(cxp, by + bh * 0.2f, cxp, cyp, paint)
                fillCircle(canvas, cxp, cyp, cell * 0.07f, RGBA(240, 240, 240))
                val ang = rainPhase * 4.2f
                for (k in 0..2) {
                    val a = ang + k * 2.094f
                    canvas.drawLine(cxp, cyp, cxp + cos(a) * cell * 0.32f, cyp + sin(a) * cell * 0.32f, paint)
                }
            }
            bl.service == "solar_plant" -> {
                drawSolidBox(canvas, bx + bw * 0.04f, by + bh * 0.62f, bw * 0.92f, bh * 0.28f, hpx * 0.12f, RGBA(70, 92, 78))
                drawSolidBox(canvas, bx + bw * 0.08f, by + bh * 0.12f, bw * 0.38f, bh * 0.30f, hpx * 0.22f, RGBA(40, 70, 130))
                drawSolidBox(canvas, bx + bw * 0.52f, by + bh * 0.12f, bw * 0.38f, bh * 0.30f, hpx * 0.22f, RGBA(40, 70, 130))
                drawSolidBox(canvas, bx + bw * 0.08f, by + bh * 0.48f, bw * 0.38f, bh * 0.28f, hpx * 0.18f, RGBA(50, 86, 150))
                drawSolidBox(canvas, bx + bw * 0.52f, by + bh * 0.48f, bw * 0.38f, bh * 0.28f, hpx * 0.18f, RGBA(50, 86, 150))
                drawSolidBox(canvas, bx + bw * 0.40f, by + bh * 0.40f, bw * 0.20f, bh * 0.20f, hpx * 0.4f, RGBA(200, 204, 208))
            }
            bl.service == "coal_plant" -> {
                drawSolidBox(canvas, bx + bw * 0.06f, by + bh * 0.28f, bw * 0.58f, bh * 0.58f, hpx * 0.7f, RGBA(92, 90, 86))
                drawSolidBox(canvas, bx + bw * 0.68f, by + bh * 0.18f, bw * 0.14f, bh * 0.22f, hpx * 1.4f, RGBA(110, 108, 102))
                drawSolidBox(canvas, bx + bw * 0.84f, by + bh * 0.22f, bw * 0.12f, bh * 0.18f, hpx * 1.15f, RGBA(118, 116, 110))
                fillCircle(canvas, bx + bw * 0.75f, by - hpx * 0.9f, cell * 0.08f, RGBA(170, 170, 170, 140))
            }
            bl.service == "nuclear_plant" -> {
                drawSolidBox(canvas, bx + bw * 0.08f, by + bh * 0.42f, bw * 0.84f, bh * 0.42f, hpx * 0.45f, RGBA(188, 196, 188))
                fillCircle(canvas, bx + bw * 0.32f, by + bh * 0.38f, min(bw, bh) * 0.22f, RGBA(210, 218, 210))
                fillCircle(canvas, bx + bw * 0.68f, by + bh * 0.38f, min(bw, bh) * 0.22f, RGBA(210, 218, 210))
                drawSolidBox(canvas, bx + bw * 0.44f, by + bh * 0.18f, bw * 0.12f, bh * 0.18f, hpx * 1.1f, RGBA(90, 120, 90))
            }
            bl.service == "clinic" -> {
                drawSolidBox(canvas, bx + bw * 0.08f, by + bh * 0.18f, bw * 0.84f, bh * 0.64f, hpx * 0.7f, RGBA(236, 236, 240))
                drawSolidBox(canvas, bx + bw * 0.38f, by + bh * 0.28f, bw * 0.24f, bh * 0.12f, hpx * 0.88f, RGBA(210, 70, 70))
                drawSolidBox(canvas, bx + bw * 0.46f, by + bh * 0.18f, bw * 0.08f, bh * 0.32f, hpx * 0.92f, RGBA(210, 70, 70))
                drawSolidBox(canvas, bx + bw * 0.16f, by + bh * 0.62f, bw * 0.22f, bh * 0.12f, hpx * 0.18f, RGBA(90, 140, 190))
            }
            bl.service == "hospital" -> {
                drawSolidBox(canvas, bx + bw * 0.06f, by + bh * 0.14f, bw * 0.88f, bh * 0.72f, hpx * 1.05f, RGBA(236, 240, 244))
                drawSolidBox(canvas, bx + bw * 0.40f, by + bh * 0.20f, bw * 0.20f, bh * 0.10f, hpx * 1.22f, RGBA(210, 70, 70))
                drawSolidBox(canvas, bx + bw * 0.46f, by + bh * 0.10f, bw * 0.08f, bh * 0.30f, hpx * 1.28f, RGBA(210, 70, 70))
                drawSolidBox(canvas, bx + bw * 0.14f, by + bh * 0.58f, bw * 0.72f, bh * 0.18f, hpx * 0.22f, RGBA(70, 120, 180))
            }
            bl.service == "school" -> {
                drawSolidBox(canvas, bx + bw * 0.08f, by + bh * 0.22f, bw * 0.84f, bh * 0.62f, hpx * 0.7f, RGBA(232, 214, 170))
                drawPitchedRoof(canvas, bx + bw * 0.08f, by + bh * 0.22f, bw * 0.84f, bh * 0.62f, hpx * 0.7f, RGBA(150, 70, 62))
                drawSolidBox(canvas, bx + bw * 0.18f, by + bh * 0.55f, bw * 0.18f, bh * 0.18f, hpx * 0.22f, RGBA(80, 130, 180))
                drawSolidBox(canvas, bx + bw * 0.64f, by + bh * 0.55f, bw * 0.18f, bh * 0.18f, hpx * 0.22f, RGBA(80, 130, 180))
                drawSolidBox(canvas, bx + bw * 0.42f, by + bh * 0.62f, bw * 0.16f, bh * 0.16f, hpx * 0.18f, RGBA(90, 70, 50))
            }
            bl.service == "middle_school" -> {
                drawSolidBox(canvas, bx + bw * 0.06f, by + bh * 0.16f, bw * 0.88f, bh * 0.70f, hpx * 0.9f, RGBA(220, 204, 168))
                drawSolidBox(canvas, bx + bw * 0.14f, by + bh * 0.30f, bw * 0.18f, bh * 0.16f, hpx * 0.28f, RGBA(70, 120, 170))
                drawSolidBox(canvas, bx + bw * 0.68f, by + bh * 0.30f, bw * 0.18f, bh * 0.16f, hpx * 0.28f, RGBA(70, 120, 170))
                drawSolidBox(canvas, bx + bw * 0.40f, by + bh * 0.62f, bw * 0.20f, bh * 0.18f, hpx * 0.2f, RGBA(90, 70, 50))
            }
            bl.service == "university" -> {
                drawSolidBox(canvas, bx + bw * 0.08f, by + bh * 0.28f, bw * 0.84f, bh * 0.58f, hpx * 0.85f, RGBA(210, 200, 178))
                drawSolidBox(canvas, bx + bw * 0.32f, by + bh * 0.10f, bw * 0.36f, bh * 0.28f, hpx * 1.35f, RGBA(188, 176, 150))
                drawSolidBox(canvas, bx + bw * 0.42f, by + bh * 0.62f, bw * 0.16f, bh * 0.18f, hpx * 0.22f, RGBA(80, 60, 40))
            }
            bl.service == "fire_station" -> {
                drawSolidBox(canvas, bx + bw * 0.08f, by + bh * 0.22f, bw * 0.84f, bh * 0.62f, hpx * 0.7f, RGBA(210, 70, 60))
                drawSolidBox(canvas, bx + bw * 0.18f, by + bh * 0.58f, bw * 0.28f, bh * 0.18f, hpx * 0.22f, RGBA(40, 44, 48))
                drawSolidBox(canvas, bx + bw * 0.54f, by + bh * 0.58f, bw * 0.28f, bh * 0.18f, hpx * 0.22f, RGBA(40, 44, 48))
                drawSolidBox(canvas, bx + bw * 0.38f, by + bh * 0.28f, bw * 0.24f, bh * 0.10f, hpx * 0.88f, RGBA(250, 250, 248))
            }
            bl.service == "police" -> {
                drawSolidBox(canvas, bx + bw * 0.10f, by + bh * 0.18f, bw * 0.80f, bh * 0.66f, hpx * 0.75f, RGBA(50, 80, 150))
                drawSolidBox(canvas, bx + bw * 0.38f, by + bh * 0.26f, bw * 0.24f, bh * 0.10f, hpx * 0.92f, RGBA(230, 210, 70))
                drawSolidBox(canvas, bx + bw * 0.22f, by + bh * 0.58f, bw * 0.56f, bh * 0.16f, hpx * 0.2f, RGBA(30, 40, 70))
            }
            bl.service == "landfill" -> {
                drawSolidBox(canvas, bx + bw * 0.04f, by + bh * 0.50f, bw * 0.92f, bh * 0.40f, hpx * 0.18f, RGBA(120, 118, 90))
                drawSolidBox(canvas, bx + bw * 0.22f, by + bh * 0.36f, bw * 0.28f, bh * 0.28f, hpx * 0.32f, RGBA(90, 92, 70))
                drawSolidBox(canvas, bx + bw * 0.52f, by + bh * 0.48f, bw * 0.22f, bh * 0.22f, hpx * 0.24f, RGBA(110, 108, 80))
                drawSolidBox(canvas, bx + bw * 0.70f, by + bh * 0.18f, bw * 0.18f, bh * 0.22f, hpx * 0.4f, RGBA(80, 140, 80))
            }
            bl.service == "incinerator" -> {
                drawSolidBox(canvas, bx + bw * 0.08f, by + bh * 0.28f, bw * 0.58f, bh * 0.56f, hpx * 0.7f, RGBA(110, 108, 102))
                drawSolidBox(canvas, bx + bw * 0.70f, by + bh * 0.16f, bw * 0.20f, bh * 0.28f, hpx * 1.2f, RGBA(90, 88, 84))
                fillCircle(canvas, bx + bw * 0.80f, by - hpx * 0.7f, cell * 0.07f, RGBA(160, 160, 160, 150))
            }
            bl.service == "pump_station" -> {
                drawSolidBox(canvas, bx + bw * 0.12f, by + bh * 0.28f, bw * 0.76f, bh * 0.52f, hpx * 0.5f, RGBA(70, 130, 170))
                fillCircle(canvas, bx + bw * 0.32f, by + bh * 0.4f, cell * 0.12f, RGBA(50, 90, 130))
                fillCircle(canvas, bx + bw * 0.68f, by + bh * 0.48f, cell * 0.10f, RGBA(50, 90, 130))
            }
            bl.service == "water_tower" -> {
                drawSolidBox(canvas, bx + bw * 0.38f, by + bh * 0.42f, bw * 0.24f, bh * 0.42f, hpx * 0.85f, RGBA(90, 120, 140))
                drawSolidBox(canvas, bx + bw * 0.22f, by + bh * 0.10f, bw * 0.56f, bh * 0.38f, hpx * 0.55f, RGBA(70, 140, 190))
                drawSolidBox(canvas, bx + bw * 0.30f, by + bh * 0.02f, bw * 0.40f, bh * 0.18f, hpx * 0.22f, RGBA(90, 160, 210))
            }
            bl.service == "sewage" -> {
                drawSolidBox(canvas, bx + bw * 0.08f, by + bh * 0.28f, bw * 0.84f, bh * 0.56f, hpx * 0.5f, RGBA(86, 118, 92))
                fillCircle(canvas, bx + bw * 0.32f, by + bh * 0.46f, cell * 0.12f, RGBA(50, 90, 70))
                fillCircle(canvas, bx + bw * 0.68f, by + bh * 0.50f, cell * 0.10f, RGBA(50, 90, 70))
            }
            bl.service == "airport" -> {
                drawSolidBox(canvas, bx, by, bw, bh, hpx * 0.10f, RGBA(168, 176, 184))
                drawSolidBox(canvas, bx + bw * 0.18f, by + bh * 0.28f, bw * 0.64f, bh * 0.44f, hpx * 0.7f, RGBA(210, 214, 220))
                drawSolidBox(canvas, bx + bw * 0.04f, by + bh * 0.46f, bw * 0.92f, bh * 0.12f, hpx * 0.08f, RGBA(90, 96, 104))
                drawSolidBox(canvas, bx + bw * 0.46f, by + bh * 0.10f, bw * 0.08f, bh * 0.80f, hpx * 0.08f, RGBA(90, 96, 104))
            }
            bl.service == "rail_station" -> {
                drawSolidBox(canvas, bx + bw * 0.1f, by + bh * 0.18f, bw * 0.8f, bh * 0.64f, hpx * 0.85f, RGBA(70, 92, 128))
                drawSolidBox(canvas, bx + bw * 0.18f, by + bh * 0.55f, bw * 0.64f, bh * 0.18f, hpx * 0.22f, RGBA(230, 210, 90))
                drawSolidBox(canvas, bx + bw * 0.08f, by + bh * 0.78f, bw * 0.84f, bh * 0.08f, hpx * 0.10f, RGBA(48, 48, 52))
            }
            bl.service == "metro" -> {
                drawSolidBox(canvas, bx + bw * 0.12f, by + bh * 0.22f, bw * 0.76f, bh * 0.58f, hpx * 0.55f, RGBA(48, 72, 110))
                drawSolidBox(canvas, bx + bw * 0.22f, by + bh * 0.58f, bw * 0.56f, bh * 0.16f, hpx * 0.18f, RGBA(230, 210, 80))
            }
            bl.service == "bus_stop" -> {
                drawSolidBox(canvas, bx + bw * 0.22f, by + bh * 0.42f, bw * 0.56f, bh * 0.32f, hpx * 0.28f, RGBA(40, 90, 170))
                drawSolidBox(canvas, bx + bw * 0.18f, by + bh * 0.32f, bw * 0.64f, bh * 0.08f, hpx * 0.16f, RGBA(230, 232, 238))
                drawSolidBox(canvas, bx + bw * 0.46f, by + bh * 0.18f, bw * 0.08f, bh * 0.28f, hpx * 0.55f, RGBA(70, 70, 74))
            }
            bl.service == "harbor" -> {
                drawSolidBox(canvas, bx, by, bw, bh, hpx * 0.10f, RGBA(70, 110, 140))
                drawSolidBox(canvas, bx + bw * 0.12f, by + bh * 0.18f, bw * 0.50f, bh * 0.50f, hpx * 0.6f, RGBA(150, 120, 80))
                drawSolidBox(canvas, bx + bw * 0.08f, by + bh * 0.72f, bw * 0.84f, bh * 0.16f, hpx * 0.16f, RGBA(90, 90, 86))
            }
            bl.service == "cemetery" -> {
                drawSolidBox(canvas, bx + bw * 0.04f, by + bh * 0.58f, bw * 0.92f, bh * 0.32f, hpx * 0.10f, RGBA(110, 130, 108))
                drawSolidBox(canvas, bx + bw * 0.18f, by + bh * 0.28f, bw * 0.12f, bh * 0.22f, hpx * 0.32f, RGBA(176, 176, 170))
                drawSolidBox(canvas, bx + bw * 0.44f, by + bh * 0.22f, bw * 0.12f, bh * 0.28f, hpx * 0.38f, RGBA(176, 176, 170))
                drawSolidBox(canvas, bx + bw * 0.70f, by + bh * 0.32f, bw * 0.12f, bh * 0.20f, hpx * 0.28f, RGBA(176, 176, 170))
            }
            bl.service == "crematorium" -> {
                drawSolidBox(canvas, bx + bw * 0.12f, by + bh * 0.22f, bw * 0.76f, bh * 0.58f, hpx * 0.65f, RGBA(150, 148, 142))
                drawSolidBox(canvas, bx + bw * 0.70f, by + bh * 0.12f, bw * 0.16f, bh * 0.20f, hpx * 1.05f, RGBA(90, 88, 84))
            }
            bl.service == "prison" -> {
                drawSolidBox(canvas, bx + bw * 0.08f, by + bh * 0.16f, bw * 0.84f, bh * 0.70f, hpx * 0.8f, RGBA(120, 124, 128))
                drawSolidBox(canvas, bx + bw * 0.18f, by + bh * 0.30f, bw * 0.18f, bh * 0.14f, hpx * 0.28f, RGBA(40, 44, 50))
                drawSolidBox(canvas, bx + bw * 0.64f, by + bh * 0.30f, bw * 0.18f, bh * 0.14f, hpx * 0.28f, RGBA(40, 44, 50))
            }
            bl.service == "tv_tower" -> {
                drawSolidBox(canvas, bx + bw * 0.40f, by + bh * 0.55f, bw * 0.20f, bh * 0.30f, hpx * 0.4f, RGBA(170, 170, 176))
                drawSolidBox(canvas, bx + bw * 0.44f, by + bh * 0.18f, bw * 0.12f, bh * 0.18f, hpx * 1.8f, RGBA(210, 210, 216))
                fillCircle(canvas, bx + bw * 0.50f, by - hpx * 1.35f, cell * 0.08f, RGBA(220, 80, 70))
            }
            bl.service == "stadium" -> {
                drawSolidBox(canvas, bx + bw * 0.04f, by + bh * 0.22f, bw * 0.92f, bh * 0.18f, hpx * 0.55f, RGBA(90, 140, 90))
                drawSolidBox(canvas, bx + bw * 0.04f, by + bh * 0.62f, bw * 0.92f, bh * 0.18f, hpx * 0.55f, RGBA(80, 128, 80))
                drawSolidBox(canvas, bx + bw * 0.04f, by + bh * 0.32f, bw * 0.16f, bh * 0.38f, hpx * 0.45f, RGBA(86, 132, 86))
                drawSolidBox(canvas, bx + bw * 0.80f, by + bh * 0.32f, bw * 0.16f, bh * 0.38f, hpx * 0.45f, RGBA(86, 132, 86))
                fillOval(canvas, bx + bw * 0.22f, by + bh * 0.36f, bw * 0.56f, bh * 0.28f, RGBA(210, 210, 200))
            }
            bl.service == "stock_exchange" -> {
                drawSolidBox(canvas, bx + bw * 0.08f, by + bh * 0.16f, bw * 0.84f, bh * 0.70f, hpx * 1.1f, RGBA(176, 168, 150))
                drawSolidBox(canvas, bx + bw * 0.18f, by + bh * 0.28f, bw * 0.18f, bh * 0.18f, hpx * 0.32f, RGBA(80, 120, 170))
                drawSolidBox(canvas, bx + bw * 0.64f, by + bh * 0.28f, bw * 0.18f, bh * 0.18f, hpx * 0.32f, RGBA(80, 120, 170))
            }
            bl.zone == "residential" && bl.level == 1 -> {
                val wall = when (variant) {
                    0 -> RGBA(236, 214, 150)
                    1 -> RGBA(224, 196, 168)
                    else -> RGBA(210, 186, 150)
                }
                val roof = when (variant) {
                    0 -> RGBA(150, 78, 62)
                    1 -> RGBA(92, 118, 86)
                    else -> RGBA(168, 92, 70)
                }
                if (variant == 0) {
                    drawSolidBox(canvas, bx + bw * 0.06f, by + bh * 0.22f, bw * 0.52f, bh * 0.62f, hpx, wall)
                    drawPitchedRoof(canvas, bx + bw * 0.06f, by + bh * 0.22f, bw * 0.52f, bh * 0.62f, hpx, roof)
                    drawSolidBox(canvas, bx + bw * 0.62f, by + bh * 0.46f, bw * 0.28f, bh * 0.38f, hpx * 0.55f, wall.shade(0.9))
                } else if (variant == 1) {
                    drawSolidBox(canvas, bx + bw * 0.18f, by + bh * 0.14f, bw * 0.64f, bh * 0.70f, hpx * 1.12f, wall)
                    drawPitchedRoof(canvas, bx + bw * 0.18f, by + bh * 0.14f, bw * 0.64f, bh * 0.70f, hpx * 1.12f, roof)
                    drawSolidBox(canvas, bx + bw * 0.72f, by + bh * 0.52f, bw * 0.16f, bh * 0.28f, hpx * 0.4f, RGBA(120, 88, 64))
                } else {
                    drawSolidBox(canvas, bx + bw * 0.04f, by + bh * 0.32f, bw * 0.40f, bh * 0.50f, hpx * 0.78f, wall)
                    drawSolidBox(canvas, bx + bw * 0.48f, by + bh * 0.16f, bw * 0.44f, bh * 0.66f, hpx, wall.shade(0.92))
                    drawPitchedRoof(canvas, bx + bw * 0.48f, by + bh * 0.16f, bw * 0.44f, bh * 0.66f, hpx, roof)
                }
            }
            bl.zone == "residential" && bl.level == 2 -> {
                val wall = if (variant == 0) RGBA(228, 204, 164) else RGBA(214, 186, 150)
                if (variant == 0) {
                    drawSolidBox(canvas, bx + bw * 0.04f, by + bh * 0.16f, bw * 0.42f, bh * 0.70f, hpx * 0.82f, wall)
                    drawSolidBox(canvas, bx + bw * 0.50f, by + bh * 0.10f, bw * 0.44f, bh * 0.76f, hpx, wall.shade(0.9))
                } else if (variant == 1) {
                    drawSolidBox(canvas, bx + bw * 0.10f, by + bh * 0.12f, bw * 0.80f, bh * 0.74f, hpx, wall)
                    drawPitchedRoof(canvas, bx + bw * 0.10f, by + bh * 0.12f, bw * 0.80f, bh * 0.74f, hpx, RGBA(140, 80, 70))
                } else {
                    drawSolidBox(canvas, bx + bw * 0.08f, by + bh * 0.20f, bw * 0.36f, bh * 0.64f, hpx * 0.7f, wall)
                    drawSolidBox(canvas, bx + bw * 0.46f, by + bh * 0.08f, bw * 0.44f, bh * 0.76f, hpx * 1.15f, wall.shade(0.88))
                }
            }
            bl.zone == "residential" -> {
                val wall = if (variant == 0) RGBA(210, 186, 150) else RGBA(196, 176, 158)
                drawSolidBox(canvas, bx + bw * 0.10f, by + bh * 0.08f, bw * 0.80f, bh * 0.82f, hpx, wall)
                drawSolidBox(canvas, bx + bw * 0.18f, by + bh * 0.16f, bw * 0.22f, bh * 0.18f, hpx * 0.18f, RGBA(70, 92, 112))
            }
            bl.zone == "commercial" && bl.level == 1 -> {
                val wall = when (variant) {
                    0 -> RGBA(226, 176, 150)
                    1 -> RGBA(210, 150, 142)
                    else -> RGBA(196, 168, 140)
                }
                val awning = when (variant) {
                    0 -> RGBA(190, 70, 70)
                    1 -> RGBA(70, 110, 170)
                    else -> RGBA(210, 150, 60)
                }
                drawSolidBox(canvas, bx + bw * 0.08f, by + bh * 0.16f, bw * 0.84f, bh * 0.70f, hpx, wall)
                drawSolidBox(canvas, bx + bw * 0.16f, by + bh * 0.56f, bw * 0.68f, bh * 0.22f, hpx * 0.12f, RGBA(40, 50, 70))
                drawSolidBox(canvas, bx + bw * 0.10f, by + bh * 0.48f, bw * 0.80f, bh * 0.10f, hpx * 0.08f, awning)
            }
            bl.zone == "commercial" -> {
                val wall = if (variant == 0) RGBA(214, 160, 138) else RGBA(186, 150, 168)
                drawSolidBox(canvas, bx + bw * 0.08f, by + bh * 0.10f, bw * 0.84f, bh * 0.78f, hpx, wall)
                drawSolidBox(canvas, bx + bw * 0.16f, by + bh * 0.52f, bw * 0.68f, bh * 0.26f, hpx * 0.16f, RGBA(40, 50, 70))
            }
            bl.zone == "industrial" -> {
                val shed = if (variant == 0) RGBA(176, 168, 148) else RGBA(158, 154, 140)
                if (variant == 0) {
                    drawSolidBox(canvas, bx + bw * 0.04f, by + bh * 0.24f, bw * 0.60f, bh * 0.60f, hpx * 0.78f, shed)
                    drawSolidBox(canvas, bx + bw * 0.68f, by + bh * 0.34f, bw * 0.26f, bh * 0.48f, hpx * 1.25f, RGBA(110, 108, 100))
                } else if (variant == 1) {
                    drawSolidBox(canvas, bx + bw * 0.08f, by + bh * 0.18f, bw * 0.84f, bh * 0.64f, hpx * 0.72f, shed)
                    drawSolidBox(canvas, bx + bw * 0.16f, by + bh * 0.06f, bw * 0.16f, bh * 0.20f, hpx * 1.35f, RGBA(120, 118, 110))
                    drawSolidBox(canvas, bx + bw * 0.64f, by + bh * 0.06f, bw * 0.16f, bh * 0.20f, hpx * 1.15f, RGBA(110, 108, 100))
                } else {
                    drawSolidBox(canvas, bx + bw * 0.06f, by + bh * 0.28f, bw * 0.46f, bh * 0.56f, hpx * 0.7f, shed)
                    drawSolidBox(canvas, bx + bw * 0.54f, by + bh * 0.16f, bw * 0.38f, bh * 0.68f, hpx, RGBA(128, 122, 108))
                }
            }
            bl.zone == "office" -> {
                val wall = if (variant == 0) RGBA(168, 196, 224) else RGBA(150, 176, 210)
                drawSolidBox(canvas, bx + bw * 0.14f, by + bh * 0.08f, bw * 0.72f, bh * 0.82f, hpx, wall)
                drawSolidBox(canvas, bx + bw * 0.22f, by + bh * 0.16f, bw * 0.56f, bh * 0.18f, hpx * 0.22f, RGBA(180, 210, 230))
            }
            else -> drawSolidBox(canvas, bx, by, bw, bh, hpx, base)
        }
        val label = if (bl.isService) {
            World.serviceConfig(bl.service)?.name ?: ""
        } else {
            buildingName(bl.zone ?: "residential", tx, ty)
        }
        if (label.isNotEmpty() && cell >= 11) {
            drawNameOnFrontWall(canvas, bx, by, bw, bh, hpx, label)
        }
        if (World.tile(tx, ty)?.onFire == true) {
            drawFireParticles(canvas, bx + bw * 0.5f, by + bh * 0.18f - hpx)
        }
    }

    private fun drawFireParticles(canvas: Canvas, cx: Float, cy: Float) {
        val t = rainPhase
        for (i in 0 until 7) {
            val seed = i * 1.7f
            val rise = ((t * (1.6f + i * 0.18f) + seed) % 1.2f)
            val px = cx + sin(t * 3.1f + seed) * cell * 0.10f
            val py = cy - rise * cell * 0.55f
            val s = cell * (0.16f - rise * 0.08f)
            val a = (220 * (1f - rise / 1.2f)).toInt().coerceIn(40, 230)
            fillCircle(canvas, px, py, s, RGBA(255, 90 + i * 8, 30, a))
        }
        for (i in 0 until 4) {
            val seed = i * 2.3f
            val rise = ((t * 0.9f + seed) % 1.4f)
            fillCircle(
                canvas,
                cx + sin(t * 1.4f + seed) * cell * 0.14f,
                cy - rise * cell * 0.7f,
                cell * 0.07f,
                RGBA(70, 70, 74, (120 * (1f - rise / 1.4f)).toInt().coerceIn(20, 120))
            )
        }
    }

    /** 全名贴在体块前墙中部，字宽不超过这栋建筑 */
    private fun drawNameOnFrontWall(
        canvas: Canvas, bx: Float, by: Float, bw: Float, bh: Float, hpx: Float, name: String
    ) {
        if (typeface == null) return
        val H = max(hpx, 1.5f)
        val foot = by + bh
        val wallTop = foot - H
        val cx = bx + bw * 0.5f
        val cy = wallTop + H * 0.42f
        val maxW = bw * 0.86f
        var fs = min(cell * 0.30f, maxW / max(1, name.length))
        fs = max(6f, fs)
        var tw = measure(name, fs)
        while (tw > maxW && fs > 6f) {
            fs -= 0.35f
            tw = measure(name, fs)
        }
        if (tw > maxW) {
            fs = max(5.5f, maxW / max(1, name.length))
            tw = measure(name, fs)
        }
        val th = fs * 1.05f
        val padX = max(1.6f, fs * 0.18f)
        val bgW = min(maxW, tw + padX * 2f)
        fillRoundRect(
            canvas, cx - bgW / 2f, cy - th / 2f - 1f, bgW, th + 2f, 1.8f,
            RGBA(28, 32, 36, 165)
        )
        drawText(canvas, cx, cy, fs, RGBA(255, 252, 246), name, TAlign.CENTER, 245)
    }

    private fun overlayHue(cat: String): RGBA = when (cat) {
        Config.ServiceCat.POWER -> RGBA(70, 160, 255)
        Config.ServiceCat.WATER -> RGBA(40, 140, 200)
        Config.ServiceCat.GARBAGE -> RGBA(110, 160, 70)
        Config.ServiceCat.HEALTH -> RGBA(220, 80, 90)
        Config.ServiceCat.EDUCATION -> RGBA(210, 160, 50)
        Config.ServiceCat.SAFETY -> RGBA(80, 90, 210)
        Config.ServiceCat.TRANSIT -> RGBA(90, 120, 190)
        Config.ServiceCat.DEATH -> RGBA(120, 120, 130)
        Config.ServiceCat.AMENITY -> RGBA(70, 170, 90)
        Config.ServiceCat.LANDMARK -> RGBA(180, 110, 200)
        else -> RGBA(70, 190, 110)
    }

    private fun drawTreeBlocks(canvas: Canvas, sx: Float, sy: Float, tx: Int, ty: Int) {
        val jx = (hash01(tx, ty, 5) - 0.5f) * cell * 0.16f
        val jy = (hash01(tx, ty, 6) - 0.5f) * cell * 0.16f
        val cx = sx + cell * 0.5f + jx
        val cy = sy + cell * 0.58f + jy
        val bark = RGBA(118, 88, 58)
        val leafA = RGBA(54, 118, 68)
        val leafB = RGBA(70, 140, 78)
        val leafC = RGBA(46, 102, 58)
        boxFlip = hash01(tx, ty, 11) > 0.5f
        fillRoundRect(canvas, cx - cell * 0.18f, cy + cell * 0.04f, cell * 0.36f, cell * 0.16f, 3f, RGBA(28, 34, 30, 45))
        drawBox(canvas, cx - cell * 0.06f, cy - cell * 0.02f, cell * 0.12f, cell * 0.22f, cell * 0.28f, bark)
        drawBox(canvas, cx - cell * 0.22f, cy - cell * 0.22f, cell * 0.34f, cell * 0.28f, cell * 0.16f, leafC)
        drawBox(canvas, cx - cell * 0.04f, cy - cell * 0.30f, cell * 0.30f, cell * 0.26f, cell * 0.18f, leafA)
        drawBox(canvas, cx - cell * 0.16f, cy - cell * 0.14f, cell * 0.28f, cell * 0.24f, cell * 0.14f, leafB)
        drawBox(canvas, cx + cell * 0.02f, cy - cell * 0.10f, cell * 0.24f, cell * 0.22f, cell * 0.12f, leafA)
    }

    private fun drawHillBlocks(canvas: Canvas, sx: Float, sy: Float, tx: Int, ty: Int) {
        val h = cell * (0.18f + (World.elevation(tx, ty) % 80) / 220f)
        val rock = RGBA(132, 128, 118)
        fillRoundRect(canvas, sx + cell * 0.10f, sy + cell * 0.42f, cell * 0.80f, cell * 0.38f, 4f, RGBA(28, 34, 30, 35))
        drawBox(canvas, sx + cell * 0.12f, sy + cell * 0.32f, cell * 0.76f, cell * 0.50f, h * 0.45f, RGBA(138, 148, 118))
        drawBox(canvas, sx + cell * 0.22f, sy + cell * 0.18f, cell * 0.52f, cell * 0.34f, h, RGBA(158, 168, 132))
        if (hash01(tx, ty, 4) > 0.45f) {
            drawBox(canvas, sx + cell * 0.58f, sy + cell * 0.46f, cell * 0.16f, cell * 0.14f, cell * 0.08f, rock)
        }
        if (hash01(tx, ty, 7) > 0.62f) {
            drawBox(canvas, sx + cell * 0.18f, sy + cell * 0.50f, cell * 0.12f, cell * 0.10f, cell * 0.06f, rock.shade(0.9))
        }
    }

    private fun drawWaterRipple(canvas: Canvas, sx: Float, sy: Float, tx: Int, ty: Int) {
        val col = if ((tx + ty) % 2 == 0) Config.COLORS.water else Config.COLORS.waterAlt
        val off = (sin((tx + rainPhase * 0.35f) * 1.7f) + cos((ty - rainPhase * 0.22f) * 1.4f)) * cell * 0.04f
        strokeColor(col.shade(1.18), 90, 0.9f)
        canvas.drawLine(sx + cell * 0.12f, sy + cell * 0.38f + off, sx + cell * 0.88f, sy + cell * 0.38f + off, paint)
        strokeColor(col.shade(0.85), 50, 0.7f)
        canvas.drawLine(sx + cell * 0.18f, sy + cell * 0.58f - off, sx + cell * 0.82f, sy + cell * 0.58f - off, paint)
    }

    private fun drawVehicleBox(
        canvas: Canvas,
        sx: Float,
        sy: Float,
        dir: Int,
        color: RGBA,
        longBody: Boolean,
        selected: Boolean
    ) {
        val bounce = (sin(rainPhase * 9.5f) * cell * 0.012f)
        val horiz = dir == 0 || dir == 2
        val L = cell * if (longBody) 0.58f else 0.36f
        val W = cell * if (longBody) 0.18f else 0.14f
        val lift = cell * 0.10f + bounce
        val rx: Float
        val ry: Float
        val rw: Float
        val rh: Float
        if (horiz) {
            rx = sx - L / 2f; ry = sy - W / 2f; rw = L; rh = W
        } else {
            rx = sx - W / 2f; ry = sy - L / 2f; rw = W; rh = L
        }
        val rad = max(1.4f, cell * 0.06f)
        fillRoundRect(canvas, rx + 1.8f, ry + 2.6f, rw, rh, rad, RGBA(28, 34, 30, 55))
        val rubber = RGBA(36, 36, 40)
        val wheel = max(2.2f, cell * 0.08f)
        if (horiz) {
            fillRoundRect(canvas, rx + rw * 0.12f, ry + rh - wheel * 0.35f, wheel, wheel * 0.72f, 1.2f, rubber)
            fillRoundRect(canvas, rx + rw * 0.70f, ry + rh - wheel * 0.35f, wheel, wheel * 0.72f, 1.2f, rubber)
            fillRoundRect(canvas, rx + rw * 0.12f, ry - wheel * 0.25f, wheel, wheel * 0.72f, 1.2f, rubber)
            fillRoundRect(canvas, rx + rw * 0.70f, ry - wheel * 0.25f, wheel, wheel * 0.72f, 1.2f, rubber)
        } else {
            fillRoundRect(canvas, rx - wheel * 0.25f, ry + rh * 0.12f, wheel * 0.72f, wheel, 1.2f, rubber)
            fillRoundRect(canvas, rx - wheel * 0.25f, ry + rh * 0.70f, wheel * 0.72f, wheel, 1.2f, rubber)
            fillRoundRect(canvas, rx + rw - wheel * 0.35f, ry + rh * 0.12f, wheel * 0.72f, wheel, 1.2f, rubber)
            fillRoundRect(canvas, rx + rw - wheel * 0.35f, ry + rh * 0.70f, wheel * 0.72f, wheel, 1.2f, rubber)
        }
        fillRoundRect(canvas, rx, ry - lift * 0.15f, rw, rh, rad, color.shade(0.62))
        fillRoundRect(canvas, rx, ry - lift, rw, rh * 0.78f, rad, color)
        fillRoundRect(canvas, rx + rw * 0.08f, ry - lift - cell * 0.04f, rw * 0.84f, rh * 0.42f, rad, color.shade(1.08))
        strokeColor(color.shade(0.42), 90, max(0.5f, cell * 0.018f))
        canvas.drawLine(rx + rw * 0.18f, ry - lift + rh * 0.18f, rx + rw * 0.82f, ry - lift + rh * 0.18f, paint)
        if (selected) {
            strokeRoundRect(canvas, rx - 1.5f, ry - lift - 1.5f, rw + 3f, rh + 3f, rad, RGBA(220, 80, 50), 255, 1.6f)
        }
        val glass = if (nightLevel > 0.35f) RGBA(255, 220, 140) else RGBA(70, 92, 112)
        if (horiz) {
            val wx = if (dir == 0) rx + rw * 0.52f else rx + rw * 0.16f
            fillRoundRect(canvas, wx, ry - lift + rh * 0.12f, rw * 0.28f, rh * 0.38f, 1.2f, glass)
            fillRoundRect(canvas, rx + rw * 0.42f, ry - lift - cell * 0.02f, cell * 0.05f, cell * 0.05f, 1f, RGBA(40, 48, 56))
            fillRoundRect(canvas, rx + rw * 0.42f, ry - lift + rh * 0.52f, cell * 0.05f, cell * 0.05f, 1f, RGBA(40, 48, 56))
        } else {
            val wy = if (dir == 1) ry - lift + rh * 0.50f else ry - lift + rh * 0.14f
            fillRoundRect(canvas, rx + rw * 0.18f, wy, rw * 0.64f, rh * 0.22f, 1.2f, glass)
            fillRoundRect(canvas, rx - cell * 0.02f, ry - lift + rh * 0.42f, cell * 0.05f, cell * 0.05f, 1f, RGBA(40, 48, 56))
            fillRoundRect(canvas, rx + rw - cell * 0.03f, ry - lift + rh * 0.42f, cell * 0.05f, cell * 0.05f, 1f, RGBA(40, 48, 56))
        }
        val light = if (dir == 0 || dir == 1) RGBA(255, 230, 160) else RGBA(210, 70, 60)
        if (horiz) {
            val lx = if (dir == 0) rx + rw - cell * 0.08f else rx
            fillRoundRect(canvas, lx, ry - lift + rh * 0.18f, cell * 0.07f, cell * 0.07f, 1f, light)
        } else {
            val ly = if (dir == 1) ry + rh - cell * 0.10f else ry - lift
            fillRoundRect(canvas, rx + rw * 0.38f, ly, cell * 0.07f, cell * 0.07f, 1f, light)
        }
        if (nightLevel > 0.4f) {
            val hx = if (dir == 0) rx + rw else if (dir == 2) rx else sx
            val hy = if (dir == 1) ry + rh else if (dir == 3) ry - lift else sy
            fillCircle(canvas, hx, hy, cell * 0.06f, RGBA(255, 236, 170, 180))
        }
    }

    private fun drawPitchedRoof(
        canvas: Canvas, x: Float, y: Float, w: Float, h: Float, lift: Float, col: RGBA
    ) {
        val ox = min(w * 0.36f, cell * 0.42f)
        val oy = min(h * 0.28f, cell * 0.30f)
        val H = max(lift, 1.5f)
        val peak = min(h * 0.28f, cell * 0.22f)
        val flip = boxFlip
        val blx = if (flip) x + ox else x
        val brx = if (flip) x + w else x + w - ox
        val tlx = if (flip) x else x + ox
        val trx = if (flip) x + w - ox else x + w
        val by = y + h
        val roofY = by - H
        val ridgeY = roofY - peak
        // 后坡（亮）
        path.reset()
        path.moveTo(tlx, roofY)
        path.lineTo((tlx + trx) * 0.5f, ridgeY)
        path.lineTo(trx, roofY)
        path.close()
        fillPath(canvas, path, col.shade(1.08))
        // 前坡（暗）
        path.reset()
        path.moveTo(blx, roofY)
        path.lineTo((blx + brx) * 0.5f, ridgeY + oy * 0.2f)
        path.lineTo(brx, roofY)
        path.close()
        fillPath(canvas, path, col.shade(0.78))
        strokeColor(col.shade(0.55), 160, max(0.6f, cell * 0.02f))
        canvas.drawLine(tlx, roofY, (tlx + trx) * 0.5f, ridgeY, paint)
        canvas.drawLine((tlx + trx) * 0.5f, ridgeY, trx, roofY, paint)
        canvas.drawLine(blx, roofY, (blx + brx) * 0.5f, ridgeY + oy * 0.2f, paint)
        canvas.drawLine((blx + brx) * 0.5f, ridgeY + oy * 0.2f, brx, roofY, paint)
    }

    private fun drawSolidBox(
        canvas: Canvas, x: Float, y: Float, w: Float, h: Float, lift: Float, base: RGBA
    ) {
        drawBox(canvas, x, y, w, h, lift, base)
    }

    /**
     * 俯视格子上的斜二测体块：前墙 + 侧墙 + 顶面。
     * 不改相机，只在本格内向右上（或左上）挤出厚度。
     */
    private fun drawBox(
        canvas: Canvas, rx0: Float, ry0: Float, rw0: Float, rh0: Float,
        hpx: Float, base: RGBA
    ) {
        val cell = this.cell
        if (rw0 <= 1.2f || rh0 <= 1.2f) return
        val pad = max(0.6f, cell * 0.04f)
        val x = rx0 + pad
        val y = ry0 + pad
        val w = max(2f, rw0 - pad * 2)
        val h = max(2f, rh0 - pad * 2)
        val H = max(hpx, 1.5f)
        val ox = min(w * 0.36f, cell * 0.42f)
        val oy = min(h * 0.28f, cell * 0.30f)
        val flip = boxFlip

        val blx = if (flip) x + ox else x
        val brx = if (flip) x + w else x + w - ox
        val tlx = if (flip) x else x + ox
        val trx = if (flip) x + w - ox else x + w
        val by = y + h
        val fy = by - oy

        // 落地震影
        path.reset()
        path.moveTo(blx + 1.6f, by + 1.8f)
        path.lineTo(brx + 1.6f, by + 1.8f)
        path.lineTo(trx + 1.6f, fy + 1.8f)
        path.lineTo(tlx + 1.6f, fy + 1.8f)
        path.close()
        fillPath(canvas, path, RGBA(28, 34, 30, 70))

        val front = base.shade(0.78)
        val side = base.shade(0.52)
        val top = base.shade(Config.BUILD.roofLight.toDouble())
        val edge = base.shade(0.38)

        // 侧墙（暗面）
        path.reset()
        if (!flip) {
            path.moveTo(brx, by)
            path.lineTo(trx, fy)
            path.lineTo(trx, fy - H)
            path.lineTo(brx, by - H)
        } else {
            path.moveTo(blx, by)
            path.lineTo(tlx, fy)
            path.lineTo(tlx, fy - H)
            path.lineTo(blx, by - H)
        }
        path.close()
        fillPath(canvas, path, side)

        // 前墙（亮面）
        path.reset()
        path.moveTo(blx, by)
        path.lineTo(brx, by)
        path.lineTo(brx, by - H)
        path.lineTo(blx, by - H)
        path.close()
        fillPath(canvas, path, front)

        // 顶面（最亮）
        path.reset()
        path.moveTo(blx, by - H)
        path.lineTo(brx, by - H)
        path.lineTo(trx, fy - H)
        path.lineTo(tlx, fy - H)
        path.close()
        fillPath(canvas, path, top)

        // 棱线
        val ew = max(0.7f, cell * 0.022f)
        strokeColor(edge, 200, ew)
        canvas.drawLine(blx, by, blx, by - H, paint)
        canvas.drawLine(brx, by, brx, by - H, paint)
        canvas.drawLine(blx, by - H, brx, by - H, paint)
        canvas.drawLine(brx, by - H, trx, fy - H, paint)
        canvas.drawLine(blx, by - H, tlx, fy - H, paint)
        if (!flip) canvas.drawLine(trx, fy, trx, fy - H, paint)
        else canvas.drawLine(tlx, fy, tlx, fy - H, paint)

        // 前墙楼层线 + 窗户
        if (H >= cell * 0.22f && cell >= 8) {
            val floors = max(1, floor(H / (cell * 0.22f)).toInt())
            val fw = brx - blx
            val cols = max(1, floor(fw / (cell * 0.18f)).toInt())
            val lit = nightLevel > 0.3f
            for (fi in 1 until floors) {
                val ly = by - H * (fi / floors.toFloat())
                strokeColor(base.shade(0.58), 150, max(0.45f, cell * 0.012f))
                canvas.drawLine(blx + 1f, ly, brx - 1f, ly, paint)
            }
            if (cell >= 10 && cols > 0) {
                val ww = min(cell * 0.08f, fw / (cols + 1) * 0.55f)
                val wh = min(cell * 0.10f, H / (floors + 1) * 0.55f)
                for (wi in 0 until cols) {
                    for (wj in 0 until floors) {
                        val wx = blx + fw * ((wi + 0.5f) / cols) - ww * 0.5f
                        val wy = by - H * ((wj + 0.62f) / floors) - wh * 0.5f
                        val on = !lit || ((wi * 7 + wj * 13) % 5 != 0)
                        fillRect(
                            canvas, wx, wy, ww, wh,
                            if (lit && on) RGBA(255, 214, 118, 230)
                            else if (lit) RGBA(48, 56, 70, 200)
                            else base.shade(0.42)
                        )
                    }
                }
            }
        }
    }

    /** 成长建筑按坐标确定性取名 */
    private fun buildingName(zone: String, x: Int, y: Int): String {
        val pre: List<String>
        val suf: List<String>
        when (zone) {
            "residential" -> {
                pre = Config.NAMES.RES_PRE
                suf = Config.NAMES.RES_SUF
            }
            "commercial" -> {
                pre = Config.NAMES.COM_PRE
                suf = Config.NAMES.COM_SUF
            }
            "office" -> {
                pre = Config.NAMES.OFF_PRE
                suf = Config.NAMES.OFF_SUF
            }
            else -> {
                pre = Config.NAMES.IND_PRE
                suf = Config.NAMES.IND_SUF
            }
        }
        val i = (x * 7 + y * 13) % pre.size
        val j = (x * 5 + y * 11) % suf.size
        return pre[i] + suf[j]
    }

    /** 天空色：按一天内时间插值（清晨→白昼→黄昏→夜晚） */
    private fun skyColor(tod: Float): RGBA {
        val frames = listOf(
            0.0f to RGBA(20, 26, 50),
            0.08f to RGBA(255, 205, 165),
            0.2f to RGBA(178, 210, 235),
            0.45f to RGBA(178, 210, 235),
            0.55f to RGBA(238, 170, 120),
            0.65f to RGBA(28, 36, 66),
            1.0f to RGBA(20, 26, 50)
        )
        return lerpColor(frames, tod)
    }

    private fun nightFactor(tod: Float): Float = when {
        tod < 0.06f -> 1f                         // 深夜（与 1.0 连续，回绕无跳变）
        tod < 0.16f -> (0.16f - tod) / 0.10f      // 清晨 1→0
        tod < 0.5f -> 0f                          // 白天
        tod < 0.6f -> (tod - 0.5f) / 0.10f        // 黄昏 0→1
        else -> 1f                                // 夜晚
    }

    private fun lerpColor(frames: List<Pair<Float, RGBA>>, t: Float): RGBA {
        if (t <= frames.first().first) return frames.first().second
        for (i in 0 until frames.size - 1) {
            val (t0, c0) = frames[i]
            val (t1, c1) = frames[i + 1]
            if (t <= t1) {
                val k = if (t1 > t0) (t - t0) / (t1 - t0) else 0f
                return RGBA(
                    (c0.r + (c1.r - c0.r) * k).toInt(),
                    (c0.g + (c1.g - c0.g) * k).toInt(),
                    (c0.b + (c1.b - c0.b) * k).toInt(),
                    255
                )
            }
        }
        return frames.last().second
    }
}
