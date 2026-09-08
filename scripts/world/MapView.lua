-- ============================================================================
-- MapView — 俯视格子地图（raw NanoVG 渲染 + 相机）
--   渲染顺序：地形色块 → 分区底色 → 道路 → 建筑(微立体) → 标签 → 选中框
--   相机：screen = (world - cam) * cell   （Y 轴向下，行序即屏幕序）
--   手势：拖拽平移 / 滚轮+按钮缩放 / 双指捏合 / 点击选格
--   模式 B 分辨率：nvgBeginFrame(vg, logicalW, logicalH, dpr)
-- ============================================================================

local UI = require("urhox-libs/UI")
local Config = require("Config")
local World = require("world.World")
local Growth = require("world.Growth")
local Sfx = require("Sfx")

local MapView = {}
local MAP = Config.MAP
local C = Config.COLORS

-- ---------------------------------------------------------------------------
-- 模块状态
-- ---------------------------------------------------------------------------
local vg_ = nil
local fontId_ = -1
local cam_ = { x = 0, y = 0, scale = 1 }     -- scale: cell 逻辑像素
local view_ = { w = 360, h = 640 }           -- 地图视口（逻辑像素）
local topInset_ = 150                        -- 顶部 UI 遮挡（逻辑像素）
local bottomInset_ = 120                     -- 底部工具栏遮挡
local selected_ = nil                        -- {x,y}
local hover_ = nil                           -- {x,y}
local toast_ = nil                           -- {msg, t}
local onTileChanged_ = nil                   -- 格子内容变化回调（刷新右侧 UI）
local tool_ = nil                            -- 当前工具 {kind,id,...}；nil=选中/平移
local GameDataRef = nil                      -- 注入 GameData（避免循环 require）

-- 拖拽状态
local drag_ = { active = false, mode = nil, moved = false, sx = 0, sy = 0, cx = 0, cy = 0, lastTile = nil }

function MapView.setGameData(gd)
  GameDataRef = gd
end

-- 屏幕坐标 → 格子坐标（仅当命中地图可视区）
local function tileUnderCursor(mx, my)
  if my <= topInset_ or my >= view_.h - bottomInset_ then return nil end
  local tx, ty = MapView.tileAt(mx, my)
  local w = World.current
  if not w or tx < 1 or tx > w.cols or ty < 1 or ty > w.rows then return nil end
  return tx, ty
end

-- ---------------------------------------------------------------------------
-- 工具
-- ---------------------------------------------------------------------------
local function rgba(c, aOverride)
  return nvgRGBA(c[1], c[2], c[3], aOverride or c[4])
end

local function shade(c, k)
  return { math.min(255, math.floor(c[1] * k)), math.min(255, math.floor(c[2] * k)),
           math.min(255, math.floor(c[3] * k)), c[4] }
end

local function clamp(v, lo, hi)
  if v < lo then return lo elseif v > hi then return hi end
  return v
end

local function screenToWorld(sx, sy)
  local wx = sx / (MAP.baseCell * cam_.scale) + cam_.x
  local wy = sy / (MAP.baseCell * cam_.scale) + cam_.y
  return wx, wy
end

local function worldToScreen(wx, wy)
  local cell = MAP.baseCell * cam_.scale
  return (wx - cam_.x) * cell, (wy - cam_.y) * cell
end

-- 屏幕坐标 → 格子坐标（1-based，可能越界）
function MapView.tileAt(sx, sy)
  local wx, wy = screenToWorld(sx, sy)
  local tx = math.floor(wx) + 1
  local ty = math.floor(wy) + 1
  return tx, ty
end

-- 可见格子范围（带边界裁剪）
local function visibleRange()
  local w = World.current
  local x0, y0 = MapView.tileAt(0, 0)
  local x1, y1 = MapView.tileAt(view_.w, view_.h)
  x0 = clamp(x0, 1, w.cols); x1 = clamp(x1, 1, w.cols)
  y0 = clamp(y0, 1, w.rows); y1 = clamp(y1, 1, w.rows)
  return x0, x1, y0, y1
end

local function camExtent()
  local cell = MAP.baseCell * cam_.scale
  return view_.w / cell, view_.h / cell
end

-- 相机钳制：地图不能完全移出视口
function MapView.clampCamera()
  local w = World.current
  if not w then return end
  local ew, eh = camExtent()
  local margin = 0.15
  cam_.x = clamp(cam_.x, -ew * margin, math.max(-ew * margin, w.cols - ew * (1 - margin)))
  cam_.y = clamp(cam_.y, -eh * margin, math.max(-eh * margin, w.rows - eh * (1 - margin)))
end

-- 以屏幕某点为锚缩放
function MapView.zoomAt(factor, anchorX, anchorY)
  local oldCell = MAP.baseCell * cam_.scale
  local newCell = clamp(oldCell * factor, MAP.baseCell * MAP.minScale, MAP.baseCell * MAP.maxScale)
  if newCell == oldCell then return end
  local wx, wy = screenToWorld(anchorX, anchorY)
  cam_.scale = newCell / MAP.baseCell
  local cell = MAP.baseCell * cam_.scale
  cam_.x = wx - anchorX / cell
  cam_.y = wy - anchorY / cell
  MapView.clampCamera()
