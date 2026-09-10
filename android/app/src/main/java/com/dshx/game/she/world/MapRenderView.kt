package com.dshx.game.she.world

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
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

data class Tool(
    val kind: String,
    val roadKind: String? = null,
    val zoneKey: String? = null,
    val id: String? = null
)

private class IsoBox(
    val x: Float, val y: Float, val z: Float,
    val w: Float, val d: Float, val h: Float,
    val col: RGBA,
    val seam: Boolean = false,
    val round: Float = 0f,
    val glass: Boolean = false
)

private class DustPuff(var x: Float, var y: Float, var life: Float, var z: Float)

class MapRenderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val oval = RectF()
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
    var overlay: String = ""

    private var nightLevel = 0f
    private var rainPhase = 0f
    private val rainDrops = Array(160) { RainDrop() }
    private val dust = ArrayList<DustPuff>(24)

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
    private val halfW: Float get() = cell * 0.72f
    private val halfH: Float get() = cell * 0.36f
    private val elevScale: Float get() = cell * 0.016f

    private var dragActive = false
    private var dragMode: String? = null
    private var dragMoved = false
    private var dragSx = 0f
    private var dragSy = 0f
    private var dragCx = 0f
    private var dragCy = 0f
    private var dragLastTile: String? = null
    private var ptrDown = false
    private var ptrX = 0f
    private var ptrY = 0f
    private var activePointerId = -1
    private var pinchActive = false
    private var pinchDist0 = 0f
    private var pinchScale0 = 1f
    private val density: Float get() = resources.displayMetrics.density
    private val dirs = arrayOf(intArrayOf(1, 0), intArrayOf(0, 1), intArrayOf(-1, 0), intArrayOf(0, -1))
    private var ambientAcc = 0f
    fun initView() {
        typeface = try {
            Typeface.createFromAsset(context.assets, "fonts/ZCOOLKuaiLe-Regular.ttf")
        } catch (_: Throwable) {
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
        camScale = 1.35f
        val w = World.current
        setCenterTile((w?.spawnX ?: 8).toFloat(), (w?.spawnY ?: 8).toFloat())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewW = w / density
        viewH = h / density
        clampCamera()
    }

    private fun originX() = viewW * 0.50f
    private fun originY() = viewH * 0.42f

    private fun isoX(wx: Float, wy: Float): Float =
        ((wx - camX) - (wy - camY)) * halfW + originX()

    private fun isoY(wx: Float, wy: Float, wz: Float): Float =
        ((wx - camX) + (wy - camY)) * halfH + originY() - wz

    private fun groundZ(wx: Float, wy: Float): Float =
        World.surfaceAt(wx, wy) * elevScale

    private fun screenToWorld(sx: Float, sy: Float): Pair<Float, Float> {
        val u = (sx - originX()) / halfW
        val v = (sy - originY()) / halfH
        val wx = (u + v) * 0.5f + camX
        val wy = (v - u) * 0.5f + camY
        return wx to wy
    }

    private fun screenDeltaToWorld(dx: Float, dy: Float): Pair<Float, Float> {
        val u = dx / halfW
        val v = dy / halfH
        return (u + v) * 0.5f to (v - u) * 0.5f
    }

    private fun tileAt(sx: Float, sy: Float): Pair<Int, Int> {
        val (wx, wy) = screenToWorld(sx, sy)
        return floor(wx).toInt() + 1 to floor(wy).toInt() + 1
    }

    private fun clamp(v: Float, lo: Float, hi: Float): Float = max(lo, min(hi, v))

    private fun clampCamera() {
        val w = World.current ?: return
        camX = clamp(camX, -6f, w.cols + 6f)
        camY = clamp(camY, -6f, w.rows + 6f)
    }

    private fun zoomAt(factor: Float, anchorX: Float, anchorY: Float) {
        val (wx, wy) = screenToWorld(anchorX, anchorY)
        val newScale = clamp(camScale * factor, Config.MAP.minScale, Config.MAP.maxScale)
        if (newScale == camScale) return
        camScale = newScale
        val (nx, ny) = screenToWorld(anchorX, anchorY)
        camX += wx - nx
        camY += wy - ny
        clampCamera()
    }

    fun zoomCentered(factor: Float) = zoomAt(factor, viewW / 2f, viewH / 2f)

    private fun setCenterTile(tx: Float, ty: Float) {
        camX = tx - 0.5f
        camY = ty - 0.5f
        clampCamera()
    }

    fun selectTile(tx: Int, ty: Int) {
        val w = World.current ?: return
        selectedX = tx.coerceIn(1, w.cols)
        selectedY = ty.coerceIn(1, w.rows)
        hasSelection = true
        onTileChanged?.invoke()
    }

    fun trySelectVehicle(mx: Float, my: Float): Boolean {
        val (wx, wy) = screenToWorld(mx, my)
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
                        val target = clamp(pinchScale0 * (dist / pinchDist0), Config.MAP.minScale, Config.MAP.maxScale)
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
        val (tx, ty) = tileAt(mx, my)
        val w = World.current ?: return null
        if (tx < 1 || tx > w.cols || ty < 1 || ty > w.rows) return null
        return tx to ty
    }

    private fun onPress() {
        if (ptrY <= topInset || ptrY >= viewH - bottomInset) return
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
                    val (dwx, dwy) = screenDeltaToWorld(dx, dy)
                    camX = dragCx - dwx
                    camY = dragCy - dwy
                    clampCamera()
                }
            }
        }
        if (ptrX > 0 && ptrY > topInset && ptrY < viewH - bottomInset) {
            val (tx, ty) = tileAt(ptrX, ptrY)
            if (World.inBounds(tx, ty)) {
                hoverX = tx; hoverY = ty; hasHover = true
            } else hasHover = false
        } else hasHover = false
    }

    private fun onRelease() {
        val tile = tileUnderCursor(ptrX, ptrY)
        if (dragActive && dragMode == "pan" && !dragMoved) {
            if (!trySelectVehicle(ptrX, ptrY) && tile != null && World.inBounds(tile.first, tile.second)) {
                selectTile(tile.first, tile.second)
            }
        }
        if (dragActive && dragMode == "tool") onTileChanged?.invoke()
        dragActive = false
        dragMode = null
        dragMoved = false
        dragLastTile = null
    }

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
        val it = dust.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.life -= dt * 1.8f
            p.y -= dt * 0.12f
            if (p.life <= 0f) it.remove()
        }
        ambientAcc += dt
        if (ambientAcc > 2.4f && camScale > 1.05f) {
            ambientAcc = 0f
            val (tx, ty) = tileAt(viewW * 0.5f, viewH * 0.5f)
            val t = World.tile(tx, ty)
            when {
                t?.building?.zone == "industrial" -> Sfx.play("sfx_engine", 0.18f)
                t?.road == "highway" && Traffic.visitorsToday > 0 -> Sfx.play("sfx_engine", 0.14f)
                t?.road != null && Traffic.localMoving > 0 -> Sfx.play("sfx_engine", 0.10f)
            }
        }
    }

    private fun fillColor(c: RGBA, alpha: Int = c.a) {
        paint.color = c.argb(alpha)
        paint.style = Paint.Style.FILL
        paint.strokeWidth = 0f
    }

    private fun strokeColor(c: RGBA, alpha: Int = c.a, width: Float) {
        paint.color = c.argb(alpha)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeCap = Paint.Cap.ROUND
    }

    private fun fillRect(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, c: RGBA, alpha: Int = c.a) {
        fillColor(c, alpha)
        canvas.drawRect(x, y, x + w, y + h, paint)
    }

    private fun fillRoundRect(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, r: Float, c: RGBA, alpha: Int = c.a) {
        fillColor(c, alpha)
        canvas.drawRoundRect(x, y, x + w, y + h, r, r, paint)
    }

    private fun strokeRoundRect(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, r: Float, c: RGBA, alpha: Int = c.a, width: Float) {
        strokeColor(c, alpha, width)
        canvas.drawRoundRect(x, y, x + w, y + h, r, r, paint)
    }

    private fun fillCircle(canvas: Canvas, x: Float, y: Float, r: Float, c: RGBA, alpha: Int = c.a) {
        fillColor(c, alpha)
        canvas.drawCircle(x, y, r, paint)
    }

    private fun strokeCircle(canvas: Canvas, x: Float, y: Float, r: Float, c: RGBA, alpha: Int = c.a, width: Float) {
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

    private enum class TAlign { CENTER, LEFT, RIGHT }

    private fun drawText(canvas: Canvas, sx: Float, sy: Float, size: Float, c: RGBA, text: String, align: TAlign, alpha: Int = 255) {
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
        canvas.drawText(text, sx, sy - (fm.ascent + fm.descent) / 2f, paint)
    }

    private fun measure(text: String, size: Float): Float {
        paint.typeface = typeface
        paint.textSize = size
        return paint.measureText(text)
    }

    private fun air(wx: Float, wy: Float): Float {
        val dx = wx - camX
        val dy = wy - camY
        return clamp(sqrt(dx * dx + dy * dy) / 18f, 0f, 1f)
    }

    private fun fog(c: RGBA, k: Float): RGBA {
        val mist = RGBA(186, 198, 206)
        return c.mix(mist, k * 0.42f).shade((1.0 - k * 0.18).coerceIn(0.55, 1.0))
    }

    private fun hash01(x: Int, y: Int, salt: Int = 0): Float {
        var n = x * 374761393 + y * 668265263 + salt * 1274126177
        n = (n xor (n ushr 13)) * 1274126177
        return ((n ushr 16) and 0x7fff) / 32767f
    }

    private fun diamond(cx: Float, cy: Float, hw: Float, hh: Float): Path {
        path.reset()
        path.moveTo(cx, cy - hh)
        path.lineTo(cx + hw, cy)
        path.lineTo(cx, cy + hh)
        path.lineTo(cx - hw, cy)
        path.close()
        return path
    }

    private fun drawIsoBox(canvas: Canvas, b: IsoBox, airK: Float) {
        val col = fog(b.col, airK)
        val top = if (b.glass) col.shade(1.08).withAlpha((col.a * 0.55f).toInt()) else col.shade(1.18)
        val left = col.shade(if (b.glass) 0.78 else 0.86)
        val right = col.shade(if (b.glass) 0.52 else 0.58)
        val hw = halfW * b.w * 0.5f
        val hh = halfH * b.d * 0.5f
        val lift = b.h * cell
        val sx = isoX(b.x + b.w * 0.5f, b.y + b.d * 0.5f)
        val sy = isoY(b.x + b.w * 0.5f, b.y + b.d * 0.5f, b.z)
        val cut = (b.round * cell * 0.08f).coerceIn(0f, hw * 0.18f)

        val shx = sx + cell * 0.18f
        val shy = sy + cell * 0.10f
        fillPath(canvas, diamond(shx, shy, hw * 0.92f, hh * 0.92f), RGBA(28, 34, 30, (42 * (1f - airK)).toInt()))

        path.reset()
        path.moveTo(sx - hw + cut, sy)
        path.lineTo(sx, sy + hh)
        path.lineTo(sx, sy + hh - lift)
        path.lineTo(sx - hw + cut, sy - lift)
        path.close()
        fillPath(canvas, path, left)

        path.reset()
        path.moveTo(sx + hw - cut, sy)
        path.lineTo(sx, sy + hh)
        path.lineTo(sx, sy + hh - lift)
        path.lineTo(sx + hw - cut, sy - lift)
        path.close()
        fillPath(canvas, path, right)

        path.reset()
        path.moveTo(sx, sy - hh - lift)
        path.lineTo(sx + hw - cut, sy - lift)
        path.lineTo(sx, sy + hh * 0.02f - lift)
        path.lineTo(sx - hw + cut, sy - lift)
        path.close()
        fillPath(canvas, path, top)

        val outline = col.shade(0.32)
        strokeColor(outline, (210 * (1f - airK * 0.5f)).toInt(), max(1.15f, cell * 0.055f))
        path.reset()
        path.moveTo(sx, sy - hh - lift)
        path.lineTo(sx + hw - cut, sy - lift)
        path.lineTo(sx + hw - cut, sy)
        path.lineTo(sx, sy + hh)
        path.lineTo(sx - hw + cut, sy)
        path.lineTo(sx - hw + cut, sy - lift)
        path.close()
        canvas.drawPath(path, paint)

        if (b.seam && lift > cell * 0.12f) {
            strokeColor(col.shade(0.42), 90, max(0.5f, cell * 0.018f))
            canvas.drawLine(sx, sy - lift, sx, sy + hh - lift, paint)
            var fy = sy + hh - cell * 0.18f
            while (fy > sy + hh - lift + cell * 0.06f) {
                canvas.drawLine(sx - hw * 0.55f, fy, sx + hw * 0.55f, fy, paint)
                fy -= cell * 0.22f
            }
        }
    }

    private fun sortDraw(list: MutableList<IsoBox>) {
        list.sortWith { a, b ->
            val da = a.x + a.y + a.w * 0.5f + a.d * 0.5f
            val db = b.x + b.y + b.w * 0.5f + b.d * 0.5f
            val c = da.compareTo(db)
            if (c != 0) c else a.z.compareTo(b.z)
        }
    }

    private fun visibleRange(): IntArray {
        val w = World.current ?: return intArrayOf(1, 1, 1, 1)
        val pad = 4
        val corners = arrayOf(0f to topInset, viewW to topInset, 0f to viewH, viewW to viewH)
        var minX = w.cols; var maxX = 1; var minY = w.rows; var maxY = 1
        for ((sx, sy) in corners) {
            val (tx, ty) = tileAt(sx, sy)
            minX = min(minX, tx); maxX = max(maxX, tx)
            minY = min(minY, ty); maxY = max(maxY, ty)
        }
        return intArrayOf(
            (minX - pad).coerceIn(1, w.cols),
            (maxX + pad).coerceIn(1, w.cols),
            (minY - pad).coerceIn(1, w.rows),
            (maxY + pad).coerceIn(1, w.rows)
        )
    }

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
        nightLevel = nightFactor(GameData.timeOfDay)
        fillRect(canvas, 0f, 0f, viewW, viewH, skyColor(GameData.timeOfDay))

        val vis = visibleRange()
        val x0 = vis[0]; val x1 = vis[1]; val y0 = vis[2]; val y1 = vis[3]
        val tiles = ArrayList<IntArray>((x1 - x0 + 1) * (y1 - y0 + 1))
        for (ty in y0..y1) for (tx in x0..x1) tiles.add(intArrayOf(tx, ty, tx + ty))
        tiles.sortBy { it[2] }

        val waterTiles = ArrayList<IntArray>()
        for (txy in tiles) {
            val tx = txy[0]; val ty = txy[1]
            val t = w.grid[ty - 1][tx - 1]
            if (t.terrain == "water") waterTiles.add(txy) else drawTerrainTile(canvas, tx, ty, t)
        }
        drawWaterSheet(canvas, waterTiles)

        val showNet = overlay in listOf("metro", "rail", "district") ||
            tool?.kind in listOf("metro", "rail", "district") ||
            (tool?.kind == "road" && (tool?.roadKind == "metro" || tool?.roadKind == "rail"))
        if (showNet) drawNetworks(canvas, tiles)

        for (txy in tiles) {
            val tx = txy[0]; val ty = txy[1]
            val t = w.grid[ty - 1][tx - 1]
            if (t.road != null) drawRoadTile(canvas, tx, ty, t)
            else if (t.rail) drawRailTile(canvas, tx, ty)
        }

        val ents = ArrayList<IsoBox>(256)
        for (txy in tiles) {
            val tx = txy[0]; val ty = txy[1]
            val t = w.grid[ty - 1][tx - 1]
            if (t.building == null && t.road == null) {
                if (t.terrain == "forest") addTree(ents, tx, ty)
                else if (t.terrain == "hill") addRocks(ents, tx, ty)
                else if (t.terrain != "water" && hash01(tx, ty, 9) > 0.82f) addGrassTuft(ents, tx, ty)
            }
            val bl = t.building ?: continue
            if (bl.isService && (bl.ax != tx || bl.ay != ty)) continue
            addBuilding(ents, bl, tx, ty)
        }
        addVehicles(ents)
        sortDraw(ents)
        for (b in ents) {
            val k = air(b.x, b.y)
            drawIsoBox(canvas, b, k)
        }

        drawCoverage(canvas, tiles)
        drawLabels(canvas, tiles)
        drawHoverSelect(canvas)
        if (nightLevel > 0.02f) fillRect(canvas, 0f, 0f, viewW, viewH, RGBA(18, 24, 52, (nightLevel * 88).toInt()))
        if (Ambience.lightning > 0.04f) fillRect(canvas, 0f, 0f, viewW, viewH, RGBA(230, 236, 255, (Ambience.lightning * 90).toInt()))
        drawWeather(canvas)
        drawToast(canvas)
    }

    private fun drawTerrainTile(canvas: Canvas, tx: Int, ty: Int, t: Tile) {
        val wx0 = tx - 1f
        val wy0 = ty - 1f
        val steps = if (cell >= 16) 3 else 2
        val step = 1f / steps
        val airK = air(wx0 + 0.5f, wy0 + 0.5f)
        val base = fog(terrainColor(t, tx, ty), airK)
        val locked = !World.isUnlocked(tx, ty) && t.road != "highway"
        for (iy in 0 until steps) for (ix in 0 until steps) {
            val x = wx0 + ix * step
            val y = wy0 + iy * step
            val z00 = groundZ(x, y)
            val z10 = groundZ(x + step, y)
            val z01 = groundZ(x, y + step)
            val z11 = groundZ(x + step, y + step)
            val z = (z00 + z10 + z01 + z11) * 0.25f
            val slope = ((z00 + z01) - (z10 + z11)) / (cell * 0.4f + 0.001f)
            val lit = clamp(0.82f + slope * 0.35f + hash01(tx * 10 + ix, ty * 10 + iy, 3) * 0.06f, 0.62f, 1.12f)
            val col = if (locked) base.mix(RGBA(28, 36, 42), 0.45f).shade(lit.toDouble()) else base.shade(lit.toDouble())
            val sx = isoX(x + step * 0.5f, y + step * 0.5f)
            val sy = isoY(x + step * 0.5f, y + step * 0.5f, z)
            val hw = halfW * step * 0.56f
            val hh = halfH * step * 0.56f
            fillPath(canvas, diamond(sx, sy, hw, hh), col)
            if (ix == 0 || iy == 0) {
                strokeColor(col.shade(0.72), 50, 0.6f)
                canvas.drawPath(diamond(sx, sy, hw, hh), paint)
            }
        }
        if (t.zone != "none" && t.road == null && t.building == null) {
            val sx = isoX(wx0 + 0.5f, wy0 + 0.5f)
            val sy = isoY(wx0 + 0.5f, wy0 + 0.5f, groundZ(wx0 + 0.5f, wy0 + 0.5f))
            fillPath(canvas, diamond(sx, sy, halfW * 0.42f, halfH * 0.42f), zoneTint(t.zone).withAlpha(70))
        }
    }

    private fun terrainColor(t: Tile, tx: Int, ty: Int): RGBA {
        val C = Config.COLORS
        return when (t.terrain) {
            "forest" -> C.forest
            "hill" -> C.hill
            "plain" -> C.plain
            else -> if ((tx + ty) % 2 == 0) C.grass else C.grassAlt
        }
    }

    private fun zoneTint(zone: String): RGBA {
        val C = Config.COLORS
        return when (zone) {
            "residential" -> C.zoneResidential
            "commercial" -> C.zoneCommercial
            "industrial" -> C.zoneIndustrial
            "office" -> C.zoneOffice
            else -> C.grass
        }
    }

    private fun drawWaterSheet(canvas: Canvas, tiles: ArrayList<IntArray>) {
        if (tiles.isEmpty()) return
        val phase = rainPhase
        for (txy in tiles) {
            val tx = txy[0]; val ty = txy[1]
            val wx = tx - 0.5f; val wy = ty - 0.5f
            val z = 8f * elevScale
            val airK = air(wx, wy)
            val wf = World.waterFrac(wx, wy)
            val deep = fog(RGBA(78, 138, 176), airK)
            val shoal = fog(RGBA(140, 188, 196), airK)
            val col = deep.mix(shoal, 1f - wf)
            val sx = isoX(wx, wy)
            val sy = isoY(wx, wy, z)
            fillPath(canvas, diamond(sx, sy, halfW * 0.58f, halfH * 0.58f), col.withAlpha(if (wf < 0.7f) 175 else 220))
            strokeColor(col.shade(1.18), (90 * (1f - airK * 0.4f)).toInt(), 0.9f)
            val off = ((sin((wx + phase * 0.35f) * 2.2f) + cos((wy - phase * 0.22f) * 1.7f)) * 0.12f)
            canvas.drawLine(sx - halfW * 0.28f, sy + off * cell, sx + halfW * 0.28f, sy + off * cell, paint)
            strokeColor(col.shade(0.85), 50, 0.7f)
            canvas.drawLine(sx - halfW * 0.18f, sy + halfH * 0.18f - off * cell, sx + halfW * 0.18f, sy + halfH * 0.18f - off * cell, paint)
        }
    }

    private fun drawNetworks(canvas: Canvas, tiles: ArrayList<IntArray>) {
        for (txy in tiles) {
            val tx = txy[0]; val ty = txy[1]
            val t = World.current!!.grid[ty - 1][tx - 1]
            val wx = tx - 0.5f; val wy = ty - 0.5f
            val z = groundZ(wx, wy)
            val sx = isoX(wx, wy); val sy = isoY(wx, wy, z)
            if (t.district != 0 && (tool?.kind == "district" || overlay == "district")) {
                fillPath(canvas, diamond(sx, sy, halfW * 0.45f, halfH * 0.45f), RGBA(80, 140, 200, 55))
            }
            if ((overlay == "metro" || tool?.roadKind == "metro") && t.metro) {
                fillPath(canvas, diamond(sx, sy, halfW * 0.22f, halfH * 0.22f), RGBA(40, 80, 160, 190))
            }
            if ((overlay == "rail" || tool?.roadKind == "rail") && t.rail) {
                fillPath(canvas, diamond(sx, sy, halfW * 0.28f, halfH * 0.28f), RGBA(70, 70, 78, 210))
            }
        }
    }

    private fun drawRoadTile(canvas: Canvas, tx: Int, ty: Int, t: Tile) {
        val wx = tx - 0.5f; val wy = ty - 0.5f
        val z = groundZ(wx, wy) + cell * 0.02f
        val C = Config.COLORS
        val col = when (t.road) {
            "highway" -> C.roadHighway
            "avenue" -> C.roadAvenue
            "dirt" -> C.roadDirt
            else -> C.roadLocal
        }
        val airK = air(wx, wy)
        val sx = isoX(wx, wy); val sy = isoY(wx, wy, z)
        fillPath(canvas, diamond(sx, sy, halfW * 0.52f, halfH * 0.52f), fog(col, airK))
        strokeColor(fog(col.shade(1.25), airK), 180, if (t.road == "highway") 1.6f else 1.0f)
        val wld = World.current!!
        val up = wld.grid.getOrNull(ty - 2)?.get(tx - 1)?.road != null
        val down = wld.grid.getOrNull(ty)?.get(tx - 1)?.road != null
        val left = wld.grid[ty - 1].getOrNull(tx - 2)?.road != null
        val right = wld.grid[ty - 1].getOrNull(tx)?.road != null
        if (left || right) canvas.drawLine(isoX(tx - 1f, wy), isoY(tx - 1f, wy, z), isoX(tx.toFloat(), wy), isoY(tx.toFloat(), wy, z), paint)
        if (up || down) canvas.drawLine(isoX(wx, ty - 1f), isoY(wx, ty - 1f, z), isoX(wx, ty.toFloat()), isoY(wx, ty.toFloat(), z), paint)
        if ((left || right) && (up || down)) {
            val cycle = (Growth.simTime * 1.2).toInt() % 4
            val lamp = if (cycle < 2) RGBA(210, 60, 50) else RGBA(70, 180, 90)
            fillCircle(canvas, sx + halfW * 0.18f, sy - cell * 0.18f, cell * 0.05f, lamp)
        }
    }

    private fun drawRailTile(canvas: Canvas, tx: Int, ty: Int) {
        val wx = tx - 0.5f; val wy = ty - 0.5f
        val z = groundZ(wx, wy)
        val sx = isoX(wx, wy); val sy = isoY(wx, wy, z)
        fillPath(canvas, diamond(sx, sy, halfW * 0.3f, halfH * 0.3f), RGBA(72, 72, 78))
        strokeColor(RGBA(210, 210, 214), 200, 1.1f)
        canvas.drawLine(sx - halfW * 0.2f, sy, sx + halfW * 0.2f, sy, paint)
    }

    private fun addGrassTuft(out: ArrayList<IsoBox>, tx: Int, ty: Int) {
        val wx = tx - 1f + 0.3f + hash01(tx, ty, 1) * 0.4f
        val wy = ty - 1f + 0.3f + hash01(tx, ty, 2) * 0.4f
        out.add(IsoBox(wx, wy, groundZ(wx, wy), 0.12f, 0.10f, 0.08f, RGBA(90, 140, 80), round = 0.4f))
    }

    private fun addRocks(out: ArrayList<IsoBox>, tx: Int, ty: Int) {
        val n = 1 + (hash01(tx, ty, 4) * 2).toInt()
        for (i in 0 until n) {
            val wx = tx - 1f + 0.2f + hash01(tx, ty, 10 + i) * 0.55f
            val wy = ty - 1f + 0.2f + hash01(tx, ty, 20 + i) * 0.55f
            out.add(IsoBox(wx, wy, groundZ(wx, wy), 0.16f + hash01(tx, i, 3) * 0.1f, 0.14f, 0.10f, RGBA(132, 128, 118), round = 0.5f))
        }
    }

    private fun addTree(out: ArrayList<IsoBox>, tx: Int, ty: Int) {
        val jitterX = (hash01(tx, ty, 5) - 0.5f) * 0.18f
        val jitterY = (hash01(tx, ty, 6) - 0.5f) * 0.18f
        val cx = tx - 1f + 0.5f + jitterX
        val cy = ty - 1f + 0.5f + jitterY
        val z = groundZ(cx, cy)
        val bark = RGBA(118, 88, 58)
        val leafA = RGBA(54, 118, 68)
        val leafB = RGBA(70, 140, 78)
        val leafC = RGBA(46, 102, 58)
        val trunkH = 0.38f
        out.add(IsoBox(cx - 0.055f, cy - 0.055f, z, 0.11f, 0.11f, trunkH, bark, seam = true, round = 0.2f))
        val layers = arrayOf(
            Triple(-0.18f, -0.16f, leafC),
            Triple(0.04f, -0.22f, leafA),
            Triple(-0.10f, 0.02f, leafB),
            Triple(0.08f, 0.06f, leafA)
        )
        for ((i, L) in layers.withIndex()) {
            val w = 0.36f - i * 0.04f
            out.add(
                IsoBox(
                    cx + L.first, cy + L.second,
                    z + trunkH * cell * 0.72f + i * cell * 0.07f,
                    w, w * 0.88f, 0.15f, L.third, round = 0.9f
                )
            )
        }
    }

    private fun addBuilding(out: ArrayList<IsoBox>, bl: Building, tx: Int, ty: Int) {
        val ax = if (bl.isService) bl.ax else tx
        val ay = if (bl.isService) bl.ay else ty
        val x = ax - 1f
        val y = ay - 1f
        val z = groundZ(x + bl.w * 0.5f, y + bl.h * 0.5f)
        val seed = tx * 17 + ty * 31 + bl.level * 9
        val variant = seed % 3
        val C = Config.COLORS
        if (bl.isService) {
            addService(out, bl, x, y, z)
            return
        }
        var anim = 1f
        val age = Growth.simTime - bl.born
        if (age < 0.6) {
            val k = max(0f, (age / 0.6).toFloat())
            anim = 0.25f + 0.75f * (k * k * (3 - 2 * k))
        }
        val base = if (bl.abandoned) C.bAbandoned else when (bl.zone) {
            "commercial" -> C.bCommercial
            "industrial" -> C.bIndustrial
            "office" -> C.bOffice
            else -> C.bResidential
        }
        when (bl.zone) {
            "residential" -> {
                val h = (0.42f + bl.level * 0.38f) * anim
                val roof = when (variant) {
                    0 -> RGBA(150, 78, 62)
                    1 -> RGBA(120, 88, 64)
                    else -> RGBA(168, 92, 70)
                }
                if (bl.level == 1 && variant == 0) {
                    out.add(IsoBox(x + 0.08f, y + 0.18f, z, 0.58f, 0.62f, h, base, seam = true, round = 0.2f))
                    out.add(IsoBox(x + 0.12f, y + 0.10f, z + h * cell * 0.85f, 0.50f, 0.30f, 0.18f, roof, round = 0.15f))
                    out.add(IsoBox(x + 0.68f, y + 0.42f, z, 0.24f, 0.40f, h * 0.55f, base.shade(0.9), round = 0.2f))
                } else if (bl.level == 1 && variant == 2) {
                    out.add(IsoBox(x + 0.06f, y + 0.32f, z, 0.42f, 0.50f, h * 0.8f, base, seam = true, round = 0.2f))
                    out.add(IsoBox(x + 0.50f, y + 0.18f, z, 0.42f, 0.62f, h, base.shade(0.92), seam = true, round = 0.2f))
                    out.add(IsoBox(x + 0.54f, y + 0.08f, z + h * cell * 0.9f, 0.34f, 0.24f, 0.16f, roof, round = 0.2f))
                } else {
                    out.add(IsoBox(x + 0.12f, y + 0.12f, z, 0.76f, 0.76f, h, base, seam = true, round = 0.15f))
                    out.add(IsoBox(x + 0.16f, y + 0.08f, z + h * cell * 0.92f, 0.68f, 0.28f, 0.14f, roof, round = 0.1f))
                }
            }
            "commercial" -> {
                val h = (0.38f + bl.level * 0.28f) * anim
                val awning = when (variant) {
                    0 -> RGBA(190, 70, 70)
                    1 -> RGBA(70, 110, 170)
                    else -> RGBA(210, 150, 60)
                }
                out.add(IsoBox(x + 0.10f, y + 0.14f, z, 0.80f, 0.72f, h, base, seam = true, round = 0.15f))
                out.add(IsoBox(x + 0.16f, y + 0.52f, z + h * cell * 0.35f, 0.68f, 0.18f, 0.08f, RGBA(40, 50, 70), glass = true, round = 0.1f))
                out.add(IsoBox(x + 0.12f, y + 0.48f, z + h * cell * 0.55f, 0.76f, 0.10f, 0.06f, awning, round = 0.4f))
            }
            "industrial" -> {
                val h = (0.32f + bl.level * 0.22f) * anim
                out.add(IsoBox(x + 0.06f, y + 0.20f, z, 0.62f, 0.62f, h, base, seam = true, round = 0.1f))
                out.add(IsoBox(x + 0.70f, y + 0.34f, z, 0.22f, 0.40f, h * 1.25f, RGBA(110, 108, 100), seam = true, round = 0.1f))
                if (variant != 0) out.add(IsoBox(x + 0.18f, y + 0.08f, z, 0.16f, 0.16f, h * 1.55f, RGBA(120, 118, 110), round = 0.2f))
            }
            else -> {
                val h = (0.85f + bl.level * 0.55f) * anim
                out.add(IsoBox(x + 0.14f, y + 0.10f, z, 0.72f, 0.78f, h, base, seam = true, round = 0.08f))
                out.add(IsoBox(x + 0.20f, y + 0.16f, z + h * cell * 0.55f, 0.60f, 0.18f, 0.22f, RGBA(180, 210, 230), glass = true, round = 0.05f))
            }
        }
        if (World.tile(tx, ty)?.onFire == true) {
            out.add(IsoBox(x + 0.38f, y + 0.38f, z + cell * 0.7f, 0.22f, 0.22f, 0.18f, RGBA(255, 120, 40), round = 0.8f))
        }
    }

    private fun addService(out: ArrayList<IsoBox>, bl: Building, x: Float, y: Float, z: Float) {
        val bw = bl.w.toFloat(); val bh = bl.h.toFloat()
        fun box(px: Float, py: Float, w: Float, d: Float, h: Float, c: RGBA, seam: Boolean = false, round: Float = 0.15f, glass: Boolean = false) {
            out.add(IsoBox(x + px * bw, y + py * bh, z, w * bw, d * bh, h, c, seam, round, glass))
        }
        when (bl.service) {
            "park" -> {
                box(0.08f, 0.08f, 0.84f, 0.84f, 0.04f, RGBA(92, 148, 96), round = 0.3f)
                box(0.22f, 0.48f, 0.08f, 0.08f, 0.28f, RGBA(92, 70, 48), round = 0.2f)
                box(0.18f, 0.28f, 0.28f, 0.28f, 0.16f, RGBA(46, 108, 58), round = 0.9f)
                box(0.52f, 0.18f, 0.22f, 0.22f, 0.14f, RGBA(62, 128, 70), round = 0.9f)
            }
            "plaza" -> {
                box(0.04f, 0.04f, 0.92f, 0.92f, 0.05f, RGBA(188, 186, 176), round = 0.1f)
                box(0.38f, 0.38f, 0.24f, 0.24f, 0.22f, RGBA(170, 90, 80), round = 0.2f)
            }
            "wind_farm" -> {
                box(0.32f, 0.52f, 0.36f, 0.32f, 0.18f, RGBA(210, 214, 218), round = 0.2f)
                box(0.46f, 0.46f, 0.08f, 0.08f, 1.15f, RGBA(230, 230, 230), round = 0.4f)
            }
            "solar_plant" -> {
                box(0.08f, 0.12f, 0.38f, 0.34f, 0.08f, RGBA(40, 70, 130), round = 0.1f)
                box(0.52f, 0.12f, 0.38f, 0.34f, 0.08f, RGBA(40, 70, 130), round = 0.1f)
                box(0.08f, 0.52f, 0.38f, 0.34f, 0.08f, RGBA(50, 86, 150), round = 0.1f)
                box(0.52f, 0.52f, 0.38f, 0.34f, 0.08f, RGBA(50, 86, 150), round = 0.1f)
                box(0.40f, 0.40f, 0.20f, 0.20f, 0.22f, RGBA(200, 204, 208), round = 0.15f)
            }
            "coal_plant" -> {
                box(0.06f, 0.28f, 0.58f, 0.58f, 0.42f, RGBA(92, 90, 86), seam = true)
                box(0.68f, 0.18f, 0.14f, 0.22f, 0.95f, RGBA(110, 108, 102), round = 0.3f)
                box(0.84f, 0.22f, 0.12f, 0.18f, 0.78f, RGBA(118, 116, 110), round = 0.3f)
            }
            "nuclear_plant" -> {
                box(0.08f, 0.42f, 0.84f, 0.42f, 0.28f, RGBA(188, 196, 188), seam = true)
                box(0.18f, 0.18f, 0.28f, 0.28f, 0.32f, RGBA(210, 218, 210), round = 0.9f)
                box(0.54f, 0.18f, 0.28f, 0.28f, 0.32f, RGBA(210, 218, 210), round = 0.9f)
            }
            "clinic" -> {
                box(0.08f, 0.18f, 0.84f, 0.64f, 0.42f, RGBA(236, 236, 240), seam = true)
                box(0.38f, 0.28f, 0.24f, 0.12f, 0.08f, RGBA(210, 70, 70), round = 0.1f)
            }
            "hospital" -> {
                box(0.06f, 0.14f, 0.88f, 0.72f, 0.62f, RGBA(236, 240, 244), seam = true)
                box(0.40f, 0.18f, 0.20f, 0.10f, 0.08f, RGBA(210, 70, 70))
                box(0.14f, 0.56f, 0.72f, 0.18f, 0.12f, RGBA(70, 120, 180), glass = true)
            }
            "school" -> {
                box(0.08f, 0.28f, 0.84f, 0.56f, 0.38f, RGBA(232, 214, 170), seam = true)
                box(0.14f, 0.16f, 0.72f, 0.28f, 0.22f, RGBA(168, 78, 62), round = 0.1f)
                box(0.42f, 0.62f, 0.16f, 0.16f, 0.12f, RGBA(90, 70, 50))
            }
            "middle_school" -> {
                box(0.06f, 0.22f, 0.88f, 0.64f, 0.48f, RGBA(220, 204, 168), seam = true)
                box(0.28f, 0.08f, 0.44f, 0.22f, 0.28f, RGBA(160, 80, 68))
            }
            "university" -> {
                box(0.08f, 0.28f, 0.84f, 0.58f, 0.42f, RGBA(210, 200, 178), seam = true)
                box(0.32f, 0.10f, 0.36f, 0.28f, 0.55f, RGBA(188, 176, 150))
            }
            "fire_station" -> {
                box(0.08f, 0.22f, 0.84f, 0.62f, 0.38f, RGBA(210, 70, 60), seam = true)
                box(0.18f, 0.58f, 0.28f, 0.18f, 0.16f, RGBA(40, 44, 48), round = 0.1f)
                box(0.54f, 0.58f, 0.28f, 0.18f, 0.16f, RGBA(40, 44, 48), round = 0.1f)
            }
            "police" -> {
                box(0.10f, 0.18f, 0.80f, 0.66f, 0.42f, RGBA(50, 80, 150), seam = true)
                box(0.38f, 0.26f, 0.24f, 0.10f, 0.08f, RGBA(230, 210, 70))
            }
            "landfill" -> {
                box(0.08f, 0.18f, 0.50f, 0.50f, 0.18f, RGBA(90, 92, 70), round = 0.7f)
                box(0.48f, 0.38f, 0.36f, 0.36f, 0.14f, RGBA(110, 108, 80), round = 0.7f)
                box(0.70f, 0.18f, 0.18f, 0.22f, 0.22f, RGBA(80, 140, 80))
            }
            "incinerator" -> {
                box(0.08f, 0.28f, 0.58f, 0.56f, 0.38f, RGBA(110, 108, 102), seam = true)
                box(0.70f, 0.16f, 0.20f, 0.28f, 0.72f, RGBA(90, 88, 84), round = 0.25f)
            }
            "pump_station" -> {
                box(0.12f, 0.28f, 0.76f, 0.52f, 0.28f, RGBA(70, 130, 170), seam = true)
                box(0.22f, 0.32f, 0.22f, 0.22f, 0.16f, RGBA(50, 90, 130), round = 0.8f)
            }
            "water_tower" -> {
                box(0.38f, 0.38f, 0.24f, 0.24f, 0.55f, RGBA(90, 120, 140), seam = true)
                box(0.28f, 0.28f, 0.44f, 0.44f, 0.28f, RGBA(70, 140, 190), round = 0.9f)
            }
            "sewage" -> {
                box(0.08f, 0.28f, 0.84f, 0.56f, 0.28f, RGBA(86, 118, 92), seam = true)
                box(0.24f, 0.36f, 0.22f, 0.22f, 0.16f, RGBA(50, 90, 70), round = 0.8f)
            }
            "airport" -> {
                box(0.04f, 0.08f, 0.92f, 0.84f, 0.06f, RGBA(168, 176, 184))
                box(0.18f, 0.28f, 0.64f, 0.44f, 0.32f, RGBA(210, 214, 220), seam = true)
            }
            "rail_station" -> {
                box(0.10f, 0.18f, 0.80f, 0.64f, 0.42f, RGBA(70, 92, 128), seam = true)
                box(0.18f, 0.55f, 0.64f, 0.18f, 0.10f, RGBA(230, 210, 90))
            }
            "metro" -> {
                box(0.12f, 0.22f, 0.76f, 0.58f, 0.28f, RGBA(48, 72, 110), seam = true)
                box(0.22f, 0.58f, 0.56f, 0.16f, 0.08f, RGBA(230, 210, 80))
            }
            "bus_stop" -> {
                box(0.22f, 0.42f, 0.56f, 0.32f, 0.16f, RGBA(40, 90, 170), round = 0.2f)
                box(0.18f, 0.32f, 0.64f, 0.08f, 0.06f, RGBA(230, 232, 238))
            }
            "harbor" -> {
                box(0.12f, 0.18f, 0.50f, 0.50f, 0.32f, RGBA(150, 120, 80), seam = true)
                box(0.08f, 0.72f, 0.84f, 0.16f, 0.08f, RGBA(90, 90, 86))
            }
            "cemetery" -> {
                box(0.08f, 0.08f, 0.84f, 0.84f, 0.04f, RGBA(110, 130, 108))
                box(0.18f, 0.28f, 0.12f, 0.10f, 0.16f, RGBA(176, 176, 170))
                box(0.44f, 0.22f, 0.12f, 0.10f, 0.20f, RGBA(176, 176, 170))
            }
            "crematorium" -> {
                box(0.12f, 0.22f, 0.76f, 0.58f, 0.32f, RGBA(150, 148, 142), seam = true)
                box(0.70f, 0.12f, 0.16f, 0.20f, 0.55f, RGBA(90, 88, 84), round = 0.2f)
            }
            "prison" -> {
                box(0.08f, 0.16f, 0.84f, 0.70f, 0.42f, RGBA(120, 124, 128), seam = true)
            }
            "tv_tower" -> {
                box(0.40f, 0.55f, 0.20f, 0.30f, 0.22f, RGBA(170, 170, 176))
                box(0.44f, 0.18f, 0.12f, 0.18f, 1.35f, RGBA(210, 210, 216), round = 0.3f)
                box(0.46f, 0.16f, 0.08f, 0.08f, 0.10f, RGBA(220, 80, 70), round = 0.8f)
            }
            "stadium" -> {
                box(0.08f, 0.18f, 0.84f, 0.64f, 0.22f, RGBA(90, 140, 90), round = 0.8f)
                box(0.22f, 0.32f, 0.56f, 0.36f, 0.10f, RGBA(210, 210, 200), round = 0.7f)
            }
            "stock_exchange" -> {
                box(0.08f, 0.16f, 0.84f, 0.70f, 0.62f, RGBA(176, 168, 150), seam = true)
                box(0.18f, 0.28f, 0.18f, 0.18f, 0.16f, RGBA(80, 120, 170), glass = true)
            }
            else -> box(0.12f, 0.12f, 0.76f, 0.76f, 0.42f, Config.COLORS.bCivic, seam = true)
        }
    }

    private fun addVehicles(out: ArrayList<IsoBox>) {
        val bounce = (sin(rainPhase * 9.5f) * 0.012f)
        for (c in Traffic.cars) {
            if (c.parked) continue
            val v = dirs[c.dir]
            var wx = c.x - 1 + v[0] * c.prog + 0.5f
            var wy = c.y - 1 + v[1] * c.prog + 0.5f
            val kind = World.tile(c.x, c.y)?.road
            val lane = when (kind) {
                "highway" -> 0.16f
                "avenue" -> 0.14f
                else -> 0.10f
            }
            when (c.dir) {
                0 -> wy += lane
                2 -> wy -= lane
                1 -> wx -= lane
                3 -> wx += lane
            }
            addCarRig(out, wx, wy, c.dir, c.color, longBody = c.kind == "freight", bounce = bounce, selected = Traffic.selected === c)
            if (!c.parked && c.cruise > 0.4f && hash01(c.x, c.y, (rainPhase * 8).toInt()) > 0.96f && dust.size < 18) {
                dust.add(DustPuff(wx, wy, 0.45f, groundZ(wx, wy)))
            }
        }
        for (v in Transit.vehicles) {
            val (wx, wy) = Transit.vehicleCell(v)
            addCarRig(out, wx, wy, 0, RGBA(40, 90, 170), longBody = true, bounce = bounce, selected = false, bus = true)
        }
        for (ev in CitySystems.cars) {
            val (wx, wy) = CitySystems.screenCell(ev)
            val col = when (ev.kind) {
                "fire" -> RGBA(220, 70, 50)
                "ambulance" -> RGBA(240, 240, 245)
                "police" -> RGBA(50, 80, 180)
                "garbage" -> RGBA(90, 118, 86)
                else -> RGBA(40, 40, 40)
            }
            addCarRig(out, wx, wy, 0, col, longBody = ev.kind == "garbage" || ev.kind == "fire", bounce = bounce, selected = false)
        }
        for (tr in Traffic.trains) {
            val wx = tr.x - 1 + dirs[tr.dir][0] * tr.prog + 0.5f
            val wy = tr.y - 1 + dirs[tr.dir][1] * tr.prog + 0.5f
            addTrain(out, wx, wy, tr.dir, Traffic.selectedTrain === tr)
        }
        for (pl in Traffic.planes) {
            val z = groundZ(pl.x - 0.5f, pl.y - 0.5f) + pl.alt * cell * 0.18f
            out.add(IsoBox(pl.x - 0.85f, pl.y - 0.62f, z, 0.70f, 0.18f, 0.10f, RGBA(230, 232, 238), round = 0.4f))
            out.add(IsoBox(pl.x - 0.55f, pl.y - 0.92f, z + cell * 0.04f, 0.12f, 0.78f, 0.04f, RGBA(210, 214, 222), round = 0.2f))
        }
        for (p in dust) {
            out.add(IsoBox(p.x - 0.06f, p.y - 0.06f, p.z, 0.12f, 0.12f, 0.06f, RGBA(186, 176, 150, (90 * p.life).toInt()), round = 0.8f))
        }
    }

    private fun addCarRig(
        out: ArrayList<IsoBox>,
        cx: Float, cy: Float, dir: Int, color: RGBA,
        longBody: Boolean, bounce: Float, selected: Boolean,
        bus: Boolean = false
    ) {
        val z = groundZ(cx, cy) + cell * 0.04f + bounce * cell
        val horiz = dir == 0 || dir == 2
        val L = if (bus) 0.78f else if (longBody) 0.70f else 0.48f
        val W = if (bus) 0.30f else if (longBody) 0.28f else 0.24f
        val bodyW = if (horiz) L else W
        val bodyD = if (horiz) W else L
        val x = cx - bodyW * 0.5f
        val y = cy - bodyD * 0.5f
        val body = if (selected) color.shade(1.08) else color
        val cabin = color.shade(0.78)
        val glass = if (nightLevel > 0.35f) RGBA(255, 220, 140) else RGBA(70, 92, 112)
        val rubber = RGBA(36, 36, 40)
        val bumper = color.shade(0.62)
        val wheel = 0.10f
        val inset = 0.04f
        fun wheelAt(px: Float, py: Float) {
            out.add(IsoBox(px, py, z, wheel, wheel * 0.72f, 0.08f, rubber, round = 0.7f, seam = true))
        }
        if (horiz) {
            wheelAt(x + inset, y - 0.02f)
            wheelAt(x + bodyW - wheel - inset, y - 0.02f)
            wheelAt(x + inset, y + bodyD - wheel * 0.55f)
            wheelAt(x + bodyW - wheel - inset, y + bodyD - wheel * 0.55f)
        } else {
            wheelAt(x - 0.02f, y + inset)
            wheelAt(x - 0.02f, y + bodyD - wheel - inset)
            wheelAt(x + bodyW - wheel * 0.55f, y + inset)
            wheelAt(x + bodyW - wheel * 0.55f, y + bodyD - wheel - inset)
        }
        out.add(IsoBox(x, y, z + cell * 0.04f, bodyW, bodyD, 0.14f, body, seam = true, round = 0.35f))
        val cabinOff = when (dir) {
            0 -> 0.38f to 0.12f
            2 -> 0.08f to 0.12f
            1 -> 0.12f to 0.38f
            else -> 0.12f to 0.08f
        }
        val cw = if (horiz) bodyW * 0.42f else bodyW * 0.72f
        val cd = if (horiz) bodyD * 0.70f else bodyD * 0.42f
        out.add(IsoBox(x + cabinOff.first * bodyW, y + cabinOff.second * bodyD, z + cell * 0.14f, cw, cd, 0.12f, cabin, round = 0.3f))
        out.add(IsoBox(x + cabinOff.first * bodyW + 0.04f, y + cabinOff.second * bodyD + 0.03f, z + cell * 0.18f, cw * 0.7f, cd * 0.55f, 0.07f, glass, glass = true, round = 0.2f))
        val bumperW = if (horiz) 0.08f else bodyW * 0.9f
        val bumperD = if (horiz) bodyD * 0.9f else 0.08f
        val bx = if (dir == 0) x + bodyW - 0.08f else if (dir == 2) x else x + bodyW * 0.05f
        val by = if (dir == 1) y + bodyD - 0.08f else if (dir == 3) y else y + bodyD * 0.05f
        out.add(IsoBox(bx, by, z + cell * 0.05f, bumperW, bumperD, 0.06f, bumper, round = 0.4f))
        val light = if (dir == 0 || dir == 1) RGBA(255, 230, 160) else RGBA(210, 70, 60)
        out.add(IsoBox(bx, by, z + cell * 0.08f, bumperW * 0.45f, bumperD * 0.45f, 0.04f, light, round = 0.5f))
        val mir = RGBA(40, 48, 56)
        if (horiz) {
            out.add(IsoBox(x + bodyW * 0.42f, y - 0.05f, z + cell * 0.16f, 0.06f, 0.05f, 0.05f, mir, round = 0.4f))
            out.add(IsoBox(x + bodyW * 0.42f, y + bodyD, z + cell * 0.16f, 0.06f, 0.05f, 0.05f, mir, round = 0.4f))
        } else {
            out.add(IsoBox(x - 0.05f, y + bodyD * 0.42f, z + cell * 0.16f, 0.05f, 0.06f, 0.05f, mir, round = 0.4f))
            out.add(IsoBox(x + bodyW, y + bodyD * 0.42f, z + cell * 0.16f, 0.05f, 0.06f, 0.05f, mir, round = 0.4f))
        }
        if (selected) {
            out.add(IsoBox(x - 0.04f, y - 0.04f, z, bodyW + 0.08f, bodyD + 0.08f, 0.02f, RGBA(220, 80, 50, 90), round = 0.2f))
        }
    }

    private fun addTrain(out: ArrayList<IsoBox>, cx: Float, cy: Float, dir: Int, selected: Boolean) {
        val z = groundZ(cx, cy)
        val horiz = dir == 0 || dir == 2
        val L = 1.15f; val W = 0.28f
        val x = cx - (if (horiz) L else W) * 0.5f
        val y = cy - (if (horiz) W else L) * 0.5f
        val bw = if (horiz) L else W
        val bd = if (horiz) W else L
        out.add(IsoBox(x, y, z, bw, bd, 0.22f, RGBA(36, 52, 78), seam = true, round = 0.2f))
        out.add(IsoBox(x + bw * 0.1f, y + bd * 0.15f, z + cell * 0.16f, bw * 0.8f, bd * 0.7f, 0.10f, RGBA(70, 92, 112), glass = true, round = 0.1f))
        if (selected) out.add(IsoBox(x - 0.04f, y - 0.04f, z, bw + 0.08f, bd + 0.08f, 0.02f, RGBA(220, 80, 50, 90)))
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

    private fun drawCoverage(canvas: Canvas, tiles: ArrayList<IntArray>) {
        if (overlay.isEmpty()) return
        if (overlay == "traffic") {
            for (txy in tiles) {
                val tx = txy[0]; val ty = txy[1]
                val t = World.current!!.grid[ty - 1][tx - 1]
                if (t.road == null) continue
                val flow = Traffic.flowAt(tx, ty)
                val col = when {
                    flow > 40 -> RGBA(220, 80, 70, 150)
                    flow > 12 -> RGBA(230, 190, 70, 150)
                    else -> RGBA(90, 200, 120, 120)
                }
                val wx = tx - 0.5f; val wy = ty - 0.5f
                val sx = isoX(wx, wy); val sy = isoY(wx, wy, groundZ(wx, wy))
                fillPath(canvas, diamond(sx, sy, halfW * 0.46f, halfH * 0.46f), col)
            }
            return
        }
        if (overlay == "landvalue") {
            for (txy in tiles) {
                val tx = txy[0]; val ty = txy[1]
                val t = World.current!!.grid[ty - 1][tx - 1]
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
                val wx = tx - 0.5f; val wy = ty - 0.5f
                fillPath(canvas, diamond(isoX(wx, wy), isoY(wx, wy, groundZ(wx, wy)), halfW * 0.46f, halfH * 0.46f), col)
            }
            return
        }
        if (overlay in listOf("district", "metro", "rail")) return
        val hue = overlayHue(overlay)
        val red = RGBA(210, 70, 60, 70)
        for (txy in tiles) {
            val tx = txy[0]; val ty = txy[1]
            val t = World.current!!.grid[ty - 1][tx - 1]
            val wx = tx - 0.5f; val wy = ty - 0.5f
            val sx = isoX(wx, wy); val sy = isoY(wx, wy, groundZ(wx, wy))
            val b = t.building
            if (b != null && b.isService) {
                val cfg = World.serviceConfig(b.service)
                if (cfg != null && cfg.category == overlay && b.ax == tx && b.ay == ty) {
                    fillPath(canvas, diamond(sx, sy, halfW * 0.55f, halfH * 0.55f), hue.withAlpha(160))
                }
            } else {
                val fac = World.coveringFacility(tx, ty, overlay)
                if (fac != null) {
                    val a = if (b != null) 120 else 70
                    fillPath(canvas, diamond(sx, sy, halfW * 0.48f, halfH * 0.48f), hue.withAlpha(a))
                } else if (b != null && !b.isService) {
                    fillPath(canvas, diamond(sx, sy, halfW * 0.48f, halfH * 0.48f), red)
                }
            }
        }
        for (e in World.allBuildings()) {
            if (!e.b.isService) continue
            val cfg = World.serviceConfig(e.b.service) ?: continue
            if (cfg.category != overlay) continue
            val (ccx, ccy) = World.coverCenter(e)
            val rTiles = World.coverRadius(cfg) + 0.5f
            val cx = isoX(ccx, ccy)
            val cy = isoY(ccx, ccy, groundZ(ccx, ccy))
            val rx = halfW * rTiles * 1.414f
            val ry = halfH * rTiles * 1.414f
            oval.set(cx - rx, cy - ry, cx + rx, cy + ry)
            path.reset()
            path.addOval(oval, Path.Direction.CW)
            strokeColor(hue.withAlpha(200), 255, 2.0f)
            canvas.drawPath(path, paint)
            fillColor(hue.withAlpha(28))
            canvas.drawPath(path, paint)
            val name = cfg.name
            val capTxt = when {
                cfg.powerCap > 0 -> name + " 电" + cfg.powerCap + " 半径" + World.coverRadius(cfg)
                cfg.waterCap > 0 -> name + " 水" + cfg.waterCap + " 半径" + World.coverRadius(cfg)
                else -> name + " 半径" + World.coverRadius(cfg) + "格"
            }
            if (typeface != null && cell >= 9) {
                drawText(canvas, cx, cy - ry - 8f, max(10f, cell * 0.38f), hue.shade(0.45), capTxt, TAlign.CENTER, 235)
            }
        }
    }

    private fun drawLabels(canvas: Canvas, tiles: ArrayList<IntArray>) {
        if (typeface == null || cell < 10) return
        val w = World.current ?: return
        if (cell >= 14) {
            for (line in w.roadLines) {
                if (line.kind != "avenue" && line.kind != "highway") continue
                val lx: Float; val ly: Float
                if (line.dir == "v") {
                    lx = isoX(line.segX[0] - 0.5f, line.labelY - 0.5f)
                    ly = isoY(line.segX[0] - 0.5f, line.labelY - 0.5f, groundZ(line.segX[0] - 0.5f, line.labelY - 0.5f))
                } else {
                    lx = isoX(line.labelX - 0.5f, line.segY[0] - 0.5f)
                    ly = isoY(line.labelX - 0.5f, line.segY[0] - 0.5f, groundZ(line.labelX - 0.5f, line.segY[0] - 0.5f))
                }
                drawText(canvas, lx, ly, cell * 0.42f, RGBA(90, 82, 64), line.name, TAlign.CENTER, 210)
            }
        }
        if (cell >= 13) {
            for (txy in tiles) {
                val tx = txy[0]; val ty = txy[1]
                val bl = w.grid[ty - 1][tx - 1].building ?: continue
                if (!bl.isService || bl.ax != tx || bl.ay != ty) continue
                val sc = World.serviceConfig(bl.service) ?: continue
                val wx = bl.ax - 1 + bl.w / 2f
                val wy = bl.ay - 1 + bl.h / 2f
                val sx = isoX(wx, wy)
                val sy = isoY(wx, wy, groundZ(wx, wy) + cell * 0.55f)
                val txt = serviceSymbol(bl.service ?: "").ifEmpty { sc.name }
                drawText(canvas, sx, sy, min(cell * 0.42f, 13f), RGBA(255, 255, 255), txt, TAlign.CENTER, 240)
            }
        }
        for (lb in w.labels) {
            val sx = isoX(lb.x - 0.5f, lb.y - 0.5f)
            val sy = isoY(lb.x - 0.5f, lb.y - 0.5f, groundZ(lb.x - 0.5f, lb.y - 0.5f))
            drawText(canvas, sx, sy, cell * 0.4f, RGBA(255, 255, 255), lb.text, TAlign.CENTER, 255)
        }
    }

    private fun isoDiamondAtTile(tx: Int, ty: Int, w: Float, h: Float): Path {
        val wx = tx - 1 + w * 0.5f
        val wy = ty - 1 + h * 0.5f
        val sx = isoX(wx, wy)
        val sy = isoY(wx, wy, groundZ(wx, wy))
        return diamond(sx, sy, halfW * w * 0.52f, halfH * h * 0.52f)
    }

    private fun drawHoverSelect(canvas: Canvas) {
        if (tool != null && hasHover) {
            val ok = toolValidAt(hoverX, hoverY)
            var gw = 1f; var gh = 1f
            if (tool?.kind == "service") {
                val sc = World.serviceConfig(tool?.id)
                if (sc != null) {
                    gw = sc.sizeW.toFloat(); gh = sc.sizeH.toFloat()
                    val cxw = hoverX - 1 + sc.sizeW / 2f
                    val cyw = hoverY - 1 + sc.sizeH / 2f
                    val cr = World.coverRadius(sc) + 0.5f
                    val cx = isoX(cxw, cyw); val cy = isoY(cxw, cyw, groundZ(cxw, cyw))
                    oval.set(cx - halfW * cr * 1.414f, cy - halfH * cr * 1.414f, cx + halfW * cr * 1.414f, cy + halfH * cr * 1.414f)
                    path.reset()
                    path.addOval(oval, Path.Direction.CW)
                    fillColor(RGBA(96, 200, 140, 28)); canvas.drawPath(path, paint)
                    strokeColor(RGBA(96, 200, 140, 140), 255, 1.4f); canvas.drawPath(path, paint)
                    val capTxt = when {
                        sc.powerCap > 0 -> "电容量 " + sc.powerCap + " · 半径 " + World.coverRadius(sc)
                        sc.waterCap > 0 -> "水容量 " + sc.waterCap + " · 半径 " + World.coverRadius(sc)
                        else -> "半径 " + World.coverRadius(sc) + " 格"
                    }
                    if (typeface != null) drawText(canvas, cx, cy + halfH * cr * 1.414f + 10f, 11f, RGBA(40, 90, 60), capTxt, TAlign.CENTER, 230)
                }
            }
            val col = if (ok) Config.COLORS.ghostOk else Config.COLORS.ghostBad
            fillPath(canvas, isoDiamondAtTile(hoverX, hoverY, gw, gh), col)
        } else if (hasHover) {
            val t = World.tile(hoverX, hoverY)
            val ax = t?.building?.ax ?: hoverX
            val ay = t?.building?.ay ?: hoverY
            val bw = (t?.building?.w ?: 1).toFloat()
            val bh = (t?.building?.h ?: 1).toFloat()
            strokePath(canvas, isoDiamondAtTile(ax, ay, bw, bh), Config.COLORS.hoverStroke, 255, 1.6f)
        }
        if (hasSelection) {
            val t = World.tile(selectedX, selectedY)
            val ax = t?.building?.ax ?: selectedX
            val ay = t?.building?.ay ?: selectedY
            val bw = (t?.building?.w ?: 1).toFloat()
            val bh = (t?.building?.h ?: 1).toFloat()
            fillPath(canvas, isoDiamondAtTile(ax, ay, bw, bh), Config.COLORS.selectFill)
            strokePath(canvas, isoDiamondAtTile(ax, ay, bw, bh), Config.COLORS.selectStroke, 255, 2f)
        }
    }

    private fun drawWeather(canvas: Canvas) {
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
            2 -> fillRect(canvas, 0f, 0f, viewW, viewH, RGBA(215, 222, 228, 55))
        }
    }

    private fun drawToast(canvas: Canvas) {
        if (toastMsg == null || typeface == null) return
        val alpha = if (toastT > 2.6f) clamp((3.2f - toastT) / 0.6f, 0f, 1f) else 1f
        val fs = 12f
        val tw = measure(toastMsg!!, fs)
        val px = viewW / 2f
        val py = viewH - bottomInset - 30f
        fillRoundRect(canvas, px - tw / 2 - 10, py - fs, tw + 20, fs * 2 + 6, 8f, RGBA(250, 250, 248, 235), (235 * alpha).toInt())
        strokeRoundRect(canvas, px - tw / 2 - 10, py - fs, tw + 20, fs * 2 + 6, 8f, Config.COLORS.accentRed, (200 * alpha).toInt(), 1f)
        drawText(canvas, px, py + 3, fs, RGBA(176, 66, 66), toastMsg!!, TAlign.CENTER, (255 * alpha).toInt())
    }

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
        tod < 0.06f -> 1f
        tod < 0.16f -> (0.16f - tod) / 0.10f
        tod < 0.5f -> 0f
        tod < 0.6f -> (tod - 0.5f) / 0.10f
        else -> 1f
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
