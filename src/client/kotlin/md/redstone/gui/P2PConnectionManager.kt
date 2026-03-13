package md.redstone.gui

import md.redstone.Mossy
import md.redstone.moss.P2PWorldInfo
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.ConnectScreen
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.resolver.ServerAddress

/**
 * Direct TCP fallback for worlds that explicitly advertise a reachable address.
 */
object P2PConnectionManager {
    private val blockedHosts = setOf("localhost", "127.0.0.1", "0.0.0.0", "::1", "[::1]")
    
    /**
     * Returns true when the world exposes a usable direct address.
     */
    fun canConnectDirectly(
        world: P2PWorldInfo,
    ): Boolean {
        val host = sanitizeHost(world.hostAddress())
        return host.isNotEmpty() && host.lowercase() !in blockedHosts && world.port() in 1..65535
    }
    
    /**
     * Opens a direct TCP connection to the advertised world address.
     */
    fun connectDirect(world: P2PWorldInfo, mc: Minecraft, parentScreen: Screen): Boolean {
        if (!canConnectDirectly(world)) {
            return false
        }
        
        val host = sanitizeHost(world.hostAddress())
        val address = "$host:${world.port()}"
        val serverData = ServerData(world.worldName(), address, ServerData.Type.OTHER)
        val serverAddress = ServerAddress.parseString(address)

        Mossy.LOGGER.info("Opening direct TCP fallback to {} for '{}'", address, world.worldName())
        ConnectScreen.startConnecting(parentScreen, mc, serverAddress, serverData, false, null)
        return true
    }

    private fun sanitizeHost(host: String?): String {
        return host?.trim()?.removePrefix("[")?.removeSuffix("]") ?: ""
    }
}
