package uk.televo.player

/**
 * The list of servers the customer can pick from on the login screen.
 *
 * ┌─────────────────────────────────────────────────────────────────────┐
 * │  EDIT THESE THREE URLs.                                              │
 * │                                                                     │
 * │  Put the real Xtream line for each server as  http://HOST:PORT      │
 * │  (the part BEFORE /player_api.php or /get.php in a working line).    │
 * │  No trailing slash. Keep http:// or https:// exactly as the         │
 * │  provider gives it.                                                  │
 * │                                                                     │
 * │  Example of a real value:  http://line.myprovider.tv:8080           │
 * └─────────────────────────────────────────────────────────────────────┘
 */
object Servers {

    data class Server(val name: String, val baseUrl: String)

    val ALL: List<Server> = listOf(
        Server("Server 1", "http://vpn.mydnhost.com"),
        Server("Server 2", "http://vpn.cloudplayme.com"),
        Server("Server 3", "http://vpn.watchmenow.xyz"),
        Server("Server 4", "http://vpn.mydnwatch.net"),
        Server("Server 5", "http://vpn.dnwatchnow.com")
    )

    val names: List<String> get() = ALL.map { it.name }

    fun at(index: Int): Server? = ALL.getOrNull(index)

    /** Find the saved server by its base URL (so Home can show the right name). */
    fun byBaseUrl(url: String?): Server? = ALL.firstOrNull { it.baseUrl == url }
}
