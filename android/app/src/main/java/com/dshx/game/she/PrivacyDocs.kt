package com.dshx.game.she

/** 本应用自身收集的信息条目 */
data class PrivacyItem(
    val kind: String,
    val detail: String,
    val purpose: String
)

/** 第三方 SDK 条目 */
data class SdkInfo(
    val name: String,
    val vendor: String
)

/** 第三方 SDK 收集明细 */
data class SdkDetail(
    val name: String,
    val collect: String,
    val purpose: String
)

object PrivacyDocs {
    /** 完整《隐私政策》跳外部浏览器 */
    const val POLICY_URL = "http://yanyususu.online:5555/doushi.html"

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
        SdkInfo("穿山甲", "北京巨量引擎网络技术有限公司"),
        SdkInfo("优量汇", "深圳市腾讯计算机系统有限公司"),
        SdkInfo("快手联盟", "北京快手广告有限公司"),
        SdkInfo("百度联盟", "北京百度网讯科技有限公司"),
        SdkInfo("Sigmob", "北京创智汇聚科技股份有限公司"),
        SdkInfo("优推", "北京乐游阳光科技有限公司"),
        SdkInfo("塔酷", "广州塔酷信息科技有限公司"),
        SdkInfo("Adgain", "北京数字悦动科技有限公司"),
        SdkInfo("天璇", "上海优比客思科技有限公司"),
        SdkInfo("Adview", "天津快友世纪科技有限公司"),
        SdkInfo("Adscope（倍孜）", "上海倍孜网络技术有限公司"),
        SdkInfo("UGdesk（多盟）", "多盟智胜网络技术（北京）有限公司"),
        SdkInfo("脉盟", "上海孛樊信息科技有限公司"),
        SdkInfo("Menta", "上海芒拓网络科技有限公司"),
        SdkInfo("美数", "北京美数信息科技有限公司"),
        SdkInfo("Funlink", "北京泛连科技有限公司"),
        SdkInfo("宸星", "湖北众宸嘉实信息科技有限公司"),
        SdkInfo("Oppo", "广东欢太科技有限公司"),
        SdkInfo("小米", "小米新加坡科技有限公司"),
        SdkInfo("Vivo", "广东天宸网络科技有限公司"),
        SdkInfo("鲸鸿动能（华为）", "华为终端有限公司"),
        SdkInfo("Qbmob", "杭州趣变网络科技有限公司"),
        SdkInfo("Advista", "上海推易软件技术有限公司"),
        SdkInfo("欢效", "北京欢效网络科技有限公司"),
        SdkInfo("MediaPrime", "北京泛为信息科技有限公司"),
        SdkInfo("聚推", "杭州推啊网络科技有限公司"),
        SdkInfo("Tap登录", "易玩（上海）网络科技有限公司")
    )

    val SDK_DETAILS: List<SdkDetail> = listOf(
        SdkDetail("优量汇SDK", "AndroidID、设备IP", "广告投放、广告效果监测、广告归因、反作弊"),
        SdkDetail("AdGain SDK", "AndroidID、OAID、WiFi的BSSID", "广告投放、广告归因、反作弊、广告效果统计"),
        SdkDetail("优推广告SDK", "OAID", "广告投放、广告效果统计、反作弊"),
        SdkDetail("Tap登录SDK", "AndroidID", "实现账号登录功能、登录状态维护及账号安全验证")
    )
}
