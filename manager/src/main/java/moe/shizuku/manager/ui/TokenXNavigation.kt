package moe.shizuku.manager.ui

enum class Detail {
    APP_OPS, SHELL, FIREWALL, AUTOSTART, STEALTH, TERMINAL, INTENTS, PERMISSIONS,
    APPEARANCE, TOKENX, GUIDE, CONNECTIONS, INTEGRATION, DEVICE, STARTUP, WIRELESS,
    ACCESS, THEME, ABOUT, SEARCH
}

enum class TokenXPage(val route: String, val title: String) {
    HOME("home", "Home"), APPS("apps", "Apps"), SYSTEM("system", "System"),
    TOOLS("tools", "Tools"), STYLE("style", "Style")
}

fun Detail.route() = "detail:$name"

fun validTokenXRoute(route: String): Boolean = TokenXPage.entries.any { it.route == route } ||
    (route.startsWith("detail:") && runCatching { Detail.valueOf(route.substringAfter(':')) }.isSuccess)

data class TokenXNavigationState(val current: String = "home", val history: List<String> = emptyList()) {
    fun navigate(route: String): TokenXNavigationState {
        require(validTokenXRoute(route))
        return if (route == current) this else TokenXNavigationState(route, (history + current).takeLast(30))
    }

    fun back(): TokenXNavigationState = if (history.isEmpty()) TokenXNavigationState()
        else TokenXNavigationState(history.last(), history.dropLast(1))
}

data class TokenXSearchEntry(val title: String, val summary: String, val category: String,
                             val destination: Detail, val keywords: String = "")

object TokenXSearchCatalog {
    val entries = listOf(
        TokenXSearchEntry("Backend connections", "Root, System, wireless and USB connection controls", "System", Detail.CONNECTIONS, "uid 0 uid 1000 uid 2000 connect heartbeat binder"),
        TokenXSearchEntry("Startup & recovery", "Boot startup, watchdog and start method", "System", Detail.STARTUP, "restart reliability boot guardian auto disable debugging"),
        TokenXSearchEntry("ADB & pairing", "TCP port, persistence and legacy pairing", "System", Detail.WIRELESS, "wireless wifi usb network debugging"),
        TokenXSearchEntry("System integration", "Live integration identity and diagnostics", "System", Detail.INTEGRATION, "system server framework uid 1000 binder"),
        TokenXSearchEntry("Control Center", "Routing, Boot Guardian and framework operations", "System", Detail.TOKENX, "d2 power restart root xposed lsposed"),
        TokenXSearchEntry("Device & runtime", "Manager version, kernel and Android security state", "System", Detail.DEVICE, "selinux seccomp fingerprint model firmware"),
        TokenXSearchEntry("Interactive shell", "Open the built-in shell session", "Tools", Detail.SHELL, "terminal commands console"),
        TokenXSearchEntry("RISH v3 export", "Export matching rish and rish_shizuku.dex files", "Tools", Detail.TERMINAL, "root system shell uid termux bin protocol"),
        TokenXSearchEntry("App operations", "Inspect and manage application operations", "Tools", Detail.APP_OPS, "appops access permissions"),
        TokenXSearchEntry("Firewall", "Application network controls", "Tools", Detail.FIREWALL, "internet wifi data"),
        TokenXSearchEntry("Autostart", "Application startup controls", "Tools", Detail.AUTOSTART, "boot apps"),
        TokenXSearchEntry("Permissions", "Battery optimization and required app access", "Tools", Detail.PERMISSIONS, "authorization notifications local network background"),
        TokenXSearchEntry("Stealth", "Manager visibility settings", "Tools", Detail.STEALTH, "hide package"),
        TokenXSearchEntry("Intents", "Integration actions and commands", "Tools", Detail.INTENTS, "adb shortcuts automation"),
        TokenXSearchEntry("Access & utilities", "Permission, visibility and integration shortcuts", "Tools", Detail.ACCESS),
        TokenXSearchEntry("Setup guide", "TokenX usage and backend guidance", "Tools", Detail.GUIDE, "help install instructions"),
        TokenXSearchEntry("Appearance Studio", "Glass, backgrounds, navigation and presets", "Style", Detail.APPEARANCE, "colors opacity blur radius floating bar dock core dark light wine red black"),
        TokenXSearchEntry("Theme & language", "Brightness, color theme and app language", "Style", Detail.THEME, "dark light amoled contrast translate"),
        TokenXSearchEntry("About TokenX", "Version, package information and updates", "Tools", Detail.ABOUT, "credits license source developer"),
    )

    fun search(query: String): List<TokenXSearchEntry> {
        val words = query.trim().lowercase(java.util.Locale.ROOT).split(Regex("\\s+")).filter { it.isNotEmpty() }
        return entries.filter { entry ->
            val text = "${entry.title} ${entry.summary} ${entry.category} ${entry.keywords}".lowercase(java.util.Locale.ROOT)
            words.all { it in text }
        }
    }
}

/** Reserve the complete dock including its configurable gap and a content margin. */
fun dockReservedHeight(height: Float, gap: Float): Float = height.coerceIn(56f, 78f) +
    2f * (8f + gap.coerceIn(0f, 28f)) + 16f

/** Keep all five tab targets at least 48dp wide whenever the window permits it. */
fun dockWidth(available: Float, requestedFraction: Float, core: Float): Float =
    maxOf(available * requestedFraction.coerceIn(.62f, 1f), 5f * 48f + 8f + core).coerceAtMost(available)
