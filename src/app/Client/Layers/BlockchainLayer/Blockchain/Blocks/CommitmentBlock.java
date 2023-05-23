package app.Client.Layers.BlockchainLayer.Blockchain.Blocks;

import app.Client.Utils.ByteUtils;
import app.Client.Layers.BlockchainLayer.Blockchain.Blockchain;

import java.util.ArrayList;

public class CommitmentBlock extends SuccessorBlock {
    private byte[] signature;
    private byte[] voteHash;
    private byte[] publicKey;
    private long keyOriginBlockIndex;

    public CommitmentBlock(Blockchain blockchain, long index, byte[] predecessorHash, byte[] voteHash, byte[] publicKey, long keyOriginBlockIndex) {
        super(blockchain, index, predecessorHash);
        this.voteHash = voteHash;
        this.publicKey = publicKey;
        this.keyOriginBlockIndex = keyOriginBlockIndex;
    }

    @Override
    public byte[] getPrefix() {
        return "Commitment block - ".getBytes();
    }

    @Override
    public byte[] getContentBytes() {
        ArrayList<byte[]> byteParts = new ArrayList<>();
        byteParts.add(voteHash);
        byteParts.add(publicKey);
        byteParts.add(ByteUtils.longToByteArray(keyOriginBlockIndex));
        return ByteUtils.concatenateByteArrays(byteParts);
    }

    @Override
    public byte[] getValidationBytes() {
        return ByteUtils.encodeWithLengthByte(signature);
    }

    public void setSignature(byte[] signature) {
        this.signature = signature;
    }

    public byte[] getVoteHash() {
        return voteHash;
    }

    public byte[] getPublicKey() {
        return publicKey;
    }

    public long getKeyOriginBlockIndex() {
        return keyOriginBlockIndex;
    }

    private boolean isEqualSameType(CommitmentBlock otherBlock) {
        boolean isEqual = keyOriginBlockIndex == otherBlock.getKeyOriginBlockIndex();
        if (isEqual) {
            isEqual = ByteUtils.byteArraysAreEqual(voteHash, otherBlock.getVoteHash());
        }
        if (isEqual) {
            isEqual = ByteUtils.byteArraysAreEqual(publicKey, otherBlock.getPublicKey());
        }
        if (isEqual) {
            return index == otherBlock.getIndex();
        } else {
            return false;
        }
    }

    @Override
    protected boolean isEqualSameTypeWrapper(Block otherBlock) {
        return isEqualSameType((CommitmentBlock) otherBlock);
    }
}
