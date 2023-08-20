package com.nosota.mwallet.error;

public class WalletNotFoundException extends Exception{
    public WalletNotFoundException() {
    }

    public WalletNotFoundException(String message) {
        super(message);
    }

    public WalletNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    public WalletNotFoundException(Throwable cause) {
        super(cause);
    }

    public WalletNotFoundException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
