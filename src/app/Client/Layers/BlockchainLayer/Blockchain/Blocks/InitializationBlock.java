package app.Client.Layers.BlockchainLayer.Blockchain.Blocks;

import app.Client.Utils.ByteUtils;
import app.Client.Layers.BlockchainLayer.Blockchain.Blockchain;

public class InitializationBlock extends Block {
//    public static final String PREFIX = "Initialization block - ";
    private byte[] signature;

    public InitializationBlock(Blockchain blockchain) {
        super(blockchain);
        index = 0;
    }

    @Override
    public byte[] getPrefix() {
        return "Initialization block - ".getBytes();
    }

    @Override
    public long getChainScore() {
        return 0;
    }

    @Override
    public byte[] getInternalBytes() {
        return new byte[0];
    }

    @Override
    public byte[] getValidationBytes() {
        return ByteUtils.encodeWithLengthByte(signature);
    }

    public void setSignature(byte[] signature) {
        this.signature = signature;
    }

    private boolean isEqualSameType(InitializationBlock otherBlock) {
        return true;
    }

    @Override
    protected boolean isEqualSameTypeWrapper(Block otherBlock) {
        return isEqualSameType((InitializationBlock) otherBlock);
    }
}
