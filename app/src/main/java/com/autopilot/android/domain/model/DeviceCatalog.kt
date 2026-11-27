package com.autopilot.android.domain.model

import kotlin.random.Random

enum class DeviceCategory(val label: String) {
    ALL("All / Random"),
    WINDOWS("Windows"),
    ANDROID("Android"),
    IPHONE("iPhone"),
    MACBOOK_SILICON("MacBook Silicon"),
    MACBOOK_INTEL("MacBook Intel"),
    LINUX("Linux")
}

data class DeviceProfile(
    val id: String,
    val label: String,
    val category: DeviceCategory,
    val userAgent: String,
    val platformHint: String,
    val isMobile: Boolean,
    val model: String,
    val manufacturer: String,
    val versionRelease: String
)

/** Static device identities shared by traffic runs and automation profiles. */
object DeviceCatalog {

    private fun android(
        label: String, model: String, manufacturer: String,
        androidVersion: String, build: String, chrome: String
    ) = DeviceProfile(
        id = "android-$label",
        label = label,
        category = DeviceCategory.ANDROID,
        userAgent = "Mozilla/5.0 (Linux; Android $androidVersion; $model Build/$build) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chrome Mobile Safari/537.36",
        platformHint = "Android",
        isMobile = true,
        model = model,
        manufacturer = manufacturer,
        versionRelease = androidVersion
    )

