-- ============================================================================
-- MapScreen — 主界面（都市天际线式）
--   顶部：资金/人口/满意/日期 + RCI 需求条 + 时间速度(暂停/1x/2x/4x) + 简报
--   底部：道路 | 住宅区 | 商业区 | 工业区 | 推平 | 服务 | 查看
--   工具激活时地图上有绿/红幽灵预览；服务有范围圈
-- ============================================================================

local UI = require("urhox-libs/UI")
local Config = require("Config")
local World = require("world.World")
local GameData = require("GameData")
local Growth = require("world.Growth")
local MapView = require("world.MapView")
local UIHelper = require("UIHelper")
local Sfx = require("Sfx")

local MapScreen = {}
local C = Config.COLORS

-- ---------------------------------------------------------------------------
-- 状态
-- ---------------------------------------------------------------------------
local mode_ = "view"            -- view | road | zone | bulldoze | service
local zoneKey_ = "residential"  -- 当前分区色
local roadKind_ = "local"
local selService_ = nil
local serviceOpen_ = false
local roadOpen_ = false
local policyOpen_ = false
local helpOpen_ = false
local tutShown_ = false
local uiTimer_ = 0

-- ---------------------------------------------------------------------------
-- 工具同步
-- ---------------------------------------------------------------------------
local function syncTool()
  if mode_ == "road" then
    MapView.setTool({ kind = "road", roadKind = roadKind_ })
  elseif mode_ == "zone" then
    MapView.setTool({ kind = "zone", zoneKey = zoneKey_ })
  elseif mode_ == "bulldoze" then
    MapView.setTool({ kind = "bulldoze" })
  elseif mode_ == "service" then
    if selService_ then
      MapView.setTool({ kind = "service", id = selService_ })
    else
      MapView.setTool(nil)
    end
  else
    MapView.setTool(nil)
  end
end

local function selectMode(m)
  Sfx.play("sfx_click")
  if mode_ == m and m ~= "zone" then
    mode_ = "view"
    serviceOpen_ = false
    roadOpen_ = false
  else
    mode_ = m
    serviceOpen_ = (m == "service") and (selService_ == nil)
    roadOpen_ = false
  end
  syncTool()
  MapScreen.refresh()
end

local function setZoneKey(zk)
  Sfx.play("sfx_click", 0.6)
  zoneKey_ = zk
  mode_ = "zone"
  serviceOpen_ = false
  syncTool()
  MapScreen.refresh()
end

-- ---------------------------------------------------------------------------
-- 顶部资源条（行1：资源；行2：RCI + 速度）
-- ---------------------------------------------------------------------------
local function buildTopBar()
  return UI.Panel {
    position = "absolute",
    top = 10,
    left = 10,
    right = 10,
    zIndex = 20,
    flexDirection = "column",
    gap = 6,
    children = {
      -- 行1：资源胶囊
      UI.Panel {
        id = "topBarInner",
        flexDirection = "row",
        alignItems = "center",
        justifyContent = "space-between",
        gap = 6,
        padding = { top = 8, bottom = 8, left = 12, right = 8 },
        backgroundColor = C.panelWhite,
        borderRadius = 18,
        boxShadow = { { x = 0, y = 2, blur = 8, spread = 0, color = { 96, 86, 66, 80 }, inset = false } },
        pointerEvents = "auto",
        children = {},
      },
      -- 行2：RCI 需求 + 时间速度
      UI.Panel {
        flexDirection = "row",
        alignItems = "center",
        justifyContent = "space-between",
        gap = 6,
        children = {
          -- RCI 需求条
          UI.Panel {
            flexDirection = "row",
            alignItems = "center",
            gap = 8,
            padding = { top = 6, bottom = 6, left = 12, right = 12 },
            backgroundColor = C.panelWhite,
            borderRadius = 14,
            pointerEvents = "auto",
            children = {
              UI.Panel {
                flexDirection = "row", alignItems = "center", gap = 3,
                children = {
                  UI.Label { text = "住", fontSize = 10, fontColor = C.accentGreen },
                  UI.Panel {
                    id = "rciR", width = 34, height = 6, borderRadius = 3,
                    backgroundColor = { 200, 200, 190, 255 }, children = {
                      UI.Panel { id = "rciRf", width = "50%", height = 6, borderRadius = 3, backgroundColor = C.accentGreen },
                    },
                  },
                },
              },
              UI.Panel {
                flexDirection = "row", alignItems = "center", gap = 3,
                children = {
                  UI.Label { text = "商", fontSize = 10, fontColor = C.accentBlue },
                  UI.Panel {
                    id = "rciC", width = 34, height = 6, borderRadius = 3,
                    backgroundColor = { 200, 200, 190, 255 }, children = {
                      UI.Panel { id = "rciCf", width = "50%", height = 6, borderRadius = 3, backgroundColor = C.accentBlue },
                    },
                  },
                },
              },
              UI.Panel {
                flexDirection = "row", alignItems = "center", gap = 3,
                children = {
                  UI.Label { text = "工", fontSize = 10, fontColor = C.accentGold },
                  UI.Panel {
                    id = "rciI", width = 34, height = 6, borderRadius = 3,
                    backgroundColor = { 200, 200, 190, 255 }, children = {
                      UI.Panel { id = "rciIf", width = "50%", height = 6, borderRadius = 3, backgroundColor = C.accentGold },
                    },
                  },
                },
              },
            },
          },
          -- 速度控制
          UI.Panel {
            id = "speedBar",
            flexDirection = "row",
            alignItems = "center",
            gap = 4,
            padding = { top = 4, bottom = 4, left = 6, right = 6 },
            backgroundColor = C.panelWhite,
            borderRadius = 14,
            pointerEvents = "auto",
            children = {},
          },
        },
      },
    },
  }
