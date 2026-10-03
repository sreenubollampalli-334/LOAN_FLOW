package com.loanflow.disbursement.service;

import com.loanflow.disbursement.dto.InitiateDisbursementRequest;
import com.loanflow.disbursement.entity.Disbursement;
import org.springframework.stereotype.Component;

@Component
public class DisbursementProcessor {

    private static final ThreadLocal<String> TEST_FAILURE_REASON = new ThreadLocal<>();

    public static void setTestFailure(String failureReason) {
        TEST_FAILURE_REASON.set(failureReason);
    }

    public static void clearTestFailure() {
        TEST_FAILURE_REASON.remove();
    }

    public static class SimulationResult {
        private final boolean success;
        private final String failureReason;

        public SimulationResult(boolean success, String failureReason) {
            this.success = success;
            this.failureReason = failureReason;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getFailureReason() {
            return failureReason;
        }
    }

    /**
     * Executes a deterministic simulation.
     * Production/standard requests must NOT be controlled by client JSON payloads.
     * Controlled test failure mechanisms (ThreadLocal override or test header) are supported for automated tests.
     */
    public SimulationResult simulateDisbursement(Disbursement disbursement, InitiateDisbursementRequest request, String testSimulateFailureHeader, String testFailureReasonHeader) {
        // 1. ThreadLocal test override (for unit tests)
        String threadLocalReason = TEST_FAILURE_REASON.get();
        if (threadLocalReason != null) {
            return new SimulationResult(false, threadLocalReason);
        }

        // 2. Test-only header override (for integration tests)
        if ("true".equalsIgnoreCase(testSimulateFailureHeader)) {
            String reason = (testFailureReasonHeader != null && !testFailureReasonHeader.isBlank())
                    ? testFailureReasonHeader
                    : "Simulated test transfer timeout";
            return new SimulationResult(false, reason);
        }

        // 3. Default deterministic processor behavior (Production: Success for valid approved loan requests)
        return new SimulationResult(true, null);
    }
}
