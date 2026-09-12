package com.dshx.game.she

data class ThirdPartySdk(
    val name: String,
    val vendor: String,
    val purpose: String,
    val permission: String,
    val collect: String,
    val timing: String,
    val policyUrl: String
)

object PrivacyDocs {
    const val LOCAL_URL = "file:///android_asset/privacy.html"
    const val TAP_POLICY = "https://developer.taptap.cn/docs/sdk/start/agreement/"
    const val CSJ_POLICY = "https://www.csjplatform.com/privacy/partner"
    const val GDT_POLICY = "https://qzs.gdtimg.com/union/res/union_cdn/page/dev_rules/ylh_sdk_privacy_statement.html"
    const val KS_POLICY = "https://www.kuaishou.com/about/policy?tab=privacy"
    const val BAIDU_POLICY = "https://union.baidu.com/bqt/#/legal/privacy"
    const val SIGMOB_POLICY = "https://www.sigmob.com/policy"
    const val TOPON_POLICY = "https://www.toponad.com/zh-cn/opt-out"
    const val ADGAIN_POLICY = "https://www.adgain.cn/privacy.html"
    const val OAID_POLICY = "https://www.msa-alliance.cn/col.jsp?id=120"

    val SDKS: List<ThirdPartySdk> = listOf(
        ThirdPartySdk(
            "TapTap 登录 SDK（TapSDK Login）",
            "易玩（上海）网络科技有限公司",
            "TapTap 账号登录、身份鉴权",
            "网络访问 INTERNET、网络状态 ACCESS_NETWORK_STATE",
            "AndroidID、设备型号、系统版本、设备 CPU 信息、网络类型、设备内存信息",
            "用户点击「同意并继续」之后才初始化；AndroidID 仅在初始化时及用户主动点登录授权时各获取一次",
            TAP_POLICY
        ),
        ThirdPartySdk(
            "TapTap 合规认证 SDK（TapSDK Compliance）",
            "易玩（上海）网络科技有限公司",
            "实名认证与防沉迷",
            "网络访问 INTERNET、网络状态 ACCESS_NETWORK_STATE",
            "AndroidID、设备型号、系统版本、网络类型（登录成功后用于认证）",
            "用户同意隐私政策并完成 TapTap 登录后调用",
            TAP_POLICY
        ),
        ThirdPartySdk(
            "拓新广告 SDK（Tosin）",
            "上海拓新网络科技有限公司",
            "广告聚合、激励视频展示与填充",
            "网络访问 INTERNET、网络状态 ACCESS_NETWORK_STATE、Wi-Fi 状态 ACCESS_WIFI_STATE",
            "AndroidID、OAID、设备型号、系统版本、网络类型、广告曝光与点击记录",
            "用户同意隐私政策，且首次主动观看激励视频时才初始化",
            TAP_POLICY
        ),
        ThirdPartySdk(
            "穿山甲广告 SDK",
            "北京巨量引擎网络技术有限公司",
            "广告投放、监测、归因与反作弊",
            "网络访问 INTERNET、网络状态 ACCESS_NETWORK_STATE",
            "AndroidID、OAID、设备型号、系统版本、网络类型、广告行为记录",
            "用户同意隐私政策且进入广告展示流程后，由广告聚合按需调用",
            CSJ_POLICY
        ),
        ThirdPartySdk(
            "优量汇 SDK（广点通）",
            "深圳市腾讯计算机系统有限公司",
            "广告投放、监测、归因与反作弊",
            "网络访问 INTERNET、网络状态 ACCESS_NETWORK_STATE",
            "AndroidID、OAID、设备型号、系统版本、网络类型、广告行为记录",
            "用户同意隐私政策且进入广告展示流程后，由广告聚合按需调用",
            GDT_POLICY
        ),
        ThirdPartySdk(
            "快手联盟广告 SDK",
            "北京快手科技有限公司",
            "广告投放、监测、归因与反作弊",
            "网络访问 INTERNET、网络状态 ACCESS_NETWORK_STATE",
            "AndroidID、OAID、设备型号、系统版本、网络类型、广告行为记录",
            "用户同意隐私政策且进入广告展示流程后，由广告聚合按需调用",
            KS_POLICY
        ),
        ThirdPartySdk(
            "百度联盟广告 SDK",
            "北京百度网讯科技有限公司",
            "广告投放、监测、归因与反作弊",
            "网络访问 INTERNET、网络状态 ACCESS_NETWORK_STATE",
            "AndroidID、OAID、设备型号、系统版本、网络类型、广告行为记录",
            "用户同意隐私政策且进入广告展示流程后，由广告聚合按需调用",
            BAIDU_POLICY
        ),
        ThirdPartySdk(
            "Sigmob 广告 SDK",
            "北京创智汇聚科技股份有限公司",
            "广告投放、监测、归因与反作弊",
            "网络访问 INTERNET、网络状态 ACCESS_NETWORK_STATE",
            "AndroidID、OAID、设备型号、系统版本、网络类型、广告行为记录",
            "用户同意隐私政策且进入广告展示流程后，由广告聚合按需调用",
            SIGMOB_POLICY
        ),
        ThirdPartySdk(
            "TopOn 广告 SDK",
            "广州塔酷信息科技有限公司",
            "广告聚合、投放决策与效果统计",
            "网络访问 INTERNET、网络状态 ACCESS_NETWORK_STATE",
            "AndroidID、OAID、设备型号、系统版本、网络类型、广告行为记录",
            "用户同意隐私政策且进入广告展示流程后，由广告聚合按需调用",
            TOPON_POLICY
        ),
        ThirdPartySdk(
            "AdGain 广告 SDK",
            "北京数字悦动科技有限公司",
            "广告投放、监测与填充",
            "网络访问 INTERNET、网络状态 ACCESS_NETWORK_STATE",
            "AndroidID、OAID、设备型号、系统版本、网络类型、广告行为记录",
            "用户同意隐私政策且进入广告展示流程后，由广告聚合按需调用",
            ADGAIN_POLICY
        ),
        ThirdPartySdk(
            "移动智能终端补充设备标识体系统一调用 SDK（OAID）",
            "移动安全联盟（MSA）",
            "在不使用 IMEI 的前提下提供广告设备标识",
            "网络访问 INTERNET",
            "OAID",
            "用户同意隐私政策且广告 SDK 初始化后调用",
            OAID_POLICY
        ),
        ThirdPartySdk(
            "TapTap 广告 SDK",
            "易玩（上海）网络科技有限公司",
            "广告投放与监测",
            "网络访问 INTERNET、网络状态 ACCESS_NETWORK_STATE",
            "AndroidID、OAID、设备型号、系统版本、网络类型、广告行为记录",
            "用户同意隐私政策且进入广告展示流程后，由广告聚合按需调用",
            TAP_POLICY
        )
    )
}
