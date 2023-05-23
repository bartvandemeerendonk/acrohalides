package app.Client.Layers.BlockchainLayer;

import app.Client.Layers.BlockchainLayer.KeyWithOrigin;

public class PoolResponse {
    private KeyWithOrigin oldPublicKey;
    private byte[] poolPublicKey;
    private byte[] poolKeySignature;
    private byte[] dilutionApplicationSignature;
    private byte[] inviteSignature;
    private byte[] signature;

    public PoolResponse(byte[] oldPublicKey, long oldKeyOriginBlockIndex, byte[] poolPublicKey, byte[] poolKeySignature, byte[] dilutionApplicationSignature, byte[] inviteSignature, byte[] signature) {
        this.oldPublicKey = new KeyWithOrigin(oldPublicKey, oldKeyOriginBlockIndex);
        this.poolPublicKey = poolPublicKey;
        this.poolKeySignature = poolKeySignature;
        this.dilutionApplicationSignature = dilutionApplicationSignature;
        this.inviteSignature = inviteSignature;
        this.signature = signature;
    }

    public byte[] getPoolPublicKey() {
        return poolPublicKey;
    }

    public byte[] getPoolKeySignature() {
        return poolKeySignature;
    }

    public byte[] getOldPublicKey() {
        return oldPublicKey.getPublicKey();
    }

    public long getOldKeyOriginBlockIndex() {
        return oldPublicKey.getKeyOriginBlockIndex();
    }

    public byte[] getDilutionApplicationSignature() {
        return dilutionApplicationSignature;
    }

    public byte[] getInviteSignature() {
        return inviteSignature;
    }

    public byte[] getSignature() {
        return signature;
    }
}
