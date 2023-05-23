package app.Client.Layers.BlockchainLayer.Blockchain.Blocks;

import app.Client.Utils.ByteUtils;
import app.Client.Layers.BlockchainLayer.Blockchain.Blockchain;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public abstract class Block {
    public static final int INDEX_SIZE = 8;
    protected Blockchain blockchain;
    protected long index;
    private HashSet<Long> blockUseIndices;

    protected Block(Blockchain blockchain) {
        this.blockchain = blockchain;
        blockUseIndices = new HashSet<>();
    }

    public Blockchain getBlockchain() {
        return blockchain;
    }

    public long getIndex() {
        return index;
    }

    public abstract byte[] getPrefix();

    public abstract long getChainScore();

    public abstract byte[] getInternalBytes();

    public byte[] getManagerPublicKey() {
        return blockchain.getManagerPublicKey();
    }

    public byte[] getBytesWithoutValidation() {
        ArrayList<byte[]> byteArrays = new ArrayList<>();
        byteArrays.add(getPrefix());
        byteArrays.add(blockchain.getManagerPublicKey());
        byteArrays.add(blockchain.getId());
//        byteArrays.add(ByteUtils.longToByteArray(index));
        byteArrays.add(getInternalBytes());
        return ByteUtils.concatenateByteArrays(byteArrays);
    }

    public abstract byte[] getValidationBytes();

    public byte[] getBytes() {
        ArrayList<byte[]> byteArrays = new ArrayList<>();
        byteArrays.add(getBytesWithoutValidation());
        byteArrays.add(getValidationBytes());
        return ByteUtils.concatenateByteArrays(byteArrays);
    }

    public byte[] getHash() {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-512");
            return messageDigest.digest(getBytes());
        } catch (NoSuchAlgorithmException exception) {
            System.out.println("No such algorithm for hash");
            return new byte[0];
        }
    }

    public Set<Long> getBlockUseIndices() {
        return blockUseIndices;
    }

    public void addBlockUseIndex(long newBlockUseIndex) {
        blockUseIndices.add(newBlockUseIndex);
    }

    protected abstract boolean isEqualSameTypeWrapper(Block otherBlock);

    public boolean isEqual(Block otherBlock) {
        if (otherBlock.getPrefix()[0] == getPrefix()[0] && ByteUtils.byteArraysAreEqual(getHash(), otherBlock.getHash())) {
            return isEqualSameTypeWrapper(otherBlock);
        } else {
            return false;
        }
    }

}
