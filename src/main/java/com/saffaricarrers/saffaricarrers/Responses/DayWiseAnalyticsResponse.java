package com.saffaricarrers.saffaricarrers.Responses;

import com.saffaricarrers.saffaricarrers.Dtos.DayAnalyticsDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DayWiseAnalyticsResponse {
    private List<DayAnalyticsDto> dayWiseData;
    private LocalDateTime generatedAt;
}