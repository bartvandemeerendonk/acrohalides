package app.Client.Layers.BlockchainLayer;

public class Vote {
    private byte[] voteContent;
    private long commitmentBlockIndex;
    private byte[] voteMessage;

    public Vote(byte[] voteContent, long commitmentBlockIndex, byte[] voteMessage) {
        this.voteContent = voteContent;
        this.commitmentBlockIndex = commitmentBlockIndex;
        this.voteMessage = voteMessage;
    }

    public byte[] getVoteContent() {
        return voteContent;
    }

    public long getCommitmentBlockIndex() {
        return commitmentBlockIndex;
    }

    public byte[] getVoteMessage() {
        return voteMessage;
    }
}