end

local function chipEl(idPrefix, label, valueId)
  return UI.Panel {
    flexDirection = "row", alignItems = "center", gap = 3,
    children = {
      UI.Label { text = label, fontSize = 10, fontColor = C.textMid },
      UI.Label { id = valueId, text = "-", fontSize = 13, fontWeight = "bold", fontColor = C.textDark },
    },
  }
end

local function refreshTopBar(root)
  local inner = root:FindById("topBarInner")
  if not inner then return end
  inner:RemoveAllChildren()
  local s = GameData.current
  local level = World.cityLevel()
  inner:AddChild(UIHelper.Chip("", level.name, { bold = true }))
  inner:AddChild(chipEl("f", "资金", "vFunds"))
  inner:AddChild(chipEl("p", "人口", "vPop"))
  inner:AddChild(chipEl("h", "满意", "vHappy"))
  inner:AddChild(chipEl("d", "", "vDate"))
  inner:AddChild(UI.Panel {
    id = "btnPaper",
    padding = { top = 6, bottom = 6, left = 10, right = 10 },
    backgroundColor = C.accentSoftBg, borderRadius = 12,
    justifyContent = "center", alignItems = "center",
    pointerEvents = "auto",
    onClick = function() Sfx.play("sfx_click"); if MapScreen._nav and MapScreen._nav.go then MapScreen._nav.go("newspaper") end end,
    children = { UI.Label { text = "简报", fontSize = 12, fontWeight = "bold", fontColor = C.accentRed } },
  })

  -- 速度按钮
  local sb = root:FindById("speedBar")
  if sb then
    sb:RemoveAllChildren()
    local speeds = { { "‖", 1 }, { "▶", 2 }, { "▶▶", 3 }, { "▶▶▶", 4 } }
    for _, sp in ipairs(speeds) do
      local active = GameData.speedIdx == sp[2]
      sb:AddChild(UI.Panel {
        width = sp[2] == 1 and 34 or 44, height = 26, borderRadius = 9,
        backgroundColor = active and C.accentSoftBg or { 0, 0, 0, 0 },
        justifyContent = "center", alignItems = "center",
        pointerEvents = "auto",
        onClick = function()
          Sfx.play("sfx_click", 0.5)
          GameData.setSpeed(sp[2])
          MapScreen.refresh()
        end,
        children = { UI.Label { text = sp[1], fontSize = 11, fontWeight = "bold",
                                fontColor = active and C.accentRed or C.textMid } },
      })
    end
  end
end

