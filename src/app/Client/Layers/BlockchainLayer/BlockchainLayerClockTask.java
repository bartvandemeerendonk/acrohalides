package app.Client.Layers.BlockchainLayer;

import app.Client.Layers.BlockchainLayer.BlockchainLayer;
import javafx.concurrent.Task;

public class BlockchainLayerClockTask extends Task {
    private BlockchainLayer blockchainLayer;

    public BlockchainLayerClockTask(BlockchainLayer blockchainLayer) {
        this.blockchainLayer = blockchainLayer;
    }

    @Override
    protected Object call() throws Exception {
        while (!blockchainLayer.getNetworkLayer().isExiting()) {
            blockchainLayer.update();
            Thread.sleep(10);
        }
        return null;
    }
}
