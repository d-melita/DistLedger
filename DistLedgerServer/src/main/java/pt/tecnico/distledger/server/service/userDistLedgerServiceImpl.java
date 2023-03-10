package pt.tecnico.distledger.server.service;

import pt.tecnico.distledger.server.exceptions.*;
import io.grpc.Status;
import pt.ulisboa.tecnico.distledger.contract.user.UserServiceGrpc;
import pt.ulisboa.tecnico.distledger.contract.user.UserDistLedger.*;
import io.grpc.stub.StreamObserver;
import pt.tecnico.distledger.server.domain.ServerState;

public class userDistLedgerServiceImpl extends UserServiceGrpc.UserServiceImplBase {
    private final String DEFAULT_ERROR_MESSAGE = "Operation Failed";
    private ServerState state;

    public userDistLedgerServiceImpl(ServerState state) {
        this.state = state;
    }

    @Override
    public void createAccount(CreateAccountRequest request, StreamObserver<CreateAccountResponse> responseObserver) {
        try {
            state.createAccount(request.getUserId());
            CreateAccountResponse response = CreateAccountResponse.newBuilder().build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (AccountAlreadyExistsException e) {
            responseObserver
                    .onError(Status.ALREADY_EXISTS.withDescription(e.getMessage()).asRuntimeException());
        } catch (ServerUnavailableException e) {
            responseObserver.onError(Status.UNAVAILABLE.asRuntimeException());
        } catch (Exception e) {
            responseObserver
                    .onError(Status.UNKNOWN.withDescription(DEFAULT_ERROR_MESSAGE).asRuntimeException());
        }
    }

    @Override
    public void deleteAccount(DeleteAccountRequest request, StreamObserver<DeleteAccountResponse> responseObserver) {
        try {
            state.deleteAccount(request.getUserId());
            DeleteAccountResponse response = DeleteAccountResponse.newBuilder().build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (AccountDoesntExistException  e) {
            responseObserver
                    .onError(Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
        } catch (DeleteBrokerAccountException e) {
            responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).asRuntimeException());
        }  catch (ServerUnavailableException e) {
            responseObserver.onError(Status.UNAVAILABLE.asRuntimeException());
        }  catch (AccountHasBalanceException e) {
            responseObserver.onError(Status.FAILED_PRECONDITION.withDescription(e.getMessage()).asRuntimeException());
        } catch (Exception e) {
            responseObserver
                    .onError(Status.UNKNOWN.withDescription(DEFAULT_ERROR_MESSAGE).asRuntimeException());
        }
    }

    @Override
    public void balance(BalanceRequest request, StreamObserver<BalanceResponse> responseObserver) {
        try {
            int balance = state.getAccountBalance(request.getUserId());
            BalanceResponse response = BalanceResponse.newBuilder().setValue(balance).build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (AccountDoesntExistException e) {
            responseObserver
                    .onError(Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
        } catch (ServerUnavailableException e) {
            responseObserver.onError(Status.UNAVAILABLE.asRuntimeException());
        } catch (Exception e) {
            responseObserver
                    .onError(Status.UNKNOWN.withDescription(DEFAULT_ERROR_MESSAGE).asRuntimeException());
        }
    }

    @Override
    public void transferTo(TransferToRequest request, StreamObserver<TransferToResponse> responseObserver) {
        try {
            state.transfer(request.getAccountFrom(), request.getAccountTo(), request.getAmount());
            TransferToResponse response = TransferToResponse.newBuilder().build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (AccountDoesntExistException e) {
            responseObserver
                    .onError(Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
        } catch (InsufficientFundsException e) {
            responseObserver
                    .onError(Status.FAILED_PRECONDITION.withDescription(e.getMessage()).asRuntimeException());
        } catch (ServerUnavailableException e) {
            responseObserver
                    .onError(Status.UNAVAILABLE.asRuntimeException());
        } catch (InvalidAmountException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
        } catch (Exception e) {
            responseObserver
                    .onError(Status.UNKNOWN.withDescription(DEFAULT_ERROR_MESSAGE).asRuntimeException());
        }
    }
}