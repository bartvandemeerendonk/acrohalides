package app.Client.Layers.PrivacyLayer;

import app.Client.Layers.BlockchainLayer.Blockchain.Blocks.RegistrationBlock;
import app.Client.Utils.PrivacyUtils;
import app.Client.Layers.ApplicationLayer.Election;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import java.security.KeyPair;

public class Identity {
    private KeyPair keyPair;
    private long keyOriginBlockIndex;
    private long keyUseBlockIndex;
    private Cipher privateKeyDecryption;
    private RegistrationBlock registrationBlock;
    private byte[] vote;
    private byte[] voteSalt;
    private long commitBlockIndex;
    private Election election;

    public Identity(KeyPair keyPair) {
        this.keyPair = keyPair;
        this.keyOriginBlockIndex = -1;
        this.keyUseBlockIndex = -1;
        this.privateKeyDecryption = PrivacyUtils.getPrivateKeyDecryption(keyPair.getPrivate());
        this.vote = null;
        this.voteSalt = null;
        this.commitBlockIndex = -1;
        this.election = null;
    }

    public KeyPair getKeyPair() {
        return keyPair;
    }

    public long getKeyOriginBlockIndex() {
        return keyOriginBlockIndex;
    }

    public long getKeyUseBlockIndex() {
        return keyUseBlockIndex;
    }

    public void setKeyOriginBlockIndex(long keyOriginBlockIndex, Election election) {
        this.keyOriginBlockIndex = keyOriginBlockIndex;
        this.election = election;
    }

    public void setKeyUseBlockIndex(long keyUseBlockIndex) {
        this.keyUseBlockIndex = keyUseBlockIndex;
    }

    public byte[] decryptWithPrivateKey(byte[] message) {
        try {
            return privateKeyDecryption.doFinal(message);
        } catch (BadPaddingException exception) {
            System.err.println("Couldn't decrypt with private key due to BadPaddingException");
            return null;
        } catch (IllegalBlockSizeException exception) {
            System.err.println("Couldn't decrypt with private key due to BadPaddingException");
            return null;
        }
    }

    public byte[] getPublicKey() {
        return PrivacyUtils.getPublicKeyBytesFromKeyPair(keyPair);
    }

    public RegistrationBlock getRegistrationBlock() {
        return registrationBlock;
    }

    public void setRegistrationBlock(RegistrationBlock registrationBlock) {
        this.registrationBlock = registrationBlock;
    }

    public byte[] getVote() {
        return vote;
    }

    public void setVote(byte[] vote) {
        this.vote = vote;
    }

    public byte[] getVoteSalt() {
        return voteSalt;
    }

    public void setVoteSalt(byte[] voteSalt) {
        this.voteSalt = voteSalt;
    }

    public long getCommitBlockIndex() {
        return commitBlockIndex;
    }

    public void setCommitBlockIndex(long commitBlockIndex) {
        this.commitBlockIndex = commitBlockIndex;
    }

    public Election getElection() {
        return election;
    }
}
