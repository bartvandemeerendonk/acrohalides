package app.Client.Layers.BlockchainLayer.Blockchain.Blocks;

import app.Client.Utils.ByteUtils;
import app.Client.Layers.BlockchainLayer.Blockchain.Blockchain;

public class DepthBlock extends SuccessorBlock {
    private byte[] signature;

    public DepthBlock(Blockchain blockchain, long index, byte[] predecessorHash) {
        super(blockchain, index, predecessorHash);
    }

    @Override
    public byte[] getPrefix() {
        return "0 Depth block - ".getBytes();
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

    private boolean isEqualSameType(DepthBlock otherBlock) {
        return index == otherBlock.getIndex();
    }

    @Override
    protected boolean isEqualSameTypeWrapper(Block otherBlock) {
        return isEqualSameType((DepthBlock) otherBlock);
    }
}
