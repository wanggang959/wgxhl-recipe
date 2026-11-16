package com.wgxhl.recipe.commute;

import lombok.Data;
import java.time.LocalTime;

@Data
public class CommuteSettings {
    private LocalTime arrivalTime;
    private LocalTime noticeTime;
    private Integer bufferMinutes;
    private Integer fallbackMinutes;
    private String workDays;
    private String originName;
    private Double originLat;
    private Double originLon;
    private String destinationName;
    private Double destinationLat;
    private Double destinationLon;
    private Boolean emailEnabled;
    private Boolean siteEnabled;
    private Boolean pushEnabled;
    private Integer noticeDay = 0;
}
