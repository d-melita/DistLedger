package pt.tecnico.distledger.server.domain;

import pt.tecnico.distledger.server.domain.exceptions.*;
import pt.tecnico.distledger.server.domain.operation.*;
import pt.tecnico.distledger.utils.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class ServerState {
    private boolean isActive = true;
    private Map<String, Integer> accounts;
    private final List<Operation> ledger;
    private List<Integer> replicaTS = new ArrayList<>();
    private List<Integer> valueTS = new ArrayList<>();
    private Set<List<Integer>> registeredOps = new HashSet<List<Integer>>();
    private final int replicaId;
    private static final String BROKER = "broker";

    public ServerState(int replicaId, int numReplicas) {
        Logger.log("Initializing ServerState");
        this.ledger = new CopyOnWriteArrayList<>();
        this.accounts = new ConcurrentHashMap<>();
        Logger.log("Creating Broker Account");
        this.addAccount(BROKER, 1000);
        Logger.log("Broker Account created");
        Logger.log("ServerState initialized");
        this.replicaId = replicaId;
        for (int i = 0; i < numReplicas; i++) {
            this.replicaTS.add(0);
            this.valueTS.add(0);
        }
    }

    // User Interface Operations

    public synchronized void createAccount(String name, List<Integer> prevTS) {
        Logger.log("Creating account \'" + name + "\'");
        Logger.log("User prevTS is: " + prevTS);
        Logger.log("Replica TS is: " + getReplicaTS());
        if (!isActive) {
            throw new ServerUnavailableException();
        }
        if (accountExists(name)) {
            throw new AccountAlreadyExistsException(name);
        }
        CreateOp op = new CreateOp(name, prevTS, null);
        updateReplicaTS();
        if (TSBiggerThan(this.replicaTS, prevTS)) {
            updateValueTS();
            addAccount(name);
        }
        op.setTS(this.replicaId, this.replicaTS);
        addOperation(op);
        Logger.log("Account \'" + name + "\' created");
        Logger.log("At the end, Replica TS is: " + getReplicaTS());
    }

    public synchronized void deleteAccount(String name, List<Integer> prevTS) {
        Logger.log("Deleting account \'" + name + "\'");
        if (!isActive) {
            throw new ServerUnavailableException();
        }
        if (name.equals(BROKER)) {
            throw new DeleteBrokerAccountException(name);
        }
        if (!accountExists(name)) {
            throw new AccountDoesntExistException(name);
        }
        if (getAccountBalance(name, getReplicaTS()) > 0) {
            throw new AccountHasBalanceException(name);
        }
        DeleteOp op = new DeleteOp(name, prevTS, null);
        if (TSBiggerThan(this.replicaTS, prevTS)) {
            updateValueTS();
            removeAccount(name);
        }
        op.setTS(this.replicaId, this.replicaTS);
        addOperation(op);
        Logger.log("Account \'" + name + "\' deleted");
    }

    public synchronized void transferTo(String from, String to, Integer amount, List<Integer> prevTS) {
        Logger.log("Transferring " + amount + " from \'" + from + "\' to \'" + to + "\'");
        Logger.log("User prevTS is: " + prevTS);
        Logger.log("Replica TS is: " + getReplicaTS());
        if (!isActive) {
            throw new ServerUnavailableException();
        }
        if (amount <= 0) {
            throw new InvalidAmountException();
        }
        if (TSBiggerThan(this.replicaTS, prevTS)) {
            if (!accountExists(from) && !accountExists(to)) {
                throw new AccountDoesntExistException(from, to);
            }
            if (!accountExists(from)) {
                throw new AccountDoesntExistException(from);
            }
            if (!accountExists(to)) {
                throw new AccountDoesntExistException(to);
            }
            if (!accountHasBalance(from, amount)) {
                throw new InsufficientFundsException(from);
            }
            updateValueTS();
            updateAccount(from, -amount);
            updateAccount(to, amount);
        }
        TransferOp op = new TransferOp(from, to, amount, prevTS, null);
        updateReplicaTS();
        op.setTS(this.replicaId, this.replicaTS);
        addOperation(op);
        Logger.log("Transfer completed");
        Logger.log("At the end, Replica TS is: " + getReplicaTS());
    }

    public synchronized Integer getAccountBalance(String name, List<Integer> prevTS) {
        Logger.log("Getting balance of account \'" + name + "\'");
        Logger.log("User prevTS is: " + prevTS);
        Logger.log("Replica TS is: " + getReplicaTS());
        if (!isActive) {
            throw new ServerUnavailableException();
        }
        if (!TSBiggerThan(this.replicaTS, prevTS)) {
            throw new OperationNotStableException();
        }
        if (!accountExists(name)) {
            throw new AccountDoesntExistException(name);
        }
        Logger.log("At the end, Replica TS is: " + getReplicaTS());
        return accounts.get(name);
    }

    // Admin interface operations

    public synchronized void activate() {
        Logger.log("Admin activating server");
        this.isActive = true;
        Logger.log("Server activated");
    }

    public synchronized void deactivate() {
        Logger.log("Admin deactivating server");
        this.isActive = false;
        Logger.log("Server deactivated");
    }

    public synchronized List<Operation> getLedgerState() {
        Logger.log("Admin Getting ledger");
        return getLedger();
    }

    // Propagate ledger operations

    public synchronized void propagateState(List<Operation> ledger, List<Integer> propagatedTS) {
        for (Operation op : ledger) {
            if (registeredOps.contains(op.getTS())) { // duplicate operation
                Logger.log("Ignoring duplicate operation " + op.toString());
                continue;
            }
            Logger.log("Adding propagated operation " + op.toString());
            addOperation(op);
        }
        Logger.log("Merging propagated TS " + propagatedTS + " with replica TS " + this.replicaTS);
        mergeReplicaTS(propagatedTS);
        Logger.log("State propagated, now going to execute ledger");
        boolean noMoreExecutions = false;
        // now we will go through all operations in the ledger and execute them if they
        // are stable, updating the replicaTS after each execution
        while (!noMoreExecutions) {
            noMoreExecutions = true;
            for (Operation op : getLedger()) {
                Logger.log("Checking operation " + op.toString() + " for execution");
                if (TSBiggerThan(this.valueTS, op.getTS())) {
                    Logger.log("Ignoring operation " + op.toString() + " because it was already executed");
                    continue; // ignore operations already executed
                }
                if (TSBiggerThan(this.valueTS, op.getPrevTS())) { // prevTS < valueTS -> we can execute the operation
                    Logger.log("Executing propagated operation " + op.toString());
                    op.executeOperation(this);
                    // set operation TS if it is uninitialized
                    Logger.log("Merging propagated TS " + op.getTS() + " with replica TS " + this.replicaTS);
                    mergeValueTS(op.getTS());
                    noMoreExecutions = false; // if we can perform an operation, we need to go through the ledger again
                }
            }
        }
    }

    // Operation execution methods

    public void executeOperation(CreateOp op) {
        Logger.log("Executing create operation");
        if (accountExists(op.getAccount())) {
            return;
        }
        addAccount(op.getAccount());
    }

    public void executeOperation(DeleteOp op) {
        Logger.log("Executing delete operation");
        removeAccount(op.getAccount());
    }

    public void executeOperation(TransferOp op) {
        Logger.log("Executing transfer operation");
        String from = op.getAccount();
        String to = op.getDestAccount();
        Integer amount = op.getAmount();
        if (!accountExists(from) && !accountExists(to)) {
            return;
        }
        if (!accountExists(from)) {
            return;
        }
        if (!accountExists(to)) {
            return;
        }
        if (!accountHasBalance(from, amount)) {
            return;
        }
        updateAccount(op.getAccount(), -op.getAmount());
        updateAccount(op.getDestAccount(), op.getAmount());
    }

    // Timestamp manipulation methods

    private void addOperation(Operation op) {
        Logger.log("Adding operation " + op.toString() + " to ledger");
        this.ledger.add(op);
        registeredOps.add(op.getTS());
        Logger.log("Operation added");
    }

    private void updateReplicaTS() {
        replicaTS.set(this.replicaId, this.replicaTS.get(this.replicaId) + 1);
    }

    private void mergeReplicaTS(List<Integer> TS) {
        for (int i = 0; i < TS.size(); i++) {
            if (TS.get(i) > this.replicaTS.get(i)) {
                this.replicaTS.set(i, TS.get(i));
            }
        }
    }

    private void updateValueTS() {
        valueTS.set(this.replicaId, this.valueTS.get(this.replicaId) + 1);
    }

    private void mergeValueTS(List<Integer> TS) {
        for (int i = 0; i < TS.size(); i++) {
            if (TS.get(i) > this.valueTS.get(i)) {
                this.valueTS.set(i, TS.get(i));
            }
        }
    }

    private boolean TSBiggerThan(List<Integer> TS1, List<Integer> TS2) {
        for (int i = 0; i < TS1.size(); i++) {
            if (TS1.get(i) < TS2.get(i)) {
                return false;
            }
        }
        return true;
    }

    // Getters and Setters

    private void addAccount(String name) {
        this.accounts.put(name, 0);
    }

    private void addAccount(String name, int amount) {
        this.accounts.put(name, amount);
    }

    private void removeAccount(String name) {
        this.accounts.remove(name);
    }

    private void updateAccount(String name, int amount) {
        accounts.put(name, accounts.get(name) + amount);
    }

    private List<Operation> getLedger() {
        // create a copy of the ledger to avoid concurrent modification
        List<Operation> ledgerCopy = new CopyOnWriteArrayList<>();
        ledgerCopy.addAll(ledger);
        return ledgerCopy;
    }

    public synchronized List<Integer> getReplicaTS() {
        return this.replicaTS;
    }

    // Checker methods

    public synchronized boolean isActive() {
        return this.isActive;
    }

    private boolean accountExists(String name) {
        return accounts.get(name) != null;
    }

    private boolean accountHasBalance(String name, int amount) {
        return accounts.get(name) >= amount;
    }

    @Override
    public synchronized String toString() {
        return "ServerState{" +
                "ledger=" + ledger +
                ", accounts=" + accounts +
                '}';
    }
}
