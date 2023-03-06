package pt.tecnico.distledger.server.domain.operation;
import pt.ulisboa.tecnico.distledger.contract.DistLedgerCommonDefinitions.OperationType;

public class TransferOp extends Operation {
    private String destAccount;
    private int amount;

    public TransferOp(String fromAccount, String destAccount, int amount, OperationType type) {
        super(fromAccount, type);
        this.destAccount = destAccount;
        this.amount = amount;
    }

    public String getDestAccount() {
        return destAccount;
    }

    public void setDestAccount(String destAccount) {
        this.destAccount = destAccount;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }

}
