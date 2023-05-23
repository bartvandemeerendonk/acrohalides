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

public class CommitmentEndBlockFactory extends SuccessorBlockFactory {

    public CommitmentEndBlockFactory () {
        setPrefix("3 Commitment end block - ".getBytes());
        initialize();
        minimumMessageLength = succesBlockHeaderLength;
    }

    public byte[] getSignature(byte[] message) {
        return ByteUtils.readByteSubstringWithLengthEncoded(message, minimumMessageLength);
    }

    @Override
    public ValidationStatus doesBlockMessageWinFork(byte[] blockMessage, Block competingBlock, Blockchain blockchain) {
        switch (competingBlock.getPrefix()[0]) {
            case 'I':
            case 'R':
            case 'd':
            case 'C':
                return ValidationStatus.VALID;
            default:
                return ValidationStatus.EQUAL_FORK;
        }
    }

    public CommitmentEndBlock createBlock(Blockchain blockchain, Signature signature) {
        CommitmentEndBlock commitmentEndBlock = new CommitmentEndBlock(blockchain, blockchain.getLastBlock().getIndex() + 1, blockchain.getLastBlock().getHash());
        try {
            byte[] unsignedBlockBytes = commitmentEndBlock.getBytesWithoutValidation();
            signature.update(unsignedBlockBytes);
            byte[] signatureBytes = signature.sign();
            commitmentEndBlock.setSignature(signatureBytes);
            if (!blockchain.appendBlock(commitmentEndBlock)) {
                System.out.println("Coudln't append commitment end block");
                commitmentEndBlock = null;
            }
        } catch (SignatureException exception) {
            System.err.println("Couldn't create commitment end block due to a SignatureException");
            commitmentEndBlock = null;
        }
        return commitmentEndBlock;
    }

    public CommitmentEndBlock createBlock(Blockchain blockchain, byte[] message) {
        CommitmentEndBlock commitmentEndBlock = new CommitmentEndBlock(blockchain, getIndex(message), getPredecessorHash(message));
        byte[] managerPublicKey = blockchain.getManagerPublicKey();
        byte[] signatureBytes = getSignature(message);
        try {
            Signature signature = PrivacyUtils.createSignatureForVerifying(PrivacyUtils.getPublicKeyFromBytes(managerPublicKey));
            signature.update(commitmentEndBlock.getBytesWithoutValidation());
            if (signature.verify(signatureBytes)) {
//                System.out.println("Signature of dilution end block is valid");
                commitmentEndBlock.setSignature(signatureBytes);
            } else {
                System.out.println("Signature of commitment end block is not valid");
                commitmentEndBlock = null;
            }
        } catch (NoSuchAlgorithmException exception) {
            System.err.println("No such algorithm in process commitment end");
            commitmentEndBlock = null;
        } catch (InvalidKeySpecException exception) {
            System.err.println("Invalid key spec in process commitment end");
            commitmentEndBlock = null;
        } catch (InvalidKeyException exception) {
            System.err.println("Invalid key in process commitment end");
            commitmentEndBlock = null;
        } catch (SignatureException exception) {
            System.err.println("Signature exception in process commitment end");
            commitmentEndBlock = null;
        }
        return commitmentEndBlock;
    }

    public byte[] getBytesWithoutValidation(byte[] message) {
        byte[] bytesWithoutValidation = new byte[minimumMessageLength];
        for (int i = 0; i < bytesWithoutValidation.length; i++) {
            bytesWithoutValidation[i] = message[i];
        }
        return bytesWithoutValidation;
    }
}
