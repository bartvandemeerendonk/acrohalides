package app.Client.Layers.BlockchainLayer.Blockchain.Blocks;

import app.Client.Utils.ByteUtils;
import app.Client.Layers.BlockchainLayer.Blockchain.Blockchain;

public class DilutionStartBlock extends SuccessorBlock {
    private byte[] signature;

    public DilutionStartBlock(Blockchain blockchain, long index, byte[] predecessorHash) {
        super(blockchain, index, predecessorHash);
    }

    @Override
    public byte[] getPrefix() {
        return "dilution start block - ".getBytes();
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

    private boolean isEqualSameType(DilutionStartBlock otherBlock) {
        return index == otherBlock.getIndex();
    }

    @Override
    protected boolean isEqualSameTypeWrapper(Block otherBlock) {
        return isEqualSameType((DilutionStartBlock) otherBlock);
    }
}
