package md.redstone.moss;

import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Pointer;

/**
 * JNA interface for MOSS P2P mesh networking library.
 * Maps native functions from moss.dll/libmoss.so/libmoss.dylib
 */
public interface MossNative extends Library {
    
    // Lifecycle
    int Moss_Init(String meshId, byte[] psk, String config);
    int Moss_Start(int handle);
    int Moss_Stop(int handle);
    
    // Connectivity
    int Moss_Connect(int handle, String addr);
    
    // Pub/Sub
    int Moss_Subscribe(int handle, String channel);
    int Moss_Unsubscribe(int handle, String channel);
    int Moss_Publish(int handle, String channel, byte[] data, int len);
    
    // Callbacks
    int Moss_SetCallback(int handle, MossMessageCallback cb);
    int Moss_SetEventCallback(int handle, MossEventCallback cb);
    int Moss_SetScoringCallback(int handle, MossScoringCallback cb);
    int Moss_SetKeyStore(MossKeyStoreLoadCallback load, MossKeyStoreSaveCallback save);
    
    // Diagnostics
    Pointer Moss_GetMeshInfo(int handle);
    Pointer Moss_GetPublicKey(int handle);
    Pointer Moss_GetNATType(int handle);
    void Moss_Free(Pointer ptr);
    
    // Callback interfaces
    interface MossMessageCallback extends Callback {
        void invoke(String channel, Pointer senderId, Pointer data, int len);
    }
    
    interface MossEventCallback extends Callback {
        void invoke(int eventType, String detailJson);
    }
    
    interface MossScoringCallback extends Callback {
        double invoke(Pointer peerId, double baseScore);
    }
    
    interface MossKeyStoreLoadCallback extends Callback {
        int invoke(Pointer buffer, int capacity);
    }
    
    interface MossKeyStoreSaveCallback extends Callback {
        void invoke(Pointer data, int len);
    }
}
