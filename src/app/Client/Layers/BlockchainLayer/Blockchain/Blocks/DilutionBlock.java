package app.Client.Layers.BlockchainLayer.Blockchain.Blocks;

import app.Client.Utils.ByteUtils;
import app.Client.Layers.BlockchainLayer.Blockchain.Blockchain;
import org.bouncycastle.util.encoders.Hex;

import java.util.ArrayList;
import java.util.List;

public class DilutionBlock extends SuccessorBlock {
    private ArrayList<byte[]> newKeys;
    private ArrayList<byte[]> oldKeys;
    private ArrayList<Long> keyOriginBlockIndices;
    private ArrayList<byte[]> signatures;
    private byte[] poolIdentifier;
    private byte[] blockAssembler;
    private long blockAssemblerKeyOriginBlockIndex;
    private byte[] anonymitySet;
    private int depth;
//    private long dilutionFactor;

    public DilutionBlock(Blockchain blockchain, long index, byte[] predecessorHash) {
        super(blockchain, index, predecessorHash);
        newKeys = new ArrayList<>();
        oldKeys = new ArrayList<>();
        keyOriginBlockIndices = new ArrayList<>();
        signatures = new ArrayList<>();
        anonymitySet = null;
        depth = 0;
    }

    public void addNewKey(byte[] newKey) {
        newKeys.add(newKey);
    }

    public void addOldKey(byte[] oldKey, long keyOriginBlockIndex) {
        oldKeys.add(oldKey);
        keyOriginBlockIndices.add(keyOriginBlockIndex);
        signatures.add(null);
        if (!blockchain.isHeadless()) {
            Block originBlock = blockchain.getBlock(keyOriginBlockIndex);
            byte[] otherAnonymitySet = oldKey;
            int potentialNewDepth = 0;
            if (originBlock.getPrefix()[0] == 'D') {
                DilutionBlock originDilutionBlock = (DilutionBlock) originBlock;
                otherAnonymitySet = originDilutionBlock.getAnonymitySet();
                potentialNewDepth = originDilutionBlock.depth;
            }
            if (anonymitySet == null || ByteUtils.compareByteArrays(anonymitySet, otherAnonymitySet) > 0) {
                anonymitySet = otherAnonymitySet;
            }
            potentialNewDepth++;
            if (potentialNewDepth > depth) {
                depth = potentialNewDepth;
            }
        }
    }

    public void addSignature(byte[] oldKey, byte[] signature) {
        for (int i = 0; i < oldKeys.size(); i++) {
            if (ByteUtils.byteArraysAreEqual(oldKey, oldKeys.get(i))) {
                signatures.set(i, signature);
            }
        }
    }

    public boolean isValidated() {
        boolean toReturn = true;
        int i = 0;
        while (toReturn && i < signatures.size()) {
            if (signatures.get(i) == null) {
                toReturn = false;
            }
            i++;
        }
        return toReturn;
    }

    @Override
    public byte[] getPrefix() {
        return "Dilution block - ".getBytes();
    }

    @Override
    public byte[] getContentBytes() {
        byte[] depthByte = new byte[]{(byte) getDepth()};
        byte[] numberOfKeys = new byte[]{(byte) newKeys.size()};
        ArrayList<byte[]> oldKeysWithOriginalBlocks = new ArrayList<>();
        for (int i = 0; i < oldKeys.size(); i++) {
            ArrayList<byte[]> currentOldKeyWithOriginalBlock = new ArrayList<>();
            currentOldKeyWithOriginalBlock.add(oldKeys.get(i));
            currentOldKeyWithOriginalBlock.add(ByteUtils.longToByteArray(keyOriginBlockIndices.get(i)));
            oldKeysWithOriginalBlocks.add(ByteUtils.concatenateByteArrays(currentOldKeyWithOriginalBlock));
        }
        ArrayList<byte[]> concatenatedBytes = new ArrayList<>();
        concatenatedBytes.add(poolIdentifier);
        concatenatedBytes.add(blockAssembler);
        concatenatedBytes.add(ByteUtils.longToByteArray(blockAssemblerKeyOriginBlockIndex));
        concatenatedBytes.add(depthByte);
        concatenatedBytes.add(getAnonymitySet());
//        concatenatedBytes.add(ByteUtils.longToByteArray(dilutionFactor));
        concatenatedBytes.add(numberOfKeys);
        byte[] concatenatedOldKeys = ByteUtils.concatenateByteArrays(oldKeysWithOriginalBlocks);
        concatenatedBytes.add(concatenatedOldKeys);
        byte[] concatenatedNewKeys = ByteUtils.concatenateByteArrays(newKeys);
        concatenatedBytes.add(concatenatedNewKeys);
        return ByteUtils.concatenateByteArrays(concatenatedBytes);
    }

