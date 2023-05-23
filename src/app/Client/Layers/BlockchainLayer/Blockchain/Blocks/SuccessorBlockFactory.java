package app.Client.Layers.BlockchainLayer.Blockchain.Blocks;

import app.Client.Layers.ApplicationLayer.Election;
import app.Client.Layers.BlockchainLayer.Blockchain.Blockchain;
import app.Client.Layers.BlockchainLayer.ValidationStatus;
import app.Client.Layers.PrivacyLayer.PrivacyLayer;
import app.Client.Utils.ByteUtils;
import app.Client.Utils.PrivacyUtils;

public abstract class SuccessorBlockFactory extends BlockFactory {
    protected int succesBlockHeaderLength;

    public void initialize() {
        succesBlockHeaderLength = Election.ID_LENGTH + prefix.length + PrivacyUtils.PUBLIC_KEY_LENGTH + Block.INDEX_SIZE + PrivacyUtils.HASH_LENGTH + 8;
    }

    public byte[] getPredecessorHash(byte[] message) {
        int offset = prefix.length + PrivacyUtils.PUBLIC_KEY_LENGTH + Election.ID_LENGTH + Block.INDEX_SIZE;
        byte[] predecessorHash = new byte[PrivacyUtils.HASH_LENGTH];
        for (int i = 0; i < PrivacyUtils.HASH_LENGTH; i++) {
            predecessorHash[i] = message[i + offset];
        }
        return predecessorHash;
    }

    public long getChainScore(byte[] message) {
        int offset = prefix.length + PrivacyUtils.PUBLIC_KEY_LENGTH + Election.ID_LENGTH + Block.INDEX_SIZE + PrivacyUtils.HASH_LENGTH;
        byte[] chainScoreBytes = new byte[8];
        for (int j = 0; j < 8; j++) {
            chainScoreBytes[j] = message[offset + j];
        }
        return ByteUtils.longFromByteArray(chainScoreBytes);
    }

    public abstract SuccessorBlock createBlock(Blockchain blockchain, byte[] message);

//    public abstract boolean doesBlockMessageWinFork(byte[] blockMessage, Blockchain blockchain);

    public abstract ValidationStatus doesBlockMessageWinFork(byte[] blockMessage, Block competingBlock, Blockchain blockchain);

    public ValidationStatus checkSuccession(Blockchain blockchain, byte[] message) {
        int blockIndex = getIndex(message);
        if (blockIndex <= 0) {
            return ValidationStatus.INVALID;
        } else if (blockIndex > blockchain.getLastBlock().getIndex() + 1) {
            if (getChainScore(message) >= blockchain.getLastBlock().getChainScore()) {
                return ValidationStatus.NEED_PREDECESSOR;
//            } else if (getChainScore(message) == blockchain.getLastBlock().getChainScore()) {
//                return ValidationStatus.NEED_PREDECESSOR_EQUAL_FORK;
            } else {
                return ValidationStatus.NEED_PREDECESSOR_LOSING_FORK;
            }
        } else {
            // first check if appending in right phase
            if (blockchain.checkIfCanAppendBlockMessage(message, blockIndex)) {
                if (blockIndex == blockchain.getLastBlock().getIndex() + 1) {
                    byte[] predecessorHash = getPredecessorHash(message);
                    byte[] realPredecessorHash = blockchain.getLastBlock().getHash();
                    if (ByteUtils.byteArraysAreEqual(predecessorHash, realPredecessorHash)) {
                        return ValidationStatus.VALID;
                    } else {
                        return ValidationStatus.NEED_PREDECESSOR;
                    }
                } else if (getChainScore(message) >= blockchain.getLastBlock().getChainScore()) {
                    byte[] predecessorHash = getPredecessorHash(message);
                    byte[] realPredecessorHash = blockchain.getBlock(blockIndex - 1).getHash();
                    if (ByteUtils.byteArraysAreEqual(predecessorHash, realPredecessorHash)) {
                        Block competingBlock = blockchain.getBlock(blockIndex);
                        return doesBlockMessageWinFork(message, competingBlock, blockchain);
                    } else {
                        return ValidationStatus.NEED_PREDECESSOR;
                    }
                } else {
                    byte[] predecessorHash = getPredecessorHash(message);
                    byte[] realPredecessorHash = blockchain.getBlock(blockIndex - 1).getHash();
                    if (ByteUtils.byteArraysAreEqual(predecessorHash, realPredecessorHash)) {
                        Block competingBlock = blockchain.getBlock(blockIndex);
                        ValidationStatus validationStatus = doesBlockMessageWinFork(message, competingBlock, blockchain);
                        if (validationStatus == ValidationStatus.EQUAL_FORK) {
                            return ValidationStatus.LOSING_FORK;
                        } else if (validationStatus == ValidationStatus.LOSES_SECONDARY_CHECK) {
                            return ValidationStatus.LOSING_FORK_LOSES_SECONDARY_CHECK;
                        } else {
                            return validationStatus;
                        }
                    } else {
                        return ValidationStatus.NEED_PREDECESSOR;
                    }
                    // Block is a fork, but it loses against the existing chain
                }
            } else {
                // Block couldn't be appended in this phase
//                System.out.println("Block couldn't be appended in this phase");
                return ValidationStatus.INVALID;
            }
        }
    }

