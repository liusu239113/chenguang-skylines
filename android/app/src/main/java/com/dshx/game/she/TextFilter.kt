package com.dshx.game.she

/**
 * 屏蔽词与合规输入过滤。
 *
 * 依据 TapTap 审核规范 1.1：游戏不得含有违法、涉政、涉赌、暴力血腥、
 * 淫秽色情、人身攻击等违规内容。玩家可自由输入的地方（城市名、玩家名）
 * 必须过滤，避免被用于发布违规内容。
 */
object TextFilter {

    /** 屏蔽词表：命中即拒绝。按类别分组，便于后续扩充。 */
    private val BLOCKED: List<String> = listOf(
        // 涉政 / 国家机关
        "政府", "国务院", "党中央", "共产党", "国民党", "政协", "人大", "公安", "治安所",
        "法院", "检察院", "主席", "总统", "书记", "总理", "部长", "省长", "县长",
        "公安局", "派出所", "解放军", "军队", "军委",
        // 涉赌
        "赌场", "赌博", "博彩", "彩票", "六合彩", "老虎机", "百家乐", "筹码",
        // 暴力血腥
        "杀人", "自杀", "爆炸", "炸弹", "枪支", "枪械", "毒品", "吸毒", "贩毒",
        "暴力", "血腥", "砍人", "杀人犯", "恐怖", "恐怖分子",
        // 淫秽色情
        "色情", "情色", "淫秽", "裸体", "嫖娼", "卖淫", "成人片",
        // 违法
        "诈骗", "洗钱", "走私", "传销", "偷渡", "伪造", "假钞",
        // 人身攻击 / 侮辱
        "傻逼", "傻B", "煞笔", "智障", "脑残", "废物", "去死", "贱人", "畜生",
        "尼玛", "他妈", "你妈", "草泥马", "卧槽", "妈的",
        // 现实机构 / 品牌
        "微信", "支付宝", "淘宝", "抖音", "快手", "QQ", "腾讯", "阿里",
        // 敏感政治符号
        "台独", "港独", "藏独", "疆独", "法轮", "邪教",
        // 广告法禁用词
        "国家级", "世界级", "最高级", "第一品牌", "绝无仅有", "独一无二"
    )

    /** 单字敏感组合（防拆字绕过），命中即拒绝 */
    private val BLOCKED_PAIRS: List<String> = listOf(
        "政治", "政权", "执政", "民主", "游行", "示威", "抗议", "镇压",
        "革命", "颠覆", "叛国", "分裂"
    )

    /**
     * 校验玩家输入。
     * @return null 表示通过；否则返回给玩家看的提示。
     */
    fun validate(raw: String): String? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        if (s.length > 12) return "名称最多 12 个字"
        val flat = s.replace(" ", "").replace("\n", "").lowercase()
        for (w in BLOCKED) {
            if (flat.contains(w.lowercase())) return "名称含不适宜的词，请换一个"
        }
        for (w in BLOCKED_PAIRS) {
            if (flat.contains(w)) return "名称含不适宜的词，请换一个"
        }
        // 纯符号 / 纯数字无意义名
        if (flat.all { it.isDigit() }) return "名称不能全是数字"
        return null
    }

    /** 过滤为安全文本：不通过就返回默认名 */
    fun sanitize(raw: String, fallback: String): String {
        return if (validate(raw) == null && raw.trim().isNotEmpty()) raw.trim() else fallback
    }
}
