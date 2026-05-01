package md.redstone.moss;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Represents an active bidirectional tunnel endpoint.
 * Provides stream-like interface over MOSS pub/sub.
 */
public final class TunnelEndpoint {

    private final int streamId;
    private final String remotePeerId;
    private final String protocol;
    final String channel;
    private final MossTunnel tunnel;

    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicInteger seqNum = new AtomicInteger(0);
    private final AtomicInteger expectedReadSeq = new AtomicInteger(0);
    private final NavigableMap<Integer, byte[]> pendingFrames = new TreeMap<>();
    private final ConcurrentMap<Integer, PendingFrame> pendingAckFrames = new ConcurrentHashMap<>();

    private final BlockingQueue<byte[]> readQueue = new LinkedBlockingQueue<>();
    private byte[] pendingRead = new byte[0];
    private int pendingReadOffset = 0;

    TunnelEndpoint(int streamId, String remotePeerId, String protocol, String channel, MossTunnel tunnel) {
        this.streamId = streamId;
        this.remotePeerId = remotePeerId;
        this.protocol = protocol;
        this.channel = channel;
        this.tunnel = tunnel;
    }

    void setConnected(boolean connected) {
        this.connected.set(connected);
        synchronized (this) {
            notifyAll();
        }
    }

    void handleAccept() {
        setConnected(true);
    }

    synchronized void handleData(int seq, byte[] data) {
        int expected = expectedReadSeq.get();
        if (seq < expected) {
            return;
        }
        if (seq > expected) {
            pendingFrames.putIfAbsent(seq, data);
            return;
        }

        offerOrdered(data);
        while (true) {
            byte[] next = pendingFrames.remove(expectedReadSeq.get());
            if (next == null) {
                return;
            }
            offerOrdered(next);
        }
    }

    void handleClose() {
        closed.set(true);
        connected.set(false);
        pendingFrames.clear();
        pendingAckFrames.clear();
        readQueue.offer(new byte[0]);
    }

    int nextSeq() {
        return seqNum.getAndIncrement();
    }

    void trackOutbound(int seq, byte[] data) {
        pendingAckFrames.put(seq, new PendingFrame(seq, data, System.currentTimeMillis()));
    }

    void handleAck(int seq) {
        pendingAckFrames.remove(seq);
    }

    List<PendingFrame> collectRetransmits(long nowMillis, long retryDelayMillis) {
        List<PendingFrame> frames = new ArrayList<>();
        for (PendingFrame frame : pendingAckFrames.values()) {
            if (nowMillis - frame.lastSentAtMillis >= retryDelayMillis) {
                frame.lastSentAtMillis = nowMillis;
                frames.add(frame);
            }
        }
        return frames;
    }

    private void offerOrdered(byte[] data) {
        expectedReadSeq.incrementAndGet();
        readQueue.offer(data);
    }

    static final class PendingFrame {
        final int seq;
        final byte[] payload;
        volatile long lastSentAtMillis;

        PendingFrame(int seq, byte[] payload, long lastSentAtMillis) {
            this.seq = seq;
            this.payload = payload;
            this.lastSentAtMillis = lastSentAtMillis;
        }
    }

    public boolean isConnected() {
        return connected.get() && !closed.get();
    }

    public boolean isClosed() {
        return closed.get();
    }

    public int getStreamId() {
        return streamId;
    }

    public String getRemotePeerId() {
        return remotePeerId;
    }

    public String getProtocol() {
        return protocol;
    }

    public InputStream getInputStream() {
        return new TunnelInputStream();
    }

    public OutputStream getOutputStream() {
        return new TunnelOutputStream();
    }

    public void close() {
        if (closed.compareAndSet(false, true)) {
            connected.set(false);
            pendingFrames.clear();
            pendingAckFrames.clear();
            tunnel.closeTunnel(streamId);
            readQueue.offer(new byte[0]);
        }
    }

    public int read(byte[] buffer) throws IOException {
        if (closed.get()) {
            return -1;
        }

        try {
            int copied = copyPending(buffer);
            if (copied > 0) {
                return copied;
            }

            byte[] data = readQueue.take();
            if (data.length == 0) {
                return -1;
            }

            return copyIntoBuffer(data, buffer);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Read interrupted", e);
        }
    }

    public void write(byte[] data, int offset, int length) throws IOException {
        if (closed.get()) {
            throw new IOException("Tunnel closed");
        }

        byte[] chunk = new byte[length];
        System.arraycopy(data, offset, chunk, 0, length);
        tunnel.sendData(streamId, chunk);
    }

    private synchronized int copyPending(byte[] buffer) {
        if (pendingRead.length == 0 || pendingReadOffset >= pendingRead.length) {
            pendingRead = new byte[0];
            pendingReadOffset = 0;
            return 0;
        }

        int len = Math.min(buffer.length, pendingRead.length - pendingReadOffset);
        System.arraycopy(pendingRead, pendingReadOffset, buffer, 0, len);
        pendingReadOffset += len;
        if (pendingReadOffset >= pendingRead.length) {
            pendingRead = new byte[0];
            pendingReadOffset = 0;
        }
        return len;
    }

    private synchronized int copyIntoBuffer(byte[] data, byte[] buffer) {
        int len = Math.min(data.length, buffer.length);
        System.arraycopy(data, 0, buffer, 0, len);
        if (len < data.length) {
            pendingRead = data;
            pendingReadOffset = len;
        } else {
            pendingRead = new byte[0];
            pendingReadOffset = 0;
        }
        return len;
    }

    private class TunnelInputStream extends InputStream {
        private byte[] currentBuffer = null;
        private int currentPos = 0;

        @Override
        public int read() throws IOException {
            if (currentBuffer == null || currentPos >= currentBuffer.length) {
                currentBuffer = readQueue.poll();
                if (currentBuffer == null) {
                    try {
                        currentBuffer = readQueue.take();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Read interrupted", e);
                    }
                }
                currentPos = 0;
                if (currentBuffer.length == 0) {
                    return -1;
                }
            }

            return currentBuffer[currentPos++] & 0xFF;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (closed.get()) {
                return -1;
            }
            if (b == null) {
                throw new NullPointerException("Buffer is null");
            }
            if (off < 0 || len < 0 || off + len > b.length) {
                throw new IndexOutOfBoundsException("Invalid offset/length");
            }
            if (len == 0) {
                return 0;
            }

            byte[] buffer = new byte[len];
            int read = TunnelEndpoint.this.read(buffer);
            if (read <= 0) {
                return read;
            }
            System.arraycopy(buffer, 0, b, off, read);
            return read;
        }

        @Override
        public void close() throws IOException {
            TunnelEndpoint.this.close();
        }
    }

    private class TunnelOutputStream extends OutputStream {
        @Override
        public void write(int b) throws IOException {
            write(new byte[]{(byte) b});
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            TunnelEndpoint.this.write(b, off, len);
        }

        @Override
        public void flush() throws IOException {
            // Data is sent immediately.
        }

        @Override
        public void close() throws IOException {
            TunnelEndpoint.this.close();
        }
    }
}
