-- ============================================================================
-- NewspaperScreen — 城市简报（新闻回顾 flavor，纯 UI）
-- ============================================================================

local UI = require("urhox-libs/UI")
local Config = require("Config")
local GameData = require("GameData")
local World = require("world.World")

local NewspaperScreen = {}
local C = Config.COLORS

local function newsCard(n)
  local urgent = n.tag == "头条"
  return UI.Panel {
    width = "100%",
    flexDirection = "column",
    padding = { top = 10, bottom = 10, left = 12, right = 12 },
    backgroundColor = C.chipBg,
    borderRadius = 12,
    borderWidth = 1,
    borderColor = urgent and C.accentRed or C.border2,
    borderLeftWidth = 3,
    borderLeftColor = urgent and C.accentRed or C.accentGreen,
    marginBottom = 8,
    children = {
      UI.Panel {
        flexDirection = "row", justifyContent = "space-between", width = "100%",
        children = {
          UI.Label { text = n.tag or "快讯", fontSize = 10, fontWeight = "bold",
                     fontColor = urgent and C.accentRed or C.accentGreen },
          UI.Label { text = n.month, fontSize = 10, fontColor = C.textFaint },
        },
      },
      UI.Label { text = n.headline, fontSize = 14, fontWeight = "bold", fontColor = C.textDark, marginTop = 2 },
      UI.Label { text = n.body, fontSize = 11, fontColor = C.textMid, lineHeight = 1.5, marginTop = 2 },
    },
  }
end

function NewspaperScreen.create(Navigator)
  return UI.Panel {
    id = "screen_newspaper",
    width = "100%",
    height = "100%",
    flexDirection = "column",
    visible = false,
    backgroundColor = C.uiBackdrop,
    pointerEvents = "auto",
    children = {
      -- 顶栏
      UI.Panel {
        flexDirection = "row",
        alignItems = "center",
        justifyContent = "space-between",
        width = "100%",
        padding = { top = 12, bottom = 12, left = 14, right = 14 },
        children = {
          UI.Panel {
            id = "paperBack",
            flexDirection = "row", alignItems = "center", gap = 4,
            padding = { top = 8, bottom = 8, left = 14, right = 14 },
            backgroundColor = C.panelWhite, borderRadius = 18, pointerEvents = "auto",
            onClick = function() Navigator.go("map") end,
            boxShadow = { { x = 0, y = 2, blur = 8, color = { 90, 100, 90, 80 } } },
            children = {
              UI.Label { text = "← 返回地图", fontSize = 13, fontWeight = "bold", fontColor = C.textDark },
            },
          },
          UI.Label { text = "晨光简报", fontSize = 16, fontWeight = "bold", fontColor = C.textDark },
          UI.Panel { width = 84 },  -- 占位平衡
        },
      },

      -- 概览卡
      UI.Panel {
        id = "paperStats",
        width = "100%",
        flexDirection = "column",
        padding = { top = 12, bottom = 12, left = 16, right = 16 },
        backgroundColor = C.panelWhite, borderRadius = 16,
        margin = { top = 0, bottom = 8, left = 14, right = 14 },
      },

      -- 新闻滚动
      UI.ScrollView {
        flex = 1,
        width = "100%",
        padding = { top = 4, bottom = 16, left = 14, right = 14 },
        children = {
          UI.Panel { id = "paperNews", flexDirection = "column", width = "100%", children = {} },
        },
      },
    },
  }
end

function NewspaperScreen.onShow()
  local root = UI.GetRoot()
  if not root then return end
  local s = GameData.current
  local level = World.cityLevel()

  local stats = root:FindById("paperStats")
  if stats then
    stats:RemoveAllChildren()
    stats:AddChild(UI.Label {
      text = level.name .. " · " .. GameData.monthLabel() .. " 概况",
      fontSize = 13, fontWeight = "bold", fontColor = C.textDark, marginBottom = 6,
    })
    stats:AddChild(UI.Panel {
      flexDirection = "row", justifyContent = "space-between", width = "100%",
      children = {
        UI.Label { text = "财政 ¥" .. tostring(math.floor(s.funds)) .. "万", fontSize = 12, fontColor = C.accentGold },
        UI.Label { text = "人口 " .. tostring(s.population), fontSize = 12, fontColor = C.textDark },
        UI.Label { text = "满意度 " .. tostring(math.floor(s.happiness)), fontSize = 12,
                   fontColor = s.happiness >= 60 and C.accentGreen or C.accentRed },
      },
    })
  end

  local area = root:FindById("paperNews")
  if area then
    area:RemoveAllChildren()
    local news = GameData.recentNews(20)
    if #news == 0 then
      area:AddChild(newsCard({ tag = "头条", headline = "暂无报道", body = "城市还在建设中。", month = GameData.monthLabel() }))
    else
      for i = #news, 1, -1 do
        area:AddChild(newsCard(news[i]))
      end
    end
  end
end

return NewspaperScreen
