package app.Client.Layers.BlockchainLayer.Blockchain.Blocks;

import app.Client.Utils.ByteUtils;
import app.Client.Layers.BlockchainLayer.Blockchain.Blockchain;

import java.util.ArrayList;

public abstract class SuccessorBlock extends Block {
    private byte[] predecessorHash;
    private long chainScore;

    protected SuccessorBlock(Blockchain blockchain, long index, byte[] predecessorHash) {
        super(blockchain);
        this.index = index;
        this.predecessorHash = predecessorHash;
        this.chainScore = 0;
    }

    public abstract byte[] getContentBytes();

    @Override
    public byte[] getInternalBytes() {
        ArrayList<byte[]> internalBytesArray = new ArrayList<>();
        internalBytesArray.add(ByteUtils.longToByteArray(index));
        internalBytesArray.add(predecessorHash);
        internalBytesArray.add(ByteUtils.longToByteArray(chainScore));
        internalBytesArray.add(getContentBytes());
        return ByteUtils.concatenateByteArrays(internalBytesArray);
    }

    public byte[] getPredecessorHash() {
        return predecessorHash;
    }

    public long getChainScore() {
        return chainScore;
    }

    public void setChainScore(long chainScore) {
        this.chainScore = chainScore;
    }
}
