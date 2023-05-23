package app.Client.Layers.NetworkLayer;

import app.Client.Layers.NetworkLayer.NetworkLayer;

public interface NetworkInterface {
    public void sendMessage(String ipAddress, int port, byte[] message);
    public void sendUnicastMessage(String ipAddress, int port, byte[] message);
    public void connect(String ipAddress, int port);
    public String getIpAddress();
    public int getPort();
    public void setNetworkLayer(NetworkLayer networkLayer);
}
