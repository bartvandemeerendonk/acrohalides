package app.Client.Layers.NetworkLayer;

public class LocalNetworkInterface implements NetworkInterface {
    private String ipAddress;
    private int port;
    private LocalNexus localNexus;
    private NetworkLayer networkLayer;

    public LocalNetworkInterface(String ipAddress, int port, LocalNexus localNexus, NetworkLayer networkLayer) {
        this.ipAddress = ipAddress;
        this.port = port;
        this.localNexus = localNexus;
        localNexus.register(this);
        this.networkLayer = networkLayer;
    }

    @Override
    public void sendMessage(String ipAddress, int port, byte[] message) {
        LocalNetworkInterface destination = localNexus.getLocalNetworkInterface(ipAddress, port);
        if (destination != null) {
            destination.receiveMessage(this.ipAddress, this.port, message);
        } else {
            System.out.println("Couldn't find IP address!");
        }
    }

    @Override
    public void sendUnicastMessage(String ipAddress, int port, byte[] message) {
        LocalNetworkInterface destination = localNexus.getLocalNetworkInterface(ipAddress, port);
        if (destination != null) {
            destination.receiveUnicastMessage(this.ipAddress, this.port, message);
        }
    }

    @Override
    public void connect(String ipAddress, int port) {
        LocalNetworkInterface destination = localNexus.getLocalNetworkInterface(ipAddress, port);
        if (destination != null) {
            destination.receiveConnection(this.ipAddress, this.port);
        }
    }

    @Override
    public String getIpAddress() {
        return ipAddress;
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public void setNetworkLayer(NetworkLayer networkLayer) {
        this.networkLayer = networkLayer;
    }

    public void receiveMessage(String ipAddress, int port, byte[] message) {
//        System.out.println("LNI receive message");
        networkLayer.receiveAnonymousBroadcast(message, ipAddress, port);
    }

    public void receiveUnicastMessage(String ipAddress, int port, byte[] message) {
        networkLayer.receiveUnicast(message, ipAddress, port);
    }

    public void receiveConnection(String ipAddress, int port) {
        networkLayer.receiveConnection(ipAddress, port);
    }
}
