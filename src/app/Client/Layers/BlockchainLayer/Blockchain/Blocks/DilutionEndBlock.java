package app.Client.Layers.BlockchainLayer.Blockchain.Blocks;

import app.Client.Utils.ByteUtils;
import app.Client.Layers.BlockchainLayer.Blockchain.Blockchain;

import java.util.ArrayList;

public class DilutionEndBlock extends SuccessorBlock {
    private byte[] signature;
    private byte[] candidateString;

    public DilutionEndBlock(Blockchain blockchain, long index, byte[] predecessorHash) {
        super(blockchain, index, predecessorHash);
    }

    @Override
    public byte[] getPrefix() {
        return "end block - ".getBytes();
    }

    @Override
    public byte[] getContentBytes() {
        ArrayList<byte[]> contentParts = new ArrayList<>();
        contentParts.add(ByteUtils.intToByteArray(candidateString.length));
        contentParts.add(candidateString);
        return ByteUtils.concatenateByteArrays(contentParts);
    }

    @Override
    public byte[] getValidationBytes() {
        return ByteUtils.encodeWithLengthByte(signature);
    }

    public void setSignature(byte[] signature) {
        this.signature = signature;
    }

    public byte[] getCandidateString() {
        return candidateString;
    }

    public void setCandidateString(byte[] candidateString) {
        this.candidateString = candidateString;
    }

    private boolean isEqualSameType(DilutionEndBlock otherBlock) {
        return index == otherBlock.getIndex();
    }

    @Override
    protected boolean isEqualSameTypeWrapper(Block otherBlock) {
        return isEqualSameType((DilutionEndBlock) otherBlock);
    }
}
