package app.Client.Layers.BlockchainLayer;

public class DilutionApplication {
    private KeyWithOrigin keyWithOrigin;
    private byte[] signature;

    public DilutionApplication(KeyWithOrigin keyWithOrigin, byte[] signature) {
        this.keyWithOrigin = keyWithOrigin;
        this.signature = signature;
    }

    public KeyWithOrigin getKeyWithOrigin() {
        return keyWithOrigin;
    }

    public byte[] getSignature() {
        return signature;
    }
}
