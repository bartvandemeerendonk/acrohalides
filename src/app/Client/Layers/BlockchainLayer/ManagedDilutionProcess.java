package app.Client.Layers.BlockchainLayer;

import app.Client.Layers.BlockchainLayer.Blockchain.Blockchain;
import app.Client.Layers.BlockchainLayer.Blockchain.Blocks.DilutionBlock;
import app.Client.Utils.ByteUtils;

import java.security.Signature;
import java.util.HashSet;

public class ManagedDilutionProcess extends DilutionProcess {
    private BlockchainLayer blockchainLayer;
    private Blockchain blockchain;
    private HashSet<byte[]> receivedNewKeys;
    private byte[] publicKeyAsPoolManager;
    private long keyOriginBlockIndexAsPoolManager;
    private Signature signatureAsPoolManager;
    private boolean includeSelf;
    private boolean assemblingDilutionPool;
    private HashSet<PoolResponse> poolResponses;
    private int desiredPoolSize = 2;
    private String nameOfLastMessageReceived;

    public ManagedDilutionProcess(BlockchainLayer blockchainLayer, Blockchain blockchain) {
        super();
        this.blockchainLayer = blockchainLayer;
        this.blockchain = blockchain;
        if (desiredPoolSize > 2) {
            PoolSizeDecrementTask poolSizeDecrementTask = new PoolSizeDecrementTask(this);
            Thread poolSizeDecrementThread = new Thread(poolSizeDecrementTask);
            poolSizeDecrementThread.start();
        }
    }

    public DilutionBlock getUnvalidatedDilutionBlock() {
        return unvalidatedDilutionBlock;
    }

    public void setUnvalidatedDilutionBlock(DilutionBlock unvalidatedDilutionBlock) {
        this.unvalidatedDilutionBlock = unvalidatedDilutionBlock;
    }

    public HashSet<byte[]> getReceivedNewKeys() {
        return receivedNewKeys;
    }

    public void setReceivedNewKeys(HashSet<byte[]> receivedNewKeys) {
        this.receivedNewKeys = receivedNewKeys;
    }

    public byte[] getPublicKeyAsPoolManager() {
        return publicKeyAsPoolManager;
    }

    public void setPublicKeyAsPoolManager(byte[] publicKeyAsPoolManager) {
        this.publicKeyAsPoolManager = publicKeyAsPoolManager;
    }

    public long getKeyOriginBlockIndexAsPoolManager() {
        return keyOriginBlockIndexAsPoolManager;
    }

    public void setKeyOriginBlockIndexAsPoolManager(long keyOriginBlockIndexAsPoolManager) {
        this.keyOriginBlockIndexAsPoolManager = keyOriginBlockIndexAsPoolManager;
    }

    public Signature getSignatureAsPoolManager() {
        return signatureAsPoolManager;
    }

    public void setSignatureAsPoolManager(Signature signatureAsPoolManager) {
        this.signatureAsPoolManager = signatureAsPoolManager;
    }

    public boolean isAssemblingDilutionPool() {
        return assemblingDilutionPool;
    }

    public void setAssemblingDilutionPool(boolean assemblingDilutionPool) {
        this.assemblingDilutionPool = assemblingDilutionPool;
    }

    public HashSet<PoolResponse> getPoolResponses() {
        return poolResponses;
    }

    public void setPoolResponses(HashSet<PoolResponse> poolResponses) {
        this.poolResponses = poolResponses;
    }

    public boolean isIncludeSelf() {
        return includeSelf;
    }

    public int getSelfIncludingNumber() {
        if (includeSelf) {
            return 1;
        } else {
            return 0;
        }
    }

    public void setIncludeSelf(boolean includeSelf) {
        this.includeSelf = includeSelf;
    }

    public void decrementDesiredPoolSize() {
        desiredPoolSize--;
        blockchainLayer.possiblyStartDilutionPool(blockchain);
    }

    public int getDesiredPoolSize() {
        return desiredPoolSize;
    }

    public String getNameOfLastMessageReceived() {
        return nameOfLastMessageReceived;
    }

    public void setNameOfLastMessageReceived(String nameOfLastMessageReceived) {
        this.nameOfLastMessageReceived = nameOfLastMessageReceived;
    }

    public void addReceipt(byte[] oldKey) {
        boolean receiptFound = false;
        for (PoolResponse poolResponse: poolResponses) {
            if (!receiptFound && ByteUtils.byteArraysAreEqual(oldKey, poolResponse.getOldPublicKey())) {
                receiptFound = true;
            }
        }
        if (receiptFound) {
            poolReceipts.add(oldKey);
        }
    }

    public int getNumberOfPoolReceipts() {
        return poolReceipts.size();
    }

    public byte[] getPublicKeyForDilutionApplication() {
        return publicKeyAsPoolManager;
    }

    public long getKeyOriginBlockIndexForDilutionApplication() {
        return keyOriginBlockIndexAsPoolManager;
    }

    public byte[] getPublicKeyOfPoolManager() {
        return publicKeyAsPoolManager;
    }

    public long getKeyOriginBlockIndexOfPoolManager() {
        return keyOriginBlockIndexAsPoolManager;
    }
}
