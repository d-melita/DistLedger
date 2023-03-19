package pt.tecnico.distledger.server.domain;

import pt.tecnico.distledger.server.exceptions.SecondaryServerWriteOperationException;

public class SecondaryServerState extends ServerState {

    @Override
    public synchronized void createAccount(String name) {
        throw new SecondaryServerWriteOperationException();
    }

    @Override
    public synchronized void deleteAccount(String name) {
        throw new SecondaryServerWriteOperationException();
    }

    @Override
    public synchronized void transfer(String from, String to, Integer amount) {
        throw new SecondaryServerWriteOperationException();
    }
}
