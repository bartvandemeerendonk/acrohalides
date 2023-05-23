package app.Client.Layers.BlockchainLayer.Blockchain.Blocks;

import app.Client.Utils.ByteUtils;
import app.Client.Layers.BlockchainLayer.Blockchain.Blockchain;

import java.util.ArrayList;

public class RegistrationBlock extends SuccessorBlock {
    private byte[] voterId;
    private byte[] publicKey;
    private byte[] signature;

    public RegistrationBlock(Blockchain blockchain, long index, byte[] predecessorHash, byte[] voterId, byte[] publicKey) {
        super(blockchain, index, predecessorHash);
        this.voterId = voterId;
        this.publicKey = publicKey;
    }

    @Override
    public byte[] getPrefix() {
        return "Registration block - ".getBytes();
    }

    @Override
    public byte[] getContentBytes() {
        ArrayList<byte[]> byteArrays = new ArrayList<>();
        byteArrays.add(voterId);
        byteArrays.add(publicKey);
        return ByteUtils.concatenateByteArrays(byteArrays);
    }

    @Override
    public byte[] getValidationBytes() {
        return ByteUtils.encodeWithLengthByte(signature);
    }

    public void setSignature(byte[] signature) {
        this.signature = signature;
    }

    public byte[] getVoterId() {
        return voterId;
    }

    public byte[] getPublicKey() {
        return publicKey;
    }

    private boolean isEqualSameType(RegistrationBlock otherBlock) {
        return index == otherBlock.getIndex() && ByteUtils.byteArraysAreEqual(voterId, otherBlock.getVoterId()) && ByteUtils.byteArraysAreEqual(publicKey, otherBlock.getPublicKey());
    }

    @Override
    protected boolean isEqualSameTypeWrapper(Block otherBlock) {
        return isEqualSameType((RegistrationBlock) otherBlock);
    }
}