end

function MapView.zoomCentered(factor)
  MapView.zoomAt(factor, view_.w / 2, view_.h / 2)
end

function MapView.setCenterTile(tx, ty)
  local ew, eh = camExtent()
  cam_.x = tx - ew / 2
  cam_.y = ty - eh / 2
  MapView.clampCamera()
end

function MapView.selectTile(tx, ty)
  local w = World.current
  if not w then return end
  tx = clamp(tx, 1, w.cols); ty = clamp(ty, 1, w.rows)
  selected_ = { x = tx, y = ty }
  if onTileChanged_ then onTileChanged_(selected_) end
end

function MapView.getSelection()
  return selected_
end

function MapView.clearSelection()
  selected_ = nil
end

function MapView.setToast(msg)
  toast_ = { msg = msg, t = 0 }
end

function MapView.setViewport(w, h, topInset, bottomInset)
  view_.w, view_.h = w, h
  topInset_ = topInset or topInset_
  bottomInset_ = bottomInset or bottomInset_
end

function MapView.setTileChangedCallback(fn)
  onTileChanged_ = fn
end

-- ---------------------------------------------------------------------------
-- 初始化
-- ---------------------------------------------------------------------------
function MapView.init(context)
  vg_ = context
  -- 游戏字体：站酷快乐体（活泼圆润，随包发布）；缺字回退 MiSans/Twemoji
  fontId_ = nvgCreateFont(vg_, "game", "Fonts/ZCOOLKuaiLe-Regular.ttf")
  if fontId_ == -1 then
    print("[MapView] WARN: game font missing, fallback to MiSans")
    fontId_ = nvgCreateFont(vg_, "game", "Fonts/MiSans-Regular.ttf")
  end
  if fontId_ ~= -1 then
    pcall(function()
      local misans = nvgCreateFont(vg_, "misans", "Fonts/MiSans-Regular.ttf")
      if misans ~= -1 then nvgAddFallbackFontId(vg_, fontId_, misans) end
      local twemoji = nvgCreateFont(vg_, "twemoji", "Fonts/Twemoji.Mozilla.ttf")
      if twemoji ~= -1 then nvgAddFallbackFontId(vg_, fontId_, twemoji) end
    end)
    print("[MapView] font ready id=" .. tostring(fontId_))
  else
    print("[MapView] ERROR: all fonts load failed")
  end
end

function MapView.dispose()
  vg_ = nil
end

-- 居中到出生城区（拉近视角，第一眼看到成片街区）
function MapView.resetCamera()
  cam_.scale = 1.3
  MapView.setCenterTile((World.current.spawnX or 8) + 1.5, (World.current.spawnY or 8) + 1)
end

-- ---------------------------------------------------------------------------
-- 每帧输入（由 main 在 Update 中调用）
-- ---------------------------------------------------------------------------
function MapView.setTool(tool)
  tool_ = tool
end

function MapView.getTool()
  return tool_
end

-- 工具笔刷：在格子 tx,ty 应用当前工具。返回是否成功、消息
function MapView.applyTool(tx, ty)
  if not tool_ then return false end
  if not World.inBounds(tx, ty) then return false end
  if tool_.kind == "road" then
    local ok, msg = GameDataRef.placeRoad(tx, ty, tool_.roadKind)
    if ok then Sfx.play("sfx_click", 0.5) elseif msg then MapView.setToast(msg) end
    return ok
  elseif tool_.kind == "zone" then
    local ok, msg = GameDataRef.paintZone(tx, ty, tool_.zoneKey)
    if ok then Sfx.play("sfx_click", 0.35) elseif msg then MapView.setToast(msg) end
    return ok
  elseif tool_.kind == "bulldoze" then
    local ok = GameDataRef.bulldoze(tx, ty)
    if ok then Sfx.play("sfx_demolish", 0.6) end
    return ok
  elseif tool_.kind == "service" then
    local ok, msg = GameDataRef.placeService(tool_.id, tx, ty)
    if ok then Sfx.play("sfx_build") elseif msg then MapView.setToast(msg) end
    return ok
  end
  return false
end

-- 当前工具下，格子 tx,ty 是否可放置（幽灵预览颜色）
function MapView.toolValidAt(tx, ty)
  if not tool_ or not World.inBounds(tx, ty) then return false end
  if tool_.kind == "road" then
    local ok = World.canRoad(tx, ty)
    return ok
  elseif tool_.kind == "zone" then
    local t = World.tile(tx, ty)
    return t ~= nil and not t.road and not t.building and t.terrain ~= "water"
  elseif tool_.kind == "bulldoze" then
    local t = World.tile(tx, ty)
    return t ~= nil and (t.building ~= nil or t.road ~= nil)
  elseif tool_.kind == "service" then
    local ok = World.canPlaceService(tool_.id, tx, ty)
    return ok
  end
  return false
end

