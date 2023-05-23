package app.Client.Layers.BlockchainLayer.Blockchain.Blocks;

import app.Client.Utils.ByteUtils;
import app.Client.Layers.BlockchainLayer.Blockchain.Blockchain;
import app.Client.Layers.ApplicationLayer.Election;
import app.Client.Utils.PrivacyUtils;

public abstract class BlockFactory {
    protected byte[] prefix;
    protected int minimumMessageLength;

    protected void setPrefix(byte[] prefix) {
        this.prefix = prefix;
    }

    public byte[] getPrefix() {
        return prefix;
    }

    public boolean checkPrefix(byte[] message) {
        boolean isValid = true;
        int i = 0;
        while (isValid && i < prefix.length) {
            if (message[i] != prefix[i]) {
                isValid = false;
            }
            i++;
        }
        return isValid;
    }

    public boolean isValid(byte[] message) {
        boolean isValid = message.length > minimumMessageLength;
        if (isValid) {
            isValid = checkPrefix(message);
        }
        return isValid;
    }

    public byte[] getManagerPublicKey(byte[] message) {
        return ByteUtils.readByteSubstring(message, prefix.length, PrivacyUtils.PUBLIC_KEY_LENGTH);
    }

    public byte[] getChainId(byte[] message) {
        return ByteUtils.readByteSubstring(message, prefix.length + PrivacyUtils.PUBLIC_KEY_LENGTH, Election.ID_LENGTH);
    }

    public int getIndex(byte[] message) {
        return ByteUtils.intFromByteArray(ByteUtils.readByteSubstring(message, prefix.length + PrivacyUtils.PUBLIC_KEY_LENGTH + Election.ID_LENGTH, 4));
    }

    public abstract Block createBlock(Blockchain blockchain, byte[] message);

    public int getMinimumMessageLength() {
        return minimumMessageLength;
    }
}
