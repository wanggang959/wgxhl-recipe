package com.wgxhl.recipe.commute;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class CommuteRecordInput {
    private String id;
    private LocalDate commuteDate;
    private String direction;
    private LocalDateTime departureTime;
    private LocalDateTime arrivalTime;
    private String transport;
    private String remark;
    private Boolean includeStats;
    private String actualRain;
}
