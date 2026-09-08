-- ============================================================================
-- UIHelper — 地图叠层 UI 小部件工厂（圆角白卡 / 半透明 / 莫兰迪）
--   仿参考图：白色圆角胶囊 + 轻投影；竖屏；不透明内容用 panelWhite
-- ============================================================================

local UI = require("urhox-libs/UI")
local Config = require("Config")

local UIHelper = {}
local C = Config.COLORS

-- 资金格式化：>=10000 万显示 "x.x万"
function UIHelper.fmtMoney(v)
  if v >= 10000 or v <= -10000 then
    return string.format("%.1f万", v / 10000)
  end
  return tostring(math.floor(v))
end

function UIHelper.fmtPop(v)
  if v >= 10000 then return string.format("%.1f万", v / 10000) end
  return tostring(v)
end

-- 通用圆角白卡（opt 支持 id/position/top/left/right/bottom/zIndex/width/flex 等透传）
function UIHelper.Card(children, opt)
  opt = opt or {}
  local def = {
    id = opt.id,
    flexDirection = opt.row and "row" or "column",
    alignItems = opt.align or "center",
    justifyContent = opt.justify,
    gap = opt.gap or 8,
    padding = opt.padding or { top = 8, bottom = 8, left = 14, right = 14 },
    backgroundColor = opt.bg or C.panelWhite,
    borderRadius = opt.radius or 18,
    borderWidth = opt.borderWidth or 0,
    borderColor = opt.borderColor or C.panelShadow,
    boxShadow = { { x = 0, y = 2, blur = 8, spread = 0, color = { 90, 100, 90, 70 }, inset = false } },
    pointerEvents = "auto",
    children = children,
  }
  for _, k in ipairs({ "position", "top", "left", "right", "bottom", "width", "height",
                       "maxWidth", "flex", "zIndex", "opacity", "flexWrap", "marginLeft" }) do
    if opt[k] ~= nil then def[k] = opt[k] end
  end
  return UI.Panel(def)
end

-- 资源小胶囊（前缀文字 + 数值；label 为空则只显示数值）
function UIHelper.Chip(label, value, opt)
  opt = opt or {}
  local kids = {}
  if label and label ~= "" then
    table.insert(kids, UI.Label { text = label, fontSize = 10, fontColor = C.textMid })
  end
  table.insert(kids, UI.Label {
    text = value, fontSize = 13, fontWeight = "bold",
    fontColor = opt.color or C.textDark,
  })
  return UI.Panel {
    flexDirection = "row",
    alignItems = "center",
    gap = 4,
    children = kids,
  }
end

-- 圆形浮动按钮（+ / − 等）
function UIHelper.RoundButton(text, opt)
  opt = opt or {}
  return UI.Panel {
    id = opt.id,
    width = opt.size or 44,
    height = opt.size or 44,
    borderRadius = (opt.size or 44) / 2,
    backgroundColor = opt.bg or C.panelWhite,
    justifyContent = "center",
    alignItems = "center",
    onClick = opt.onClick,
    pointerEvents = "auto",
    shadow = { offsetY = 2, blur = 8, color = { 90, 100, 90, 90 } },
    children = {
      UI.Label { text = text, fontSize = opt.fontSize or 22, fontWeight = "bold",
                 fontColor = opt.color or C.textDark, textAlign = "center" },
    },
  }
end

-- 底部工具栏项（纯文字，active 时高亮底色）
function UIHelper.ToolItem(text, opt)
  opt = opt or {}
  local active = opt.active
  return UI.Panel {
    id = opt.id,
    flexDirection = "column",
    alignItems = "center",
    justifyContent = "center",
    width = opt.width or 62,
    height = 52,
    borderRadius = 14,
    backgroundColor = active and C.accentSoftBg or { 0, 0, 0, 0 },
    gap = 0,
    onClick = opt.onClick,
    pointerEvents = "auto",
    children = {
      UI.Label { text = text, fontSize = 15, fontWeight = "bold",
                 fontColor = active and C.accentRed or C.textMid,
                 textAlign = "center" },
    },
  }
end

-- 信息卡行（label 左 / value 右）
function UIHelper.InfoRow(label, value, opt)
  opt = opt or {}
  return UI.Panel {
    width = "100%",
    flexDirection = "row",
    justifyContent = "space-between",
    alignItems = "center",
    padding = { top = 3, bottom = 3, left = 0, right = 0 },
    children = {
      UI.Label { text = label, fontSize = 12, fontColor = C.textMid },
      UI.Label { text = value, fontSize = 12, fontColor = opt.color or C.textDark,
                 textAlign = "right", flexShrink = 1 },
    },
  }
end

-- 建筑 / 工具选择小卡（建造抽屉里横向排布）
function UIHelper.PickChip(text, sub, opt)
  opt = opt or {}
  local sel = opt.selected
  return UI.Panel {
    id = opt.id,
    width = opt.width or 82,
    flexDirection = "column",
    alignItems = "center",
    justifyContent = "center",
    padding = { top = 8, bottom = 8, left = 4, right = 4 },
    borderRadius = 12,
    backgroundColor = opt.bg or C.panelWhite,
    borderWidth = sel and 2 or 1,
    borderColor = sel and (opt.accent or C.accentRed) or C.border2,
    opacity = opt.disabled and 0.45 or 1.0,
    gap = 3,
    onClick = opt.onClick,
    pointerEvents = "auto",
    children = {
      UI.Label { text = text, fontSize = 12, fontWeight = "bold", fontColor = C.textDark,
                 textAlign = "center" },
      UI.Label { text = sub, fontSize = 10, fontColor = opt.subColor or C.textMid,
                 textAlign = "center" },
    },
  }
end

return UIHelper
