-- ============================================================================
-- GameData — 实时城市模拟（都市天际线式）
--   时间持续流动：tick(dt) 由主循环每帧调用，内部按速度倍率推进
--   每游戏日小额收支；每 30 日自动翻月 → 结算新闻 / 政策倒计时 / 晋级检查
-- ============================================================================

local Config = require("Config")
local World = require("world.World")

local GameData = {}

local T = Config.TIME
local E = Config.ECONOMY

---@class PolicyActive
---@field id string
---@field daysLeft integer

---@class NewsItem
---@field month string
---@field headline string
---@field body string
---@field tag string

---@class CityState
---@field year integer
---@field month integer
---@field day integer
---@field funds number
---@field population number
---@field happiness number
---@field totalIncome number
---@field totalSpent number
---@field activePolicies PolicyActive[]
---@field policyCooldowns table<string, integer>
---@field news NewsItem[]

-- ---------------------------------------------------------------------------
-- 状态
-- ---------------------------------------------------------------------------
local function createState()
  ---@type CityState
  local st = {
    year = 2026, month = 4, day = 1,
    funds = Config.RESOURCES.funds.start,
    population = 0,
    happiness = Config.RESOURCES.happiness.start,
    totalIncome = 0,
    totalSpent = 0,
    activePolicies = {},
    policyCooldowns = {},
    news = {},
  }
  return st
end

---@type CityState
GameData.current = nil

GameData.speedIdx = 2        -- 默认 1x
GameData.pendingLevelUp = false
GameData.monthFlash = false

local dayAcc_ = 0

function GameData.init(seed)
  World.generate(seed or 20260408)
  GameData.current = createState()
  World._pop = 0
  GameData.speedIdx = 2
  GameData.pendingLevelUp = false
  GameData.monthFlash = false
  dayAcc_ = 0
  GameData.pushNews("城市奠基",
    Config.WORLD.city .. "迎来新任" .. Config.WORLD.playerRole ..
    "。沿大道修路、划分区，城市将随时间自然生长。", "头条")
end

function GameData.reset(seed)
  GameData.init(seed)
end

-- ---------------------------------------------------------------------------
-- 时间与速度
-- ---------------------------------------------------------------------------
function GameData.speed()
  return T.speeds[GameData.speedIdx] or 1
end

function GameData.setSpeed(idx)
  if T.speeds[idx] then GameData.speedIdx = idx end
end

function GameData.dateLabel()
  local s = GameData.current
  return string.format("%04d.%02d.%02d", s.year, s.month, s.day)
end

function GameData.monthLabel()
  local s = GameData.current
  return string.format("%04d.%02d", s.year, s.month)
end

-- ---------------------------------------------------------------------------
-- 政策效果聚合
-- ---------------------------------------------------------------------------
local function policyMul(key)
  local m = 1
  local s = GameData.current
  for _, ap in ipairs(s.activePolicies) do
    for _, p in ipairs(Config.POLICIES) do
      if p.id == ap.id and p.effect[key] then
        m = m * p.effect[key]
      end
    end
  end
  return m
end

-- ---------------------------------------------------------------------------
-- 每日结算
-- ---------------------------------------------------------------------------
local serviceCache_ = nil

local function computeHappinessTarget(st)
  -- 基础 + 服务覆盖（覆盖的成长楼越多加成越高，封顶）+ 污染惩罚
  local target = 52
  local w = World.current
  for _, e in ipairs(World.allBuildings()) do
    local b = e.b
    if b.service then
      local cfg = World.serviceConfig(b.service)
      if cfg then
        local covered = 0
        for dy = -cfg.radius, cfg.radius do
          for dx = -cfg.radius, cfg.radius do
            local t = World.tile(e.x + dx, e.y + dy)
            if t and t.building and not t.building.service then covered = covered + 1 end
          end
        end
        target = target + cfg.happy * math.min(1.2, covered / 14)
      end
    end
  end
  target = target - st.pollution * E.pollutionHappy
  return math.max(Config.RESOURCES.happiness.min, math.min(Config.RESOURCES.happiness.max, target))
