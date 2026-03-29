package com.saffaricarrers.saffaricarrers.Dtos;


import com.saffaricarrers.saffaricarrers.Entity.CarrierRoute;
import com.saffaricarrers.saffaricarrers.Entity.Package;
import com.saffaricarrers.saffaricarrers.Entity.CarrierProfile;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.List;

// Main response DTO
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LocationSearchResponse {
    private int carriersCount;
    private int sendersCount;
    private List<SimpleCarrierDto> carriers;
    private List<SimplePackageDto> packages;

    public int getCarriersCount() {
        return carriersCount;
    }

    public void setCarriersCount(int carriersCount) {
        this.carriersCount = carriersCount;
    }

    public int getSendersCount() {
        return sendersCount;
    }

    public void setSendersCount(int sendersCount) {
        this.sendersCount = sendersCount;
    }

    public List<SimpleCarrierDto> getCarriers() {
        return carriers;
    }

    public void setCarriers(List<SimpleCarrierDto> carriers) {
        this.carriers = carriers;
    }

    public List<SimplePackageDto> getPackages() {
        return packages;
    }

    public void setPackages(List<SimplePackageDto> packages) {
        this.packages = packages;
    }
}
