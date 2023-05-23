package app.Client.Layers.BlockchainLayer;

public class Invite {
    private byte[] poolIdentifier;
    private byte[] publicKeyOfPoolManager;
    private long keyOriginBlockIndexOfPoolManager;
    private byte[] dilutionApplicationSignature;
    private byte[] inviteSignature;

    public Invite(byte[] poolIdentifier, byte[] publicKeyOfPoolManager, long keyOriginBlockIndexOfPoolManager, byte[] dilutionApplicationSignature, byte[] inviteSignature) {
        this.poolIdentifier = poolIdentifier;
        this.publicKeyOfPoolManager = publicKeyOfPoolManager;
        this.keyOriginBlockIndexOfPoolManager = keyOriginBlockIndexOfPoolManager;
        this.dilutionApplicationSignature = dilutionApplicationSignature;
        this.inviteSignature = inviteSignature;
    }

    public byte[] getPoolIdentifier() {
        return poolIdentifier;
    }

    public byte[] getPublicKeyOfPoolManager() {
        return publicKeyOfPoolManager;
    }

    public long getKeyOriginBlockIndexOfPoolManager() {
        return keyOriginBlockIndexOfPoolManager;
    }

    public byte[] getDilutionApplicationSignature() {
        return dilutionApplicationSignature;
    }

    public byte[] getInviteSignature() {
        return inviteSignature;
    }
}
