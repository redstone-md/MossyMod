package md.redstone.netty;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.util.concurrent.DefaultThreadFactory;
import md.redstone.Mossy;
import md.redstone.moss.MossManager;
import md.redstone.moss.MossTunnel;
import md.redstone.moss.TunnelEndpoint;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Netty Channel implementation over MOSS P2P tunnel.
 * 
 * This channel wraps a MossTunnel endpoint to provide
 * a Netty-compatible interface for Minecraft's networking.
 */
public class MossyChannel extends AbstractChannel {
    
    private static final ChannelMetadata METADATA = new ChannelMetadata(false, 16);
    private static final ExecutorService readExecutor = Executors.newCachedThreadPool(
        new DefaultThreadFactory("mossy-channel-read", true)
    );
    private static final ExecutorService closeExecutor = Executors.newCachedThreadPool(
        new DefaultThreadFactory("mossy-channel-close", true)
    );
    
    private final MossyChannelConfig config = new MossyChannelConfig(this);
    private final AtomicBoolean open = new AtomicBoolean(true);
    private final AtomicBoolean active = new AtomicBoolean(false);
    private final AtomicBoolean readPending = new AtomicBoolean(false);
    
    private volatile TunnelEndpoint tunnel;
    private volatile MossTunnel tunnelManager;
    private volatile MossySocketAddress remoteAddress;
    private volatile Thread readThread;
    
    public MossyChannel() {
        super(null);
    }
    
    public MossyChannel(Channel parent) {
        super(parent);
    }
    
    @Override
    public ChannelMetadata metadata() {
        return METADATA;
    }
    
    @Override
    public ChannelConfig config() {
        return config;
    }
    
    @Override
    public boolean isOpen() {
        return open.get();
    }
    
    @Override
    public boolean isActive() {
        return active.get() && tunnel != null && tunnel.isConnected();
    }
    
    @Override
    public SocketAddress localAddress() {
        return null;
    }
    
    @Override
    public SocketAddress remoteAddress() {
        return remoteAddress;
    }
    
    @Override
    protected AbstractUnsafe newUnsafe() {
        return new MossyUnsafe();
    }
    
    @Override
    protected boolean isCompatible(EventLoop loop) {
        return true;
    }
    
    @Override
    protected SocketAddress localAddress0() {
        return null;
    }
    
    @Override
    protected SocketAddress remoteAddress0() {
        return remoteAddress;
    }
    
    @Override
    protected void doBind(SocketAddress localAddress) {
        // Client channel doesn't bind
    }
    
    @Override
    protected void doDisconnect() {
        doClose();
    }
    
    @Override
    protected void doClose() {
        if (open.compareAndSet(true, false)) {
            active.set(false);
            MossyDebug.recordEvent("Closing client channel " + MossyDebug.describeAddress(remoteAddress));
            Mossy.LOGGER.info("Closing MOSS client channel for {}", MossyDebug.describeAddress(remoteAddress));
            if (readThread != null) {
                readThread.interrupt();
                readThread = null;
            }
            MossTunnel mgr = tunnelManager;
            TunnelEndpoint endpoint = tunnel;
            tunnelManager = null;
            tunnel = null;
            if (mgr != null || endpoint != null) {
                closeExecutor.submit(() -> {
                    try {
                        if (mgr != null) {
                            mgr.stop();
                        } else {
                            endpoint.close();
                        }
                    } catch (Exception e) {
                        Mossy.LOGGER.warn("MOSS client tunnel cleanup failed", e);
                    }
                });
            }
        }
    }
    
