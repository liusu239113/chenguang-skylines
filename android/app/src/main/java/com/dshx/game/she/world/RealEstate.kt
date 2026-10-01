package com.dshx.game.she.world

import com.dshx.game.she.Config
import kotlin.math.max
import kotlin.random.Random

/**
 * 地产经营：楼市行情、楼宇估值、挂牌出售 / 回购、地皮转让。
 *
 * 核心抉择：**持有** → 每天收物业费；**出售** → 一次性套现，但永久失去该楼收益。
 * 行情高时套现赚差价，行情低时回购囤资产。
 */
object RealEstate {

    /** 楼市行情指数：1.0 为基准，0.70~1.40 之间均值回归 */
    var marketIndex: Double = 1.0
    var soldCount: Int = 0
    var landSold: Int = 0

    /** 出售手续费 / 回购溢价 / 地皮转让手续费 */
    const val SELL_FEE = 0.06
    const val BUYBACK_FEE = 0.10
    const val LAND_FEE = 0.08

    fun reset() {
        marketIndex = 1.0
        soldCount = 0
        landSold = 0
    }

    /** 每日更新行情：向 1.0 均值回归 + 小幅随机波动 */
    fun tickDay() {
        val meanPull = (1.0 - marketIndex) * 0.02
        val noise = (Random.nextDouble() - 0.5) * 0.030
        marketIndex = Config.clamp(marketIndex + meanPull + noise, 0.70, 1.40)
    }

    fun marketLabel(): String = when {
        marketIndex >= 1.28 -> "过热"
        marketIndex >= 1.12 -> "火热"
        marketIndex >= 0.92 -> "平稳"
        marketIndex >= 0.80 -> "偏冷"
        else -> "低迷"
    }

    /** 单栋楼的市场估值（万） */
    fun buildingValue(e: BuildingEntry): Double {
        val b = e.b
        if (b.isService) return 0.0
        val lv = World.landValue(e.x, e.y)
        val lvl = max(1, b.level)
        val zoneMul = when (b.zone) {
            "commercial" -> 1.35
            "office" -> 1.60
            "industrial" -> 0.90
            else -> 1.00
        }
        val ageDays = ((Growth.simTime - b.born) / Config.TIME.daySeconds).toInt()
        val ageMul = max(0.55, 1.0 - ageDays * 0.0006)
        val abandMul = if (b.abandoned) 0.25 else 1.0
        val cap = b.cap()
        val occMul = 0.55 + 0.45 * (if (cap > 0) b.occupied().toDouble() / cap else 0.5)
        val base = (60.0 + lv * 22.0) * (1.0 + 0.65 * (lvl - 1))
        return base * zoneMul * ageMul * abandMul * occMul * marketIndex
    }

    /** 出售所得（扣手续费） */
    fun sellProceeds(e: BuildingEntry): Double = buildingValue(e) * (1.0 - SELL_FEE)

    /** 回购价（含溢价） */
    fun buybackCost(e: BuildingEntry): Double = buildingValue(e) * (1.0 + BUYBACK_FEE)

    /** 空地地皮估值（万） */
    fun landValueAt(x: Int, y: Int): Double =
        (20.0 + World.landValue(x, y) * 10.0) * marketIndex

    fun landProceeds(x: Int, y: Int): Double = landValueAt(x, y) * (1.0 - LAND_FEE)

    /** 重新统计已售建筑数 */
    fun refreshCounts() {
        soldCount = World.allBuildings().count { !it.b.isService && it.b.sold }
    }

    /** 自持建筑总市值 */
    fun ownedAssetValue(): Double {
        var v = 0.0
        for (e in World.allBuildings()) {
            if (e.b.isService || e.b.sold) continue
            v += buildingValue(e)
        }
        return v
    }

    /** 已售出资产总市值 */
    fun soldAssetValue(): Double {
        var v = 0.0
        for (e in World.allBuildings()) {
            if (e.b.isService || !e.b.sold) continue
            v += buildingValue(e)
        }
        return v
    }

    /** 自持建筑占用的居住/岗位数（用于物业费分成） */
    fun ownedShare(): Double {
        var owned = 0.0
        var total = 0.0
        for (e in World.allBuildings()) {
            if (e.b.isService) continue
            val o = e.b.occupied().toDouble()
            total += o
            if (!e.b.sold) owned += o
        }
        return if (total <= 0.0) 1.0 else owned / total
    }
}
