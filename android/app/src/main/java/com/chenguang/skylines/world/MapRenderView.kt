package com.chenguang.skylines.world

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.chenguang.skylines.Config
import com.chenguang.skylines.GameData
import com.chenguang.skylines.RGBA
import com.chenguang.skylines.Sfx
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

// ============================================================================
// MapRenderView — 俯视格子地图（Android Canvas 2D 渲染 + 相机 + 手势）
//   与 scripts/world/MapView.lua 1:1 对应
//   渲染顺序：地形色块 → 分区底色 → 道路 → 建筑(微立体) → 标签 → 选中框
//   相机：screen = (world - cam) * cell
//   手势：拖拽平移 / 双指捏合 / 点击选格 / 工具笔刷拖拽
//   坐标系：逻辑像素(dp)，onDraw 内统一 scale(density)
// ============================================================================

data class Tool(
    val kind: String,               // road | zone | bulldoze | service
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
        camScale = 1.3f
        val w = World.current
        setCenterTile((w?.spawnX ?: 8) + 1.5f, (w?.spawnY ?: 8) + 1f)
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

    fun clearSelection() {
        hasSelection = false
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
        }
        return false
    }

    fun toolValidAt(tx: Int, ty: Int): Boolean {
        val t = tool ?: return false
        if (!World.inBounds(tx, ty)) return false
        return when (t.kind) {
            "road" -> World.canRoad(tx, ty).first
            "zone" -> {
                val tile = World.tile(tx, ty)
                tile != null && tile.road == null && tile.building == null && tile.terrain != "water"
            }
            "bulldoze" -> {
                val tile = World.tile(tx, ty)
                tile != null && (tile.building != null || tile.road != null)
            }
            "service" -> World.canPlaceService(t.id ?: return false, tx, ty).first
            else -> false
        }
    }

    // -----------------------------------------------------------------------
    // 动态车辆
    // -----------------------------------------------------------------------
    private class Car(
        var x: Int, var y: Int, var dir: Int,
        var prog: Float, var speed: Float, var color: RGBA
    )

    private val cars = mutableListOf<Car>()

    private val carColors = listOf(
        RGBA(242, 240, 236), RGBA(198, 92, 78), RGBA(96, 128, 182),
        RGBA(234, 194, 88), RGBA(134, 170, 134), RGBA(96, 98, 104)
    )
    private val dirs = arrayOf(intArrayOf(1, 0), intArrayOf(0, 1), intArrayOf(-1, 0), intArrayOf(0, -1))
    private const val CARS_TARGET = 26

    private fun isRoadCell(x: Int, y: Int) = World.tile(x, y)?.road != null

    private fun spawnCar() {
        val w = World.current ?: return
        repeat(40) {
            val x = Random.nextInt(2, w.cols)
            val y = Random.nextInt(2, w.rows)
            if (isRoadCell(x, y)) {
                val opts = mutableListOf<Int>()
                for (d in dirs.indices) {
                    if (isRoadCell(x + dirs[d][0], y + dirs[d][1])) opts.add(d)
                }
                if (opts.isNotEmpty()) {
                    cars.add(
                        Car(
                            x = x, y = y,
                            dir = opts[Random.nextInt(opts.size)],
                            prog = Random.nextFloat() * 0.6f,
                            speed = 1.4f + Random.nextFloat() * 1.8f,
                            color = carColors[Random.nextInt(carColors.size)]
                        )
                    )
                    return
                }
            }
        }
    }

    /** 0-based 版本：DIRS 环形的对面方向 */
    private fun oppDir(d: Int) = (d + 2) % 4

    private fun updateCars(dt: Float) {
        val w = World.current ?: return
        while (cars.size < CARS_TARGET) spawnCar()
        for (i in cars.indices.reversed()) {
            val c = cars[i]
            if (!isRoadCell(c.x, c.y)) {
                cars.removeAt(i)      // 路被拆了
            } else {
                c.prog += c.speed * dt
                while (c.prog >= 1) {
                    c.prog -= 1
                    val v = dirs[c.dir]
                    c.x += v[0]
                    c.y += v[1]
                    fun ok(d: Int): Boolean {
                        val vv = dirs[d]
                        return isRoadCell(c.x + vv[0], c.y + vv[1])
                    }
                    if (!ok(c.dir)) {
                        val rev = oppDir(c.dir)
                        val opts = mutableListOf<Int>()
                        for (d in 0..3) if (d != rev && ok(d)) opts.add(d)
                        c.dir = if (opts.isNotEmpty()) opts[Random.nextInt(opts.size)] else rev
                    } else if (Random.nextFloat() < 0.18f) {
                        val rev = oppDir(c.dir)
                        val opts = mutableListOf<Int>()
                        for (d in 0..3) if (d != rev && d != c.dir && ok(d)) opts.add(d)
                        if (opts.isNotEmpty()) c.dir = opts[Random.nextInt(opts.size)]
                    }
                }
            }
        }
    }

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
            if (tile != null && World.inBounds(tile.first, tile.second)) {
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

    /** 每帧：toast 计时 + 车辆 */
    fun update(dt: Float) {
        if (toastMsg != null) {
            toastT += dt
            if (toastT > 3.2f) {
                toastMsg = null
                toastT = 0f
            }
        }
        updateCars(dt)
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

        fillRect(canvas, 0f, 0f, viewW, viewH, C.uiBackdrop)

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
                            val n = 2 + ((tx * 7 + ty * 13) % 2)
                            for (k in 0 until n) {
                                val ox = ((tx * 31 + ty * 17 + k * 37) % 100) / 100f
                                val oy = ((tx * 13 + ty * 29 + k * 53) % 100) / 100f
                                val r = cell * (0.10f + ((tx + k * 5 + ty) % 3) * 0.025f)
                                fillCircle(
                                    canvas,
                                    sx + cell * (0.20f + ox * 0.60f),
                                    sy + cell * (0.20f + oy * 0.60f),
                                    r, RGBA(96, 138, 94, 235)
                                )
                            }
                        }
                        t.terrain == "hill" && cell >= 10 -> {
                            val cxp = sx + cell * 0.5f
                            val cyp = sy + cell * 0.64f
                            path.reset()
                            path.moveTo(cxp - cell * 0.28f, cyp)
                            path.lineTo(cxp, cyp - cell * 0.36f)
                            path.lineTo(cxp + cell * 0.28f, cyp)
                            path.close()
                            fillPath(canvas, path, RGBA(142, 152, 124, 255))
                        }
                        t.terrain == "water" && cell >= 10 -> {
                            strokeColor(RGBA(255, 255, 255, 42), 255, max(1f, cell * 0.05f))
                            canvas.drawLine(
                                sx + cell * 0.15f, sy + cell * 0.5f,
                                sx + cell * 0.85f, sy + cell * 0.5f, paint
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

        // ---- 3) 道路中心虚线（大道黄线） ----
        if (cell >= 10) {
            val pts = mutableListOf<Float>()
            for (ty in y0..y1) {
                for (tx in x0..x1) {
                    val t = w.grid[ty - 1][tx - 1]
                    if (t.road != null && t.road == "avenue") {
                        val sx = worldToScreenX((tx - 1).toFloat())
                        val sy = worldToScreenY((ty - 1).toFloat())
                        pts.add(sx); pts.add(sy + cell * 0.5f)
                        pts.add(sx + cell); pts.add(sy + cell * 0.5f)
                    }
                }
            }
            if (pts.isNotEmpty()) {
                fillLines(canvas, pts.toFloatArray(), RGBA(190, 160, 80, 160), 255, max(1f, cell * 0.06f))
            }
        }

        // ---- 3.5) 车辆 ----
        if (cell >= 9) {
            for (c in cars) {
                val v = dirs[c.dir]
                val sx = worldToScreenX(c.x - 1 + v[0] * c.prog + 0.5f)
                val sy = worldToScreenY(c.y - 1 + v[1] * c.prog + 0.5f)
                if (sx > -cell && sy > -cell && sx < viewW + cell && sy < viewH + cell) {
                    val horiz = (c.dir == 0 || c.dir == 2)
                    val L = cell * 0.46f
                    val W = cell * 0.30f
                    val rx: Float; val ry: Float; val rw: Float; val rh: Float
                    if (horiz) {
                        rx = sx - L / 2f; ry = sy - W / 2f; rw = L; rh = W
                    } else {
                        rx = sx - W / 2f; ry = sy - L / 2f; rw = W; rh = L
                    }
                    val rad = max(1.5f, cell * 0.08f)
                    // 阴影
                    fillRoundRect(canvas, rx + 1, ry + 1.5f, rw, rh, rad, RGBA(60, 70, 60, 60))
                    // 车身
                    fillRoundRect(canvas, rx, ry, rw, rh, rad, c.color)
                    // 车窗
                    if (horiz) {
                        val wx = if (c.dir == 0) rx + rw * 0.55f else rx + rw * 0.18f
                        fillRect(canvas, wx, ry + rh * 0.18f, rw * 0.27f, rh * 0.64f, RGBA(70, 84, 96))
                    } else {
                        val wy = if (c.dir == 1) ry + rh * 0.55f else ry + rh * 0.18f
                        fillRect(canvas, rx + rw * 0.18f, wy, rw * 0.64f, rh * 0.27f, RGBA(70, 84, 96))
                    }
                }
            }
        }

        // ---- 4) 建筑 ----
        val grownH = floatArrayOf(0.30f, 0.55f, 0.95f)
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
                if (isService) {
                    if (bl.service == "park" || bl.service == "plaza") {
                        base = C.bService; hFactor = 0.08f
                    } else {
                        base = C.bCivic; hFactor = 0.5f
                    }
                } else {
                    base = when (bl.zone) {
                        "residential" -> C.bResidential
                        "commercial" -> C.bCommercial
                        "industrial" -> C.bIndustrial
                        else -> C.bResidential
                    }
                    hFactor = grownH.getOrElse((bl.level - 1).coerceIn(0, 2)) { 0.3f }
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
                val hpx = clamp(bw * 0.4f * hFactor * anim, 0f, cell * 2.6f)
                val shrink = (anim - 1) * bw * 0.5f
                drawBox(
                    canvas,
                    sx - shrink,
                    sy - shrink * (bh / bw),
                    bw + shrink * 2,
                    bh + shrink * 2 * (bh / bw),
                    hpx, base
                )
            }
        }

        // ---- 5) 标签 ----
        val labelAlpha = clamp((cell - 7) / 6f, 0f, 1f)
        if (labelAlpha > 0.05f && typeface != null) {
            // 路名：沿大道
            if (cell >= 14) {
                for (line in w.roadLines) {
                    if (line.kind != "avenue") continue
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
            // 服务设施名
            if (cell >= 16) {
                for (ty in y0..y1) {
                    for (tx in x0..x1) {
                        val t = w.grid[ty - 1][tx - 1]
                        val bl = t.building ?: continue
                        if (!bl.isService || bl.ax != tx || bl.ay != ty) continue
                        val sc = World.serviceConfig(bl.service) ?: continue
                        val sx = worldToScreenX(bl.ax - 1 + bl.w / 2f)
                        val sy = worldToScreenY(bl.ay - 1 + bl.h / 2f)
                        val chars = sc.name.length
                        val fs = min(cell * 0.5f, cell * bl.w * 0.92f / chars)
                        drawText(canvas, sx, sy, fs, RGBA(60, 76, 58), sc.name, TAlign.CENTER, 235)
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
                    fillCircle(canvas, cx, cy, cell * (sc.radius + 0.5f), RGBA(96, 200, 140, 26))
                    strokeCircle(canvas, cx, cy, cell * (sc.radius + 0.5f), RGBA(96, 200, 140, 90), 255, 1f)
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
        t.road?.let { return if (it == "avenue") C.roadAvenue else C.roadLocal }
        when (t.zone) {
            "residential" -> return C.zoneResidential
            "commercial" -> return C.zoneCommercial
            "industrial" -> return C.zoneIndustrial
        }
        when (t.terrain) {
            "water" -> return if ((x + y) % 2 == 0) C.water else C.waterAlt
            "forest" -> return C.forest
            "hill" -> return C.hill
            "plain" -> return C.plain
        }
        return if ((x + y) % 2 == 0) C.grass else C.grassAlt
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
        if (hpx > 1.5 && cell >= 6) {
            // 侧面
            path.reset()
            path.moveTo(rx, ry + rh)
            path.lineTo(rx, ry - hpx)
            path.lineTo(rx + rw, ry - hpx)
            path.lineTo(rx + rw, ry + rh)
            path.close()
            fillPath(canvas, path, base.shade(Config.BUILD.sideShade.toDouble()))
            // 窗户
            if (cell >= 18 && hpx >= cell * 0.4f) {
                val cols = max(1, floor(rw / (cell * 0.30f)).toInt())
                val rows = max(1, floor((hpx + rh) / (cell * 0.30f)).toInt() - 1)
                fillColor(base.shade(0.52))
                for (wi in 0 until cols) {
                    for (wj in 0 until rows) {
                        val wx = rx + (rw - cols * cell * 0.30f) * 0.5f + wi * cell * 0.30f + cell * 0.06f
                        val wy = ry + rh - cell * 0.22f - wj * cell * 0.30f
                        if (wy > ry - hpx + cell * 0.05f) {
                            canvas.drawRect(wx, wy - cell * 0.08f, wx + cell * 0.11f, wy - cell * 0.08f + cell * 0.13f, paint)
                        }
                    }
                }
            }
            // 顶面
            fillRoundRect(
                canvas, rx, ry - hpx, rw, rh, min(2.5f, cell * 0.1f),
                base.shade(Config.BUILD.roofLight.toDouble())
            )
            strokeRoundRect(
                canvas, rx, ry - hpx, rw, rh, min(2.5f, cell * 0.1f),
                base.shade(0.55), 255, max(0.5f, cell * 0.02f)
            )
        } else {
            fillRoundRect(canvas, rx, ry, rw, rh, min(2.5f, cell * 0.12f), base)
        }
        // 地面投影
        fillRect(canvas, rx + 1, ry + rh - 1, rw - 2, max(1.5f, cell * 0.08f), RGBA(60, 70, 60, 46))
    }
}
