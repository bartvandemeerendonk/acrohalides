package app.Client.Layers.ApplicationLayer;

import java.security.KeyPair;

public class ElectionManagerRole {
    private KeyPair keyPair;

    public ElectionManagerRole(KeyPair keyPair) {
        this.keyPair = keyPair;
    }

    public KeyPair getKeyPair() {
        return keyPair;
    }
}
