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

public class DilutionStartBlockFactory extends SuccessorBlockFactory {

    public DilutionStartBlockFactory () {
        setPrefix("dilution start block - ".getBytes());
        initialize();
        minimumMessageLength = succesBlockHeaderLength;
    }

    public byte[] getSignature(byte[] message) {
        return ByteUtils.readByteSubstringWithLengthEncoded(message, minimumMessageLength);
    }

    @Override
    public ValidationStatus doesBlockMessageWinFork(byte[] blockMessage, Block competingBlock, Blockchain blockchain) {
        if (competingBlock.getPrefix()[0] == 'R') {
            return ValidationStatus.VALID;
        } else {
            return ValidationStatus.LOSES_PRIMARY_CHECK;
        }
    }

    public DilutionStartBlock createBlock(Blockchain blockchain, Signature signature) {
            DilutionStartBlock dilutionStartBlock = new DilutionStartBlock(blockchain, blockchain.getLastBlock().getIndex() + 1, blockchain.getLastBlock().getHash());
            dilutionStartBlock.setChainScore(blockchain.getLastBlock().getChainScore());
            try {
                byte[] unsignedBlockBytes = dilutionStartBlock.getBytesWithoutValidation();
                signature.update(unsignedBlockBytes);
                byte[] signatureBytes = signature.sign();
                dilutionStartBlock.setSignature(signatureBytes);
                if (!blockchain.appendBlock(dilutionStartBlock)) {
                    dilutionStartBlock = null;
                }
            } catch (SignatureException exception) {
                System.err.println("Couldn't create dilution start block due to a SignatureException");
                dilutionStartBlock = null;
            }
            return dilutionStartBlock;
    }

    public DilutionStartBlock createBlock(Blockchain blockchain, byte[] message) {
        DilutionStartBlock dilutionStartBlock = new DilutionStartBlock(blockchain, getIndex(message), getPredecessorHash(message));
        long chainscore = getChainScore(message);
        dilutionStartBlock.setChainScore(chainscore);
        byte[] managerPublicKey = blockchain.getManagerPublicKey();
        byte[] signatureBytes = getSignature(message);
        try {
            Signature signature = PrivacyUtils.createSignatureForVerifying(PrivacyUtils.getPublicKeyFromBytes(managerPublicKey));
            signature.update(dilutionStartBlock.getBytesWithoutValidation());
            if (signature.verify(signatureBytes)) {
//                System.out.println("Signature of dilution start block is valid");
                dilutionStartBlock.setSignature(signatureBytes);
            } else {
                System.out.println("Signature of dilution start block is not valid");
                dilutionStartBlock = null;
            }
        } catch (NoSuchAlgorithmException exception) {
            System.err.println("No such algorithm in process dilution start");
            dilutionStartBlock = null;
        } catch (InvalidKeySpecException exception) {
            System.err.println("Invalid key spec in process dilution start");
            dilutionStartBlock = null;
        } catch (InvalidKeyException exception) {
            System.err.println("Invalid key in process dilution start");
            dilutionStartBlock = null;
        } catch (SignatureException exception) {
            System.err.println("Signature exception in process dilution start");
            dilutionStartBlock = null;
        }
        return dilutionStartBlock;
    }

    public byte[] getBytesWithoutValidation(byte[] message) {
        byte[] bytesWithoutValidation = new byte[minimumMessageLength];
        for (int i = 0; i < bytesWithoutValidation.length; i++) {
            bytesWithoutValidation[i] = message[i];
        }
        return bytesWithoutValidation;
    }
}