-- 每秒轻量刷新（只改文字/条宽，不重建）
local function refreshLive(root)
  local s = GameData.current
  if not s then return end
  local function setT(id, txt, color)
    local el = root:FindById(id)
    if el and el.SetText then
      el:SetText(txt)
      if color and el.SetStyle then el:SetStyle({ fontColor = color }) end
    end
  end
  setT("vFunds", "¥" .. UIHelper.fmtMoney(s.funds) .. "万", C.accentGold)
  setT("vPop", UIHelper.fmtPop(math.floor(s.population)))
  setT("vHappy", tostring(math.floor(s.happiness)), s.happiness >= 55 and C.accentGreen or C.accentRed)
  setT("vDate", GameData.dateLabel())
  local d = Growth.lastDemand
  for id, v in pairs({ rciRf = d.r, rciCf = d.c, rciIf = d.i }) do
    local el = root:FindById(id)
    if el and el.SetStyle then el:SetStyle({ width = tostring(math.max(4, math.floor(v * 100))) .. "%" }) end
  end
end

-- ---------------------------------------------------------------------------
-- 底部工具栏
-- ---------------------------------------------------------------------------
local function buildBottomBar()
  return UI.Panel {
    id = "toolBarInner",
    position = "absolute",
    bottom = 14,
    left = 10,
    right = 10,
    zIndex = 20,
    flexDirection = "row",
    justifyContent = "space-around",
    alignItems = "center",
    gap = 2,
    padding = { top = 4, bottom = 4, left = 4, right = 4 },
    backgroundColor = C.panelWhite,
    borderRadius = 22,
    boxShadow = { { x = 0, y = 2, blur = 8, spread = 0, color = { 96, 86, 66, 80 }, inset = false } },
    pointerEvents = "auto",
    children = {},
  }
end

local function refreshBottomBar(root)
  local bar = root:FindById("toolBarInner")
  if not bar then return end
  bar:RemoveAllChildren()
  local items = {
    { key = "road",     label = "道路" },
    { key = "zone",     label = "住宅", zone = "residential" },
    { key = "zone",     label = "商业", zone = "commercial" },
    { key = "zone",     label = "工业", zone = "industrial" },
    { key = "bulldoze", label = "推平" },
    { key = "service",  label = "服务" },
    { key = "view",     label = "查看" },
  }
  for _, it in ipairs(items) do
    local active
    if it.key == "zone" then
      active = (mode_ == "zone" and zoneKey_ == it.zone)
    else
      active = (mode_ == it.key)
    end
    bar:AddChild(UIHelper.ToolItem(it.label, {
      active = active,
      width = 44,
      onClick = function()
        if it.key == "zone" then
          if mode_ == "zone" and zoneKey_ == it.zone then
            selectMode("view")
          else
            setZoneKey(it.zone)
          end
        else
          selectMode(it.key)
        end
      end,
    }))
  end
end

-- ---------------------------------------------------------------------------
-- 服务选择抽屉 / 道路类型抽屉
-- ---------------------------------------------------------------------------
local function buildDrawer()
  return UI.Panel {
    id = "drawer",
    position = "absolute",
    bottom = 80,
    left = 10,
    right = 10,
    zIndex = 21,
    visible = false,
  }
end

