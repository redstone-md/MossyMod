package md.redstone.netty;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.util.concurrent.DefaultThreadFactory;

import java.nio.channels.ClosedChannelException;
import md.redstone.Mossy;
import md.redstone.moss.TunnelEndpoint;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Child channel created by MossyServerChannel for accepted connections.
 */
public class MossyServerChildChannel extends AbstractChannel {

    private static final ChannelMetadata METADATA = new ChannelMetadata(false, 16);
    private static final ExecutorService readExecutor = Executors.newCachedThreadPool(
        new DefaultThreadFactory("mossy-child-read", true)
    );
    private static final ExecutorService closeExecutor = Executors.newCachedThreadPool(
        new DefaultThreadFactory("mossy-child-close", true)
    );
    
    private final ChannelConfig config = new DefaultChannelConfig(this);
    private final AtomicBoolean open = new AtomicBoolean(true);
    private final AtomicBoolean active = new AtomicBoolean(false);
    private final AtomicBoolean reading = new AtomicBoolean(false);
    
    private volatile TunnelEndpoint tunnel;
    private volatile MossySocketAddress remoteAddress;
    
    MossyServerChildChannel(Channel parent, TunnelEndpoint tunnel) {
        super(parent);
        this.tunnel = tunnel;
        
        this.remoteAddress = new MossySocketAddress(
            new MossyAddress(tunnel.getRemotePeerId(), tunnel.getProtocol())
        );
    }
    
    void activate() {
        if (active.compareAndSet(false, true)) {
            MossyDebug.recordEvent("Server channel active " + MossyDebug.describeAddress(remoteAddress));
            Mossy.LOGGER.info("Activated MOSS server channel for {}", MossyDebug.describeAddress(remoteAddress));
            pipeline().fireChannelActive();
            startReading();
        }
    }
    
    private void startReading() {
        if (!reading.compareAndSet(false, true)) {
            return;
        }
        readExecutor.submit(() -> {
            try {
                while (isActive()) {
                    byte[] buffer = new byte[4096];
                    int read = tunnel.read(buffer);
                    
                    if (read < 0) {
                        MossyDebug.recordEvent("Server tunnel EOF " + MossyDebug.describeAddress(remoteAddress));
                        Mossy.LOGGER.info("MOSS server tunnel reached EOF for {}", MossyDebug.describeAddress(remoteAddress));
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
                if (isActive()) {
                    MossyDebug.recordEvent("Server read loop failed");
                    Mossy.LOGGER.error("MOSS server read loop failed for {}", MossyDebug.describeAddress(remoteAddress), e);
                    pipeline().fireExceptionCaught(e);
                }
            } finally {
                reading.set(false);
            }
        });
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
        return new MossyChildUnsafe();
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
    }
    
    @Override
    protected void doDisconnect() {
        doClose();
    }
    
    @Override
    protected void doClose() {
        if (open.compareAndSet(true, false)) {
            active.set(false);
            MossyDebug.recordEvent("Closing server channel " + MossyDebug.describeAddress(remoteAddress));
            Mossy.LOGGER.info("Closing MOSS server channel for {}", MossyDebug.describeAddress(remoteAddress));
            TunnelEndpoint t = tunnel;
            tunnel = null;
            if (t != null) {
                closeExecutor.submit(() -> {
                    try {
                        t.close();
                    } catch (Exception e) {
                        Mossy.LOGGER.warn("MOSS server tunnel cleanup failed", e);
                    }
                });
            }
        }
    }
    
    @Override
    protected void doBeginRead() {
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
    
    private class MossyChildUnsafe extends AbstractUnsafe {
        @Override
        public void connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
            promise.setFailure(new UnsupportedOperationException("Server child channel cannot connect"));
        }
    }
}
