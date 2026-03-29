package com.saffaricarrers.saffaricarrers.Responses;

import com.saffaricarrers.saffaricarrers.Dtos.AlertDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AdminAlertsResponse {
    private List<AlertDto> criticalAlerts;
    private List<AlertDto> warnings;
    private LocalDateTime generatedAt;
}