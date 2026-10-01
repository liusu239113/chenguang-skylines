package com.dshx.game.she

/**
 * 种子系统：地图完全由种子确定性生成，同一种子必然得到同一张地图。
 *
 * 用途：
 *  - 玩家开局填种子，复现指定地图
 *  - 游戏中可导出当前地图的种子，分享给他人
 *  - 内置若干「精选种子」，标注地形特征（平原/多水/丘陵），方便论坛交流
 *
 * 合规：所有种子名与描述均为架空设定，不含现实地名、机构或人物。
 */
object SeedLib {

    data class SeedEntry(
        val seed: Int,
        val name: String,        // 架空地名
        val tag: String,         // 地形标签
        val desc: String         // 地形说明
    )

    /**
     * 精选种子：地形特征已人工挑选并标注。
     * tag 取值：平原 / 多水 / 丘陵 / 均衡 / 群岛
     */
    val FEATURED: List<SeedEntry> = listOf(
        SeedEntry(20260408, "初始之地", "均衡", "官方默认地形，平原为主，有一条河与少量丘陵，适合新手起步。"),
        SeedEntry(100001, "苍原", "平原", "一马平川，几乎没有山丘，适合大范围铺路盖楼。"),
        SeedEntry(100002, "水泽", "多水", "河网密布、湖泊众多，水运与港口的天然舞台。"),
        SeedEntry(100003, "层峦", "丘陵", "丘陵连绵，可用平地有限，考验填挖与立体规划。"),
        SeedEntry(100004, "碧湾", "多水", "临海港湾地形，水岸线长，适合做滨海新区。"),
        SeedEntry(100005, "旷野", "平原", "开阔平原，无山无水，纯粹的白纸起步。"),
        SeedEntry(100006, "群岛", "群岛", "多块小陆地被水隔开，必须靠桥梁连成一体。"),
        SeedEntry(100007, "青谷", "均衡", "缓坡与河谷交错，地形温和，适合中等规模新区。"),
        SeedEntry(100008, "磐石", "丘陵", "山体厚重，平地集中在谷地，适合紧凑布局。"),
        SeedEntry(100009, "长川", "多水", "一条大江贯穿全图，两岸发展，桥位是战略资源。")
    )

    /** 按标签分组，供 UI 展示 */
    fun byTag(): Map<String, List<SeedEntry>> = FEATURED.groupBy { it.tag }

    fun find(seed: Int): SeedEntry? = FEATURED.firstOrNull { it.seed == seed }

    /** 随机挑一个精选种子 */
    fun randomFeatured(): SeedEntry = FEATURED[kotlin.random.Random.nextInt(FEATURED.size)]

    /** 生成一个随机种子（避开精选种子的号段，便于区分） */
    fun randomSeed(): Int = kotlin.random.Random.nextInt(200000, 999999)

    /**
     * 导出分享文本：种子 + 当前新区信息，方便贴到论坛。
     * 例：【地产大亨】种子 100002 · 水泽（多水）｜人口 3200 · 满意 78
     */
    fun shareText(seed: Int, cityName: String, pop: Int, happy: Int): String {
        val e = find(seed)
        val head = if (e != null) "种子 $seed · ${e.name}（${e.tag}）" else "种子 $seed"
        return "【${Config.TITLE}】$head ｜ $cityName · 人口 $pop · 满意 $happy"
    }

    /**
     * 解析玩家粘贴的分享文本或纯数字，返回种子。
     * 兼容：纯数字 / 含"种子 123456"的整段文本。
     */
    fun parse(input: String): Int? {
        val s = input.trim()
        if (s.isEmpty()) return null
        s.toIntOrNull()?.let { return if (it in 1..999999999) it else null }
        val m = Regex("""种子\s*(\d{1,9})""").find(s) ?: return null
        return m.groupValues.getOrNull(1)?.toIntOrNull()
    }
}
