package app.Client.Layers.BlockchainLayer;

import java.util.List;

public class BackgroundDilutionProcess {
    private KeyWithOrigin poolManager;
    private byte[] sessionPublicKey;
    private byte[] poolIdentifier;
    private List<KeyWithOrigin> members;
    private int counter;
    private int signatureCounter;

    public BackgroundDilutionProcess(KeyWithOrigin poolManager, byte[] sessionPublicKey, byte[] poolIdentifier, List<KeyWithOrigin> members) {
        this.poolManager = poolManager;
        this.sessionPublicKey = sessionPublicKey;
        this.poolIdentifier = poolIdentifier;
        this.members = members;
        counter = members.size();
        signatureCounter = members.size();
    }

    public KeyWithOrigin getPoolManager() {
        return poolManager;
    }

    public byte[] getSessionPublicKey() {
        return sessionPublicKey;
    }

    public void setSessionPublicKey(byte[] sessionPublicKey) {
        this.sessionPublicKey = sessionPublicKey;
    }

    public void decrement() {
        counter--;
    }

    public int getCounter() {
        return counter;
    }

    public void decrementSignatures() {
        signatureCounter--;
    }

    public int getSignatureCounter() {
        return signatureCounter;
    }

    public List<KeyWithOrigin> getMembers() {
        return members;
    }

    public byte[] getPoolIdentifier() {
        return poolIdentifier;
    }
}
