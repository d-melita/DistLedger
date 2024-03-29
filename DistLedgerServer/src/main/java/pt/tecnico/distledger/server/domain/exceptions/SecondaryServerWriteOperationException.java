package pt.tecnico.distledger.server.domain.exceptions;

public class SecondaryServerWriteOperationException extends RuntimeException {
    public SecondaryServerWriteOperationException() {
        super("Currently connected to a secondary server, write operations are not allowed");
    }
}
