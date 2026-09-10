package com.dshx.game.she.world

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
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
    val kind: String,               // road | zone | bulldoze | service | pipe | cable | bus | district
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
    }

    fun resetCamera() {
        camScale = 1.55f
        val w = World.current
        setCenterTile((w?.spawnX ?: 8).toFloat(), (w?.spawnY ?: 8).toFloat())
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
        val margin = 0.15f
        camX = clamp(camX, -ew * margin, max(-ew * margin, w.cols - ew * (1 - margin)))
        camY = clamp(camY, -eh * margin, max(-eh * margin, w.rows - eh * (1 - margin)))
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
        camX = tx - camExtentW() / 2f
        camY = ty - camExtentH() / 2f
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
            selectedX = plane.ax
            selectedY = plane.ay
            hasSelection = true
            Sfx.play("sfx_engine", 0.45f)
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
                val (ok, msg) = GameData.placeService(t.id ?: return false, tx, ty)
                if (ok) Sfx.play("sfx_build") else if (msg != null) setToast(msg)
                return ok
            }
            "district" -> {
                val (ok, msg) = GameData.paintDistrict(tx, ty)
                if (ok) Sfx.play("sfx_click", 0.25f) else if (msg != null) setToast(msg)
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
                tile != null && tile.road == null && tile.building == null && tile.terrain != "water"
            }
            "bulldoze" -> {
                val tile = World.tile(tx, ty)
                tile != null && (tile.building != null || tile.road != null || tile.metro || tile.rail)
            }
            "service" -> World.canPlaceService(t.id ?: return false, tx, ty).first
            "district" -> World.tile(tx, ty)?.terrain != "water"
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

    private fun fillLines(canvas: Canvas, pts: FloatArray, c: RGBA, alpha: Int = c.a, width: Float) {
        strokeColor(c, alpha, width)
        paint.strokeCap = Paint.Cap.BUTT
        canvas.drawLines(pts, paint)
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
                if (!World.isUnlocked(tx, ty) && t.road != "highway") {
                    fillRect(canvas, sx, sy, cell + 0.5f, cell + 0.5f, RGBA(28, 36, 42, 150))
                }
            }
        }

        // ---- 1.4) 区划底纹 / 地铁隧 / 铁轨 ----
        val showNet = overlay in listOf("metro", "rail", "district") ||
            tool?.kind in listOf("metro", "rail", "district") ||
            (tool?.kind == "road" && (tool?.roadKind == "metro" || tool?.roadKind == "rail"))
        if (showNet) {
            for (ty in y0..y1) {
                for (tx in x0..x1) {
                    val t = w.grid[ty - 1][tx - 1]
                    val sx = worldToScreenX((tx - 1).toFloat())
                    val sy = worldToScreenY((ty - 1).toFloat())
                    if (t.district != 0 && (tool?.kind == "district" || overlay == "district")) {
                        val hue = (t.district * 47) % 180
                        fillRect(canvas, sx, sy, cell + 0.5f, cell + 0.5f, RGBA(80 + hue / 3, 140, 200 - hue / 4, 55))
                    }
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
                        t.terrain == "forest" -> {
                            val lift = cell * 0.18f
                            fillRoundRect(canvas, sx + cell * 0.22f, sy + cell * 0.38f, cell * 0.16f, cell * 0.28f, 2f, RGBA(92, 70, 48))
                            fillCircle(canvas, sx + cell * 0.30f, sy + cell * 0.28f - lift * 0.2f, cell * 0.22f, RGBA(70, 118, 78))
                            fillCircle(canvas, sx + cell * 0.62f, sy + cell * 0.42f, cell * 0.16f, RGBA(86, 132, 88))
                        }
                        t.terrain == "hill" && cell >= 8 -> {
                            val h = cell * (0.22f + (World.elevation(tx, ty) % 80) / 180f)
                            fillRoundRect(canvas, sx + cell * 0.12f, sy + cell * 0.28f, cell * 0.76f, cell * 0.58f, 3f, RGBA(120, 128, 102, 70))
                            fillRoundRect(canvas, sx + cell * 0.16f, sy + cell * 0.22f - h * 0.15f, cell * 0.68f, cell * 0.52f, 3f, RGBA(138, 148, 118))
                            fillRoundRect(canvas, sx + cell * 0.22f, sy + cell * 0.12f - h, cell * 0.56f, cell * 0.36f, 3f, RGBA(158, 168, 132))
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

        // ---- 3) 道路车道线（单车道灰中线 / 大道双黄线）+ 路口红绿灯 ----
        if (cell >= 8) {
            val roadPt = mutableListOf<Float>()
            val avePt = mutableListOf<Float>()
            val hwyPt = mutableListOf<Float>()
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
                    if (kind == "highway") {
                        if (horiz) {
                            hwyPt.add(sx); hwyPt.add(cy)
                            hwyPt.add(sx + cell); hwyPt.add(cy)
                        }
                        if (vert) {
                            hwyPt.add(cx); hwyPt.add(sy)
                            hwyPt.add(cx); hwyPt.add(sy + cell)
                        }
                    } else if (kind == "avenue") {
                        if (horiz) {
                            avePt.add(sx); avePt.add(cy)
                            avePt.add(sx + cell); avePt.add(cy)
                        }
                        if (vert) {
                            avePt.add(cx); avePt.add(sy)
                            avePt.add(cx); avePt.add(sy + cell)
                        }
                    } else {
                        if (horiz) {
                            roadPt.add(sx); roadPt.add(cy)
                            roadPt.add(sx + cell); roadPt.add(cy)
                        }
                        if (vert) {
                            roadPt.add(cx); roadPt.add(sy)
                            roadPt.add(cx); roadPt.add(sy + cell)
                        }
                    }
                    if ((left || right) && (up || down)) {
                        cross.add(Triple(cx, cy, if (kind == "avenue") 1 else 0))
                    }
                }
            }
            if (roadPt.isNotEmpty()) {
                fillLines(canvas, roadPt.toFloatArray(), RGBA(150, 145, 132, 200), 255, max(1f, cell * 0.05f))
            }
            if (avePt.isNotEmpty()) {
                fillLines(canvas, avePt.toFloatArray(), RGBA(200, 165, 60, 220), 255, max(1f, cell * 0.07f))
            }
            if (hwyPt.isNotEmpty()) {
                fillLines(canvas, hwyPt.toFloatArray(), RGBA(240, 240, 245, 230), 255, max(1.4f, cell * 0.08f))
            }
            if (railPt.isNotEmpty()) {
                fillLines(canvas, railPt.toFloatArray(), RGBA(48, 48, 52, 240), 255, max(2.2f, cell * 0.16f))
                fillLines(canvas, railPt.toFloatArray(), RGBA(210, 210, 214, 220), 255, max(0.8f, cell * 0.04f))
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
                val kind = World.tile(c.x, c.y)?.road
                val lane = cell * when (kind) {
                    "highway" -> 0.20f
                    "avenue" -> 0.18f
                    else -> 0.12f
                }
                when (c.dir) {
                    0 -> sy += lane
                    2 -> sy -= lane
                    1 -> sx -= lane
                    3 -> sx += lane
                }
                if (sx > -cell && sy > -cell && sx < viewW + cell && sy < viewH + cell) {
                    val horiz = (c.dir == 0 || c.dir == 2)
                    val freight = c.kind == "freight"
                    val L = cell * if (freight) 0.62f else 0.48f
                    val W = cell * if (freight) 0.34f else 0.26f
                    val lift = cell * 0.10f
                    val rx: Float; val ry: Float; val rw: Float; val rh: Float
                    if (horiz) {
                        rx = sx - L / 2f; ry = sy - W / 2f; rw = L; rh = W
                    } else {
                        rx = sx - W / 2f; ry = sy - L / 2f; rw = W; rh = L
                    }
                    val rad = max(1.4f, cell * 0.06f)
                    val sel = Traffic.selected === c
                    fillRoundRect(canvas, rx + 1.4f, ry + 2.2f, rw, rh, rad, RGBA(40, 48, 40, 70))
                    fillRoundRect(canvas, rx, ry - lift * 0.15f, rw, rh, rad, c.color.shade(0.72))
                    fillRoundRect(canvas, rx, ry - lift, rw, rh * 0.78f, rad, c.color)
                    if (sel) {
                        strokeRoundRect(canvas, rx - 1.5f, ry - lift - 1.5f, rw + 3f, rh + 3f, rad, RGBA(220, 80, 50), 255, 1.6f)
                    }
                    val glass = if (nightLevel > 0.35f) RGBA(255, 220, 140) else RGBA(70, 92, 112)
                    if (horiz) {
                        val wx = if (c.dir == 0) rx + rw * 0.52f else rx + rw * 0.16f
                        fillRect(canvas, wx, ry - lift + rh * 0.16f, rw * 0.28f, rh * 0.42f, glass)
                    } else {
                        val wy = if (c.dir == 1) ry - lift + rh * 0.50f else ry - lift + rh * 0.14f
                        fillRect(canvas, rx + rw * 0.18f, wy, rw * 0.64f, rh * 0.22f, glass)
                    }
                    if (nightLevel > 0.4f) {
                        val hx = if (c.dir == 0) rx + rw else if (c.dir == 2) rx else sx
                        val hy = if (c.dir == 1) ry + rh else if (c.dir == 3) ry - lift else sy
                        fillCircle(canvas, hx, hy, cell * 0.06f, RGBA(255, 236, 170, 180))
                    }
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
                drawVehicleBox(canvas, sx, sy, 0, RGBA(40, 90, 170), longBody = true, selected = false)
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
                drawVehicleBox(canvas, sx, sy, 0, col, longBody = ev.kind == "garbage", selected = false)
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

        // ---- 4) 建筑（顶面+左右侧面+投影，按类型/等级/坐标长成不同体量） ----
        for (ty in y0..y1) {
            for (tx in x0..x1) {
                val t = w.grid[ty - 1][tx - 1]
                val bl = t.building ?: continue
                val isService = bl.isService
                val anchor = (!isService) || (bl.ax == tx && bl.ay == ty)
                if (!anchor) continue
                val sx = worldToScreenX((if (isService) bl.ax else tx) - 1f)
                val sy = worldToScreenY((if (isService) bl.ay else ty) - 1f)
                val bw = cell * bl.w
                val bh = cell * bl.h
                val base: RGBA
                val hFactor: Float
                val seed = tx * 17 + ty * 31 + bl.level * 9
                if (isService) {
                    if (bl.service == "park" || bl.service == "plaza") {
                        base = C.bService; hFactor = 0.08f
                    } else {
                        base = C.bCivic
                        hFactor = when (bl.service) {
                            "water_tower" -> 1.35f
                            "tv_tower" -> 2.4f
                            "stadium" -> 0.42f
                            "university" -> 1.15f
                            "coal_plant", "nuclear_plant" -> 1.05f
                            "wind_farm" -> 1.6f
                            else -> 0.72f
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
                            1 -> 0.55f
                            2 -> 1.05f
                            else -> 1.85f
                        }
                        "commercial" -> when (bl.level) {
                            1 -> 0.48f
                            2 -> 0.85f
                            else -> 1.35f
                        }
                        "industrial" -> when (bl.level) {
                            1 -> 0.42f
                            2 -> 0.68f
                            else -> 0.95f
                        }
                        "office" -> when (bl.level) {
                            1 -> 1.15f
                            2 -> 1.75f
                            else -> 2.45f
                        }
                        else -> 0.8f
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
                val hpx = clamp(cell * 0.55f * hFactor * anim, 0f, cell * 3.4f)
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
        } else if (overlay.isNotEmpty() && overlay !in listOf("district", "metro", "rail")) {
            val green = RGBA(70, 190, 110, 110)
            val red = RGBA(210, 70, 60, 95)
            val blue = RGBA(50, 110, 210, 130)
            for (ty in y0..y1) {
                for (tx in x0..x1) {
                    val t = w.grid[ty - 1][tx - 1]
                    val sx = worldToScreenX(tx - 1f)
                    val sy = worldToScreenY(ty - 1f)
                    val b = t.building
                    if (b != null && b.isService) {
                        val cfg = World.serviceConfig(b.service)
                        if (cfg != null && cfg.category == overlay && b.ax == tx && b.ay == ty) {
                            fillRect(canvas, sx, sy, cell * b.w, cell * b.h, blue)
                        }
                    } else if (b != null && !b.isService) {
                        val ok = World.isCoveredBy(tx, ty, overlay)
                        fillRect(canvas, sx, sy, cell, cell, if (ok) green else red)
                    }
                }
            }
            for (e in World.allBuildings()) {
                if (!e.b.isService) continue
                val cfg = World.serviceConfig(e.b.service) ?: continue
                if (cfg.category != overlay) continue
                val cx = worldToScreenX(e.x - 1 + cfg.sizeW / 2f)
                val cy = worldToScreenY(e.y - 1 + cfg.sizeH / 2f)
                val r = cell * (World.coverRadius(cfg) + 0.5f)
                strokeCircle(canvas, cx, cy, r, RGBA(40, 90, 200, 160), 255, 1.6f)
            }
        }

        // ---- 5) 标签 ----
        val labelAlpha = clamp((cell - 7) / 6f, 0f, 1f)
        if (labelAlpha > 0.05f && typeface != null) {
            // 路名：沿大道
            if (cell >= 14) {
                for (line in w.roadLines) {
                    if (line.kind != "avenue" && line.kind != "highway") continue
                    if (line.dir == "v") {
                        val ly = worldToScreenY((line.labelY - 0.5f))
                        val lx = worldToScreenX(line.segX[0] - 0.5f)
                        if (lx > -80 && lx < viewW + 80 && ly > topInset - 40 && ly < viewH) {
                            val chars = line.name.toList()
                            for (i in chars.indices) {
                                drawText(
                                    canvas, lx, ly + i * (cell * 0.85f), cell * 0.62f,
                                    RGBA(130, 120, 90), chars[i].toString(), TAlign.CENTER, 215
                                )
                            }
                        }
                    } else {
                        val lx = worldToScreenX(line.labelX - 0.5f)
                        val ly = worldToScreenY(line.segY[0] - 0.5f)
                        if (ly > topInset - 20 && ly < viewH + 20 && lx > -160 && lx < viewW + 160) {
                            drawText(
                                canvas, lx, ly, cell * 0.62f,
                                RGBA(130, 120, 90), line.name, TAlign.CENTER, 215
                            )
                        }
                    }
                }
            }
            // 服务设施名/符号（电水垃圾交通用符号，生活医疗教育消防用名字）
            if (cell >= 14) {
                for (ty in y0..y1) {
                    for (tx in x0..x1) {
                        val t = w.grid[ty - 1][tx - 1]
                        val bl = t.building ?: continue
                        if (!bl.isService || bl.ax != tx || bl.ay != ty) continue
                        val sc = World.serviceConfig(bl.service) ?: continue
                        val sx = worldToScreenX(bl.ax - 1 + bl.w / 2f)
                        val sy = worldToScreenY(bl.ay - 1 + bl.h / 2f)
                        val sym = serviceSymbol(bl.service ?: "")
                        if (sym.isNotEmpty()) {
                            val fs = min(cell * 0.6f, cell * bl.w * 0.9f)
                            drawText(canvas, sx, sy, fs, RGBA(255, 255, 255), sym, TAlign.CENTER, 245)
                        } else {
                            val chars = sc.name.length
                            val fs = min(cell * 0.5f, cell * bl.w * 0.92f / chars)
                            drawText(canvas, sx, sy, fs, RGBA(60, 76, 58), sc.name, TAlign.CENTER, 235)
                        }
                    }
                }
            }
            // 成长建筑名只在选中或高缩放时画在屋顶上方，避免盖住立面
            if (cell >= 22) {
                for (ty in y0..y1) {
                    for (tx in x0..x1) {
                        val t = w.grid[ty - 1][tx - 1]
                        val bl = t.building ?: continue
                        if (bl.isService) continue
                        val selected = selectedX == tx && selectedY == ty
                        if (!selected && cell < 28) continue
                        val sx = worldToScreenX(tx - 1f)
                        val sy = worldToScreenY(ty - 1f)
                        val bw = cell * bl.w
                        val name = buildingName(bl.zone ?: "residential", tx, ty)
                        val fs = min(cell * 0.22f, bw * 0.7f / name.length)
                        drawText(
                            canvas, sx + bw / 2f, sy - cell * 0.55f,
                            fs, RGBA(70, 64, 52), name, TAlign.CENTER, if (selected) 240 else 170
                        )
                    }
                }
            }
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
        val pad = max(1.4f, cell * 0.08f)
        val bx = rx0 + pad
        val by = ry0 + pad
        val bw = rw0 - pad * 2
        val bh = rh0 - pad * 2
        val seed = tx * 17 + ty * 31 + bl.level * 9
        val variant = seed % 3
        when {
            bl.service == "park" -> {
                fillRect(canvas, bx, by, bw, bh, RGBA(92, 148, 96))
                fillRect(canvas, bx + bw * 0.08f, by + bh * 0.62f, bw * 0.84f, bh * 0.18f, RGBA(210, 198, 140))
                fillCircle(canvas, bx + bw * 0.28f, by + bh * 0.32f, cell * 0.16f, RGBA(46, 108, 58))
                fillCircle(canvas, bx + bw * 0.62f, by + bh * 0.22f, cell * 0.12f, RGBA(62, 128, 70))
                fillRoundRect(canvas, bx + bw * 0.22f, by + bh * 0.48f, cell * 0.08f, cell * 0.18f, 1.5f, RGBA(92, 70, 48))
                fillRoundRect(canvas, bx + bw * 0.56f, by + bh * 0.38f, cell * 0.07f, cell * 0.16f, 1.5f, RGBA(92, 70, 48))
            }
            bl.service == "plaza" -> {
                fillRect(canvas, bx, by, bw, bh, RGBA(188, 186, 176))
                fillRect(canvas, bx + bw * 0.12f, by + bh * 0.12f, bw * 0.76f, bh * 0.76f, RGBA(214, 210, 196))
                drawSolidBox(canvas, bx + bw * 0.38f, by + bh * 0.38f, bw * 0.24f, bh * 0.24f, hpx * 0.55f, RGBA(170, 90, 80))
                fillCircle(canvas, bx + bw * 0.18f, by + bh * 0.22f, cell * 0.08f, RGBA(70, 130, 80))
                fillCircle(canvas, bx + bw * 0.82f, by + bh * 0.78f, cell * 0.08f, RGBA(70, 130, 80))
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
                fillRect(canvas, bx, by, bw, bh, RGBA(70, 92, 78))
                fillRect(canvas, bx + bw * 0.08f, by + bh * 0.12f, bw * 0.38f, bh * 0.34f, RGBA(40, 70, 130))
                fillRect(canvas, bx + bw * 0.52f, by + bh * 0.12f, bw * 0.38f, bh * 0.34f, RGBA(40, 70, 130))
                fillRect(canvas, bx + bw * 0.08f, by + bh * 0.52f, bw * 0.38f, bh * 0.34f, RGBA(50, 86, 150))
                fillRect(canvas, bx + bw * 0.52f, by + bh * 0.52f, bw * 0.38f, bh * 0.34f, RGBA(50, 86, 150))
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
                fillRect(canvas, bx + bw * 0.38f, by + bh * 0.32f - hpx * 0.2f, bw * 0.24f, bh * 0.12f, RGBA(210, 70, 70))
                fillRect(canvas, bx + bw * 0.46f, by + bh * 0.22f - hpx * 0.2f, bw * 0.08f, bh * 0.32f, RGBA(210, 70, 70))
                fillRect(canvas, bx + bw * 0.16f, by + bh * 0.62f, bw * 0.22f, bh * 0.12f, RGBA(90, 140, 190, 180))
            }
            bl.service == "hospital" -> {
                drawSolidBox(canvas, bx + bw * 0.06f, by + bh * 0.14f, bw * 0.88f, bh * 0.72f, hpx * 1.05f, RGBA(236, 240, 244))
                fillRect(canvas, bx + bw * 0.40f, by + bh * 0.22f - hpx * 0.35f, bw * 0.20f, bh * 0.10f, RGBA(210, 70, 70))
                fillRect(canvas, bx + bw * 0.46f, by + bh * 0.12f - hpx * 0.35f, bw * 0.08f, bh * 0.30f, RGBA(210, 70, 70))
                fillRect(canvas, bx + bw * 0.14f, by + bh * 0.58f, bw * 0.72f, bh * 0.18f, RGBA(70, 120, 180, 160))
            }
            bl.service == "school" -> {
                drawSolidBox(canvas, bx + bw * 0.08f, by + bh * 0.22f, bw * 0.84f, bh * 0.62f, hpx * 0.7f, RGBA(232, 214, 170))
                drawPitchedRoof(canvas, bx + bw * 0.08f, by + bh * 0.22f, bw * 0.84f, bh * 0.62f, hpx * 0.7f, RGBA(150, 70, 62))
                fillRect(canvas, bx + bw * 0.18f, by + bh * 0.55f, bw * 0.18f, bh * 0.18f, RGBA(80, 130, 180, 180))
                fillRect(canvas, bx + bw * 0.64f, by + bh * 0.55f, bw * 0.18f, bh * 0.18f, RGBA(80, 130, 180, 180))
                fillRect(canvas, bx + bw * 0.42f, by + bh * 0.62f, bw * 0.16f, bh * 0.16f, RGBA(90, 70, 50))
            }
            bl.service == "middle_school" -> {
                drawSolidBox(canvas, bx + bw * 0.06f, by + bh * 0.16f, bw * 0.88f, bh * 0.70f, hpx * 0.9f, RGBA(220, 204, 168))
                fillRect(canvas, bx + bw * 0.14f, by + bh * 0.30f, bw * 0.18f, bh * 0.16f, RGBA(70, 120, 170, 180))
                fillRect(canvas, bx + bw * 0.68f, by + bh * 0.30f, bw * 0.18f, bh * 0.16f, RGBA(70, 120, 170, 180))
                fillRect(canvas, bx + bw * 0.40f, by + bh * 0.62f, bw * 0.20f, bh * 0.18f, RGBA(90, 70, 50))
            }
            bl.service == "university" -> {
                drawSolidBox(canvas, bx + bw * 0.08f, by + bh * 0.28f, bw * 0.84f, bh * 0.58f, hpx * 0.85f, RGBA(210, 200, 178))
                drawSolidBox(canvas, bx + bw * 0.32f, by + bh * 0.10f, bw * 0.36f, bh * 0.28f, hpx * 1.35f, RGBA(188, 176, 150))
                fillRect(canvas, bx + bw * 0.42f, by + bh * 0.62f, bw * 0.16f, bh * 0.18f, RGBA(80, 60, 40))
            }
            bl.service == "fire_station" -> {
                drawSolidBox(canvas, bx + bw * 0.08f, by + bh * 0.22f, bw * 0.84f, bh * 0.62f, hpx * 0.7f, RGBA(210, 70, 60))
                fillRect(canvas, bx + bw * 0.18f, by + bh * 0.58f, bw * 0.28f, bh * 0.18f, RGBA(40, 44, 48))
                fillRect(canvas, bx + bw * 0.54f, by + bh * 0.58f, bw * 0.28f, bh * 0.18f, RGBA(40, 44, 48))
                fillRect(canvas, bx + bw * 0.38f, by + bh * 0.28f, bw * 0.24f, bh * 0.10f, RGBA(250, 250, 248))
            }
            bl.service == "police" -> {
                drawSolidBox(canvas, bx + bw * 0.10f, by + bh * 0.18f, bw * 0.80f, bh * 0.66f, hpx * 0.75f, RGBA(50, 80, 150))
                fillRect(canvas, bx + bw * 0.38f, by + bh * 0.26f, bw * 0.24f, bh * 0.10f, RGBA(230, 210, 70))
                fillRect(canvas, bx + bw * 0.22f, by + bh * 0.58f, bw * 0.56f, bh * 0.16f, RGBA(30, 40, 70, 180))
            }
            bl.service == "landfill" -> {
                fillRect(canvas, bx, by, bw, bh, RGBA(120, 118, 90))
                fillCircle(canvas, bx + bw * 0.36f, by + bh * 0.46f, cell * 0.18f, RGBA(90, 92, 70))
                fillCircle(canvas, bx + bw * 0.64f, by + bh * 0.58f, cell * 0.14f, RGBA(110, 108, 80))
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
                drawSolidBox(canvas, bx + bw * 0.38f, by + bh * 0.35f, bw * 0.24f, bh * 0.5f, hpx * 0.7f, RGBA(90, 120, 140))
                fillCircle(canvas, bx + bw * 0.5f, by - hpx * 0.15f, min(bw, bh) * 0.28f, RGBA(70, 140, 190))
            }
            bl.service == "sewage" -> {
                drawSolidBox(canvas, bx + bw * 0.08f, by + bh * 0.28f, bw * 0.84f, bh * 0.56f, hpx * 0.5f, RGBA(86, 118, 92))
                fillCircle(canvas, bx + bw * 0.32f, by + bh * 0.46f, cell * 0.12f, RGBA(50, 90, 70))
                fillCircle(canvas, bx + bw * 0.68f, by + bh * 0.50f, cell * 0.10f, RGBA(50, 90, 70))
            }
            bl.service == "airport" -> {
                fillRect(canvas, bx, by, bw, bh, RGBA(168, 176, 184))
                drawSolidBox(canvas, bx + bw * 0.18f, by + bh * 0.28f, bw * 0.64f, bh * 0.44f, hpx * 0.7f, RGBA(210, 214, 220))
                fillRect(canvas, bx + bw * 0.04f, by + bh * 0.46f, bw * 0.92f, bh * 0.12f, RGBA(90, 96, 104))
                fillRect(canvas, bx + bw * 0.46f, by + bh * 0.10f, bw * 0.08f, bh * 0.80f, RGBA(90, 96, 104))
            }
            bl.service == "rail_station" -> {
                drawSolidBox(canvas, bx + bw * 0.1f, by + bh * 0.18f, bw * 0.8f, bh * 0.64f, hpx * 0.85f, RGBA(70, 92, 128))
                fillRect(canvas, bx + bw * 0.18f, by + bh * 0.55f, bw * 0.64f, bh * 0.18f, RGBA(230, 210, 90, 220))
                fillRect(canvas, bx + bw * 0.08f, by + bh * 0.78f, bw * 0.84f, bh * 0.08f, RGBA(48, 48, 52))
            }
            bl.service == "metro" -> {
                drawSolidBox(canvas, bx + bw * 0.12f, by + bh * 0.22f, bw * 0.76f, bh * 0.58f, hpx * 0.55f, RGBA(48, 72, 110))
                fillRect(canvas, bx + bw * 0.22f, by + bh * 0.58f, bw * 0.56f, bh * 0.16f, RGBA(230, 210, 80))
            }
            bl.service == "bus_stop" -> {
                drawSolidBox(canvas, bx + bw * 0.22f, by + bh * 0.42f, bw * 0.56f, bh * 0.32f, hpx * 0.28f, RGBA(40, 90, 170))
                fillRect(canvas, bx + bw * 0.18f, by + bh * 0.32f, bw * 0.64f, bh * 0.08f, RGBA(230, 232, 238))
                fillRect(canvas, bx + bw * 0.46f, by + bh * 0.18f, bw * 0.08f, bh * 0.28f, RGBA(70, 70, 74))
            }
            bl.service == "harbor" -> {
                fillRect(canvas, bx, by, bw, bh, RGBA(70, 110, 140))
                drawSolidBox(canvas, bx + bw * 0.12f, by + bh * 0.18f, bw * 0.50f, bh * 0.50f, hpx * 0.6f, RGBA(150, 120, 80))
                fillRect(canvas, bx + bw * 0.08f, by + bh * 0.72f, bw * 0.84f, bh * 0.16f, RGBA(90, 90, 86))
            }
            bl.service == "cemetery" -> {
                fillRect(canvas, bx, by, bw, bh, RGBA(110, 130, 108))
                fillRect(canvas, bx + bw * 0.18f, by + bh * 0.28f, bw * 0.12f, bh * 0.22f, RGBA(176, 176, 170))
                fillRect(canvas, bx + bw * 0.44f, by + bh * 0.22f, bw * 0.12f, bh * 0.28f, RGBA(176, 176, 170))
                fillRect(canvas, bx + bw * 0.70f, by + bh * 0.32f, bw * 0.12f, bh * 0.20f, RGBA(176, 176, 170))
            }
            bl.service == "crematorium" -> {
                drawSolidBox(canvas, bx + bw * 0.12f, by + bh * 0.22f, bw * 0.76f, bh * 0.58f, hpx * 0.65f, RGBA(150, 148, 142))
                drawSolidBox(canvas, bx + bw * 0.70f, by + bh * 0.12f, bw * 0.16f, bh * 0.20f, hpx * 1.05f, RGBA(90, 88, 84))
            }
            bl.service == "prison" -> {
                drawSolidBox(canvas, bx + bw * 0.08f, by + bh * 0.16f, bw * 0.84f, bh * 0.70f, hpx * 0.8f, RGBA(120, 124, 128))
                fillRect(canvas, bx + bw * 0.18f, by + bh * 0.30f, bw * 0.18f, bh * 0.14f, RGBA(40, 44, 50, 180))
                fillRect(canvas, bx + bw * 0.64f, by + bh * 0.30f, bw * 0.18f, bh * 0.14f, RGBA(40, 44, 50, 180))
            }
            bl.service == "tv_tower" -> {
                drawSolidBox(canvas, bx + bw * 0.40f, by + bh * 0.55f, bw * 0.20f, bh * 0.30f, hpx * 0.4f, RGBA(170, 170, 176))
                drawSolidBox(canvas, bx + bw * 0.44f, by + bh * 0.18f, bw * 0.12f, bh * 0.18f, hpx * 1.8f, RGBA(210, 210, 216))
                fillCircle(canvas, bx + bw * 0.50f, by - hpx * 1.35f, cell * 0.08f, RGBA(220, 80, 70))
            }
            bl.service == "stadium" -> {
                fillOval(canvas, bx + bw * 0.06f, by + bh * 0.18f, bw * 0.88f, bh * 0.64f, RGBA(90, 140, 90))
                fillOval(canvas, bx + bw * 0.18f, by + bh * 0.32f, bw * 0.64f, bh * 0.36f, RGBA(210, 210, 200))
            }
            bl.service == "stock_exchange" -> {
                drawSolidBox(canvas, bx + bw * 0.08f, by + bh * 0.16f, bw * 0.84f, bh * 0.70f, hpx * 1.1f, RGBA(176, 168, 150))
                fillRect(canvas, bx + bw * 0.18f, by + bh * 0.28f, bw * 0.18f, bh * 0.18f, RGBA(80, 120, 170, 160))
                fillRect(canvas, bx + bw * 0.64f, by + bh * 0.28f, bw * 0.18f, bh * 0.18f, RGBA(80, 120, 170, 160))
            }
            bl.zone == "residential" && bl.level == 1 -> {
                val roof = when (variant) {
                    0 -> RGBA(150, 78, 62)
                    1 -> RGBA(120, 88, 64)
                    else -> RGBA(168, 92, 70)
                }
                fillRect(canvas, bx, by, bw, bh, RGBA(150, 170, 130, 180))
                if (variant == 0) {
                    drawSolidBox(canvas, bx + bw * 0.08f, by + bh * 0.18f, bw * 0.58f, bh * 0.64f, hpx, base)
                    drawPitchedRoof(canvas, bx + bw * 0.08f, by + bh * 0.18f, bw * 0.58f, bh * 0.64f, hpx, roof)
                    drawSolidBox(canvas, bx + bw * 0.68f, by + bh * 0.42f, bw * 0.24f, bh * 0.4f, hpx * 0.55f, base.shade(0.9))
                } else if (variant == 1) {
                    drawSolidBox(canvas, bx + bw * 0.18f, by + bh * 0.12f, bw * 0.64f, bh * 0.72f, hpx * 1.1f, base)
                    drawPitchedRoof(canvas, bx + bw * 0.18f, by + bh * 0.12f, bw * 0.64f, bh * 0.72f, hpx * 1.1f, roof)
                } else {
                    drawSolidBox(canvas, bx + bw * 0.06f, by + bh * 0.28f, bw * 0.42f, bh * 0.52f, hpx * 0.8f, base)
                    drawSolidBox(canvas, bx + bw * 0.50f, by + bh * 0.16f, bw * 0.42f, bh * 0.64f, hpx, base.shade(0.92))
                    drawPitchedRoof(canvas, bx + bw * 0.50f, by + bh * 0.16f, bw * 0.42f, bh * 0.64f, hpx, roof)
                }
            }
            bl.zone == "residential" && bl.level == 2 -> {
                if (variant == 0) {
                    drawSolidBox(canvas, bx + bw * 0.04f, by + bh * 0.12f, bw * 0.44f, bh * 0.76f, hpx * 0.85f, base)
                    drawSolidBox(canvas, bx + bw * 0.52f, by + bh * 0.18f, bw * 0.44f, bh * 0.7f, hpx, base.shade(0.92))
                } else {
                    drawSolidBox(canvas, bx + bw * 0.1f, by + bh * 0.14f, bw * 0.8f, bh * 0.72f, hpx, base)
                    drawPitchedRoof(canvas, bx + bw * 0.1f, by + bh * 0.14f, bw * 0.8f, bh * 0.72f, hpx, RGBA(140, 80, 70))
                }
            }
            bl.zone == "residential" -> {
                drawSolidBox(canvas, bx + bw * 0.08f, by + bh * 0.1f, bw * 0.84f, bh * 0.8f, hpx, base)
            }
            bl.zone == "commercial" && bl.level == 1 -> {
                val awning = when (variant) {
                    0 -> RGBA(190, 70, 70)
                    1 -> RGBA(70, 110, 170)
                    else -> RGBA(210, 150, 60)
                }
                drawSolidBox(canvas, bx + bw * 0.1f, by + bh * 0.16f, bw * 0.8f, bh * 0.7f, hpx, base)
                fillRect(canvas, bx + bw * 0.18f, by + bh * 0.58f, bw * 0.64f, bh * 0.22f, RGBA(40, 50, 70, 200))
                fillRect(canvas, bx + bw * 0.12f, by + bh * 0.50f, bw * 0.76f, bh * 0.08f, awning)
            }
            bl.zone == "commercial" -> {
                drawSolidBox(canvas, bx + bw * 0.08f, by + bh * 0.12f, bw * 0.84f, bh * 0.76f, hpx, base)
                fillRect(canvas, bx + bw * 0.16f, by + bh * 0.55f, bw * 0.68f, bh * 0.26f, RGBA(40, 50, 70, 180))
            }
            bl.zone == "industrial" -> {
                if (variant == 0) {
                    drawSolidBox(canvas, bx + bw * 0.06f, by + bh * 0.22f, bw * 0.62f, bh * 0.62f, hpx * 0.75f, base)
                    drawSolidBox(canvas, bx + bw * 0.7f, by + bh * 0.38f, bw * 0.24f, bh * 0.46f, hpx * 1.15f, RGBA(110, 108, 100))
                } else {
                    drawSolidBox(canvas, bx + bw * 0.08f, by + bh * 0.18f, bw * 0.84f, bh * 0.64f, hpx * 0.7f, base)
                    drawSolidBox(canvas, bx + bw * 0.18f, by + bh * 0.08f, bw * 0.18f, bh * 0.22f, hpx * 1.3f, RGBA(120, 118, 110))
                    drawSolidBox(canvas, bx + bw * 0.62f, by + bh * 0.08f, bw * 0.18f, bh * 0.22f, hpx * 1.15f, RGBA(110, 108, 100))
                }
            }
            bl.zone == "office" -> {
                drawSolidBox(canvas, bx + bw * 0.12f, by + bh * 0.1f, bw * 0.76f, bh * 0.8f, hpx, base)
                fillRect(canvas, bx + bw * 0.18f, by - hpx + 3, bw * 0.64f, hpx * 0.55f, RGBA(180, 210, 230, 130))
            }
            else -> drawSolidBox(canvas, bx, by, bw, bh, hpx, base)
        }
        if (World.tile(tx, ty)?.onFire == true) {
            fillCircle(canvas, bx + bw * 0.5f, by - hpx, cell * 0.16f, RGBA(255, 120, 40, 200))
        }
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
        val horiz = dir == 0 || dir == 2
        val L = cell * if (longBody) 0.72f else 0.48f
        val W = cell * if (longBody) 0.32f else 0.26f
        val lift = cell * 0.10f
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
        fillRoundRect(canvas, rx + 1.4f, ry + 2.2f, rw, rh, rad, RGBA(40, 48, 40, 70))
        fillRoundRect(canvas, rx, ry - lift * 0.15f, rw, rh, rad, color.shade(0.72))
        fillRoundRect(canvas, rx, ry - lift, rw, rh * 0.78f, rad, color)
        if (selected) {
            strokeRoundRect(canvas, rx - 1.5f, ry - lift - 1.5f, rw + 3f, rh + 3f, rad, RGBA(220, 80, 50), 255, 1.6f)
        }
        val glass = if (nightLevel > 0.35f) RGBA(255, 220, 140) else RGBA(70, 92, 112)
        if (horiz) {
            val wx = if (dir == 0) rx + rw * 0.52f else rx + rw * 0.16f
            fillRect(canvas, wx, ry - lift + rh * 0.16f, rw * 0.28f, rh * 0.42f, glass)
        } else {
            val wy = if (dir == 1) ry - lift + rh * 0.50f else ry - lift + rh * 0.14f
            fillRect(canvas, rx + rw * 0.18f, wy, rw * 0.64f, rh * 0.22f, glass)
        }
    }

    private fun drawPitchedRoof(
        canvas: Canvas, x: Float, y: Float, w: Float, h: Float, lift: Float, col: RGBA
    ) {
        path.reset()
        path.moveTo(x - 1f, y - lift + 2f)
        path.lineTo(x + w * 0.5f, y - lift - min(h * 0.35f, cell * 0.22f))
        path.lineTo(x + w + 1f, y - lift + 2f)
        path.close()
        fillPath(canvas, path, col)
    }

    private fun drawSolidBox(
        canvas: Canvas, x: Float, y: Float, w: Float, h: Float, lift: Float, base: RGBA
    ) {
        drawBox(canvas, x - 1.2f, y - 1.2f, w + 2.4f, h + 2.4f, lift, base)
    }

    private fun drawBox(
        canvas: Canvas, rx0: Float, ry0: Float, rw0: Float, rh0: Float,
        hpx: Float, base: RGBA
    ) {
        val cell = this.cell
        val pad = max(1.5f, cell * 0.09f)
        val rx = rx0 + pad
        val ry = ry0 + pad
        val rw = rw0 - pad * 2
        val rh = rh0 - pad * 2
        val rad = max(1.2f, cell * 0.05f)
        if (hpx > 1.5 && cell >= 6) {
            fillRoundRect(canvas, rx + 1.6f, ry + 2.2f, rw, rh, rad, RGBA(40, 48, 40, 70))
            fillRoundRect(canvas, rx, ry - hpx * 0.18f, rw, rh, rad, base.shade(0.72))
            fillRoundRect(canvas, rx, ry - hpx, rw, rh * 0.86f, rad, base)
            // 楼层横线
            if (hpx >= cell * 0.28f && cell >= 10) {
                strokeColor(base.shade(0.48), 180, max(0.5f, cell * 0.012f))
                var fy = ry + rh - cell * 0.26f
                while (fy > ry - hpx + cell * 0.04f) {
                    canvas.drawLine(rx + 1, fy, rx + rw - 1, fy, paint)
                    fy -= cell * 0.26f
                }
            }
            // 窗户点阵
            if (cell >= 11 && hpx >= cell * 0.28f) {
                val cols = max(1, floor(rw / (cell * 0.22f)).toInt())
                val rows = max(1, floor((hpx + rh) / (cell * 0.22f)).toInt() - 1)
                val lit = nightLevel > 0.3f
                for (wi in 0 until cols) {
                    for (wj in 0 until rows) {
                        val wx = rx + (rw - cols * cell * 0.22f) * 0.5f + wi * cell * 0.22f + cell * 0.04f
                        val wy = ry + rh - cell * 0.18f - wj * cell * 0.22f
                        if (wy > ry - hpx + cell * 0.04f) {
                            val on = !lit || ((wi * 7 + wj * 13) % 5 != 0)
                            fillColor(
                                if (lit && on) RGBA(255, 214, 118, 230)
                                else if (lit) RGBA(48, 56, 70, 200)
                                else base.shade(0.42)
                            )
                            canvas.drawRect(
                                wx, wy - cell * 0.08f,
                                wx + cell * 0.11f, wy - cell * 0.08f + cell * 0.13f, paint
                            )
                        }
                    }
                }
            }
            fillRoundRect(
                canvas, rx, ry - hpx - 1.2f, rw, max(rh * 0.42f, cell * 0.18f),
                rad, base.shade(Config.BUILD.roofLight.toDouble())
            )
        } else {
            fillRoundRect(canvas, rx, ry, rw, rh, min(2.5f, cell * 0.12f), base)
        }
    }

    /** 基础设施/交通设施的单字符号（模型标识） */
    private fun serviceSymbol(id: String): String = when (id) {
        "wind_farm" -> "风"
        "solar_plant" -> "阳"
        "coal_plant" -> "煤"
        "nuclear_plant" -> "核"
        "water_tower" -> "水"
        "pump_station" -> "泵"
        "landfill" -> "垃"
        "incinerator" -> "焚"
        "bus_stop" -> "公"
        "metro" -> "地"
        "rail_station" -> "铁"
        "harbor" -> "港"
        "airport" -> "机"
        "police" -> "警"
        "university" -> "大"
        "sewage" -> "污"
        "cemetery" -> "墓"
        "crematorium" -> "葬"
        "prison" -> "狱"
        "stock_exchange" -> "证"
        "tv_tower" -> "塔"
        "stadium" -> "体"
        else -> ""
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
