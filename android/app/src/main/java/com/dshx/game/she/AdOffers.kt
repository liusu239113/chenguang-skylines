package com.dshx.game.she

import com.dshx.game.she.world.World

object AdOffers {
    var weeklyClaimed: Boolean = false
    var weeklyKey: Int = -1
    var lastBailoutDay: Int = -1

    fun reset() {
        weeklyClaimed = false
        weeklyKey = -1
        lastBailoutDay = -1
        AppState.adOfferOpen = false
        AppState.adOfferKind = ""
    }

    fun tickDay(s: CityState) {
        val week = s.year * 60 + s.month * 5 + ((s.day - 1) / 7)
        if (weeklyKey != week) {
            weeklyKey = week
            weeklyClaimed = false
            if (!AppState.adOfferOpen && s.day == 1) {
                AppState.adOfferKind = "daily"
                AppState.adOfferOpen = true
            }
        }
        val dayKey = s.year * 400 + s.month * 32 + s.day
        if (!AppState.adOfferOpen && s.bankruptDays >= 3 && s.funds < 50 && lastBailoutDay != dayKey) {
            lastBailoutDay = dayKey
            AppState.adOfferKind = "bailout"
            AppState.adOfferOpen = true
        }
    }

    fun offerShortfall(need: Int, action: String) {
        val s = GameData.current ?: return
        s.lastShortfall = need
        s.lastShortAction = action
        AppState.adOfferKind = "shortfall"
        AppState.adOfferOpen = true
    }

    fun grant(kind: String) {
        val s = GameData.current ?: return
        when (kind) {
            "daily" -> {
                s.funds += 120
                GameData.book("income", "other", "每周礼包", 120.0)
                weeklyClaimed = true
                MapRef.view?.setToast("每周项目礼包 +120 万")
            }
            "shortfall" -> {
                val add = maxOf(120, s.lastShortfall)
                s.funds += add
                GameData.book("income", "other", "应急注资", add.toDouble())
                MapRef.view?.setToast("应急注资 +${add}万，可继续${s.lastShortAction}")
            }
            "bailout" -> {
                s.funds += 260
                GameData.book("income", "other", "账面纾困", 260.0)
                s.bankruptDays = 0
                MapRef.view?.setToast("账面纾困 +260 万")
            }
            "doubletax" -> {
                s.doubleTaxDays = 12
                MapRef.view?.setToast("项目收益加倍 12 天")
            }
            "happy" -> {
                s.happiness = (s.happiness + 8).coerceAtMost(92.0)
                MapRef.view?.setToast("民心安抚 满意+8")
            }
            "grant" -> {
                s.funds += 90
                GameData.book("income", "other", "项目注资", 90.0)
                MapRef.view?.setToast("项目注资 +90 万")
            }
            // ---- 便利型：直击玩家真实痛点（人口涨得慢 / 楼不升级 / 贷款压身）----
            "movein" -> {
                // 即刻入住：全城空置住宅一次住满，人口立刻跳一截
                var n = 0
                for (e in World.allBuildings()) {
                    val b = e.b
                    if (b.isService || b.abandoned || b.zone != "residential") continue
                    val cap = b.cap()
                    if (b.residents < cap) {
                        n += cap - b.residents
                        b.residents = cap
                    }
                }
                s.population = World.allBuildings()
                    .filter { !it.b.isService && it.b.zone == "residential" }
                    .sumOf { it.b.residents }.toDouble()
                World.current?._pop = s.population.toInt()
                MapRef.view?.setToast(if (n > 0) "即刻入住：新增 $n 位住户" else "当前没有空置住宅")
            }
            "upgrade" -> {
                // 升级提速：全城满级以下的建筑立刻升一级，新区肉眼可见地长高
                var n = 0
                for (e in World.allBuildings()) {
                    val b = e.b
                    if (b.isService || b.abandoned) continue
                    val maxLv = Config.GROWN[b.zone]?.levels?.size ?: 3
                    if (b.level < maxLv) {
                        b.level += 1
                        n++
                    }
                }
                MapRef.view?.setToast(if (n > 0) "升级提速：$n 栋建筑升了一级" else "全城建筑已是最高级")
            }
            "relief" -> {
                // 纾困清运：清空垃圾 + 修复废弃楼，一次解决两个经营痛点
                var trash = 0
                var fixed = 0
                for (e in World.allBuildings()) {
                    val b = e.b
                    if (b.isService) continue
                    if (b.garbage > 0) {
                        trash += b.garbage
                        b.garbage = 0
                    }
                    if (b.abandoned) {
                        b.abandoned = false
                        fixed++
                    }
                }
                s.garbageBacklog = 0
                MapRef.view?.setToast("纾困完成：清运 $trash 单位垃圾，修复 $fixed 栋建筑")
            }
            "nointerest" -> {
                // 免息周转：立刻还清全部贷款，甩掉每天扣款的包袱
                if (s.loanDebt <= 0.0) {
                    MapRef.view?.setToast("当前没有未还贷款")
                } else {
                    val n = s.loanDebt
                    s.loanDebt = 0.0
                    s.loanDaily = 0.0
                    s.loanKind = ""
                    s.loanCooldown = 0
                    MapRef.view?.setToast("免息周转：已结清 " + n.toInt() + " 万贷款")
                }
            }
            "fastpolicy" -> {
                // 策略速批：清空所有策略冷却，想连开就开
                var n = 0
                for ((id, cd) in s.policyCooldowns.toMap()) {
                    if (cd > 0) {
                        s.policyCooldowns[id] = 0
                        n++
                    }
                }
                MapRef.view?.setToast(if (n > 0) "策略速批：$n 项策略可立即启用" else "当前没有冷却中的策略")
            }
            // ---- 限时增益（真实时间计时，退出游戏也保留）----
            "buff_happy" -> {
                Buffs.grant(Buffs.NO_HAPPY_DROP, 30)
                MapRef.view?.setToast("满意度守护：30 分钟内满意度不再下降")
            }
            "buff_growth" -> {
                Buffs.grant(Buffs.FAST_GROWTH, 30)
                MapRef.view?.setToast("入住加速：30 分钟内居民增长速度翻倍")
            }
            "buff_income" -> {
                Buffs.grant(Buffs.INCOME_BOOST, 30)
                MapRef.view?.setToast("收益提升：30 分钟内项目收益 +50%")
            }
            "buff_upkeep" -> {
                Buffs.grant(Buffs.NO_UPKEEP, 30)
                MapRef.view?.setToast("免维护：30 分钟内不收维护费")
            }
            "boom" -> {
                // 招商旺季：12 天收益翻倍（后期大工程靠它攒钱）
                s.doubleTaxDays = 12
                MapRef.view?.setToast("招商旺季：12 天收益翻倍")
            }
        }
        AppState.adOfferOpen = false
        AppState.adOfferKind = ""
        AppState.bumpLive()
    }
}
