package moe.shizuku.manager.tokenx

import android.content.Context

enum class TokenXAppRoute {
    AUTO,
    ROOT,
    SYSTEM,
}

object TokenXAppRouting {
    private const val PREFS = "tokenx_app_routes"
    private fun key(packageName: String) = "route:$packageName"

    fun get(context: Context, packageName: String): TokenXAppRoute {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(key(packageName), TokenXAppRoute.AUTO.name)
        return runCatching { TokenXAppRoute.valueOf(raw ?: TokenXAppRoute.AUTO.name) }
            .getOrDefault(TokenXAppRoute.AUTO)
    }

    fun set(context: Context, packageName: String, route: TokenXAppRoute) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(key(packageName), route.name)
            .apply()
    }

    fun preferredBackend(context: Context, packageName: String): TokenXBackend? =
        when (get(context, packageName)) {
            TokenXAppRoute.AUTO -> null
            TokenXAppRoute.ROOT -> TokenXBackend.ROOT
            TokenXAppRoute.SYSTEM -> TokenXBackend.SYSTEM_SERVER
        }

    fun resolve(
        context: Context,
        packageName: String,
        capability: TokenXCapability,
        state: TokenXBackendState,
    ): TokenXRoute = TokenXRouter.route(
        capability = capability,
        state = state,
        preferredBackend = preferredBackend(context, packageName),
    )
}