    @Override
    public byte[] getValidationBytes() {
        ArrayList<byte[]> signaturesWithLength = new ArrayList<>();
        for (int i = 0; i < signatures.size(); i++) {
            byte[] signature = signatures.get(i);
            if (signature != null) {
                signaturesWithLength.add(ByteUtils.encodeWithLengthByte(signature));
            }
        }
        return ByteUtils.concatenateByteArrays(signaturesWithLength);
    }

    public List<byte[]> getNewKeys() {
        return newKeys;
    }

    public List<byte[]> getOldKeys() {
        return oldKeys;
    }

    public List<Long> getKeyOriginBlockIndices() {
        return keyOriginBlockIndices;
    }

    public byte[] getAnonymitySet() {
        return anonymitySet;
    }

    public int getDepth() {
        return depth;
    }

    public void setAnonymitySet(byte[] anonymitySet) {
        this.anonymitySet = anonymitySet;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

//    public long getDilutionFactor() {
//        return dilutionFactor;
//    }

//    public void setDilutionFactor(long dilutionFactor) {
//        this.dilutionFactor = dilutionFactor;
//    }

    public byte[] getPoolIdentifier() {
        return poolIdentifier;
    }

    public void setPoolIdentifier(byte[] poolIdentifier) {
        this.poolIdentifier = poolIdentifier;
    }

    public byte[] getBlockAssembler() {
        return blockAssembler;
    }

    public void setBlockAssembler(byte[] blockAssembler) {
        this.blockAssembler = blockAssembler;
    }

    public long getBlockAssemblerKeyOriginBlockIndex() {
        return blockAssemblerKeyOriginBlockIndex;
    }

    public void setBlockAssemblerKeyOriginBlockIndex(long blockAssemblerKeyOriginBlockIndex) {
        this.blockAssemblerKeyOriginBlockIndex = blockAssemblerKeyOriginBlockIndex;
    }

    private boolean isEqualSameType(DilutionBlock otherDilutionBlock) {
        boolean blocksAreEqual = true;
        List<byte[]> otherNewKeys = otherDilutionBlock.getNewKeys();
        List<byte[]> otherOldKeys = otherDilutionBlock.getOldKeys();
        int i = 0;
        while (blocksAreEqual && i < newKeys.size()) {
            if (!ByteUtils.byteArraysAreEqual(otherOldKeys.get(i), oldKeys.get(i)) || !ByteUtils.byteArraysAreEqual(otherNewKeys.get(i), newKeys.get(i))) {
                blocksAreEqual = false;
            }
            i++;
        }
        return blocksAreEqual;
    }

    @Override
    protected boolean isEqualSameTypeWrapper(Block otherBlock) {
        return isEqualSameType((DilutionBlock) otherBlock);
    }

    public void print() {
        System.out.println(getIndex() + " Dilution block");
        System.out.println("Old keys:");
        for (int j = 0; j < oldKeys.size(); j++) {
            if (j < keyOriginBlockIndices.size()) {
                byte[] oldKey1 = oldKeys.get(j);
                long keyOriginBlockIndex = keyOriginBlockIndices.get(j);
                System.out.println(" - " + Hex.toHexString(oldKey1) + " :: " + keyOriginBlockIndex);
            }
        }
        System.out.println("New keys:");
        for (byte[] newKey: newKeys) {
            System.out.println(" - " + Hex.toHexString(newKey));
        }
    }
}
