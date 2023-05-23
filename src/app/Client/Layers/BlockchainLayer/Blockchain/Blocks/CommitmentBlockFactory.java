package app.Client.Layers.BlockchainLayer.Blockchain.Blocks;

import app.Client.Utils.ByteUtils;
import app.Client.Layers.BlockchainLayer.Blockchain.Blockchain;
import app.Client.Utils.PrivacyUtils;
import app.Client.Layers.BlockchainLayer.ValidationStatus;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;

public class CommitmentBlockFactory extends SuccessorBlockFactory {

    public CommitmentBlockFactory () {
        setPrefix("Commitment block - ".getBytes());
        initialize();
        minimumMessageLength = succesBlockHeaderLength + PrivacyUtils.VOTE_HASH_LENGTH + PrivacyUtils.PUBLIC_KEY_LENGTH + Block.INDEX_SIZE;
    }

    public byte[] getVoteHash(byte[] message) {
        return ByteUtils.readByteSubstring(message, succesBlockHeaderLength, PrivacyUtils.VOTE_HASH_LENGTH);
    }

    public byte[] getPublicKey(byte[] message) {
        return ByteUtils.readByteSubstring(message, succesBlockHeaderLength + PrivacyUtils.VOTE_HASH_LENGTH, PrivacyUtils.PUBLIC_KEY_LENGTH);
    }

    public long getKeyOriginBlockIndex(byte[] message) {
        return ByteUtils.longFromByteArray(ByteUtils.readByteSubstring(message, succesBlockHeaderLength + PrivacyUtils.VOTE_HASH_LENGTH + PrivacyUtils.PUBLIC_KEY_LENGTH, Block.INDEX_SIZE));
    }

    public byte[] getSignature(byte[] message) {
        return ByteUtils.readByteSubstringWithLengthEncoded(message, minimumMessageLength);
    }

    @Override
    public ValidationStatus doesBlockMessageWinFork(byte[] blockMessage, Block competingBlock, Blockchain blockchain) {
        switch (competingBlock.getPrefix()[0]) {
            case 'R':
            case 'I':
            case 'd':
                return ValidationStatus.VALID;
            case 'D':
            case '0':
            case '1':
            case 'e':
                return ValidationStatus.EQUAL_FORK;
            case 'C':
                CommitmentBlock competingCommitmentBlock = (CommitmentBlock) competingBlock;
                byte[] publicKeyOfMessage = getPublicKey(blockMessage);
                byte[] voteHashOfMessage = getVoteHash(blockMessage);
                byte[] publicKeyOfCompetingBlock = competingCommitmentBlock.getPublicKey();
                byte[] voteHashOfCompetingBlock = competingCommitmentBlock.getVoteHash();
                byte[] thisRelativePublicKey;
                byte[] thisRelativeVoteHash;
                byte[] competingBlockRelativePublicKey;
                byte[] competingBlockRelativeVoteHash;
                Block predecessorBlock = blockchain.getBlock(competingBlock.getIndex() - 1);
                if (predecessorBlock.getPrefix()[0] == 'C') {
                    CommitmentBlock predecessorCommitmentBlock = (CommitmentBlock) predecessorBlock;
                    thisRelativePublicKey = ByteUtils.subtractByteArrays(publicKeyOfMessage, predecessorCommitmentBlock.getPublicKey());
                    thisRelativeVoteHash = ByteUtils.subtractByteArrays(voteHashOfMessage, predecessorCommitmentBlock.getVoteHash());
                    competingBlockRelativePublicKey = ByteUtils.subtractByteArrays(publicKeyOfCompetingBlock, predecessorCommitmentBlock.getPublicKey());
                    competingBlockRelativeVoteHash = ByteUtils.subtractByteArrays(voteHashOfCompetingBlock, predecessorCommitmentBlock.getVoteHash());
                } else {
                    thisRelativePublicKey = publicKeyOfMessage;
                    thisRelativeVoteHash = voteHashOfMessage;
                    competingBlockRelativePublicKey = publicKeyOfCompetingBlock;
                    competingBlockRelativeVoteHash = voteHashOfCompetingBlock;
                }
                int keyComparison = ByteUtils.compareByteArrays(thisRelativePublicKey, competingBlockRelativePublicKey);
                if (keyComparison < 0) {
                    return ValidationStatus.EQUAL_FORK;
                } else if (keyComparison > 0) {
                    return ValidationStatus.LOSES_SECONDARY_CHECK;
                } else {
                    int voteHashComparison = ByteUtils.compareByteArrays(thisRelativeVoteHash, competingBlockRelativeVoteHash);
                    if (voteHashComparison < 0) {
                        return ValidationStatus.EQUAL_FORK;
                    } else {
                        return ValidationStatus.LOSES_SECONDARY_CHECK;
                    }
                }
            default:
                return ValidationStatus.LOSES_PRIMARY_CHECK;
        }
    }