    @Override
    protected void doBeginRead() {
        if (!isActive() || !readPending.compareAndSet(false, true)) {
            return;
        }

        readExecutor.submit(() -> {
            readThread = Thread.currentThread();
            try {
                while (isActive() && readPending.get()) {
                    byte[] buffer = new byte[4096];
                    int read = tunnel.read(buffer);
                    
                    if (read < 0) {
                        MossyDebug.recordEvent("Client tunnel EOF " + MossyDebug.describeAddress(remoteAddress));
                        Mossy.LOGGER.info("MOSS client tunnel reached EOF for {}", MossyDebug.describeAddress(remoteAddress));
                        pipeline().fireChannelInactive();
                        break;
                    }
                    
                    if (read > 0) {
                        ByteBuf buf = Unpooled.wrappedBuffer(buffer, 0, read);
                        pipeline().fireChannelRead(buf);
                        pipeline().fireChannelReadComplete();
                    }
                }
            } catch (Exception e) {
                if (open.get()) {
                    MossyDebug.recordEvent("Client read loop failed");
                    Mossy.LOGGER.error("MOSS client read loop failed for {}", MossyDebug.describeAddress(remoteAddress), e);
                    pipeline().fireExceptionCaught(e);
                }
            } finally {
                readThread = null;
                readPending.set(false);
            }
        });
    }
    
    @Override
    protected void doWrite(ChannelOutboundBuffer in) throws Exception {
        if (!isActive()) {
            Object msg;
            ClosedChannelException closed = new ClosedChannelException();
            while ((msg = in.current()) != null) {
                in.remove(closed);
            }
            return;
        }

        while (true) {
            Object msg = in.current();
            if (msg == null) break;

            if (msg instanceof ByteBuf buf) {
                try {
                    if (buf.hasArray()) {
                        tunnel.write(buf.array(), buf.arrayOffset() + buf.readerIndex(), buf.readableBytes());
                    } else {
                        byte[] data = new byte[buf.readableBytes()];
                        buf.getBytes(buf.readerIndex(), data);
                        tunnel.write(data, 0, data.length);
                    }
                    in.remove();
                } catch (IOException e) {
                    in.remove(e);
                    if (open.get()) {
                        unsafe().close(unsafe().voidPromise());
                    }
                    return;
                }
            } else {
                in.remove(new UnsupportedOperationException("Unsupported message type: " + msg.getClass()));
            }
        }
    }
    
    @Override
    protected Object filterOutboundMessage(Object msg) {
        if (msg instanceof ByteBuf) {
            return msg;
        }
        throw new UnsupportedOperationException("Unsupported message type: " + msg.getClass());
    }
    
    private class MossyUnsafe extends AbstractUnsafe {
        @Override
        public void connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
            if (!ensureOpen(promise)) return;
            
            if (!(remoteAddress instanceof MossySocketAddress mossyAddr)) {
                promise.setFailure(new IllegalArgumentException("Expected MossySocketAddress"));
                return;
            }
            
            MossyChannel.this.remoteAddress = mossyAddr;
            
            eventLoop().execute(() -> {
                try {
                    MossManager moss = MossManager.getInstance();
                    if (!moss.isRunning()) {
                        promise.setFailure(new IOException("MOSS mesh not running"));
                        return;
                    }
                    
                    MossTunnel tunnelMgr = new MossTunnel(moss);
                    tunnelMgr.start();
                    
                    String peerId = mossyAddr.getMossyAddress().getPeerIdBase64();
                    String protocol = mossyAddr.getMossyAddress().getProtocol();
                    
                    tunnelManager = tunnelMgr;
                    tunnel = tunnelMgr.connect(peerId, protocol);
                    active.set(true);

                    Mossy.LOGGER.info(
                        "MOSS client tunnel established: remote={}, protocol={}",
                        MossyDebug.shorten(peerId),
                        protocol
                    );
                    MossyDebug.recordEvent("Client tunnel established to " + MossyDebug.shorten(peerId) + " via " + protocol);
                    pipeline().fireChannelActive();
                    promise.setSuccess();
                } catch (Exception e) {
                    if (tunnelManager != null) {
                        tunnelManager.stop();
                        tunnelManager = null;
                    }
                    promise.setFailure(e);
                }
            });
        }
    }
    
    private static class MossyChannelConfig extends DefaultChannelConfig {
        MossyChannelConfig(Channel channel) {
            super(channel);
        }
        
        @Override
        public ChannelConfig setConnectTimeoutMillis(int connectTimeoutMillis) {
            super.setConnectTimeoutMillis(connectTimeoutMillis);
            return this;
        }
    }
}