local function refreshDrawer(root)
  local d = root:FindById("drawer")
  if not d then return end
  local show = (mode_ == "service" and serviceOpen_) or (mode_ == "road" and roadOpen_)
  d:SetVisible(show)
  if not show then return end
  d:RemoveAllChildren()
  local s = GameData.current
  local rows = {}

  if mode_ == "service" then
    table.insert(rows, UI.Label { text = "选择服务设施（放进城区里）", fontSize = 12,
      fontWeight = "bold", fontColor = C.textDark, marginBottom = 6 })
    local chips = {}
    for _, sv in ipairs(Config.SERVICES) do
      table.insert(chips, UIHelper.PickChip(sv.name, "¥" .. sv.cost .. "万", {
        selected = selService_ == sv.id,
        disabled = s.funds < sv.cost,
        subColor = s.funds < sv.cost and C.accentRed or C.textMid,
        width = 76,
        onClick = function()
          if s.funds >= sv.cost then
            selService_ = sv.id
            serviceOpen_ = false
            syncTool()
            MapScreen.refresh()
          else
            MapView.setToast("资金不足")
          end
        end,
      }))
    end
    table.insert(rows, UI.Panel { flexDirection = "row", flexWrap = "wrap", gap = 8, children = chips })
    if selService_ then
      local sc = World.serviceConfig(selService_)
      if sc then
        table.insert(rows, UI.Label { text = sc.desc .. " 覆盖半径 " .. sc.radius .. " 格。",
          fontSize = 10, fontColor = C.textMid, marginTop = 6 })
      end
    end
  elseif mode_ == "road" then
    table.insert(rows, UI.Label { text = "道路类型", fontSize = 12, fontWeight = "bold", fontColor = C.textDark, marginBottom = 6 })
    local chips = {}
    for _, rk in ipairs({ "local", "avenue" }) do
      local r = Config.ROAD[rk]
      table.insert(chips, UIHelper.PickChip(r.name, "¥" .. r.cost .. "/格", {
        selected = roadKind_ == rk, width = 90,
        onClick = function() roadKind_ = rk; syncTool(); roadOpen_ = false; MapScreen.refresh() end,
      }))
    end
    table.insert(rows, UI.Panel { flexDirection = "row", gap = 8, children = chips })
    table.insert(rows, UI.Label { text = "按住拖拽连续修路；分区内邻路才会长楼。",
      fontSize = 10, fontColor = C.textFaint, marginTop = 4 })
  end

  d:AddChild(UIHelper.Card(rows, {
    width = "100%", padding = { top = 12, bottom = 12, left = 14, right = 14 }, radius = 16,
    alignItems = "stretch",
  }))
end

-- ---------------------------------------------------------------------------
-- 政策 / 帮助
-- ---------------------------------------------------------------------------
local function buildPolicyPanel()
  return UI.Panel {
    id = "policyPanel",
    position = "absolute", top = 0, left = 0, right = 0, bottom = 0,
    zIndex = 25, justifyContent = "center", alignItems = "center",
    visible = false, pointerEvents = "auto",
  }
end

local function refreshPolicy(root)
  local p = root:FindById("policyPanel")
  if not p then return end
  p:SetVisible(policyOpen_)
  if not policyOpen_ then return end
  p:RemoveAllChildren()
  local s = GameData.current
  local rows = {
    UI.Label { text = "市政政策", fontSize = 16, fontWeight = "bold", fontColor = C.textDark,
               marginBottom = 8, textAlign = "center", width = "100%" },
  }
  for _, pol in ipairs(Config.POLICIES) do
    local cd = s.policyCooldowns[pol.id] or 0
    local active = false
    for _, ap in ipairs(s.activePolicies) do if ap.id == pol.id then active = true end end
    table.insert(rows, UI.Panel {
      width = "100%", flexDirection = "row", justifyContent = "space-between", alignItems = "center",
      padding = { top = 8, bottom = 8, left = 12, right = 12 },
      backgroundColor = C.chipBg, borderRadius = 12, borderWidth = 1, borderColor = C.border2,
      marginBottom = 8, pointerEvents = "auto",
      onClick = function()
        local ok, msg = GameData.activatePolicy(pol.id)
        if not ok then MapView.setToast(msg or "无法启用") else Sfx.play("sfx_click") end
        MapScreen.refresh()
      end,
      children = {
        UI.Panel {
          flexDirection = "column", flex = 1,
          children = {
            UI.Label { text = pol.name, fontSize = 13, fontWeight = "bold", fontColor = C.textDark },
            UI.Label { text = pol.desc, fontSize = 10, fontColor = C.textMid, marginTop = 2 },
          },
        },
        UI.Label {
          text = active and "生效中" or (cd > 0 and cd .. "天" or "启用"),
          fontSize = 11, fontColor = active and C.accentGreen or (cd > 0 and C.textFaint or C.accentRed),
          marginLeft = 8,
        },
      },
    })
  end
  table.insert(rows, UI.Panel {
    width = "100%", height = 40, borderRadius = 20, backgroundColor = C.accentGreen,
    justifyContent = "center", alignItems = "center", marginTop = 4, pointerEvents = "auto",
    onClick = function() policyOpen_ = false; MapScreen.refresh() end,
    children = { UI.Label { text = "关闭", fontSize = 13, fontWeight = "bold", fontColor = { 255, 255, 255, 255 } } },
  })
  p:AddChild(UI.Panel {
    position = "absolute", top = 0, left = 0, right = 0, bottom = 0,
    backgroundColor = C.veil,
    onClick = function() policyOpen_ = false; MapScreen.refresh() end,
    pointerEvents = "auto",
  })
  p:AddChild(UIHelper.Card(rows, {
    width = "88%", maxWidth = 420, padding = { top = 16, bottom = 16, left = 14, right = 14 },
    radius = 18, alignItems = "stretch",
  }))
