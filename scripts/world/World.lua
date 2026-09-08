-- ============================================================================
-- World — 世界模型（格子数据 + 规则）
--   建筑分两类：
--     grown   = { zone="residential", level=1..3, born=模拟时刻 }  分区自动生长
--     service = { service="park", ax, ay, w, h }                    玩家放置
-- ============================================================================

local Config = require("Config")

local World = {}

local MAP = Config.MAP

-- ---------------------------------------------------------------------------
-- 确定性值噪声
-- ---------------------------------------------------------------------------
local function makeNoise(seed)
  local s = seed or 1337
  local function hash(ix, iy)
    local n = ix * 374761393 + iy * 668265263 + s * 1442695040888963407 % 2147483647
    n = (n ~ (n >> 13)) * 1274126177
    n = n ~ (n >> 16)
    return (n & 0x7fffffff) / 0x7fffffff
  end
  local function smooth(t) return t * t * (3 - 2 * t) end
  local function noise(x, y)
    local x0, y0 = math.floor(x), math.floor(y)
    local tx, ty = smooth(x - x0), smooth(y - y0)
    local v00, v10 = hash(x0, y0),     hash(x0 + 1, y0)
    local v01, v11 = hash(x0, y0 + 1), hash(x0 + 1, y0 + 1)
    local a = v00 + (v10 - v00) * tx
    local b = v01 + (v11 - v01) * tx
    return a + (b - a) * ty
  end
  local function fbm(x, y, oct)
    local amp, freq, sum, norm = 1, 1, 0, 0
    for _ = 1, (oct or 3) do
      sum = sum + noise(x * freq, y * freq) * amp
      norm = norm + amp
      amp = amp * 0.5
      freq = freq * 2
    end
    return sum / norm
  end
  return { noise = noise, fbm = fbm }
end

local STREET_PRE = { "晨光", "望江", "振兴", "青年", "碧湖", "桂香", "和平", "解放", "文昌", "梧桐",
                     "朝阳", "临江", "锦绣", "长虹", "育才", "银杏" }
local STREET_SUF = { "大道", "大街", "路", "街", "南路", "北路", "东路", "西路" }

