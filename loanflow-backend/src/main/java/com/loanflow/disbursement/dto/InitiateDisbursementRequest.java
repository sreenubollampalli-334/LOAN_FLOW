package com.loanflow.disbursement.dto;

public class InitiateDisbursementRequest {

    private Boolean simulateFailure = false;
    private String failureReason;

    public InitiateDisbursementRequest() {}

    public InitiateDisbursementRequest(Boolean simulateFailure, String failureReason) {
        this.simulateFailure = simulateFailure;
        this.failureReason = failureReason;
    }

    public Boolean getSimulateFailure() {
        return simulateFailure;
    }

    public void setSimulateFailure(Boolean simulateFailure) {
        this.simulateFailure = simulateFailure;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }
}