-- ---------------------------------------------------------------------------
-- 动态车辆：沿路网行驶，路口随机转向，道路被拆则消失
-- ---------------------------------------------------------------------------
local cars_ = {}
local CAR_COLORS = {
  { 242, 240, 236, 255 }, { 198, 92, 78, 255 }, { 96, 128, 182, 255 },
  { 234, 194, 88, 255 }, { 134, 170, 134, 255 }, { 96, 98, 104, 255 },
}
local DIRS = { { 1, 0 }, { 0, 1 }, { -1, 0 }, { 0, -1 } }   -- 右 下 左 上
local CARS_TARGET = 26

local function isRoadCell(x, y)
  local t = World.tile(x, y)
  return t ~= nil and t.road ~= nil
end

local function spawnCar()
  local w = World.current
  if not w then return end
  for _ = 1, 40 do
    local x = math.random(2, w.cols - 1)
    local y = math.random(2, w.rows - 1)
    if isRoadCell(x, y) then
      local opts = {}
      for d, v in ipairs(DIRS) do
        if isRoadCell(x + v[1], y + v[2]) then opts[#opts + 1] = d end
      end
      if #opts > 0 then
        cars_[#cars_ + 1] = {
          x = x, y = y,
          dir = opts[math.random(#opts)],
          prog = math.random() * 0.6,
          speed = 1.4 + math.random() * 1.8,   -- 格/秒
          color = CAR_COLORS[math.random(#CAR_COLORS)],
        }
        return
      end
    end
  end
end

-- 反向：DIRS 环形索引 d 的对面
local function oppDir(d)
  return ((d + 1) % 4) + 1
end

function MapView.updateCars(dt)
  local w = World.current
  if not w then return end
  while #cars_ < CARS_TARGET do
    spawnCar()
  end
  for i = #cars_, 1, -1 do
    local c = cars_[i]
    if not isRoadCell(c.x, c.y) then
      table.remove(cars_, i)   -- 路被拆了
    else
      c.prog = c.prog + c.speed * dt
      while c.prog >= 1 do
        c.prog = c.prog - 1
        local v = DIRS[c.dir]
        c.x, c.y = c.x + v[1], c.y + v[2]
        local function ok(d)
          local vv = DIRS[d]
          return isRoadCell(c.x + vv[1], c.y + vv[2])
        end
        if not ok(c.dir) then
          local rev = oppDir(c.dir)
          local opts = {}
          for d = 1, 4 do
            if d ~= rev and ok(d) then opts[#opts + 1] = d end
          end
          c.dir = (#opts > 0) and opts[math.random(#opts)] or rev
        elseif math.random() < 0.18 then
          -- 十字路口偶尔转弯
          local rev = oppDir(c.dir)
          local opts = {}
          for d = 1, 4 do
            if d ~= rev and d ~= c.dir and ok(d) then opts[#opts + 1] = d end
          end
          if #opts > 0 then c.dir = opts[math.random(#opts)] end
        end
      end
    end
  end
end

-- 双指捏合（旧轮询实现已废弃，见 updatePinch）

-- ---------------------------------------------------------------------------
-- 事件驱动指针（与 UI 库同一事件通道，触摸/鼠标都可靠）
--   坐标一律为物理像素，使用时除以 dpr
-- ---------------------------------------------------------------------------
local ptr_ = { down = false, x = 0, y = 0, touchId = -1 }
local touches_ = {}                -- touchId -> {x, y}（物理像素），捏合用
local pressQueued_, releaseQueued_ = false, false

function MapView.onTouchBegin(id, x, y)
  touches_[id] = { x = x, y = y }
  if ptr_.touchId == -1 then
    ptr_.touchId = id
    ptr_.down = true
    ptr_.x, ptr_.y = x, y
    pressQueued_ = true
  end
end

function MapView.onTouchMove(id, x, y)
  local t = touches_[id]
  if t then t.x, t.y = x, y end
  if id == ptr_.touchId then
    ptr_.x, ptr_.y = x, y
  end
end

function MapView.onTouchEnd(id, x, y)
  touches_[id] = nil
  if id == ptr_.touchId then
    if x then ptr_.x, ptr_.y = x, y end
    ptr_.touchId = -1
    ptr_.down = false
    releaseQueued_ = true
  end
end

function MapView.onMouseDown(x, y)
  if ptr_.touchId ~= -1 then return end
  ptr_.down = true
  ptr_.x, ptr_.y = x, y
  pressQueued_ = true
end

function MapView.onMouseUp()
  if ptr_.touchId ~= -1 then return end
  ptr_.down = false
  releaseQueued_ = true
end

-- 双指捏合（帧间用 touches_ 表计算）
local pinchDist0_, pinchScale0_, pinchActive_ = 0, 1, false

local function updatePinch(dpr)
  local n = 0
  local a, b
  for _, t in pairs(touches_) do
    n = n + 1
    if not a then a = t else b = t end
  end
  if n >= 2 and a and b then
    local dist = math.sqrt((a.x - b.x) ^ 2 + (a.y - b.y) ^ 2)
    if not pinchActive_ then
      if dist >= 16 then
        pinchActive_ = true
        pinchDist0_ = dist
        pinchScale0_ = cam_.scale
        drag_.active = false
      end
    elseif pinchDist0_ > 0 then
      local mx = (a.x + b.x) * 0.5 / dpr
      local my = (a.y + b.y) * 0.5 / dpr
      local target = clamp(pinchScale0_ * (dist / pinchDist0_), MAP.minScale, MAP.maxScale)
      MapView.zoomAt((MAP.baseCell * target) / (MAP.baseCell * cam_.scale), mx, my)
    end
  else
    pinchActive_ = false
  end
  return pinchActive_
end

function MapView.handleInput(timeStep)
  if not World.current then return end
  local dpr = graphics:GetDPR()

  -- 滚轮缩放（PC）
  if input.mouseMoveWheel ~= 0 then
    local p = input.mousePosition
    MapView.zoomAt(input.mouseMoveWheel > 0 and 1.15 or 0.87, p.x / dpr, p.y / dpr)
  end

  -- 捏合优先：两指期间冻结拖拽/工具
  if updatePinch(dpr) then
    pressQueued_ = false
    releaseQueued_ = false
    MapView.updateCars(timeStep)
    if toast_ then
      toast_.t = toast_.t + timeStep
      if toast_.t > 3.2 then toast_ = nil end
    end
    return
  end

  local mx, my = ptr_.x / dpr, ptr_.y / dpr
  local lastTX, lastTY = tileUnderCursor(mx, my)

  -- 按下：UI 命中则放弃；否则启动拖拽/工具
  if pressQueued_ then
    pressQueued_ = false
    local uiConsumed = ptr_.touchId ~= -1 and UI.IsPointerOverUI()
    local inMap = (not uiConsumed) and my > topInset_ and my < view_.h - bottomInset_
    if inMap then
      if tool_ then
        drag_.active = true
        drag_.mode = "tool"
        drag_.moved = true
        drag_.lastTile = lastTX and (lastTX .. "," .. lastTY) or nil
        if lastTX then MapView.applyTool(lastTX, lastTY) end
      else
        drag_.active = true
        drag_.mode = "pan"
        drag_.moved = false
        drag_.sx, drag_.sy = mx, my
        drag_.cx, drag_.cy = cam_.x, cam_.y
      end
    end
  end

  -- 拖拽跟踪
  if drag_.active and ptr_.down then
    if drag_.mode == "tool" then
      if lastTX then
        local key = lastTX .. "," .. lastTY
        if key ~= drag_.lastTile then
          drag_.lastTile = key
          MapView.applyTool(lastTX, lastTY)
        end
      end
    elseif drag_.mode == "pan" then
      local dx, dy = mx - drag_.sx, my - drag_.sy
      if math.abs(dx) > 3 or math.abs(dy) > 3 then drag_.moved = true end
      if drag_.moved then
        local cell = MAP.baseCell * cam_.scale
        cam_.x = drag_.cx - dx / cell
        cam_.y = drag_.cy - dy / cell
        MapView.clampCamera()
      end
    end
  end

  -- 松开
  if releaseQueued_ then
    releaseQueued_ = false
    if drag_.active and drag_.mode == "pan" and not drag_.moved then
      if lastTX and World.inBounds(lastTX, lastTY) then
        MapView.selectTile(lastTX, lastTY)
      end
    end
    if drag_.active and drag_.mode == "tool" then
      if onTileChanged_ then onTileChanged_(selected_) end
    end
    drag_.active = false
    drag_.mode = nil
    drag_.moved = false
    drag_.lastTile = nil
  end

  -- 悬停/幽灵预览（指针位置持续更新）
  if mx > 0 and my > topInset_ and my < view_.h - bottomInset_ then
    local tx, ty = MapView.tileAt(mx, my)
    if World.inBounds(tx, ty) then
      hover_ = { x = tx, y = ty }
    else
      hover_ = nil
    end
  else
    hover_ = nil
  end

  -- toast 计时
  if toast_ then
    toast_.t = toast_.t + timeStep
    if toast_.t > 3.2 then toast_ = nil end
  end

  -- 车辆更新
  MapView.updateCars(timeStep)
end

-- ---------------------------------------------------------------------------
-- 渲染
-- ---------------------------------------------------------------------------
local function drawText(sx, sy, size, color, text, align, alpha)
  nvgFontFaceId(vg_, fontId_)
  nvgFontSize(vg_, size)
  if align == "center" then
    nvgTextAlign(vg_, NVG_ALIGN_CENTER + NVG_ALIGN_MIDDLE)
  elseif align == "left" then
    nvgTextAlign(vg_, NVG_ALIGN_LEFT + NVG_ALIGN_MIDDLE)
  else
    nvgTextAlign(vg_, NVG_ALIGN_RIGHT + NVG_ALIGN_MIDDLE)
  end
  nvgFillColor(vg_, rgba(color, alpha or 255))
  nvgText(vg_, sx, sy, text, nil)
end

-- 文本宽度测量（需先设置 font/size/align）
-- nvgTextBounds 返回：advance(number), bounds{ xmin,ymin,xmax,ymax }(table|nil)
local function measure(text)
  local advance, bounds = nvgTextBounds(vg_, 0, 0, text, nil)
  if type(advance) == "number" then return advance end
  if type(bounds) == "table" then
    local xmin = tonumber(bounds[1]) or 0
    local xmax = tonumber(bounds[3]) or 0
    return xmax - xmin
  end
  return 0
end

local function drawRoundRectPath(x, y, w, h, r)
  nvgBeginPath(vg_)
  nvgRoundedRect(vg_, x, y, w, h, r)
end

-- 地形/分区底色
local function zoneBaseColor(t, x, y)
  if t.road then
    return t.road.key == "avenue" and C.roadAvenue or C.roadLocal
  end
  if t.zone == "residential" then return C.zoneResidential end
  if t.zone == "commercial" then return C.zoneCommercial end
  if t.zone == "industrial" then return C.zoneIndustrial end
  if t.terrain == "water" then
    return ((x + y) % 2 == 0) and C.water or C.waterAlt
  end
  if t.terrain == "forest" then return C.forest end
  if t.terrain == "hill" then return C.hill end
  if t.terrain == "plain" then return C.plain end
  return ((x + y) % 2 == 0) and C.grass or C.grassAlt
end

-- 建筑颜色（按类别）
local function buildingColor(b)
  local cat = b.cat
  if cat == "residential" then return C.bResidential end
  if cat == "commercial" then return C.bCommercial end
  if cat == "industrial" then return C.bIndustrial end
  if b.id == "park" then return C.bService end
  return C.bCivic
end

-- 微立体高度系数
local function buildingHeight(b)
  local h = 0.35
  if b.id == "tower" then h = 1.6
  elseif b.id == "mall" or b.id == "techpark" or b.id == "stadium" then h = 0.95
  elseif b.id == "cityhall" or b.id == "hospital" then h = 0.85
  elseif b.id == "factory" then h = 0.55
  elseif b.id == "park" then h = 0.06
  elseif b.id == "school" then h = 0.6
  elseif b.id == "market" then h = 0.5
  elseif b.id == "villa" then h = 0.3
  end
  return h
end

function MapView.render()
  if not vg_ or not World.current then return end
  local w = World.current

  nvgBeginFrame(vg_, view_.w, view_.h, graphics:GetDPR())
  nvgScissor(vg_, 0, 0, view_.w, view_.h)

  -- 地图外底色
  nvgBeginPath(vg_)
  nvgRect(vg_, 0, 0, view_.w, view_.h)
  nvgFillColor(vg_, rgba(C.uiBackdrop))
  nvgFill(vg_)

  local cell = MAP.baseCell * cam_.scale
  local x0, x1, y0, y1 = visibleRange()

  -- ---- 1) 底色 ----
  for ty = y0, y1 do
    for tx = x0, x1 do
      local t = w.grid[ty][tx]
      local sx, sy = worldToScreen(tx - 1, ty - 1)
      nvgBeginPath(vg_)
      nvgRect(vg_, sx, sy, cell + 0.5, cell + 0.5)
      nvgFillColor(vg_, rgba(zoneBaseColor(t, tx, ty)))
      nvgFill(vg_)
    end
  end

  -- ---- 1.5) 地物装饰：树冠 / 山形 / 水纹（确定性散布，坐标决定形状） ----
  if cell >= 6 then
    for ty = y0, y1 do
      for tx = x0, x1 do
        local t = w.grid[ty][tx]
        if not t.building and not t.road then
          local sx, sy = worldToScreen(tx - 1, ty - 1)
          if t.terrain == "forest" then
            local n = 2 + ((tx * 7 + ty * 13) % 2)
            for k = 0, n - 1 do
              local ox = ((tx * 31 + ty * 17 + k * 37) % 100) / 100
              local oy = ((tx * 13 + ty * 29 + k * 53) % 100) / 100
              local r = cell * (0.10 + ((tx + k * 5 + ty) % 3) * 0.025)
              nvgBeginPath(vg_)
              nvgCircle(vg_, sx + cell * (0.20 + ox * 0.60), sy + cell * (0.20 + oy * 0.60), r)
              nvgFillColor(vg_, rgba({ 96, 138, 94, 235 }))
              nvgFill(vg_)
            end
          elseif t.terrain == "hill" and cell >= 10 then
            local cxp, cyp = sx + cell * 0.5, sy + cell * 0.64
            nvgBeginPath(vg_)
            nvgMoveTo(vg_, cxp - cell * 0.28, cyp)
            nvgLineTo(vg_, cxp, cyp - cell * 0.36)
            nvgLineTo(vg_, cxp + cell * 0.28, cyp)
            nvgClosePath(vg_)
            nvgFillColor(vg_, rgba({ 142, 152, 124, 255 }))
            nvgFill(vg_)
          elseif t.terrain == "water" and cell >= 10 then
            nvgBeginPath(vg_)
            nvgMoveTo(vg_, sx + cell * 0.15, sy + cell * 0.5)
            nvgLineTo(vg_, sx + cell * 0.85, sy + cell * 0.5)
            nvgStrokeWidth(vg_, math.max(1, cell * 0.05))
            nvgStrokeColor(vg_, rgba({ 255, 255, 255, 42 }))
            nvgStroke(vg_)
          end
        end
      end
    end
  end

  -- ---- 2) 分区格缝浅线（弱网格感，只有 zoom 够大才画） ----
  if cell >= 8 then
    nvgStrokeWidth(vg_, 0.5)
    nvgStrokeColor(vg_, rgba({ 255, 255, 255, 22 }))
    nvgBeginPath(vg_)
    for ty = y0, y1 do
      for tx = x0, x1 do
        local t = w.grid[ty][tx]
        if t.zone ~= "none" and not t.road and not t.building then
          local sx, sy = worldToScreen(tx - 1, ty - 1)
          nvgRect(vg_, sx, sy, cell, cell)
        end
      end
    end
    nvgStroke(vg_)
  end

  -- ---- 3) 道路中心虚线（大道黄线） ----
  if cell >= 10 then
    nvgStrokeWidth(vg_, math.max(1, cell * 0.06))
    nvgStrokeColor(vg_, rgba({ 190, 160, 80, 160 }))
    nvgBeginPath(vg_)
    for ty = y0, y1 do
      for tx = x0, x1 do
        local t = w.grid[ty][tx]
        if t.road and t.road.key == "avenue" then
          local sx, sy = worldToScreen(tx - 1, ty - 1)
          nvgMoveTo(vg_, sx, sy + cell * 0.5)
          nvgLineTo(vg_, sx + cell, sy + cell * 0.5)
        end
      end
    end
    nvgStroke(vg_)
  end

  -- ---- 3.5) 车辆 ----
  if cell >= 9 then
    for _, c in ipairs(cars_) do
      local v = DIRS[c.dir]
      local sx, sy = worldToScreen(c.x - 1 + v[1] * c.prog + 0.5,
                                   c.y - 1 + v[2] * c.prog + 0.5)
      if sx > -cell and sy > -cell and sx < view_.w + cell and sy < view_.h + cell then
        local horiz = (c.dir == 1 or c.dir == 3)
        local L = cell * 0.46
        local W = cell * 0.30
        local rx, ry, rw, rh
        if horiz then rx, ry, rw, rh = sx - L / 2, sy - W / 2, L, W
        else rx, ry, rw, rh = sx - W / 2, sy - L / 2, W, L end
        local rad = math.max(1.5, cell * 0.08)
        -- 阴影
        drawRoundRectPath(rx + 1, ry + 1.5, rw, rh, rad)
        nvgFillColor(vg_, rgba({ 60, 70, 60, 60 }))
        nvgFill(vg_)
        -- 车身
        drawRoundRectPath(rx, ry, rw, rh, rad)
        nvgFillColor(vg_, rgba(c.color))
        nvgFill(vg_)
        -- 车窗（朝行进方向）
        nvgFillColor(vg_, rgba({ 70, 84, 96, 255 }))
        nvgBeginPath(vg_)
        if horiz then
          local wx = (c.dir == 1) and (rx + rw * 0.55) or (rx + rw * 0.18)
          nvgRect(vg_, wx, ry + rh * 0.18, rw * 0.27, rh * 0.64)
        else
          local wy = (c.dir == 2) and (ry + rh * 0.55) or (ry + rh * 0.18)
          nvgRect(vg_, rx + rw * 0.18, wy, rw * 0.64, rh * 0.27)
        end
        nvgFill(vg_)
      end
    end
  end

  -- ---- 4) 建筑（成长楼 + 服务设施；微立体 + 生长动画） ----
  local function drawBox(rx, ry, rw, rh, hpx, base)
    local pad = math.max(1.5, cell * 0.09)
    rx, ry = rx + pad, ry + pad
    rw, rh = rw - pad * 2, rh - pad * 2
    if hpx > 1.5 and cell >= 6 then
      -- 侧面
      nvgBeginPath(vg_)
      nvgMoveTo(vg_, rx, ry + rh)
      nvgLineTo(vg_, rx, ry - hpx)
      nvgLineTo(vg_, rx + rw, ry - hpx)
      nvgLineTo(vg_, rx + rw, ry + rh)
      nvgClosePath(vg_)
      nvgFillColor(vg_, rgba(shade(base, Config.BUILD.sideShade)))
      nvgFill(vg_)
      -- 窗户
      if cell >= 18 and hpx >= cell * 0.4 then
        local cols = math.max(1, math.floor(rw / (cell * 0.30)))
        local rows = math.max(1, math.floor((hpx + rh) / (cell * 0.30)) - 1)
        nvgFillColor(vg_, rgba(shade(base, 0.52)))
        for wi = 0, cols - 1 do
          for wj = 0, rows - 1 do
            local wx = rx + (rw - cols * cell * 0.30) * 0.5 + wi * cell * 0.30 + cell * 0.06
            local wy = ry + rh - cell * 0.22 - wj * cell * 0.30
            if wy > ry - hpx + cell * 0.05 then
              nvgBeginPath(vg_)
              nvgRect(vg_, wx, wy - cell * 0.08, cell * 0.11, cell * 0.13)
              nvgFill(vg_)
            end
          end
        end
      end
      -- 顶面
      drawRoundRectPath(rx, ry - hpx, rw, rh, math.min(2.5, cell * 0.1))
      nvgFillColor(vg_, rgba(shade(base, Config.BUILD.roofLight)))
      nvgFill(vg_)
      nvgStrokeWidth(vg_, math.max(0.5, cell * 0.02))
      nvgStrokeColor(vg_, rgba(shade(base, 0.55)))
      nvgStroke(vg_)
    else
      drawRoundRectPath(rx, ry, rw, rh, math.min(2.5, cell * 0.12))
      nvgFillColor(vg_, rgba(base))
      nvgFill(vg_)
    end
    -- 地面投影
    nvgBeginPath(vg_)
    nvgRect(vg_, rx + 1, ry + rh - 1, rw - 2, math.max(1.5, cell * 0.08))
    nvgFillColor(vg_, rgba({ 60, 70, 60, 46 }))
    nvgFill(vg_)
  end

  local GROWN_H = { 0.30, 0.55, 0.95 }
  local ZONE_BCOLOR = {
    residential = C.bResidential, commercial = C.bCommercial, industrial = C.bIndustrial,
  }
  for ty = y0, y1 do
    for tx = x0, x1 do
      local t = w.grid[ty][tx]
      local bl = t.building
      if bl then
        local isService = bl.service ~= nil
        local anchor = (not isService) or (bl.ax == tx and bl.ay == ty)
        if anchor then
          local sx, sy = worldToScreen((bl.ax or tx) - 1, (bl.ay or ty) - 1)
          local bw, bh = cell * (bl.w or 1), cell * (bl.h or 1)
          local base, hFactor
          if isService then
            local sc = World.serviceConfig(bl.service)
            if bl.service == "park" or bl.service == "plaza" then
              base, hFactor = C.bService, 0.08
            else
              base, hFactor = C.bCivic, 0.5
            end
          else
            base = ZONE_BCOLOR[bl.zone] or C.bResidential
            hFactor = GROWN_H[bl.level] or 0.3
          end
          -- 生长动画：新建建筑从 30% 弹到 100%
          local anim = 1
          if not isService and bl.born then
            local age = Growth.simTime - bl.born
            if age < 0.6 then
              local k = math.max(0, age / 0.6)
              anim = 0.25 + 0.75 * (k * k * (3 - 2 * k))
            end
          end
          local hpx = clamp(bw * 0.4 * hFactor * anim, 0, cell * 2.6)
          -- 占地也随动画缩放（从中心缩）
          local shrink = (anim - 1) * bw * 0.5
          drawBox(sx - shrink, sy - shrink * (bh / bw), bw + shrink * 2, bh + shrink * 2 * (bh / bw), hpx, base)
        end
      end
    end
  end

  -- ---- 5) 标签（POI / 路名 / 建筑名） ----
  local labelAlpha = clamp((cell - 7) / 6, 0, 1)
  if labelAlpha > 0.05 and fontId_ ~= -1 then
    -- 路名：沿大道
    if cell >= 14 then
      for _, line in ipairs(w.roadLines) do
        if line.kind == "avenue" then
          local lx, ly
          if line.dir == "v" then
            local sy = line.labelY or math.floor(w.rows / 2)
            lx, ly = worldToScreen(line.seg[1].x - 0.5, sy - 0.5)
            if lx > -80 and lx < view_.w + 80 and ly > topInset_ - 40 and ly < view_.h then
              -- 竖排：按 UTF-8 逐字
              local chars = {}
              for ch in line.name:gmatch("[\1-\127\194-\244][\128-\191]*") do table.insert(chars, ch) end
              for i, ch in ipairs(chars) do
                drawText(lx, ly + (i - 1) * (cell * 0.85), cell * 0.62,
                  { 130, 120, 90, 255 }, ch, "center", 215)
              end
            end
          else
            local sx = line.labelX or math.floor(w.cols / 2)
            lx, ly = worldToScreen(sx - 0.5, line.seg[1].y - 0.5)
            if ly > topInset_ - 20 and ly < view_.h + 20 and lx > -160 and lx < view_.w + 160 then
              drawText(lx, ly, cell * 0.62, { 130, 120, 90, 255 }, line.name, "center", 215)
            end
          end
        end
      end
    end
    -- 建筑名：仅服务设施（成长楼不标名，都市天际线式干净街区）
    if cell >= 16 then
      nvgFontFaceId(vg_, fontId_)
      nvgTextAlign(vg_, NVG_ALIGN_CENTER + NVG_ALIGN_MIDDLE)
      for ty = y0, y1 do
        for tx = x0, x1 do
          local t = w.grid[ty][tx]
          local bl = t.building
          if bl and bl.service and bl.ax == tx and bl.ay == ty then
            local sc = World.serviceConfig(bl.service)
            if sc then
              local sx, sy = worldToScreen(bl.ax - 1 + bl.w / 2, bl.ay - 1 + bl.h / 2)
              local chars = 0
              for _ in sc.name:gmatch("[\1-\127\194-\244][\128-\191]*") do chars = chars + 1 end
              local fs = math.min(cell * 0.5, cell * bl.w * 0.92 / chars)
              drawText(sx, sy, fs, { 60, 76, 58, 255 }, sc.name, "center", 235)
            end
          end
        end
      end
    end
    -- 预置 POI 蓝标签（仿参考图 "一小""一初"）
    for _, lb in ipairs(w.labels) do
      local sx, sy = worldToScreen(lb.x - 0.5, lb.y - 0.5)
      if sx > -60 and sx < view_.w + 60 and sy > topInset_ - 30 and sy < view_.h + 30 then
        local pad = cell * 0.18
        local fs = cell * 0.5
        nvgFontFaceId(vg_, fontId_)
        nvgFontSize(vg_, fs)
        nvgTextAlign(vg_, NVG_ALIGN_CENTER + NVG_ALIGN_MIDDLE)
        local tw = measure(lb.text)
        drawRoundRectPath(sx - tw / 2 - pad, sy - fs / 2 - pad * 0.7, tw + pad * 2, fs + pad * 1.4, 2)
        nvgFillColor(vg_, rgba(lb.kind == "red" and C.accentRed or C.accentBlue))
        nvgFill(vg_)
        drawText(sx, sy, fs, { 255, 255, 255, 255 }, lb.text, "center", 255)
      end
    end
  end

  -- ---- 6) 悬停 / 选中框 / 工具幽灵预览 ----
  -- 工具激活时：悬停格显示 绿/红 幽灵 + 服务范围圈
  if tool_ and hover_ then
    local gx, gy = worldToScreen(hover_.x - 1, hover_.y - 1)
    local ok = MapView.toolValidAt(hover_.x, hover_.y)
    local gw, gh = cell, cell
    if tool_.kind == "service" then
      local sc = World.serviceConfig(tool_.id)
      if sc then
        gw, gh = cell * sc.size[1], cell * sc.size[2]
        -- 影响范围圈
        local cx, cy = worldToScreen(hover_.x - 1 + sc.size[1] / 2, hover_.y - 1 + sc.size[2] / 2)
        nvgBeginPath(vg_)
        nvgCircle(vg_, cx, cy, cell * (sc.radius + 0.5))
        nvgFillColor(vg_, rgba({ 96, 200, 140, 26 }))
        nvgFill(vg_)
        nvgStrokeWidth(vg_, 1)
        nvgStrokeColor(vg_, rgba({ 96, 200, 140, 90 }))
        nvgStroke(vg_)
      end
    end
    nvgBeginPath(vg_)
    nvgRect(vg_, gx, gy, gw, gh)
    nvgFillColor(vg_, rgba(ok and C.ghostOk or C.ghostBad))
    nvgFill(vg_)
    nvgStrokeWidth(vg_, 1.5)
    nvgStrokeColor(vg_, rgba(ok and { 110, 220, 140, 230 } or { 220, 90, 80, 230 }))
    nvgStroke(vg_)
  elseif hover_ then
    local hx, hy = worldToScreen(hover_.x - 1, hover_.y - 1)
    local t = World.tile(hover_.x, hover_.y)
    local cellW = cell * (t and t.building and t.building.w or 1)
    local cellH = cell * (t and t.building and t.building.h or 1)
    if t and t.building then
      hx = hx + (t.building.ax - hover_.x) * cell
      hy = hy + (t.building.ay - hover_.y) * cell
    end
    nvgBeginPath(vg_)
    nvgRect(vg_, hx, hy, cellW, cellH)
    nvgStrokeWidth(vg_, 1.5)
    nvgStrokeColor(vg_, rgba(C.hoverStroke))
    nvgStroke(vg_)
  end
  if selected_ then
    local t = World.tile(selected_.x, selected_.y)
    local sxp, syp = worldToScreen(selected_.x - 1, selected_.y - 1)
    local sw, sh = cell, cell
    if t and t.building then
      sxp = sxp + (t.building.ax - selected_.x) * cell
      syp = syp + (t.building.ay - selected_.y) * cell
      sw = cell * t.building.w
      sh = cell * t.building.h
    end
    nvgBeginPath(vg_)
    nvgRect(vg_, sxp, syp, sw, sh)
    nvgFillColor(vg_, rgba(C.selectFill))
    nvgFill(vg_)
    nvgStrokeWidth(vg_, 2)
    nvgStrokeColor(vg_, rgba(C.selectStroke))
    nvgStroke(vg_)
  end

  -- ---- 7) Toast（红字气泡） ----
  if toast_ and fontId_ ~= -1 then
    local alpha = toast_.t > 2.6 and clamp((3.2 - toast_.t) / 0.6, 0, 1) or 1
    local fs = 12
    nvgFontFaceId(vg_, fontId_)
    nvgFontSize(vg_, fs)
    nvgTextAlign(vg_, NVG_ALIGN_CENTER + NVG_ALIGN_MIDDLE)
    local tw = measure(toast_.msg)
    local px, py = view_.w / 2, view_.h - bottomInset_ - 30
    drawRoundRectPath(px - tw / 2 - 10, py - fs, tw + 20, fs * 2 + 6, 8)
    nvgFillColor(vg_, rgba({ 250, 250, 248, 235 }, alpha))
    nvgFill(vg_)
    nvgStrokeWidth(vg_, 1)
    nvgStrokeColor(vg_, rgba(C.accentRed, 200 * alpha))
    nvgStroke(vg_)
    drawText(px, py + 3, fs, { 176, 66, 66, 255 }, toast_.msg, "center", 255 * alpha)
  end

  nvgResetScissor(vg_)
  nvgEndFrame(vg_)
end

return MapView
