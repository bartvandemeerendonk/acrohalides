package app.Client.Layers.NetworkLayer;

public class MessageWithSender {
    private byte[] message;
    private Node sender;

    public MessageWithSender(byte[] message, Node sender) {
        this.message = message;
        this.sender = sender;
    }

    public byte[] getMessage() {
        return message;
    }

    public Node getSender() {
        return sender;
    }
}
