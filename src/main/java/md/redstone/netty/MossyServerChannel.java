package md.redstone.netty;

import io.netty.channel.AbstractServerChannel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.EventLoop;
import io.netty.util.concurrent.DefaultThreadFactory;
import md.redstone.moss.MossManager;
import md.redstone.moss.MossTunnel;
import md.redstone.moss.TunnelEndpoint;

import java.net.SocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Netty ServerChannel implementation over MOSS P2P tunnel.
 * 
 * Accepts incoming P2P connections through the MOSS mesh.
 */
public class MossyServerChannel extends AbstractServerChannel {
    
    private static final ChannelMetadata METADATA = new ChannelMetadata(false, 16);
    private static final ExecutorService acceptExecutor = Executors.newSingleThreadExecutor(
        new DefaultThreadFactory("mossy-server-accept", true)
    );
    
    private final ChannelConfig config = new DefaultChannelConfig(this);
    private final AtomicBoolean open = new AtomicBoolean(true);
    private final AtomicBoolean active = new AtomicBoolean(false);
    
    private volatile String protocol;
    private volatile MossTunnel tunnel;
    
    public MossyServerChannel() {
        super();
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
        return active.get() && tunnel != null && tunnel.isRunning();
    }
    
    @Override
    public SocketAddress localAddress() {
        return MossySocketAddress.forServer(protocol);
    }
    
    @Override
    public SocketAddress remoteAddress() {
        return null;
    }
    
    @Override
    protected SocketAddress localAddress0() {
        return localAddress();
    }
    
    @Override
    protected void doBind(SocketAddress localAddress) {
        if (localAddress instanceof MossySocketAddress mossyAddr) {
            this.protocol = mossyAddr.getMossyAddress().getProtocol();
        } else {
            this.protocol = "minecraft";
        }
        
        MossManager moss = MossManager.getInstance();
        if (!moss.isRunning()) {
            throw new IllegalStateException("MOSS mesh not running");
        }
        
        tunnel = new MossTunnel(moss);
        tunnel.start();
        tunnel.listen(protocol, this::handleNewConnection);
        
        active.set(true);
        pipeline().fireChannelActive();
    }
    
    @Override
    protected void doClose() {
        if (open.compareAndSet(true, false)) {
            active.set(false);
            if (tunnel != null) {
                tunnel.stop();
                tunnel = null;
            }
        }
    }
    
    @Override
    protected boolean isCompatible(EventLoop loop) {
        return true;
    }
    
    @Override
    protected void doBeginRead() {
    }
    
    private void handleNewConnection(TunnelEndpoint endpoint) {
        eventLoop().execute(() -> {
            MossyServerChildChannel child = new MossyServerChildChannel(this, endpoint);
            pipeline().fireChannelRead(child);
            pipeline().fireChannelReadComplete();
        });
    }
    
    public String getProtocol() {
        return protocol;
    }
    
    public String getLocalPeerId() {
        return MossManager.getInstance().getPublicKeyBase64();
    }
}