end

local function buildPolicyBtn()
  return UI.Panel {
    position = "absolute", left = 14, bottom = 84, zIndex = 22,
    children = {
      UIHelper.RoundButton("策", { id = "policyBtn", size = 40, fontSize = 15,
        onClick = function() Sfx.play("sfx_click", 0.6); policyOpen_ = not policyOpen_; MapScreen.refresh() end }),
    },
  }
end

local function buildHelpBtn()
  return UI.Panel {
    position = "absolute", left = 14, bottom = 134, zIndex = 22,
    children = {
      UIHelper.RoundButton("?", { id = "helpBtn", size = 40, fontSize = 20,
        onClick = function() Sfx.play("sfx_click", 0.6); helpOpen_ = not helpOpen_; MapScreen.refresh() end }),
    },
  }
end

local function buildHelpPanel()
  return UI.Panel {
    id = "helpPanel",
    position = "absolute", top = 0, left = 0, right = 0, bottom = 0,
    zIndex = 30, justifyContent = "center", alignItems = "center",
    visible = false, pointerEvents = "auto",
  }
end

local function helpRow(no, text)
  return UI.Panel {
    width = "100%", flexDirection = "row", alignItems = "flex-start", gap = 8, marginBottom = 6,
    children = {
      UI.Label { text = no, fontSize = 12, fontWeight = "bold", fontColor = C.accentRed },
      UI.Label { text = text, fontSize = 12, fontColor = C.textDark, flex = 1, lineHeight = 1.5 },
    },
  }
end

local function refreshHelp(root)
  local p = root:FindById("helpPanel")
  if not p then return end
  p:SetVisible(helpOpen_)
  if not helpOpen_ then return end
  p:RemoveAllChildren()
  local rows = {
    UI.Label { text = "新手指引 · 像都市天际线一样建城", fontSize = 15, fontWeight = "bold",
               fontColor = C.textDark, textAlign = "center", width = "100%", marginBottom = 8 },
    helpRow("操作", "单指拖动平移；双指捏合或右侧 +/− 缩放；点格子查看信息。"),
    helpRow("一步", "选【道路】，从大道边按住拖拽修路——分区内只有邻路的格子才会长楼。"),
    helpRow("二步", "点【住宅/商业/工业】激活分区笔刷，在路旁涂色（每格少量花费）。"),
    helpRow("三步", "时间自动流动：需求条（顶部住/商/工）越高，对应分区越快自动长楼、小楼升高楼。"),
    helpRow("四步", "【服务】放公园/学校进城区提升满意度和地价；【推平】拆楼拆路。"),
    helpRow("五步", "人口达标城市晋级：村庄→小镇→集镇→城区→都市→大都会。注意收支别破产。"),
    UI.Panel {
      width = "100%", height = 42, borderRadius = 21, backgroundColor = C.accentGreen,
      justifyContent = "center", alignItems = "center", marginTop = 6, pointerEvents = "auto",
      onClick = function() helpOpen_ = false; MapScreen.refresh() end,
      children = { UI.Label { text = "开始建设", fontSize = 14, fontWeight = "bold", fontColor = { 255, 255, 255, 255 } } },
    },
  }
  p:AddChild(UI.Panel {
    position = "absolute", top = 0, left = 0, right = 0, bottom = 0,
    backgroundColor = C.veil,
    onClick = function() helpOpen_ = false; MapScreen.refresh() end,
    pointerEvents = "auto",
  })
  p:AddChild(UIHelper.Card(rows, {
    width = "90%", maxWidth = 420, padding = { top = 18, bottom = 18, left = 18, right = 18 },
    radius = 18, alignItems = "stretch",
  }))
