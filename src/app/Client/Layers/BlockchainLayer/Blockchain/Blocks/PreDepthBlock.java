package app.Client.Layers.BlockchainLayer.Blockchain.Blocks;

import app.Client.Utils.ByteUtils;
import app.Client.Layers.BlockchainLayer.Blockchain.Blockchain;

public class PreDepthBlock extends SuccessorBlock {
    private byte[] signature;

    public PreDepthBlock(Blockchain blockchain, long index, byte[] predecessorHash) {
        super(blockchain, index, predecessorHash);
    }

    @Override
    public byte[] getPrefix() {
        return "1 Pre-depth block - ".getBytes();
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

    private boolean isEqualSameType(PreDepthBlock otherBlock) {
        return index == otherBlock.getIndex();
    }

    @Override
    protected boolean isEqualSameTypeWrapper(Block otherBlock) {
        return isEqualSameType((PreDepthBlock) otherBlock);
    }
}
