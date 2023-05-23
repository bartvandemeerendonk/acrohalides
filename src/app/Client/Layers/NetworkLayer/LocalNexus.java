package app.Client.Layers.NetworkLayer;

import java.util.HashMap;

public class LocalNexus {
    private static LocalNexus instance = null;
    private HashMap<String, HashMap<Integer, LocalNetworkInterface>> localNetworkInterfaces;

    public static LocalNexus getInstance() {
        if (LocalNexus.instance == null) {
            LocalNexus.instance = new LocalNexus();
        }
        return LocalNexus.instance;
    }

    public void reset() {
        this.localNetworkInterfaces = new HashMap<>();
    }

    private LocalNexus() {
        reset();
    }

    public void register(LocalNetworkInterface localNetworkInterface) {
        if (!localNetworkInterfaces.containsKey(localNetworkInterface.getIpAddress())) {
            localNetworkInterfaces.put(localNetworkInterface.getIpAddress(), new HashMap<>());
        }
        localNetworkInterfaces.get(localNetworkInterface.getIpAddress()).put(localNetworkInterface.getPort(), localNetworkInterface);
    }

    public LocalNetworkInterface getLocalNetworkInterface(String ipAddress, int port) {
        LocalNetworkInterface toReturn = null;
        for (String registeredIpAddress: localNetworkInterfaces.keySet()) {
            if (ipAddress.equals(registeredIpAddress)) {
                HashMap<Integer, LocalNetworkInterface> interfacesForIPAddress = localNetworkInterfaces.get(registeredIpAddress);
                for (Integer registeredPort: interfacesForIPAddress.keySet()) {
                    if (registeredPort.intValue() == port) {
                        toReturn = interfacesForIPAddress.get(registeredPort);
                    }
                }
            }
        }
        return toReturn;
    }
}
