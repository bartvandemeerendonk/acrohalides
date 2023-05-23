package app.Client.Layers.BlockchainLayer;

public class KeyWithOrigin {
    private byte[] publicKey;
    private long keyOriginBlockIndex;

    public KeyWithOrigin(byte[] publicKey, long keyOriginBlockIndex) {
        this.publicKey = publicKey;
        this.keyOriginBlockIndex = keyOriginBlockIndex;
    }

    public byte[] getPublicKey() {
        return publicKey;
    }

    public long getKeyOriginBlockIndex() {
        return keyOriginBlockIndex;
    }
}
