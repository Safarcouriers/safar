package com.saffaricarrers.saffaricarrers.Dtos;


import lombok.AllArgsConstructor;
        import lombok.Data;
        import lombok.NoArgsConstructor;

        import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PackageStatsDto1 {
    private Long totalPackages;
    private Long createdPackages;
    private Long requestSentPackages;
    private Long matchedPackages;
    private Long pickedUpPackages;
    private Long inTransitPackages;
    private Long deliveredPackages;
    private Long cancelledPackages;
    private Long insuredPackages;
    private Long uninsuredPackages;
    private Double totalPackageValue;
    private Map<String, Long> productTypeBreakdown;
    private Map<String, Long> transportTypeBreakdown;

    // Explicit setters (in case Lombok isn't working properly)
    public void setTotalPackages(Long totalPackages) {
        this.totalPackages = totalPackages;
    }

    public void setCreatedPackages(Long createdPackages) {
        this.createdPackages = createdPackages;
    }

    public void setRequestSentPackages(Long requestSentPackages) {
        this.requestSentPackages = requestSentPackages;
    }

    public void setMatchedPackages(Long matchedPackages) {
        this.matchedPackages = matchedPackages;
    }

    public void setPickedUpPackages(Long pickedUpPackages) {
        this.pickedUpPackages = pickedUpPackages;
    }

    public void setInTransitPackages(Long inTransitPackages) {
        this.inTransitPackages = inTransitPackages;
    }

    public void setDeliveredPackages(Long deliveredPackages) {
        this.deliveredPackages = deliveredPackages;
    }

    public void setCancelledPackages(Long cancelledPackages) {
        this.cancelledPackages = cancelledPackages;
    }

    public void setInsuredPackages(Long insuredPackages) {
        this.insuredPackages = insuredPackages;
    }

    public void setUninsuredPackages(Long uninsuredPackages) {
        this.uninsuredPackages = uninsuredPackages;
    }

    public void setTotalPackageValue(Double totalPackageValue) {
        this.totalPackageValue = totalPackageValue;
    }

    public void setProductTypeBreakdown(Map<String, Long> productTypeBreakdown) {
        this.productTypeBreakdown = productTypeBreakdown;
    }

    public void setTransportTypeBreakdown(Map<String, Long> transportTypeBreakdown) {
        this.transportTypeBreakdown = transportTypeBreakdown;
    }

    // Explicit getters
    public Long getTotalPackages() {
        return totalPackages;
    }

    public Long getCreatedPackages() {
        return createdPackages;
    }

    public Long getRequestSentPackages() {
        return requestSentPackages;
    }

    public Long getMatchedPackages() {
        return matchedPackages;
    }

    public Long getPickedUpPackages() {
        return pickedUpPackages;
    }

    public Long getInTransitPackages() {
        return inTransitPackages;
    }

    public Long getDeliveredPackages() {
        return deliveredPackages;
    }

    public Long getCancelledPackages() {
        return cancelledPackages;
    }

    public Long getInsuredPackages() {
        return insuredPackages;
    }

    public Long getUninsuredPackages() {
        return uninsuredPackages;
    }

    public Double getTotalPackageValue() {
        return totalPackageValue;
    }

    public Map<String, Long> getProductTypeBreakdown() {
        return productTypeBreakdown;
    }

    public Map<String, Long> getTransportTypeBreakdown() {
        return transportTypeBreakdown;
    }
}