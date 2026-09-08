-- ============================================================================
-- MainMenu — 标题画面（新莫兰迪风格，虚构世界观）
-- ============================================================================

local UI = require("urhox-libs/UI")
local Config = require("Config")
local GameData = require("GameData")
local Sfx = require("Sfx")

local MainMenu = {}
local C = Config.COLORS

function MainMenu.create(Navigator)
  return UI.Panel {
    id = "screen_mainMenu",
    width = "100%",
    height = "100%",
    flexDirection = "column",
    justifyContent = "center",
    alignItems = "center",
    padding = 28,
    backgroundColor = C.uiBackdrop,
    pointerEvents = "auto",
    children = {
      UI.Panel {
        width = "92%",
        maxWidth = 380,
        flexDirection = "column",
        alignItems = "center",
        padding = { top = 30, bottom = 26, left = 24, right = 24 },
        backgroundColor = C.panelWhite,
        borderRadius = 24,
        boxShadow = { { x = 0, y = 6, blur = 16, spread = 0, color = { 90, 100, 90, 90 }, inset = false } },
        gap = 6,
        children = {
          -- 报纸刊头
          UI.Label { text = Config.WORLD.country .. " · " .. Config.WORLD.city .. " · 城建日报",
                     fontSize = 10, fontColor = C.textMid, textAlign = "center", width = "100%" },
          UI.Panel { width = "100%", height = 2, backgroundColor = C.textDark, margin = { top = 6, bottom = 2 } },
          UI.Label { text = Config.TITLE, fontSize = 30, fontWeight = "bold",
                     fontColor = C.textDark, textAlign = "center", width = "100%" },
          UI.Label { text = Config.SUBTITLE, fontSize = 10, fontColor = C.textFaint, textAlign = "center", width = "100%" },
          UI.Panel {
            width = "100%", height = 5,
            borderTopWidth = 2, borderTopColor = C.textDark,
            borderBottomWidth = 1, borderBottomColor = C.textDark,
            margin = { top = 4, bottom = 12 },
          },

          UI.Label { text = "你被任命为" .. Config.WORLD.city .. "的首席" .. Config.WORLD.playerRole .. "。",
                     fontSize = 12, fontColor = C.textDark, textAlign = "center", width = "100%" },
          UI.Label { text = "规划用地、铺设路网、招商引资、控制污染，",
                     fontSize = 12, fontColor = C.textDark, textAlign = "center", width = "100%" },
          UI.Label { text = "一步步把小城建成你的都市天际线。",
                     fontSize = 12, fontColor = C.textDark, textAlign = "center", width = "100%" },

          UI.Panel {
            id = "btnStart",
            width = "100%", height = 48, borderRadius = 24,
            backgroundColor = C.accentGreen,
            justifyContent = "center", alignItems = "center",
            marginTop = 18, pointerEvents = "auto",
            onClick = function()
              Sfx.play("sfx_click")
              Navigator.go("map")
            end,
            children = {
              UI.Label { text = "开始规划", fontSize = 16, fontWeight = "bold", fontColor = { 255, 255, 255, 255 } },
            },
          },

          UI.Panel {
            id = "btnRestart",
            width = "100%", height = 44, borderRadius = 22,
            backgroundColor = C.chipBg, borderWidth = 1, borderColor = C.border2,
            justifyContent = "center", alignItems = "center",
            marginTop = 8, pointerEvents = "auto",
            onClick = function()
              GameData.reset(os.time() % 100000)
              require("world.MapView").resetCamera()
              Navigator.go("map")
            end,
            children = {
              UI.Label { text = "重建新城", fontSize = 14, fontWeight = "bold", fontColor = C.textDark },
            },
          },

          UI.Label {
            text = "全虚构世界观 · 玩法模拟经营 · 无现实机构指涉",
            fontSize = 9, fontColor = C.textFaint, marginTop = 14, textAlign = "center",
          },
        },
      },
    },
  }
end

return MainMenu