local function pickName(_, i)
  return STREET_PRE[(i % #STREET_PRE) + 1] .. STREET_SUF[((i * 7 + 3) % #STREET_SUF) + 1]
end

-- ---------------------------------------------------------------------------
-- 新建空白世界
-- ---------------------------------------------------------------------------
function World.new()
  local w = {
    cols = MAP.cols,
    rows = MAP.rows,
    grid = {},       -- grid[y][x] = { terrain, zone, road, building }
    elev = {},
    labels = {},     -- POI 标签（纯装饰，开局城市无，留空）
    roadLines = {},  -- 大道名称信息
  }
  for y = 1, w.rows do
    w.grid[y] = {}
    w.elev[y] = {}
    for x = 1, w.cols do
      w.grid[y][x] = { terrain = "grass", zone = "none", road = nil, building = nil }
      w.elev[y][x] = 100
    end
  end
  return w
end

World.current = nil

-- ---------------------------------------------------------------------------
-- 程序化生成底图：地形 + 河湖 + 大道骨架（地方道路由玩家自己修）
-- ---------------------------------------------------------------------------
function World.generate(seed)
  local w = World.new()
  local nz = makeNoise(seed)

  for y = 1, w.rows do
    for x = 1, w.cols do
      local h = nz.fbm(x * 0.09, y * 0.09, 4)
      w.elev[y][x] = math.floor(20 + h * 260)
    end
  end

  -- 河流
  local riverX = math.floor(w.cols * 0.72)
  for y = 1, w.rows do
    local rx = riverX + math.floor(nz.noise(y * 0.12, 5.5) * 6 - 3)
    local width = 2 + math.floor(nz.noise(y * 0.08, 9.1) * 2)
    for dx = -width, width do
      local x = rx + dx
      if x >= 1 and x <= w.cols then
        w.grid[y][x].terrain = "water"
        w.elev[y][x] = 8
      end
    end
  end

  -- 湖泊
  local lakeX, lakeY, lakeR = riverX - 4, math.floor(w.rows * 0.30), 5
  for y = math.max(1, lakeY - lakeR), math.min(w.rows, lakeY + lakeR) do
    for x = math.max(1, lakeX - lakeR), math.min(w.cols, lakeX + lakeR) do
      if (x - lakeX) ^ 2 + (y - lakeY) ^ 2 <= lakeR * lakeR then
        w.grid[y][x].terrain = "water"
        w.elev[y][x] = 6
      end
    end
  end

  -- 林地 / 丘陵 / 平原
  for y = 1, w.rows do
    for x = 1, w.cols do
      local t = w.grid[y][x]
      if t.terrain ~= "water" then
        local f = nz.fbm(x * 0.15 + 40, y * 0.15 + 40, 3)
        if f > 0.66 then
          t.terrain = "forest"
        elseif w.elev[y][x] > 200 then
          t.terrain = "hill"
        elseif f < 0.34 then
          t.terrain = "plain"
        end
      end
    end
  end

  -- 大道骨架（CSL 的"高速路连接"）：3 纵 3 横，免费，跨河直铺
  local function setRoad(x, y, kind)
    local t = w.grid[y][x]
    t.road = { key = kind }
    t.zone = "none"
  end

  local colAve = { math.floor(w.cols * 0.22), math.floor(w.cols * 0.5), riverX + 4 }
  local rowAve = { math.floor(w.rows * 0.20), math.floor(w.rows * 0.55), math.floor(w.rows * 0.82) }

  for i, cx in ipairs(colAve) do
    local seg = {}
    for y = 1, w.rows do
      setRoad(cx, y, "avenue")
      table.insert(seg, { x = cx, y = y })
    end
    table.insert(w.roadLines, { name = pickName(seed, 10 + i * 3), kind = "avenue", seg = seg, dir = "v",
                                labelY = math.floor(w.rows * (0.15 + i * 0.25)) })
  end
  for i, ry in ipairs(rowAve) do
    local seg = {}
    for x = 1, w.cols do
      setRoad(x, ry, "avenue")
      table.insert(seg, { x = x, y = ry })
    end
    table.insert(w.roadLines, { name = pickName(seed, 40 + i * 5), kind = "avenue", seg = seg, dir = "h",
                                labelX = math.floor(w.cols * (0.2 + i * 0.28)) })
  end

  -- 出生点：两条大道交汇附近
  w.spawnX, w.spawnY = colAve[1], rowAve[1]
  World.current = w
  return w
end

-- ---------------------------------------------------------------------------
-- 查询
-- ---------------------------------------------------------------------------
function World.inBounds(x, y)
  local w = World.current
  return x >= 1 and x <= w.cols and y >= 1 and y <= w.rows
end

function World.tile(x, y)
  local w = World.current
  if not w or x < 1 or x > w.cols or y < 1 or y > w.rows then return nil end
  return w.grid[y][x]
end

function World.elevation(x, y)
  local w = World.current
  if not w or x < 1 or x > w.cols or y < 1 or y > w.rows then return 0 end
  return w.elev[y][x]
end

function World.isRoad(x, y)
  local t = World.tile(x, y)
  return t ~= nil and t.road ~= nil
end

function World.terrainName(x, y)
  local t = World.tile(x, y)
  if not t then return "-" end
  local terr = Config.TERRAIN[t.terrain]
  return terr and terr.name or t.terrain
end

function World.zoneName(x, y)
  local t = World.tile(x, y)
  if not t then return "-" end
  if t.road then return Config.ROAD[t.road.key].name end
  if t.building then
    local b = t.building
    if b.service then
      for _, s in ipairs(Config.SERVICES) do
        if s.id == b.service then return s.name end
      end
      return "设施"
    end
    local g = Config.GROWN[b.zone]
    return (g and g.name or "建筑") .. " L" .. b.level
  end
  local z = Config.ZONE[t.zone]
  return z and z.name or "未规划"
end

function World.serviceConfig(id)
  for _, s in ipairs(Config.SERVICES) do
    if s.id == id then return s end
  end
  return nil
end

-- ---------------------------------------------------------------------------
-- 修路
-- ---------------------------------------------------------------------------
function World.canRoad(x, y)
  local t = World.tile(x, y)
  if not t then return false, "越界" end
  if t.terrain == "water" then return false, "不能铺在水上" end
  if t.building then return false, "先拆除这里的建筑" end
  if t.road then return false end   -- 静默跳过（拖拽扫过已建路）
  return true
end

function World.setRoad(x, y, kind)
  local t = World.tile(x, y)
  if not t or t.terrain == "water" or t.building then return false end
  t.road = { key = kind }
  t.zone = "none"
  return true
end

-- ---------------------------------------------------------------------------
-- 分区涂画（只能涂空地；建筑所在格不可改，需先推平）
-- ---------------------------------------------------------------------------
function World.canZone(x, y)
  local t = World.tile(x, y)
  if not t then return false, "越界" end
  if t.terrain == "water" then return false, "水域无法划区" end
  if t.road then return false end       -- 静默跳过
  if t.building then return false end   -- 静默跳过（拖拽体验）
  return true
end

function World.setZone(x, y, zone)
  local t = World.tile(x, y)
  if not t or t.terrain == "water" or t.road or t.building then return false end
  t.zone = zone
  return true
end

-- ---------------------------------------------------------------------------
-- 服务设施放置
-- ---------------------------------------------------------------------------
local function w_tileSet(w, x, y, building)
  w.grid[y][x].building = building
end

function World.canPlaceService(id, x, y)
  local s = World.serviceConfig(id)
  if not s then return false, "未知设施" end
  local sw, sh = s.size[1], s.size[2]
  for yy = y, y + sh - 1 do
    for xx = x, x + sw - 1 do
      local t = World.tile(xx, yy)
      if not t then return false, "超出地图" end
      if t.terrain == "water" then return false, "不能建在水上" end
      if t.road or t.building then return false, "该位置被占用" end
    end
  end
  return true
end

function World.placeService(id, x, y)
  local s = World.serviceConfig(id)
  local sw, sh = s.size[1], s.size[2]
  for yy = y, y + sh - 1 do
    for xx = x, x + sw - 1 do
      w_tileSet(World.current, xx, yy, { service = id, ax = x, ay = y, w = sw, h = sh })
    end
  end
  return true
end

-- ---------------------------------------------------------------------------
-- 成长建筑（由 Growth 引擎调用）
-- ---------------------------------------------------------------------------
function World.growBuilding(zone, x, y, level, born)
  local t = World.tile(x, y)
  if not t or t.building or t.road or t.terrain == "water" then return false end
  t.building = { zone = zone, level = level or 1, born = born or 0 }
  return true
end

function World.upgradeBuilding(x, y)
  local t = World.tile(x, y)
  if not t or not t.building or t.building.service then return false end
  local lv = t.building.level
  local lvDef = Config.GROWN[t.building.zone].levels
  if lv >= #lvDef then return false end
  t.building.level = lv + 1
  return true
end

-- 遍历全部建筑锚点：返回 { {x,y,b=building}, ... }（grown 为逐格，service 只在锚点）
function World.allBuildings()
  local w = World.current
  local out = {}
  for y = 1, w.rows do
    local row = w.grid[y]
    for x = 1, w.cols do
      local b = row[x].building
      if b then
        if b.service then
          if b.ax == x and b.ay == y then out[#out + 1] = { x = x, y = y, b = b } end
        else
          out[#out + 1] = { x = x, y = y, b = b }
        end
      end
    end
  end
  return out
end

-- 推土机：返回 "grown" | "service" | "road" | nil
function World.bulldoze(x, y)
  local t = World.tile(x, y)
  if not t then return nil end
  if t.building then
    local b = t.building
    if b.service then
      for yy = b.ay, b.ay + b.h - 1 do
        for xx = b.ax, b.ax + b.w - 1 do
          World.current.grid[yy][xx].building = nil
        end
      end
      return "service", b.service
    end
    t.building = nil
    return "grown", b.zone
  elseif t.road then
    local kind = t.road.key
    t.road = nil
    return "road", kind
  end
  return nil
end

-- 统计（Growth / GameData 用）
function World.stats()
  local w = World.current
  local resCap, comCap, indCap = 0, 0, 0
  local resCount, comCount, indCount = 0, 0, 0
  local pollution = 0
  local roadCount = 0
  local serviceCount = 0
  for y = 1, w.rows do
    local row = w.grid[y]
    for x = 1, w.cols do
      local t = row[x]
      if t.road then roadCount = roadCount + 1 end
      local b = t.building
      if b then
        if b.service then
          serviceCount = serviceCount + 1
        else
          local lv = Config.GROWN[b.zone].levels[b.level]
          if b.zone == "residential" then
            resCap = resCap + lv.cap
            resCount = resCount + 1
          elseif b.zone == "commercial" then
            comCap = comCap + lv.cap
            comCount = comCount + 1
          else
            indCap = indCap + lv.cap
            indCount = indCount + 1
            pollution = pollution + (lv.pollution or 0)
          end
        end
      end
    end
  end
  return {
    resCap = resCap, comCap = comCap, indCap = indCap,
    resCount = resCount, comCount = comCount, indCount = indCount,
    pollution = pollution, roadCount = roadCount, serviceCount = serviceCount,
  }
end

function World.cityLevel()
  local pop = World._pop or 0
  for i = #Config.CITY_LEVELS, 1, -1 do
    if pop >= Config.CITY_LEVELS[i].popReq then return Config.CITY_LEVELS[i] end
  end
  return Config.CITY_LEVELS[1]
end

return World
