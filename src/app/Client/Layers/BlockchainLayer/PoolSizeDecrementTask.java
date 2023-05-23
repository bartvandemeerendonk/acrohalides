package app.Client.Layers.BlockchainLayer;

import javafx.concurrent.Task;

public class PoolSizeDecrementTask extends Task {
    private static long DECREMENT_INTERVAL = 20000;
    ManagedDilutionProcess managedDilutionProcess;

    public PoolSizeDecrementTask(ManagedDilutionProcess managedDilutionProcess) {
        this.managedDilutionProcess = managedDilutionProcess;
    }

    @Override
    protected Object call() throws Exception {
        Thread.sleep(DECREMENT_INTERVAL);
        if (managedDilutionProcess.getDesiredPoolSize() > 2) {
            managedDilutionProcess.decrementDesiredPoolSize();
        }
        if (managedDilutionProcess.getDesiredPoolSize() > 2) {
            PoolSizeDecrementTask newTask = new PoolSizeDecrementTask(managedDilutionProcess);
            Thread newThread = new Thread(newTask);
            newThread.start();
        }
        return null;
    }
}