    public CommitmentBlock createBlock(Blockchain blockchain, Signature signature, byte[] voteHash, byte[] publicKey, long keyOriginBlockIndex) {
        CommitmentBlock commitmentBlock = new CommitmentBlock(blockchain, blockchain.getLastBlock().getIndex() + 1, blockchain.getLastBlock().getHash(), voteHash, publicKey, keyOriginBlockIndex);
        if (blockchain.keyWasUsedInCommitment(publicKey, keyOriginBlockIndex, blockchain.getNumberOfBlocks())) {
            commitmentBlock.setChainScore(blockchain.getLastBlock().getChainScore());
        } else {
            commitmentBlock.setChainScore(blockchain.getLastBlock().getChainScore() + 1);
        }
        try {
            byte[] unsignedBlockBytes = commitmentBlock.getBytesWithoutValidation();
            signature.update(unsignedBlockBytes);
            byte[] signatureBytes = signature.sign();
            commitmentBlock.setSignature(signatureBytes);
            if (!blockchain.appendBlock(commitmentBlock)) {
                commitmentBlock = null;
                System.out.println("CANT APPEND");
            }
        } catch (SignatureException exception) {
            System.err.println("Couldn't create commitment block due to a SignatureException");
            System.out.println("SIG EXCEPTION");
            commitmentBlock = null;
        }
        return commitmentBlock;
    }

    public CommitmentBlock createBlock(Blockchain blockchain, byte[] message) {
        byte[] voteHash = getVoteHash(message);
        byte[] publicKey = getPublicKey(message);
        long keyOriginBlockIndex = getKeyOriginBlockIndex(message);
        CommitmentBlock commitmentBlock = new CommitmentBlock(blockchain, getIndex(message), getPredecessorHash(message), voteHash, publicKey, keyOriginBlockIndex);
        commitmentBlock.setChainScore(getChainScore(message));
//        byte[] managerPublicKey = blockchain.getManagerPublicKey();
        byte[] signatureBytes = getSignature(message);
        try {
            Signature signature = PrivacyUtils.createSignatureForVerifying(PrivacyUtils.getPublicKeyFromBytes(publicKey));
            signature.update(commitmentBlock.getBytesWithoutValidation());
            if (signature.verify(signatureBytes)) {
//                System.out.println("Signature of commitment block is valid");
                commitmentBlock.setSignature(signatureBytes);
            } else {
                System.out.println("Signature of commitment block is not valid");
                commitmentBlock = null;
            }
        } catch (NoSuchAlgorithmException exception) {
            System.err.println("No such algorithm in process commitment block");
            commitmentBlock = null;
        } catch (InvalidKeySpecException exception) {
            System.err.println("Invalid key spec in process commitment block");
            commitmentBlock = null;
        } catch (InvalidKeyException exception) {
            System.err.println("Invalid key in process commitment block");
            commitmentBlock = null;
        } catch (SignatureException exception) {
            System.err.println("Signature exception in process commitment block");
            commitmentBlock = null;
        }
        return commitmentBlock;
    }

    public byte[] getBytesWithoutValidation(byte[] message) {
        byte[] bytesWithoutValidation = new byte[minimumMessageLength];
        for (int i = 0; i < bytesWithoutValidation.length; i++) {
            bytesWithoutValidation[i] = message[i];
        }
        return bytesWithoutValidation;
    }
}