end

local function onNewDay()
  local s = GameData.current
  s.day = s.day + 1

  local st = World.stats()
  s.pollution = st.pollution

  -- 人口向"容量×占用率"靠拢（占用率受满意度驱动）
  local occTarget = math.floor(st.resCap * math.max(0.25, math.min(1, s.happiness / 100)))
  if s.population < occTarget then
    s.population = math.min(occTarget, s.population + math.max(1, math.floor((occTarget - s.population) * E.occupancyPerDay)))
  else
    s.population = math.max(occTarget, s.population - math.max(1, math.floor((s.population - occTarget) * 0.15)))
  end
  World._pop = s.population

  -- 收支（万/日）
  local occRatio = st.resCap > 0 and (s.population / st.resCap) or 0
  local bizIncome = 0
  for _, e in ipairs(World.allBuildings()) do
    local b = e.b
    if not b.service then
      local lv = Config.GROWN[b.zone].levels[b.level]
      if b.zone ~= "residential" then
        bizIncome = bizIncome + lv.income * occRatio
      end
    end
  end
  local income = (s.population * E.taxPerPopPerDay + E.baseIncomePerDay) * policyMul("taxMul")
               + bizIncome * (policyMul("incomeMul") - 1)
  local upkeep = st.roadCount * E.upkeepPerRoadDay
  for _, e in ipairs(World.allBuildings()) do
    if e.b.service then
      local cfg = World.serviceConfig(e.b.service)
      if cfg then upkeep = upkeep + cfg.upkeep / 30 end
    end
  end
  local net = income - upkeep
  s.funds = s.funds + net
  if net >= 0 then s.totalIncome = s.totalIncome + net else s.totalSpent = s.totalSpent - net end

  -- 满意度向目标靠拢
  local target = computeHappinessTarget(st)
  s.happiness = s.happiness + (target - s.happiness) * 0.10

  -- 政策倒计时
  for i = #s.activePolicies, 1, -1 do
    s.activePolicies[i].daysLeft = s.activePolicies[i].daysLeft - 1
    if s.activePolicies[i].daysLeft <= 0 then
      table.remove(s.activePolicies, i)
    end
  end
  for id, cd in pairs(s.policyCooldowns) do
    if cd > 0 then s.policyCooldowns[id] = cd - 1 end
  end

  -- 晋级检查
  local level = World.cityLevel()
  if not s._lastLevel then s._lastLevel = level.level end
  if level.level > s._lastLevel then
    s._lastLevel = level.level
    GameData.pendingLevelUp = true
    GameData.pushNews("城市晋级 " .. level.name .. "！",
      string.format("人口达到 %d，晨光市升级为%s。", s.population, level.name), "头条")
  end

  -- 翻月
  if s.day > 30 then
    s.day = 1
    s.month = s.month + 1
    if s.month > 12 then
      s.month = 1
      s.year = s.year + 1
    end
    GameData.monthFlash = true
    local net30 = net >= 0 and "+" or ""
    GameData.pushNews(GameData.monthLabel() .. " 城建月报",
      string.format("人口 %d · 满意度 %d · 本日收支 %s%.1f万 · 建筑 %d 栋",
        s.population, math.floor(s.happiness), net30, net, st.resCount + st.comCount + st.indCount + st.serviceCount),
      "月报")
  end
end

-- ---------------------------------------------------------------------------
-- 主 tick（dt = 真实秒；内部乘速度）
-- ---------------------------------------------------------------------------
function GameData.tick(dt)
  local s = GameData.current
  if not s then return end
  local simDt = dt * GameData.speed()
  if simDt <= 0 then return end   -- 暂停

  dayAcc_ = dayAcc_ + simDt
  while dayAcc_ >= T.daySeconds do
    dayAcc_ = dayAcc_ - T.daySeconds
    onNewDay()
  end
