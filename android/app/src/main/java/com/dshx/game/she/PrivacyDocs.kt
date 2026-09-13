package com.dshx.game.she

/** 本应用自身收集的信息条目 */
data class PrivacyItem(
    val kind: String,
    val detail: String,
    val purpose: String
)

/**
 * 第三方 SDK 情况说明条目。
 * 按审核要求：SDK名称、开发者名称（全称）、使用目的、使用场景、
 * 调用权限说明、个人信息收集类型及方式、隐私政策链接、频次时机。
 */
data class SdkInfo(
    val name: String,
    val vendor: String,
    val purpose: String,
    val scene: String,
    val permission: String,
    val collect: String,
    val policyUrl: String,
    val timing: String
)

object PrivacyDocs {
    /** 完整《隐私政策》跳外部浏览器 */
    const val POLICY_URL = "http://yanyususu.online:5555/doushi.html"

    private const val AD_PURPOSE = "广告投放、广告监测、广告归因、反作弊"
    private const val AD_SCENE = "展示激励视频广告时（用户主动点击观看）"
    private const val AD_PERMISSION = "网络访问、网络状态；读取设备标识符用于广告归因"
    private const val AD_COLLECT = "设备标识符（OAID、AndroidID）、设备信息、网络信息；由 SDK 自动收集"
    private const val AD_TIMING = "用户同意《隐私政策》后，在广告加载与展示过程中"

    private fun adSdk(
        name: String,
        vendor: String,
        url: String,
        purpose: String = AD_PURPOSE,
        collect: String = AD_COLLECT
    ) = SdkInfo(name, vendor, purpose, AD_SCENE, AD_PERMISSION, collect, url, AD_TIMING)

    val SELF_ITEMS: List<PrivacyItem> = listOf(
        PrivacyItem("位置信息", "精确位置信息（可选）", "广告定向投放"),
        PrivacyItem(
            "网络信息",
            "IP地址、运营商信息、Wi-Fi状态、网络信号强度、网络类型、无线网SSID名称、WiFi路由器MAC地址（BSSID）、设备的MAC地址",
            "广告投放、广告监测、广告归因、反作弊"
        ),
        PrivacyItem(
            "设备信息",
            "设备制造商、品牌、设备型号、设备名称、操作系统版本、屏幕分辨率、屏幕方向、屏幕DPI、时区、语言、sim卡信息、CPU信息、可用的存储空间大小、设备启动时间、手机系统重启时间、设备姿态",
            "广告投放、反作弊"
        ),
        PrivacyItem(
            "传感器信息",
            "加速度传感器、陀螺仪传感器、线性加速度传感器、磁场传感器、旋转矢量传感器、重力传感器、压力传感器",
            "广告投放、广告互动、反作弊"
        ),
        PrivacyItem(
            "标识符",
            "OAID、Android_ID、IDFV、GAID（Google广告ID）、设备标识符（如IMEI、IMSI、ICCID、MEID等）、IDFA",
            "广告投放、广告监测、广告归因、反作弊"
        ),
        PrivacyItem(
            "应用信息",
            "宿主应用的包名、版本号、宿主应用的进程名称、运行状态、可疑行为、应用安装信息",
            "广告投放、反作弊"
        ),
        PrivacyItem(
            "使用数据",
            "产品交互数据、广告数据（如展示、点击、关闭、转化广告数据）",
            "广告投放、广告归因"
        ),
        PrivacyItem(
            "性能数据",
            "崩溃数据、性能数据",
            "减少APP崩溃、提供稳定可靠的服务"
        )
    )

