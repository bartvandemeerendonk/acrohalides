package app.Client.Layers.BlockchainLayer;

import app.Client.Datastructures.ByteMap;
import app.Client.Datastructures.ByteSet;
import app.Client.Layers.BlockchainLayer.DilutionProcess;
import app.Client.Layers.BlockchainLayer.Invite;
import app.Client.Utils.PrivacyUtils;

import java.util.ArrayList;

public class ParticipatingDilutionProcess extends DilutionProcess {
    private byte[] publicKeyForDilutionApplication;
    private long keyOriginBlockIndexForDilutionApplication;
    private byte[] poolPrivateKey;
    private byte[] publicKeyOfPoolManager;
    private long keyOriginBlockIndexOfPoolManager;
    private ByteSet oldKeys;
    private ByteMap<Long> keyOriginBlockIndices;
    private ByteSet newKeys;
    private ArrayList<Invite> backupInvites;

    public ParticipatingDilutionProcess() {
        super();
        oldKeys = new ByteSet(PrivacyUtils.PUBLIC_KEY_LENGTH);
        keyOriginBlockIndices = new ByteMap<>(PrivacyUtils.PUBLIC_KEY_LENGTH);
        newKeys = new ByteSet(PrivacyUtils.PUBLIC_KEY_LENGTH);
        backupInvites = new ArrayList<>();
    }

    public byte[] getPublicKeyForDilutionApplication() {
        return publicKeyForDilutionApplication;
    }

    public long getKeyOriginBlockIndexForDilutionApplication() {
        return keyOriginBlockIndexForDilutionApplication;
    }

    public void setPublicKeyForDilutionApplication(byte[] publicKeyForDilutionApplication, long keyOriginBlockIndexForDilutionApplication) {
        this.publicKeyForDilutionApplication = publicKeyForDilutionApplication;
        this.keyOriginBlockIndexForDilutionApplication = keyOriginBlockIndexForDilutionApplication;
    }

    public byte[] getPoolPrivateKey() {
        return poolPrivateKey;
    }

    public void setPoolPrivateKey(byte[] poolPrivateKey) {
        this.poolPrivateKey = poolPrivateKey;
    }

    public byte[] getPublicKeyOfPoolManager() {
        return publicKeyOfPoolManager;
    }

    public void setPublicKeyOfPoolManager(byte[] publicKeyOfPoolManager) {
        this.publicKeyOfPoolManager = publicKeyOfPoolManager;
    }

    public long getKeyOriginBlockIndexOfPoolManager() {
        return keyOriginBlockIndexOfPoolManager;
    }

    public void setKeyOriginBlockIndexOfPoolManager(long keyOriginBlockIndexOfPoolManager) {
        this.keyOriginBlockIndexOfPoolManager = keyOriginBlockIndexOfPoolManager;
    }

    public ByteSet getOldKeys() {
        return oldKeys;
    }

    public void addOldKey(byte[] oldKey, long keyOriginBlockIndex) {
        oldKeys.add(oldKey);
        keyOriginBlockIndices.put(oldKey, keyOriginBlockIndex);
    }

    public ByteSet getNewKeys() {
        return newKeys;
    }

    public void addNewKey(byte[] newKey) {
        newKeys.add(newKey);
    }

    public ByteMap<Long> getKeyOriginBlockIndices() {
        return keyOriginBlockIndices;
    }

    public void addReceipt(byte[] oldKey) {
        if (oldKeys.contains(oldKey)) {
            poolReceipts.add(oldKey);
        }
    }

    public int getNumberOfPoolReceipts() {
        return poolReceipts.size();
    }

    public void addBackupInvite(Invite invite) {
        backupInvites.add(invite);
    }

    public Invite popBackupInvite() {
        if (backupInvites.size() > 0) {
            Invite toReturn = backupInvites.get(0);
            backupInvites.remove(0);
            return toReturn;
        } else {
            return null;
        }
    }

}
