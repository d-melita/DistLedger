package pt.tecnico.distledger.namingserver;

import java.io.IOException;
import io.grpc.Server;
import io.grpc.ServerBuilder;

import pt.tecnico.distledger.utils.Logger;
import pt.tecnico.distledger.namingserver.domain.NamingServer;
import pt.tecnico.distledger.namingserver.service.namingServerDistLedgerServiceImpl;

public class NamingServerMain {

    private static final int namingServerPort = 5001;

    public static void main(String[] args) throws IOException, InterruptedException{

        System.out.println(NamingServerMain.class.getSimpleName());

        // check arguments
        if (args.length != 0) {
            System.err.println("Usage: mvn exec:java");
            return;
        }

        NamingServer namingServer = new NamingServer();

        // Create a new server to listen on port
        Server server = ServerBuilder.forPort(namingServerPort)
                .addService(new namingServerDistLedgerServiceImpl(namingServer))
                .build();
        Logger.log("Server created");

        // Start the server
        server.start();

        // Server threads are running in the background.
        System.out.println("Server started");

        // Do not exit the main thread. Wait until server is terminated.
        server.awaitTermination();

    }
}
