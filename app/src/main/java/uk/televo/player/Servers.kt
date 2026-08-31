package uk.televo.player

/**
 * Servers shown on the login screen.
 *
 * The live list comes from the admin panel (panel.televo.uk) so you can add,
 * edit or remove servers WITHOUT rebuilding the app — see Api.fetchHosts().
 * The list below is only a fallback used the very first time, before the app
 * has ever reached the panel.
 */
object Servers {

    /** Admin panel base URL. The app reads the host list from here. */
    const val PANEL = "https://panel.televo.uk"

    data class Server(val name: String, val baseUrl: String)

    /** First-run fallback (used only until the panel list is fetched/cached). */
    val FALLBACK: List<Server> = listOf(
        Server("Server 1", "http://vpn.mydnhost.com"),
        Server("Server 2", "http://vpn.cloudplayme.com"),
        Server("Server 3", "http://vpn.watchmenow.xyz"),
        Server("Server 4", "http://vpn.mydnwatch.net"),
        Server("Server 5", "http://vpn.dnwatchnow.com")
    )
}
