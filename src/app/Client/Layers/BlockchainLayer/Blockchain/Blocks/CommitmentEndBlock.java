package app.Client.Layers.BlockchainLayer.Blockchain.Blocks;

import app.Client.Utils.ByteUtils;
import app.Client.Layers.BlockchainLayer.Blockchain.Blockchain;

public class CommitmentEndBlock extends SuccessorBlock {
    private byte[] signature;

    public CommitmentEndBlock(Blockchain blockchain, long index, byte[] predecessorHash) {
        super(blockchain, index, predecessorHash);
    }

    @Override
    public byte[] getPrefix() {
        return "3 Commitment end block - ".getBytes();
    }

    @Override
    public byte[] getContentBytes() {
        return new byte[0];
    }

    @Override
    public byte[] getValidationBytes() {
        return ByteUtils.encodeWithLengthByte(signature);
    }

    public void setSignature(byte[] signature) {
        this.signature = signature;
    }

    private boolean isEqualSameType(CommitmentEndBlock otherBlock) {
        return index == otherBlock.getIndex();
    }

    @Override
    protected boolean isEqualSameTypeWrapper(Block otherBlock) {
        return isEqualSameType((CommitmentEndBlock) otherBlock);
    }
}