end

-- ---------------------------------------------------------------------------
-- 操作（由 MapView.applyTool 调用；全部返回 ok, msg）
-- ---------------------------------------------------------------------------
function GameData.placeRoad(x, y, kind)
  local ok, msg = World.canRoad(x, y)
  if not ok then return false, msg end
  local r = Config.ROAD[kind]
  local s = GameData.current
  if s.funds < r.cost then return false, "资金不足（需 ¥" .. r.cost .. "万）" end
  World.setRoad(x, y, kind)
  s.funds = s.funds - r.cost
  return true
end

function GameData.paintZone(x, y, zoneKey)
  if zoneKey == "none" then
    local t = World.tile(x, y)
    if t and t.zone ~= "none" then
      World.setZone(x, y, "none")
      return true
    end
    return true
  end
  local ok = World.setZone(x, y, zoneKey)
  if not ok then return true end   -- 静默跳过建筑/道路
  local s = GameData.current
  s.funds = s.funds - Config.ZONE[zoneKey].cost
  if s.funds < 0 then
    -- 资金不足：回滚
    World.setZone(x, y, "none")
    s.funds = s.funds + Config.ZONE[zoneKey].cost
    return false, "资金不足"
  end
  return true
end

function GameData.bulldoze(x, y)
  local kind, id = World.bulldoze(x, y)
  if not kind then return false end
  local s = GameData.current
  if kind == "service" then
    local cfg = World.serviceConfig(id)
    if cfg then s.funds = s.funds + math.floor(cfg.cost * 0.3) end
    GameData.pushNews("拆除设施", "退还部分造价。", "城建")
  elseif kind == "grown" then
    s.funds = s.funds + 1
  elseif kind == "road" then
    s.funds = s.funds + 2
  end
  return true
end

function GameData.placeService(id, x, y)
  local ok, msg = World.canPlaceService(id, x, y)
  if not ok then return false, msg end
  local cfg = World.serviceConfig(id)
  local s = GameData.current
  if s.funds < cfg.cost then return false, "资金不足（需 ¥" .. cfg.cost .. "万）" end
  World.placeService(id, x, y)
  s.funds = s.funds - cfg.cost
  GameData.pushNews(cfg.name .. " 建成",
    string.format("在 (%d,%d) 建成 %s，耗资 %d万。", x, y, cfg.name, cfg.cost), "城建")
  return true
end

function GameData.activatePolicy(pid)
  local s = GameData.current
  if (s.policyCooldowns[pid] or 0) > 0 then return false, "冷却中" end
  for _, ap in ipairs(s.activePolicies) do
    if ap.id == pid then return false, "已生效" end
  end
  local p
  for _, q in ipairs(Config.POLICIES) do if q.id == pid then p = q end end
  if not p then return false, "未知政策" end
  if p.effect.cost and s.funds < p.effect.cost then return false, "资金不足" end
  s.activePolicies[#s.activePolicies + 1] = { id = pid, daysLeft = p.effect.days or p.cooldown }
  s.policyCooldowns[pid] = p.cooldown
  if p.effect.happy then
    s.happiness = math.max(0, math.min(100, s.happiness + p.effect.happy))
  end
  if p.effect.cost then s.funds = s.funds - p.effect.cost end
  GameData.pushNews("新政发布：" .. p.name, p.desc, "政策")
  return true
end

-- ---------------------------------------------------------------------------
-- 新闻
-- ---------------------------------------------------------------------------
function GameData.pushNews(headline, body, tag)
  local s = GameData.current
  s.news[#s.news + 1] = { month = GameData.dateLabel(), headline = headline, body = body, tag = tag or "快讯" }
  if #s.news > 60 then table.remove(s.news, 1) end
end

function GameData.recentNews(count)
  local s = GameData.current
  count = count or 8
  local out = {}
  for i = math.max(1, #s.news - count + 1), #s.news do
    out[#out + 1] = s.news[i]
  end
  return out
end

return GameData
