-- ============================================================================
-- 《晨光都市规划》入口
--   竖屏俯视格子城建模拟：NanoVG 地图 + UI 叠层
--   渲染管线：引擎场景 → raw NanoVG(地图) → urhox-libs/UI(叠层)
-- ============================================================================

local UI = require("urhox-libs/UI")
local Config = require("Config")
local GameData = require("GameData")
local World = require("world.World")
local MapView = require("world.MapView")
local MapScreen = require("Screens.MapScreen")
local MainMenu = require("Screens.MainMenu")
local NewspaperScreen = require("Screens.NewspaperScreen")
local Growth = require("world.Growth")
local Sfx = require("Sfx")

local C = Config.COLORS

local uiRoot_ = nil
local vg_ = nil
---@type string
local currentScreen_ = "menu"

-- ---------------------------------------------------------------------------
-- 屏幕导航
-- ---------------------------------------------------------------------------
local Navigator = {}

function Navigator.go(name)
  if not uiRoot_ then return end
  currentScreen_ = name

  local menu = uiRoot_:FindById("screen_mainMenu")
  local map = uiRoot_:FindById("screen_map")
  local paper = uiRoot_:FindById("screen_newspaper")
  if menu then menu:SetVisible(name == "menu") end
  if map then map:SetVisible(name == "map") end
  if paper then paper:SetVisible(name == "newspaper") end

  if name == "map" then
    MapScreen.onShow()
  elseif name == "newspaper" then
    NewspaperScreen.onShow()
  end
  print("[Nav] -> " .. name)
end

-- ---------------------------------------------------------------------------
-- 视口同步（NanoVG 地图占全屏，UI 顶/底栏为遮挡区）
-- ---------------------------------------------------------------------------
local function refreshViewport()
  local dpr = graphics:GetDPR()
  local w, h = graphics:GetWidth() / dpr, graphics:GetHeight() / dpr
  MapView.setViewport(w, h, 70, 88)
end

-- ---------------------------------------------------------------------------
-- Start
-- ---------------------------------------------------------------------------
function Start()
  graphics.windowTitle = Config.TITLE

  -- UI 层（游戏字体：站酷快乐体，缺字由引擎 fallback 机制兜底）
  UI.Init({
    fonts = {
      { family = "sans",
        weights = {
          normal = "Fonts/ZCOOLKuaiLe-Regular.ttf",
          bold = "Fonts/ZCOOLKuaiLe-Regular.ttf",
        } },
    },
    scale = UI.Scale.DEFAULT,
  })

  -- NanoVG 地图层
  vg_ = nvgCreate(1)
  assert(vg_, "nvgCreate failed")
  MapView.init(vg_)

  -- 音效
  Sfx.init()

  -- 世界 + 状态
  GameData.init(20260408)
  MapView.setGameData(GameData)
  MapView.setTileChangedCallback(function()
    MapScreen.refresh()
  end)
  refreshViewport()
  MapView.resetCamera()

  -- UI 树（root 必须透明：raw NanoVG 地图在底层，UI context renderOrder=999990 在上层）
  uiRoot_ = UI.Panel {
    id = "root",
    width = "100%",
    height = "100%",
    backgroundColor = { 0, 0, 0, 0 },
    pointerEvents = "box-none",
    children = {
      NewspaperScreen.create(Navigator),
      MapScreen.create(Navigator),
      MainMenu.create(Navigator),
    },
  }
  UI.SetRoot(uiRoot_)

  -- 事件（必须绑定到 vg_ 上下文：全局订阅会同时命中 UI 内置 NanoVG 上下文的
  -- NanoVGRender 事件，导致地图在 UI 之上重复绘制、盖住叠层，见 examples/01、03）
  SubscribeToEvent(vg_, "NanoVGRender", "HandleNanoVGRender")
  SubscribeToEvent("Update", "HandleUpdate")
  SubscribeToEvent("ScreenMode", function()
    refreshViewport()
  end)
  SubscribeToEvent("KeyDown", "HandleKeyDown")

  -- 地图输入：事件驱动（与 UI 库同通道，触摸平台可靠）
  SubscribeToEvent("TouchBegin", function(_, ed)
    MapView.onTouchBegin(ed:GetInt("TouchID"), ed:GetInt("X"), ed:GetInt("Y"))
  end)
  SubscribeToEvent("TouchMove", function(_, ed)
    MapView.onTouchMove(ed:GetInt("TouchID"), ed:GetInt("X"), ed:GetInt("Y"))
  end)
  SubscribeToEvent("TouchEnd", function(_, ed)
    MapView.onTouchEnd(ed:GetInt("TouchID"), ed:GetInt("X"), ed:GetInt("Y"))
  end)
  SubscribeToEvent("MouseButtonDown", function(_, ed)
    if ed:GetInt("Button") == MOUSEB_LEFT then
      local p = input.mousePosition
      MapView.onMouseDown(p.x, p.y)
    end
  end)
  SubscribeToEvent("MouseButtonUp", function(_, ed)
    if ed:GetInt("Button") == MOUSEB_LEFT then
      MapView.onMouseUp()
    end
  end)

  Navigator.go("menu")
  print("=== " .. Config.TITLE .. " started ===")
end

function Stop()
  UI.Shutdown()
  if vg_ then
    nvgDelete(vg_)
    vg_ = nil
  end
end

-- ---------------------------------------------------------------------------
-- 事件处理
-- ---------------------------------------------------------------------------
---@param eventType string
---@param eventData UpdateEventData
function HandleUpdate(eventType, eventData)
  local timeStep = eventData["TimeStep"]:GetFloat()
  if currentScreen_ == "map" then
    -- 实时模拟：时间流动 → 城市成长 → 地图输入 → UI 节流刷新
    GameData.tick(timeStep)
    Growth.tick(timeStep * GameData.speed(), function(name, gain) Sfx.play(name, gain) end)
    MapView.handleInput(timeStep)
    MapScreen.tick(timeStep)
  end
end

function HandleNanoVGRender(eventType, eventData)
  if currentScreen_ == "map" then
    MapView.render()
  end
end

function HandleKeyDown(eventType, eventData)
  local key = eventData["Key"]:GetInt()
  if key == KEY_ESCAPE then
    if currentScreen_ == "map" then
      -- 有工具先收起工具；否则回菜单
      if MapView.getTool() then
        MapScreen.cancelTool()
      else
        Navigator.go("menu")
      end
    elseif currentScreen_ ~= "menu" then
      Navigator.go("menu")
    end
  end
end