    val SDKS: List<SdkInfo> = listOf(
        adSdk("穿山甲", "北京巨量引擎网络技术有限公司", "https://csjplatform.com/supportcenter/5879"),
        adSdk("优量汇", "深圳市腾讯计算机系统有限公司", "https://www.tencent.com/zh-cn/privacy-policy.html", collect = "AndroidID、设备IP"),
        adSdk("快手联盟", "北京快手广告有限公司", "https://www.kuaishou.com/about/policy?tab=privacy"),
        adSdk("百度联盟", "北京百度网讯科技有限公司", "https://union.baidu.com/bqt/#/policies"),
        adSdk("Sigmob", "北京创智汇聚科技股份有限公司", "https://www.sigmob.com/policy.html"),
        adSdk("优推", "北京乐游阳光科技有限公司", "https://youtui.gameley.com/agreement.html#ytpolice", collect = "OAID"),
        adSdk("塔酷", "广州塔酷信息科技有限公司", "https://help.takuad.com/docs/1Mn1B7"),
        adSdk("Adgain", "北京数字悦动科技有限公司", "https://www.adgain.cn/docs/privacy.html", collect = "AndroidID、OAID、WiFi的BSSID"),
        adSdk("天璇", "上海优比客思科技有限公司", "https://www.ubixai.com/ubix_sdk_Merak_privacy.html"),
        adSdk("Adview", "天津快友世纪科技有限公司", "https://www.adview.cn/user/compliance"),
        adSdk("Adscope（倍孜）", "上海倍孜网络技术有限公司", "https://sdkdoc.beizi.biz/#/zh-cn/guide/UsePrivacy"),
        adSdk("UGdesk（多盟）", "多盟智胜网络技术（北京）有限公司", "https://bluefocus.feishu.cn/wiki/"),
        adSdk("脉盟", "上海孛樊信息科技有限公司", "https://static.adwangmai.com/privacy-MaxMindSDK.html"),
        adSdk("Menta", "上海芒拓网络科技有限公司", "https://www.mentamob.com/policy.html"),
        adSdk("美数", "北京美数信息科技有限公司", "https://www.atdplus.cn/html/sdk-privacy-agreement/"),
        adSdk("Funlink", "北京泛连科技有限公司", "https://www.funlinkads.com/doc/privacy.pdf"),
        adSdk("宸星", "湖北众宸嘉实信息科技有限公司", "https://privacy.adbiding.cn/privacy.html"),
        adSdk("Oppo", "广东欢太科技有限公司", "https://u.oppomobile.com/home/_book/"),
        adSdk("小米", "小米新加坡科技有限公司", "https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2139"),
        adSdk("Vivo", "广东天宸网络科技有限公司", "https://adnet.vivo.com.cn/home/agreement/18"),
        adSdk("鲸鸿动能（华为）", "华为终端有限公司", "https://consumer.huawei.com/cn/privacy/privacy-statement-huawei/"),
        adSdk("Qbmob", "杭州趣变网络科技有限公司", "https://www.qubiankeji.com/doc/sdkPrivacyPolicy.html"),
        adSdk("Advista", "上海推易软件技术有限公司", "https://docs.divms.cn/guide/privacy.html"),
        adSdk("欢效", "北京欢效网络科技有限公司", "https://tos.adhuanxiao.com/欢效广告SDK隐私政策.html"),
        adSdk("MediaPrime", "北京泛为信息科技有限公司", "https://www.mediaprime.top/privacy"),
        adSdk("聚推", "杭州推啊网络科技有限公司", "https://ssp-web.jutuiad.cn/#/public/PrivacyPolicy"),
        SdkInfo(
            name = "Tap登录",
            vendor = "易玩（上海）网络科技有限公司",
            purpose = "实现账号登录功能、登录状态维护及账号安全验证",
            scene = "用户点击「TapTap 登录」时",
            permission = "网络访问、网络状态",
            collect = "AndroidID、设备信息（型号、系统版本、CPU、内存）、网络类型",
            policyUrl = "https://developer.taptap.cn/docs/sdk/start/agreement/",
            timing = "用户同意《隐私政策》并主动点击「TapTap 登录」后才初始化，登录时读取"
        )
    )
}
