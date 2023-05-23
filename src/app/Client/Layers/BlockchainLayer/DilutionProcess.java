package app.Client.Layers.BlockchainLayer;

import app.Client.Datastructures.ByteSet;
import app.Client.Layers.BlockchainLayer.Blockchain.Blocks.DilutionBlock;
import app.Client.Layers.PrivacyLayer.Identity;
import app.Client.Utils.PrivacyUtils;

public abstract class DilutionProcess {
    protected DilutionBlock unvalidatedDilutionBlock;
    protected Identity identityForPool;
    protected byte[] sessionPublicKey;
    protected byte[] sessionPrivateKey;
    protected byte[] currentPoolIdentifier;
    protected long lastModifiedTime;
    protected ByteSet poolReceipts;
    protected byte[] ownNewKey;
    protected byte[] poolMessage;
    private int switchCounter;

    public DilutionProcess() {
        poolReceipts = new ByteSet(PrivacyUtils.PUBLIC_KEY_LENGTH);
        switchCounter = 0;
    }

    public DilutionBlock getUnvalidatedDilutionBlock() {
        return unvalidatedDilutionBlock;
    }

    public void setUnvalidatedDilutionBlock(DilutionBlock unvalidatedDilutionBlock) {
        this.unvalidatedDilutionBlock = unvalidatedDilutionBlock;
    }

    public Identity getIdentityForPool() {
        return identityForPool;
    }

    public void setIdentityForPool(Identity identityForPool) {
        this.identityForPool = identityForPool;
    }

    public byte[] getSessionPublicKey() {
        return sessionPublicKey;
    }

    public void setSessionPublicKey(byte[] sessionPublicKey) {
        this.sessionPublicKey = sessionPublicKey;
    }

    public byte[] getSessionPrivateKey() {
        return sessionPrivateKey;
    }

    public void setSessionPrivateKey(byte[] sessionPrivateKey) {
        this.sessionPrivateKey = sessionPrivateKey;
    }

    public byte[] getCurrentPoolIdentifier() {
        return currentPoolIdentifier;
    }

    public void setCurrentPoolIdentifier(byte[] currentPoolIdentifier) {
        this.currentPoolIdentifier = currentPoolIdentifier;
    }

    public long getLastModifiedTime() {
        return lastModifiedTime;
    }

    public void setLastModifiedTime(long lastModifiedTime) {
        this.lastModifiedTime = lastModifiedTime;
    }

    public byte[] getOwnNewKey() {
        return ownNewKey;
    }

    public void setOwnNewKey(byte[] ownNewKey) {
        this.ownNewKey = ownNewKey;
    }

    public byte[] getPoolMessage() {
        return poolMessage;
    }

    public void setPoolMessage(byte[] poolMessage) {
        this.poolMessage = poolMessage;
    }

    public abstract void addReceipt(byte[] oldKey);

    public abstract byte[] getPublicKeyForDilutionApplication();

    public abstract long getKeyOriginBlockIndexForDilutionApplication();

    public abstract byte[] getPublicKeyOfPoolManager();

    public abstract long getKeyOriginBlockIndexOfPoolManager();

    public int getSwitchCounter() {
        return switchCounter;
    }

    public void incrementSwitchCounter() {
        switchCounter++;
    }
}
