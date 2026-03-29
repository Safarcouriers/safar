package com.saffaricarrers.saffaricarrers.Dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RequestsTabResponse {

    // ✅ Legacy fields (backward compatibility)
    private List<RequestSummary> requestsReceivedFromSenders; // Pending only
    private List<RequestSummary> requestsReceivedFromCarriers; // Pending only
    private int totalPendingCount;

    // ✅ NEW: Categorized by status - AS CARRIER
    private List<RequestSummary> pendingRequestsAsCarrier;
    private List<RequestSummary> activeRequestsAsCarrier;
    private List<RequestSummary> completedRequestsAsCarrier;
    private List<RequestSummary> cancelledRequestsAsCarrier;

    // ✅ NEW: Categorized by status - AS SENDER
    private List<RequestSummary> pendingRequestsAsSender;
    private List<RequestSummary> activeRequestsAsSender;
    private List<RequestSummary> completedRequestsAsSender;
    private List<RequestSummary> cancelledRequestsAsSender;

    // ✅ NEW: Total counts
    private int totalActiveCount;
    private int totalCompletedCount;
    private int totalCancelledCount;
}
