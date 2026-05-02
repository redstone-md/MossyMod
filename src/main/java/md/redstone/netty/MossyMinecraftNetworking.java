package md.redstone.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelOption;
import io.netty.channel.DefaultEventLoopGroup;
import md.redstone.Mossy;
import md.redstone.moss.TunnelEndpoint;
import net.minecraft.network.Connection;
import net.minecraft.network.RateKickingConnection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerHandshakePacketListenerImpl;


/**
 * Bridges the custom MOSS transport with Minecraft's Netty-based Connection setup.
 */
public final class MossyMinecraftNetworking {
    private static final DefaultEventLoopGroup CLIENT_EVENT_LOOPS = new DefaultEventLoopGroup(0);
    private static final DefaultEventLoopGroup SERVER_CHILD_EVENT_LOOPS = new DefaultEventLoopGroup(0);

    private MossyMinecraftNetworking() {
    }

    public static ChannelFuture connectClient(MossySocketAddress address, boolean useNativeTransport, Connection connection) {
        MossyDebug.recordEvent(
            "Creating client transport to " + address.getMossyAddress().getShortId() + " via " + address.getMossyAddress().getProtocol()
        );
        Mossy.LOGGER.info(
            "Creating client P2P transport to peer {} via protocol {}",
            address.getMossyAddress().getPeerIdBase64().substring(0, Math.min(8, address.getMossyAddress().getPeerIdBase64().length())),
            address.getMossyAddress().getProtocol()
        );
        return new Bootstrap()
            .group(CLIENT_EVENT_LOOPS)
            .handler(new ChannelInitializer<>() {
                @Override
                protected void initChannel(Channel channel) {
                    try {
                        channel.config().setOption(ChannelOption.TCP_NODELAY, true);
                    } catch (ChannelException ignored) {
                    }

                    ChannelPipeline pipeline = channel.pipeline();
                    //? if >=1.20.5 {
                    Connection.configureSerialization(pipeline, PacketFlow.CLIENTBOUND, false, null);
                    connection.configurePacketHandler(pipeline);
                    //?} else {
                    /*Connection.configureSerialization(pipeline, PacketFlow.CLIENTBOUND);
                    pipeline.addLast("packet_handler", connection);
                    *///?}
                }
            })
            .channel(MossyChannel.class)
            .connect(address);
    }

    public static Connection attachServerConnection(MinecraftServer server, TunnelEndpoint endpoint) {
        MossyDebug.recordEvent("Attaching incoming transport " + MossyDebug.describeEndpoint(endpoint));
        Mossy.LOGGER.info(
            "Attaching incoming P2P transport from peer {} on protocol {}",
            endpoint.getRemotePeerId().substring(0, Math.min(8, endpoint.getRemotePeerId().length())),
            endpoint.getProtocol()
        );
        int rateLimit = server.getRateLimitPacketsPerSecond();
        Connection connection = rateLimit > 0
            ? new RateKickingConnection(rateLimit)
            : new Connection(PacketFlow.SERVERBOUND);
        MossyDebug.recordEvent("Created server Connection for " + MossyDebug.describeEndpoint(endpoint));

        MossyServerChildChannel channel = new MossyServerChildChannel(null, endpoint);
        ChannelPipeline pipeline = channel.pipeline();
        //? if >=1.20.5 {
        Connection.configureSerialization(pipeline, PacketFlow.SERVERBOUND, false, null);
        //?} else {
        /*Connection.configureSerialization(pipeline, PacketFlow.SERVERBOUND);
        *///?}
        MossyDebug.recordEvent("Configured server pipeline for " + MossyDebug.describeEndpoint(endpoint));

        server.getConnection().getConnections().add(connection);
        //? if >=1.20.5 {
        connection.configurePacketHandler(pipeline);
        connection.setListenerForServerboundHandshake(new ServerHandshakePacketListenerImpl(server, connection));
        //?} else {
        /*pipeline.addLast("packet_handler", connection);
        connection.setListener(new ServerHandshakePacketListenerImpl(server, connection));
        *///?}
        MossyDebug.recordEvent("Installed handshake listener for " + MossyDebug.describeEndpoint(endpoint));
        SERVER_CHILD_EVENT_LOOPS.register(channel).syncUninterruptibly();
        MossyDebug.recordEvent("Registered server child channel for " + MossyDebug.describeEndpoint(endpoint));
        channel.activate();
        MossyDebug.recordEvent("Activated server child channel for " + MossyDebug.describeEndpoint(endpoint));
        return connection;
    }
}
