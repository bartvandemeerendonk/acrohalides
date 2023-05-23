package app.Client.Layers.NetworkLayer;

import javafx.concurrent.Task;

public class NetworkingTask extends Task<Void> {
    private NetworkLayer networkLayer;

    public void setNetworkLayer(NetworkLayer networkLayer) {
        this.networkLayer = networkLayer;
    }

    @Override
    protected Void call() throws Exception {
        if (!networkLayer.isExiting()) {
            networkLayer.handleMessages();
            NetworkingTask nextTask = new NetworkingTask();
            nextTask.setNetworkLayer(networkLayer);
            Thread thread = new Thread(nextTask);
            thread.start();
        } else {
            System.out.println("Shut down network thread of " + networkLayer.getIpAddress());
        }
        return null;
    }
}