    public SuccessorBlock handleSuccessionAndCreateBlock(Blockchain blockchain, byte[] message, PrivacyLayer privacyLayer) {
        int blockIndex = getIndex(message);
        byte[] predecessorHash = getPredecessorHash(message);
//        System.out.println("Got predecessor hash");
        byte[] realPredecessorHash = blockchain.getBlock(blockIndex - 1).getHash();
//        byte[] realPredecessorHash = blockchain.getLastBlock().getHash();
//        System.out.println("Got real predecessor hash");
        if (ByteUtils.byteArraysAreEqual(predecessorHash, realPredecessorHash)) {
//            System.out.println("Predecessor hash is valid");
            SuccessorBlock successorBlock = createBlock(blockchain, message);
//            System.out.println("create successorblock");
            if (successorBlock != null) {
                if (blockIndex == blockchain.getLastBlock().getIndex() + 1) {
//                    System.out.println("Index is to last");
                    if (!blockchain.appendBlock(successorBlock)) {
//                        System.out.println("Trying to append a block in the wrong phase.");
                        return null;
                    } else {
//                        System.out.println("return successorBlock");
                        return successorBlock;
                    }
                } else {
//                    System.out.println("Index is not to last: forking or requesting blocks");
                    ValidationStatus validationStatus = doesBlockMessageWinFork(message, blockchain.getBlock(blockIndex), blockchain);
                    switch (validationStatus) {
                        case EQUAL_FORK:
                            if (getChainScore(message) >= blockchain.getLastBlock().getChainScore()) {
                                validationStatus = ValidationStatus.VALID;
                            }
                            break;
                        case LOSES_SECONDARY_CHECK:
                            if (getChainScore(message) > blockchain.getLastBlock().getChainScore()) {
                                validationStatus = ValidationStatus.VALID;
                            }
                            break;
                    }
                    if (validationStatus == ValidationStatus.VALID) {
//                        System.out.println("Win fock");
                        if (blockchain.checkIfCanAppendBlockMessage(message, blockIndex)) {
//                            System.out.println("Check if can append");
                            while (blockchain.getNumberOfBlocks() > blockIndex) {
                                blockchain.discardLastBlock();
                            }
//                            System.out.println("Going to append");
                            if (blockchain.appendBlock(successorBlock)) {
                                if (privacyLayer != null) {
                                    privacyLayer.invalidateKeyBlockIndicesAfterIndex(blockIndex, blockchain.getManagerPublicKey(), blockchain.getId());
                                    privacyLayer.checkRegistrations(blockchain.getManagerPublicKey(), blockchain.getId(), blockIndex);
                                }
                                return successorBlock;
                            }
                        }
                    }
                    return null;
                }
            } else {
                System.out.println("Successor block was null");
                return null;
            }
        } else {
            System.out.println("Predecessor hash is not valid");
            System.out.println(new String(predecessorHash));
            System.out.println(new String(realPredecessorHash));
            return null;
        }
    }

    public int getSuccesBlockHeaderLength() {
        return succesBlockHeaderLength;
    }
}
