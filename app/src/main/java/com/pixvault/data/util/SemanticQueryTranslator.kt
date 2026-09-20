package com.pixvault.data.util

data class NormalizedSemanticQuery(
    val original: String,
    val encoderText: String,
    val translated: Boolean
)

/** Offline Chinese-to-English concept mapping for the bundled English-first CLIP encoder. */
object SemanticQueryTranslator {
    private val concepts = linkedMapOf(
        "海边的日落" to "sunset at the beach",
        "雨天窗边的猫" to "cat by the window on a rainy day",
        "夜晚城市灯光" to "city lights at night",
        "樱花下的合照" to "group photo under cherry blossoms",
        "桌上的咖啡与书" to "coffee and book on a table",
        "穿蓝色衣服的人" to "person wearing blue clothes",
        "紫色天空和云" to "purple sky and clouds",
        "合照" to "group photo", "自拍" to "selfie", "截图" to "screenshot",
        "二次元" to "anime", "插画" to "illustration", "玩偶" to "plush toy",
        "小女孩" to "little girl", "小男孩" to "little boy", "女孩" to "girl",
        "男孩" to "boy", "女生" to "woman", "男生" to "man", "女人" to "woman",
        "男人" to "man", "小孩" to "child", "宝宝" to "baby", "家人" to "family",
        "朋友" to "friends", "人物" to "person", "人" to "person",
        "小猫" to "cat", "猫" to "cat", "小狗" to "dog", "狗" to "dog",
        "兔子" to "rabbit", "鸟" to "bird",
        "海边" to "seaside", "海滩" to "beach", "大海" to "ocean",
        "日落" to "sunset", "夕阳" to "sunset", "日出" to "sunrise",
        "夜景" to "night view", "夜晚" to "night", "天空" to "sky", "云" to "clouds",
        "城市" to "city", "街道" to "street", "建筑" to "building", "房间" to "room",
        "室内" to "indoors", "窗边" to "window", "桌上" to "on a table", "桌子" to "table",
        "高山" to "mountain", "山" to "mountain", "雪景" to "snowy landscape",
        "雪" to "snow", "雨天" to "rainy day", "下雨" to "rain",
        "森林" to "forest", "草地" to "grassland", "湖" to "lake", "河" to "river",
        "樱花" to "cherry blossoms", "鲜花" to "flowers", "花" to "flowers",
        "美食" to "food", "食物" to "food", "咖啡" to "coffee", "蛋糕" to "cake",
        "汽车" to "car", "书" to "book",
        "红色" to "red", "橙色" to "orange", "黄色" to "yellow", "绿色" to "green",
        "蓝色" to "blue", "紫色" to "purple", "粉色" to "pink", "白色" to "white",
        "黑色" to "black", "穿着" to "wearing", "穿" to "wearing", "站着" to "standing",
        "坐着" to "sitting", "跑步" to "running", "微笑" to "smiling", "笑" to "smiling",
        "吃饭" to "eating", "喝水" to "drinking", "可爱" to "cute", "漂亮" to "beautiful",
        "明亮" to "bright", "黑暗" to "dark", "模糊" to "blurry"
    )

    fun normalize(raw: String): NormalizedSemanticQuery {
        val original = raw.trim()
        if (original.none { it.code in 0x3400..0x9FFF }) {
            return NormalizedSemanticQuery(original, original, false)
        }
        var remaining = original.lowercase()
        val terms = mutableListOf<String>()
        concepts.entries.sortedByDescending { it.key.length }.forEach { (chinese, english) ->
            if (remaining.contains(chinese)) {
                terms += english
                remaining = remaining.replace(chinese, " ")
            }
        }
        val ascii = remaining.replace(Regex("[^a-z0-9]+"), " ").trim()
        if (ascii.isNotEmpty()) terms += ascii
        val encoderText = terms.distinct().joinToString(" ").ifBlank { "photo" }
        return NormalizedSemanticQuery(original, encoderText, true)
    }
}
