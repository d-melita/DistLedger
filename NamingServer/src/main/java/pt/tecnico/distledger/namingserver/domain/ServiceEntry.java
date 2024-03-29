package pt.tecnico.distledger.namingserver.domain;

import pt.tecnico.distledger.namingserver.exceptions.RegistryFailedException;
import pt.tecnico.distledger.namingserver.exceptions.RemovalFailedException;

import java.util.List;
import java.util.ArrayList;

public class ServiceEntry {
    private String service;
    private static int maxServers;

    private List<ServerEntry> servers;

    public ServiceEntry(String service, int maxServers) {
        this.service = service;
        this.servers = new ArrayList<ServerEntry>();
        this.maxServers = maxServers;
    }

    public String getService() {
        return service;
    }

    public int getMaxServers() {
        return maxServers;
    }

    public synchronized ServerEntry getServer(String host) {
        for (ServerEntry server : servers) {
            if (server.getHost().equals(host)) {
                return server;
            }
        }
        return null;
    }

    private void addServer(ServerEntry server) {
        servers.add(server);
    }

    private void removeServer(ServerEntry server) {
        servers.remove(server);
    }

    public synchronized void addServer(String host, String qualifier) {
        if (this.getServer(host) != null) {
            throw new RegistryFailedException(host);
        }
        this.addServer(new ServerEntry(host, qualifier));
    }

    public synchronized List<String> lookupServer(String qualifier) {
        List<String> hosts = new ArrayList<String>();
        for (ServerEntry server : servers) {
            if (server.getQualifier().equals(qualifier)) {
                hosts.add(server.getHost());
            }
        }
        return hosts;
    }

    public synchronized List<String> lookupServer() {
        List<String> hosts = new ArrayList<String>();
        for (ServerEntry server : servers) {
            hosts.add(server.getHost());
        }
        return hosts;
    }

    public synchronized void removeServer(String host) {
        ServerEntry server = this.getServer(host);
        if (server != null) {
            this.removeServer(server);
        } else {
            throw new RemovalFailedException(host);
        }
    }

    public synchronized int getNumServers() {
        return servers.size();
    }
}