    private fun iphone(label: String, ios: String) = DeviceProfile(
        id = "iphone-$label",
        label = label,
        category = DeviceCategory.IPHONE,
        userAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS ${ios.replace('.', '_')} like Mac OS X) " +
            "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/${ios.substringBeforeLast('.')} Mobile/15E148 Safari/604.1",
        platformHint = "iOS",
        isMobile = true,
        model = label,
        manufacturer = "Apple",
        versionRelease = ios
    )

    private fun mac(label: String, category: DeviceCategory, browser: String) = DeviceProfile(
        id = "${category.name.lowercase()}-$label-$browser",
        label = "$label ($browser)",
        category = category,
        userAgent = when (browser) {
            "Chrome" -> "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
            "Firefox" -> "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:125.0) Gecko/20100101 Firefox/125.0"
            else -> "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 " +
                "(KHTML, like Gecko) Version/17.4 Safari/605.1.15"
        },
        platformHint = "macOS",
        isMobile = false,
        model = label,
        manufacturer = "Apple",
        versionRelease = "14.4"
    )

    private fun windows(label: String, browser: String) = DeviceProfile(
        id = "windows-$label-$browser",
        label = "$label ($browser)",
        category = DeviceCategory.WINDOWS,
        userAgent = when (browser) {
            "Edge" -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36 Edg/124.0.0.0"
            "Firefox" -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:125.0) Gecko/20100101 Firefox/125.0"
            else -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        },
        platformHint = "Windows",
        isMobile = false,
        model = label,
        manufacturer = label.substringBefore(' '),
        versionRelease = "10.0"
    )

    private fun linux(label: String, browser: String) = DeviceProfile(
        id = "linux-$label-$browser",
        label = "$label ($browser)",
        category = DeviceCategory.LINUX,
        userAgent = if (browser == "Firefox") {
            "Mozilla/5.0 (X11; Linux x86_64; rv:125.0) Gecko/20100101 Firefox/125.0"
        } else {
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        },
        platformHint = "Linux",
        isMobile = false,
        model = label,
        manufacturer = "Linux",
        versionRelease = "6.8"
    )

    private val WINDOWS_DEVICES = listOf(
        windows("Surface Laptop 5", "Edge"), windows("Surface Pro 9", "Chrome"),
        windows("Surface Book 3", "Firefox"), windows("Surface Go 4", "Edge"),
        windows("Dell XPS 13", "Chrome"), windows("Dell XPS 15", "Edge"),
        windows("Dell Latitude 7440", "Chrome"), windows("HP Spectre x360", "Edge"),
        windows("HP Envy 16", "Chrome"), windows("HP Omen 17", "Firefox"),
        windows("Lenovo ThinkPad X1 Carbon", "Chrome"), windows("Lenovo ThinkPad T14", "Edge"),
        windows("Lenovo Yoga 9i", "Chrome"), windows("ASUS ZenBook 14", "Edge"),
        windows("ASUS ROG Zephyrus", "Chrome"), windows("Acer Swift Go 14", "Chrome"),
        windows("MSI Katana 15", "Edge"), windows("Razer Blade 15", "Chrome")
    )

    private val ANDROID_DEVICES = listOf(
        android("Pixel 10 Pro", "Pixel 10 Pro", "Google", "15", "BP2A.250605.015", "138.0.7204.168"),
        android("Pixel 10", "Pixel 10", "Google", "15", "BP2A.250605.015", "138.0.7204.168"),
        android("Pixel 9 Pro Fold", "Pixel 9 Pro Fold", "Google", "14", "AD1A.240805.005", "127.0.6533.103"),
        android("Pixel 9 Pro", "Pixel 9 Pro", "Google", "14", "AD1A.240805.005", "127.0.6533.103"),
        android("Pixel 9", "Pixel 9", "Google", "14", "AD1A.240805.005", "127.0.6533.103"),
        android("Pixel 8 Pro", "Pixel 8 Pro", "Google", "14", "UQ1A.240205.004", "124.0.6367.113"),
        android("Pixel 8", "Pixel 8", "Google", "14", "UQ1A.240205.004", "124.0.6367.113"),
        android("Pixel 8a", "Pixel 8a", "Google", "14", "UQ1A.240205.004", "124.0.6367.113"),
        android("Pixel 7 Pro", "Pixel 7 Pro", "Google", "14", "TQ3A.240205.002", "122.0.6261.119"),
        android("Pixel 7", "Pixel 7", "Google", "13", "TQ2A.230505.002", "121.0.6167.143"),
        android("Pixel 7a", "Pixel 7a", "Google", "13", "TQ2A.230505.002", "121.0.6167.143"),
        android("Pixel 6 Pro", "Pixel 6 Pro", "Google", "14", "UP1A.231005.007", "120.0.6099.144"),
        android("Pixel 6a", "Pixel 6a", "Google", "13", "TQ2A.230505.002", "120.0.6099.144"),
        android("Pixel Tablet", "Pixel Tablet", "Google", "14", "UQ1A.240205.004", "124.0.6367.113"),
        android("Galaxy S24 Ultra", "SM-S928B", "samsung", "14", "UP1A.231005.007", "124.0.6367.113"),
        android("Galaxy S24+", "SM-S926B", "samsung", "14", "UP1A.231005.007", "124.0.6367.113"),
        android("Galaxy S24", "SM-S921B", "samsung", "14", "UP1A.231005.007", "124.0.6367.113"),
        android("Galaxy S23 Ultra", "SM-S918B", "samsung", "14", "TP1A.220624.014", "123.0.6312.118"),
        android("Galaxy S23+", "SM-S916B", "samsung", "13", "TP1A.220624.014", "122.0.6261.119"),
        android("Galaxy S23", "SM-S911B", "samsung", "13", "TP1A.220624.014", "122.0.6261.119"),
        android("Galaxy S22 Ultra", "SM-S908B", "samsung", "13", "TP1A.220624.014", "121.0.6167.143"),
        android("Galaxy S22", "SM-S901B", "samsung", "13", "TP1A.220624.014", "121.0.6167.143"),
        android("Galaxy S21 FE", "SM-G990B", "samsung", "12", "SP1A.210812.016", "120.0.6099.144"),
        android("Galaxy A55", "SM-A556B", "samsung", "14", "UP1A.231005.007", "124.0.6367.113"),
        android("Galaxy A54", "SM-A546B", "samsung", "14", "UP1A.231005.007", "122.0.6261.119"),
        android("Galaxy A53", "SM-A536B", "samsung", "13", "TP1A.220624.014", "120.0.6099.144"),
        android("Galaxy A35", "SM-A356B", "samsung", "14", "UP1A.231005.007", "123.0.6312.118"),
        android("Galaxy A34", "SM-A346B", "samsung", "13", "TP1A.220624.014", "121.0.6167.143"),
        android("Galaxy A25", "SM-A256B", "samsung", "13", "TP1A.220624.014", "120.0.6099.144"),
        android("Galaxy A15", "SM-A155F", "samsung", "14", "UP1A.231005.007", "122.0.6261.119"),
        android("Galaxy Z Fold5", "SM-F946B", "samsung", "14", "UP1A.231005.007", "124.0.6367.113"),
        android("Galaxy Z Flip5", "SM-F946B", "samsung", "14", "UP1A.231005.007", "124.0.6367.113"),
        android("Galaxy Tab S9", "SM-X710", "samsung", "14", "UP1A.231005.007", "123.0.6312.118"),
        android("Galaxy Tab A9+", "SM-X210", "samsung", "13", "TP1A.220624.014", "121.0.6167.143"),
        android("OnePlus 12", "CPH2581", "OnePlus", "14", "UP1A.231005.007", "124.0.6367.113"),
        android("OnePlus 12R", "CPH2585", "OnePlus", "14", "UP1A.231005.007", "124.0.6367.113"),
        android("OnePlus 11", "CPH2451", "OnePlus", "13", "TP1A.220624.014", "122.0.6261.119"),
        android("OnePlus Nord 3", "CPH2491", "OnePlus", "13", "TP1A.220624.014", "121.0.6167.143"),
        android("OnePlus Nord CE3", "CPH2565", "OnePlus", "13", "TP1A.220624.014", "120.0.6099.144"),
        android("Xiaomi 14 Pro", "23161PT47C", "Xiaomi", "14", "UQ1A.240205.004", "124.0.6367.113"),
        android("Xiaomi 14", "23127PN0CG", "Xiaomi", "14", "UQ1A.240205.004", "124.0.6367.113"),
        android("Xiaomi 13", "2211133C", "Xiaomi", "13", "TP1A.220624.014", "122.0.6261.119"),
        android("Redmi Note 13 Pro", "231013P11C", "Xiaomi", "13", "TP1A.220624.014", "121.0.6167.143"),
        android("Redmi Note 12", "22111317C", "Xiaomi", "12", "SP1A.210812.016", "120.0.6099.144"),
        android("POCO X5 Pro", "2211167PC", "Xiaomi", "12", "SP1A.210812.016", "120.0.6099.144"),
        android("Realme 12 Pro+", "RMX3840", "realme", "14", "UP1A.231005.007", "123.0.6312.118"),
        android("Realme 11 5G", "RMX3780", "realme", "13", "TP1A.220624.014", "121.0.6167.143"),
        android("Realme Narzo 60", "RMX3771", "realme", "13", "TP1A.220624.014", "120.0.6099.144"),
        android("OPPO Reno11 Pro", "CPH2521", "OPPO", "14", "UP1A.231005.007", "123.0.6312.118"),
        android("OPPO Find X6", "CPH2573", "OPPO", "13", "TP1A.220624.014", "122.0.6261.119"),
        android("OPPO A78", "CPH2569", "OPPO", "13", "TP1A.220624.014", "120.0.6099.144"),
        android("vivo X100 Pro", "V2312A", "vivo", "14", "UP1A.231005.007", "124.0.6367.113"),
        android("vivo V30", "V2318", "vivo", "14", "UP1A.231005.007", "123.0.6312.118"),
        android("vivo Y28", "V2217", "vivo", "13", "TP1A.220624.014", "121.0.6167.143"),
        android("Moto Edge 2024", "PB3B0000", "motorola", "14", "UQ1A.240205.004", "124.0.6367.113"),
        android("Moto G Play 2024", "PB3B0002", "motorola", "13", "TP1A.220624.014", "121.0.6167.143"),
        android("Moto Razr 40 Ultra", "PB3B0004", "motorola", "13", "TP1A.220624.014", "122.0.6261.119"),
        android("Huawei P60 Pro", "LNA-AL00", "HUAWEI", "13", "TP1A.220624.014", "121.0.6167.143"),
        android("Huawei Mate 60 Pro", "ALN-AL00", "HUAWEI", "13", "TP1A.220624.014", "121.0.6167.143"),
        android("Honor Magic6 Pro", "BLP-AN00", "HONOR", "14", "UP1A.231005.007", "123.0.6312.118"),
        android("Honor X7b", "CLK-AN00", "HONOR", "13", "TP1A.220624.014", "121.0.6167.143"),
        android("Nothing Phone (2a)", "A142", "Nothing", "14", "UP1A.231005.007", "124.0.6367.113"),
        android("Sony Xperia 1 VI", "XQ-EC44", "Sony", "14", "UQ1A.240205.004", "124.0.6367.113")
    )

    private val IPHONE_DEVICES = listOf(
        iphone("iPhone 17 Pro Max", "18.0"), iphone("iPhone 17 Pro", "18.0"),
        iphone("iPhone 17", "18.0"), iphone("iPhone Air", "18.0"),
        iphone("iPhone 16 Pro Max", "17.6.1"), iphone("iPhone 16 Pro", "17.6.1"),
        iphone("iPhone 16 Plus", "17.6.1"), iphone("iPhone 16", "17.6.1"),
        iphone("iPhone 15 Pro Max", "17.4.1"), iphone("iPhone 15 Pro", "17.4.1"),
        iphone("iPhone 15 Plus", "17.4.1"), iphone("iPhone 15", "17.4.1"),
        iphone("iPhone 14 Pro Max", "17.4.1"), iphone("iPhone 14 Pro", "17.4.1"),
        iphone("iPhone 14 Plus", "17.4.1"), iphone("iPhone 14", "17.4.1"),
        iphone("iPhone 13 Pro Max", "17.4.1"), iphone("iPhone 13 Pro", "17.4.1"),
        iphone("iPhone 13 mini", "17.4.1"), iphone("iPhone 13", "17.4.1"),
        iphone("iPhone 12 Pro Max", "17.4.1"), iphone("iPhone 12 Pro", "17.4.1"),
        iphone("iPhone 12 mini", "17.4.1"), iphone("iPhone 12", "17.4.1"),
        iphone("iPhone 11 Pro Max", "17.4.1"), iphone("iPhone 11 Pro", "17.4.1"),
        iphone("iPhone 11", "17.4.1"), iphone("iPhone XS Max", "16.7.6"),
        iphone("iPhone XS", "16.7.6"), iphone("iPhone XR", "16.7.6"),
        iphone("iPhone X", "16.7.6"), iphone("iPhone 8 Plus", "16.7.6"),
        iphone("iPhone 8", "16.7.6"), iphone("iPhone 7 Plus", "15.8.3"),
        iphone("iPhone 7", "15.8.3"), iphone("iPhone 6s Plus", "15.8.3"),
        iphone("iPhone 6s", "15.8.3"), iphone("iPhone SE (3rd gen)", "17.4.1"),
        iphone("iPhone SE (2nd gen)", "16.7.6"), iphone("iPhone SE (1st gen)", "15.8.3")
    )

    private val MAC_SILICON_DEVICES = listOf(
        mac("MacBook Air M1 13", DeviceCategory.MACBOOK_SILICON, "Safari"),
        mac("MacBook Air M1 13", DeviceCategory.MACBOOK_SILICON, "Chrome"),
        mac("MacBook Pro M1 13", DeviceCategory.MACBOOK_SILICON, "Safari"),
        mac("MacBook Pro M1 14", DeviceCategory.MACBOOK_SILICON, "Chrome"),
        mac("MacBook Pro M1 16", DeviceCategory.MACBOOK_SILICON, "Safari"),
        mac("MacBook Air M2 13", DeviceCategory.MACBOOK_SILICON, "Safari"),
        mac("MacBook Air M2 15", DeviceCategory.MACBOOK_SILICON, "Chrome"),
        mac("MacBook Pro M2 13", DeviceCategory.MACBOOK_SILICON, "Safari"),
        mac("MacBook Pro M2 14", DeviceCategory.MACBOOK_SILICON, "Firefox"),
        mac("MacBook Pro M2 16", DeviceCategory.MACBOOK_SILICON, "Chrome"),
        mac("MacBook Air M3 13", DeviceCategory.MACBOOK_SILICON, "Safari"),
        mac("MacBook Air M3 15", DeviceCategory.MACBOOK_SILICON, "Chrome"),
        mac("MacBook Pro M3 14", DeviceCategory.MACBOOK_SILICON, "Safari"),
        mac("MacBook Pro M3 16", DeviceCategory.MACBOOK_SILICON, "Chrome"),
        mac("MacBook Pro M3 Max 14", DeviceCategory.MACBOOK_SILICON, "Safari"),
        mac("MacBook Pro M3 Max 16", DeviceCategory.MACBOOK_SILICON, "Firefox"),
        mac("MacBook Air M4 13", DeviceCategory.MACBOOK_SILICON, "Safari"),
        mac("MacBook Air M4 15", DeviceCategory.MACBOOK_SILICON, "Chrome"),
        mac("MacBook Pro M4 14", DeviceCategory.MACBOOK_SILICON, "Safari"),
        mac("MacBook Pro M4 16", DeviceCategory.MACBOOK_SILICON, "Chrome"),
        mac("MacBook Pro M4 Max 14", DeviceCategory.MACBOOK_SILICON, "Safari"),
        mac("MacBook Pro M4 Max 16", DeviceCategory.MACBOOK_SILICON, "Firefox")
    )

    private val MAC_INTEL_DEVICES = listOf(
        mac("MacBook 12-inch 2019", DeviceCategory.MACBOOK_INTEL, "Safari"),
        mac("MacBook Air 13 2017", DeviceCategory.MACBOOK_INTEL, "Chrome"),
        mac("MacBook Air 13 2018", DeviceCategory.MACBOOK_INTEL, "Safari"),
        mac("MacBook Air 13 2019", DeviceCategory.MACBOOK_INTEL, "Chrome"),
        mac("MacBook Air 13 2020", DeviceCategory.MACBOOK_INTEL, "Firefox"),
        mac("MacBook Pro 13 2016", DeviceCategory.MACBOOK_INTEL, "Safari"),
        mac("MacBook Pro 13 2017", DeviceCategory.MACBOOK_INTEL, "Chrome"),
        mac("MacBook Pro 13 2018", DeviceCategory.MACBOOK_INTEL, "Safari"),
        mac("MacBook Pro 13 2019", DeviceCategory.MACBOOK_INTEL, "Chrome"),
        mac("MacBook Pro 13 2020", DeviceCategory.MACBOOK_INTEL, "Firefox"),
        mac("MacBook Pro 15 2016", DeviceCategory.MACBOOK_INTEL, "Safari"),
        mac("MacBook Pro 15 2017", DeviceCategory.MACBOOK_INTEL, "Chrome"),
        mac("MacBook Pro 15 2018", DeviceCategory.MACBOOK_INTEL, "Safari"),
        mac("MacBook Pro 15 2019", DeviceCategory.MACBOOK_INTEL, "Chrome"),
        mac("MacBook Pro 16 2019", DeviceCategory.MACBOOK_INTEL, "Safari")
    )

    private val LINUX_DEVICES = listOf(
        linux("Ubuntu 24.04", "Chrome"), linux("Ubuntu 24.04", "Firefox"),
        linux("Fedora 40", "Firefox"), linux("Fedora 40", "Chrome"),
        linux("Debian 12", "Firefox"), linux("Arch Linux", "Chrome"),
        linux("Manjaro 24", "Firefox"), linux("Linux Mint 22", "Chrome"),
        linux("openSUSE Leap 15.6", "Firefox"), linux("Pop!_OS 22.04", "Chrome"),
        linux("elementary OS 7", "Chrome"), linux("KDE Neon 6", "Firefox")
    )

    val ALL_PROFILES: List<DeviceProfile> = WINDOWS_DEVICES + ANDROID_DEVICES + IPHONE_DEVICES +
        MAC_SILICON_DEVICES + MAC_INTEL_DEVICES + LINUX_DEVICES

    fun forCategory(category: DeviceCategory): List<DeviceProfile> =
        if (category == DeviceCategory.ALL) ALL_PROFILES else ALL_PROFILES.filter { it.category == category }

    fun count(category: DeviceCategory): Int = forCategory(category).size

    fun random(category: DeviceCategory = DeviceCategory.ALL): DeviceProfile {
        val pool = forCategory(category)
        return pool[Random.nextInt(pool.size)]
    }
}
