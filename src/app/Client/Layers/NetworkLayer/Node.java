package app.Client.Layers.NetworkLayer;

public class Node {
    private String ipAddress;
    private int port;
    private int spamCounter;
    private long lastSpamTime;

    public Node(String ipAddress, int port) {
        this.ipAddress = ipAddress;
        this.port = port;
        this.spamCounter = 0;
        this.lastSpamTime = 0;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public int getPort() {
        return port;
    }

    public boolean equals(Node otherNode) {
        if (otherNode.getPort() != port) {
            return false;
        }
        return otherNode.getIpAddress().equals(ipAddress);
    }

    public boolean equals(String ipAddress, int port) {
        if (port != this.port) {
            return false;
        }
        return ipAddress.equals(this.ipAddress);
    }

    public int getSpamCounter() {
        return spamCounter;
    }

    public void incrementSpamCounter() {
        long currentSpamTime = System.currentTimeMillis();
        if (currentSpamTime > lastSpamTime + 100) {
            spamCounter = 0;
        }
        lastSpamTime = currentSpamTime;
        spamCounter++;
    }

    public long getLastSpamTime() {
        return lastSpamTime;
    }
}