end

-- ---------------------------------------------------------------------------
-- 信息卡（查看模式）
-- ---------------------------------------------------------------------------
local function buildInfoCard()
  return UI.Panel {
    id = "infoCard",
    position = "absolute", bottom = 88, left = 10, right = 10,
    zIndex = 20, alignItems = "center", visible = false,
  }
end

local function refreshInfoCard(root)
  local card = root:FindById("infoCard")
  if not card then return end
  local sel = MapView.getSelection()
  if not sel or mode_ ~= "view" then
    card:SetVisible(false)
    return
  end
  card:SetVisible(true)
  card:RemoveAllChildren()
  local t = World.tile(sel.x, sel.y)
  local rows = {
    UI.Label { text = "(" .. sel.x .. ", " .. sel.y .. ")  " .. GameData.dateLabel(),
               fontSize = 13, fontWeight = "bold", fontColor = C.textDark, marginBottom = 4 },
    UIHelper.InfoRow("海拔", World.elevation(sel.x, sel.y) .. "m"),
    UIHelper.InfoRow("地形", World.terrainName(sel.x, sel.y)),
    UIHelper.InfoRow("现状", World.zoneName(sel.x, sel.y), { color = C.accentBlue }),
  }
  card:AddChild(UIHelper.Card(rows, {
    alignItems = "stretch", width = "100%", padding = { top = 10, bottom = 10, left = 16, right = 16 }, radius = 16,
  }))
end

-- ---------------------------------------------------------------------------
-- 组装
-- ---------------------------------------------------------------------------
function MapScreen.create(Navigator)
  MapScreen._nav = Navigator
  return UI.Panel {
    id = "screen_map",
    width = "100%",
    height = "100%",
    visible = false,
    pointerEvents = "box-none",
    children = {
      buildTopBar(),
      buildBottomBar(),
      buildInfoCard(),
      buildDrawer(),
      buildPolicyBtn(),
      buildHelpBtn(),
      buildPolicyPanel(),
      buildHelpPanel(),
    },
  }
end

function MapScreen.refresh()
  local root = UI.GetRoot()
  if not root then return end
  local mapScreen = root:FindById("screen_map")
  if not mapScreen or not mapScreen:IsVisible() then return end
  refreshTopBar(root)
  refreshBottomBar(root)
  refreshInfoCard(root)
  refreshDrawer(root)
  refreshPolicy(root)
  refreshHelp(root)
  refreshLive(root)
end

-- 每帧调用：节流刷新实时数值；处理晋级/翻月提示
function MapScreen.tick(dt)
  uiTimer_ = uiTimer_ + dt
  if uiTimer_ >= 0.5 then
    uiTimer_ = 0
    local root = UI.GetRoot()
    if root then
      local mapScreen = root:FindById("screen_map")
      if mapScreen and mapScreen:IsVisible() then
        refreshLive(root)
      end
    end
  end
  if GameData.pendingLevelUp then
    GameData.pendingLevelUp = false
    Sfx.play("sfx_levelup")
    MapView.setToast("城市晋级 " .. World.cityLevel().name .. "！")
  elseif GameData.monthFlash then
    GameData.monthFlash = false
    Sfx.play("sfx_month")
    MapView.setToast(GameData.monthLabel() .. " 月度结算完成")
  end
end

function MapScreen.onShow()
  syncTool()
  MapView.setViewport(graphics:GetWidth() / graphics:GetDPR(),
                       graphics:GetHeight() / graphics:GetDPR(), 70, 88)
  if not tutShown_ then
    helpOpen_ = true
    tutShown_ = true
  end
  MapScreen.refresh()
end

function MapScreen.cancelTool()
  mode_ = "view"
  serviceOpen_ = false
  roadOpen_ = false
  syncTool()
  MapScreen.refresh()
end

return MapScreen
