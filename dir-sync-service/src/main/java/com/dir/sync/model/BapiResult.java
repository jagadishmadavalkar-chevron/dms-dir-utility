package com.dir.sync.model;

public class BapiResult {

    private final boolean success;
    private final String  status;
    private final String  message;
    private final String  dirNumber;

    private BapiResult(boolean success, String status, String message, String dirNumber) {
        this.success   = success;
        this.status    = status;
        this.message   = message;
        this.dirNumber = dirNumber;
    }

    public static BapiResult completed(String message, String dirNumber) {
        return new BapiResult(true, "COMPLETED", message, dirNumber);
    }

    public static BapiResult partCompleted(String message, String dirNumber) {
        return new BapiResult(true, "PART_COMPLETED", message, dirNumber);
    }

    public static BapiResult error(String message) {
        return new BapiResult(false, "ERROR", message, null);
    }

    public boolean isSuccess()   { return success;   }
    public String  getStatus()   { return status;    }
    public String  getMessage()  { return message;   }
    public String  getDirNumber(){ return dirNumber; }
}
