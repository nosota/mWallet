package com.nosota.mwallet.error;

public class TransactionGroupZeroingOutException extends Exception{
    public TransactionGroupZeroingOutException() {
    }

    public TransactionGroupZeroingOutException(String message) {
        super(message);
    }

    public TransactionGroupZeroingOutException(String message, Throwable cause) {
        super(message, cause);
    }

    public TransactionGroupZeroingOutException(Throwable cause) {
        super(cause);
    }

    public TransactionGroupZeroingOutException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
