-- ============================================================================
-- Sfx — 轻量音效播放（独立 Scene + SoundSource，全部 pcall 守卫）
--   音频设备缺失时自动禁用，不影响游戏逻辑
-- ============================================================================

local Sfx = {}

---@type Node
local node_ = nil
local source_ = nil
local enabled_ = false

function Sfx.init()
  local ok, err = pcall(function()
    local scene = Scene()
    node_ = scene:CreateChild("SfxNode")
    source_ = node_:CreateComponent("SoundSource")
    enabled_ = source_ ~= nil
  end)
  if not ok then
    print("[Sfx] disabled: " .. tostring(err))
    enabled_ = false
  else
    print("[Sfx] ready")
  end
end

--- 播放 assets/Sounds/<name>.ogg
function Sfx.play(name, gain)
  if not enabled_ or not source_ then return end
  pcall(function()
    local s = cache:GetResource("Sound", "Sounds/" .. name .. ".ogg")
    if s then
      source_:Play(s, 0, gain or 0.9)
    end
  end)
end

return Sfx
