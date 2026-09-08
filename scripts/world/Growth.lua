-- ============================================================================
-- Growth — 城市成长引擎（都市天际线核心循环）
--   分区内、邻路的空地，按 RCI 需求自动长出建筑；
--   已有成长楼在需求高 + 地价高（服务覆盖）时升级 1→3 级
-- ============================================================================

local Config = require("Config")
local World = require("world.World")

local Growth = {}

local G = Config.GROWTH

local acc_ = 0

Growth.simTime = 0          -- 累计模拟时间（秒，含速度倍率），生长动画用
Growth.lastDemand = { r = 0.5, c = 0.3, i = 0.4 }
Growth.grewCount = 0        -- 本次 tick 长了几栋（UI 提示/音效用）

function Growth.reset()
  acc_ = 0
  Growth.simTime = 0
  Growth.grewCount = 0
end

-- RCI 需求（0..1），由 GameData 传入统计
function Growth.computeDemand(st, pop)
  local normR = math.max(st.resCap + 40, 1)
  local normC = math.max(st.comCap + 30, 1)
  local normI = math.max(st.indCap + 40, 1)
  local r = (st.comCap + st.indCap) * 1.15 + 40 - pop      -- 岗位缺口 → 住宅需求
  local c = pop * 0.55 - st.comCap                          -- 消费缺口 → 商业需求
  local i = pop * 0.45 + 50 - st.indCap                     -- 外部订单 → 工业需求
  return {
    r = math.max(0, math.min(1, r / normR)),
    c = math.max(0, math.min(1, c / normC)),
    i = math.max(0, math.min(1, i / normI)),
  }
end

-- 一次成长判定
local function growthStep(sfx)
  local w = World.current
  if not w then return end
  local st = World.stats()
  local pop = World._pop or 0
  local demand = Growth.computeDemand(st, pop)
  Growth.lastDemand = demand

  -- 按需求权重选本步要生长的类型
  local pool = {}
  if demand.r >= G.demandMin then pool[#pool + 1] = { z = "residential", w = demand.r } end
  if demand.c >= G.demandMin then pool[#pool + 1] = { z = "commercial", w = demand.c } end
  if demand.i >= G.demandMin then pool[#pool + 1] = { z = "industrial", w = demand.i } end
  Growth.grewCount = 0
  if #pool == 0 then return end

  -- 收集候选空地：分区匹配 + 邻路
  local want = {}
  for _, p in ipairs(pool) do want[p.z] = true end
  local candidates = {}
  for y = 2, w.rows - 1 do
    local row = w.grid[y]
    for x = 2, w.cols - 1 do
      local t = row[x]
      if want[t.zone] and not t.building and not t.road and t.terrain ~= "water" then
        if World.isRoad(x + 1, y) or World.isRoad(x - 1, y)
        or World.isRoad(x, y + 1) or World.isRoad(x, y - 1) then
          candidates[#candidates + 1] = { x = x, y = y, zone = t.zone }
        end
      end
    end
  end
  if #candidates > 0 and math.random() < G.spawnChance then
    local pick = candidates[math.random(#candidates)]
    if World.growBuilding(pick.zone, pick.x, pick.y, 1, Growth.simTime) then
      Growth.grewCount = Growth.grewCount + 1
      if sfx then sfx("sfx_build", 0.35) end
    end
  end

  -- 升级判定：需求高 + 概率；服务覆盖（地价）提高概率
  local upCandidates = {}
  for _, e in ipairs(World.allBuildings()) do
    local b = e.b
    if not b.service and b.level < #Config.GROWN[b.zone].levels then
      upCandidates[#upCandidates + 1] = e
    end
  end
  if #upCandidates > 0 and math.random() < G.upgradeChance then
    local e = upCandidates[math.random(#upCandidates)]
    local zoneDemand = e.b.zone == "residential" and demand.r
                    or (e.b.zone == "commercial" and demand.c or demand.i)
    if zoneDemand >= G.demandMin and math.random() < zoneDemand then
      World.upgradeBuilding(e.x, e.y)
    end
  end
end

---@param dt number 已含速度倍率的模拟时间（秒）
---@param sfx fun(name: string, gain: number)|nil
function Growth.tick(dt, sfx)
  Growth.simTime = Growth.simTime + dt
  acc_ = acc_ + dt
  if acc_ >= G.tickSeconds then
    acc_ = acc_ - G.tickSeconds
    growthStep(sfx)
  end
end

return Growth
