package com.wgxhl.recipe.commute;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;

class CommuteRecommendationTest {
    @Test void actualRainUsesOnlyMatchingKnownRecordsAndFallsBackSeparately() {
        LocalDate date=LocalDate.of(2026,11,9);
        java.util.List<CommuteRecommendation.Sample> samples=Arrays.asList(
                new CommuteRecommendation.Sample(date.minusWeeks(1),40,"DRY",true),
                new CommuteRecommendation.Sample(date.minusWeeks(2),42,"DRY",true),
                new CommuteRecommendation.Sample(date.minusWeeks(3),80,"RAIN",true),
                new CommuteRecommendation.Sample(date.minusWeeks(4),150,null,true),
                new CommuteRecommendation.Sample(date.minusWeeks(5),160,"UNKNOWN",true));
        CommuteRecommendation.Result dry=CommuteRecommendation.calculateForRain(date,LocalTime.of(9,0),10,45,"DRY",samples);
        CommuteRecommendation.Result rain=CommuteRecommendation.calculateForRain(date,LocalTime.of(9,0),10,45,"RAIN",samples);
        assertEquals(41,dry.minutes);assertEquals(2,dry.sampleCount);
        assertEquals(45,rain.minutes);assertEquals(0,rain.sampleCount);assertEquals("FALLBACK",rain.source);
    }

    @Test void mailVariablesUseSendDateAndActualCommuteDay() {
        LocalDate today=LocalDate.of(2026,11,3), target=today.plusDays(1);
        CommuteRecommendation.Result r=CommuteRecommendation.calculateForRain(target,LocalTime.of(9,0),10,45,"DRY",Collections.emptyList());
        java.util.Map<String,Object> result=new java.util.HashMap<>();result.put("arrival",target.atTime(9,0).toString());result.put("bufferMinutes",10);result.put("weatherSummary","阴");
        java.util.Map<String,String> variables=CommuteService.mailVariables(today,target,result,r,r);
        assertEquals("明天",variables.get("dayLabel")); assertTrue(variables.get("sendDate").startsWith("2026-11-03"));
        assertEquals("08:05",variables.get("dryDepartureTime"));assertTrue(variables.get("rainBasis").contains("记录不足"));
    }
    @Test void selectsSameWeekdayAndWeather() {
        LocalDate date = LocalDate.of(2026, 11, 9);
        CommuteRecommendation.Result result = CommuteRecommendation.calculate(date, LocalTime.of(9, 0), 10, 35, "RAIN", Arrays.asList(
                new CommuteRecommendation.Sample(date.minusWeeks(1), 41, "RAIN", true),
                new CommuteRecommendation.Sample(date.minusWeeks(2), 42, "RAIN", true),
                new CommuteRecommendation.Sample(date.minusWeeks(3), 100, "RAIN", false)));
        assertEquals(42, result.minutes);
        assertEquals(2, result.sampleCount);
        assertEquals("SAME_DAY_WEATHER", result.source);
        assertEquals(LocalTime.of(8, 8), result.departure.toLocalTime());
    }

    @Test void fallsBackAndHandlesPreviousDay() {
        LocalDate date = LocalDate.of(2026, 11, 9);
        CommuteRecommendation.Result result = CommuteRecommendation.calculate(date, LocalTime.of(0, 15), 10, 35, null, Collections.emptyList());
        assertEquals(date.minusDays(1), result.departure.toLocalDate());
        assertEquals(LocalTime.of(23, 30), result.departure.toLocalTime());
        assertEquals("FALLBACK", result.source);
    }

    @Test void weatherUnknownNeverMatchesUnknownSamples() {
        LocalDate date = LocalDate.of(2026, 11, 9);
        CommuteRecommendation.Result result = CommuteRecommendation.calculate(date, LocalTime.NOON, 0, 30, "UNKNOWN", Arrays.asList(
                new CommuteRecommendation.Sample(date.minusWeeks(1), 20, "UNKNOWN", true),
                new CommuteRecommendation.Sample(date.minusWeeks(2), 30, "UNKNOWN", true)));
        assertEquals("SAME_DAY", result.source);
        assertEquals(25, result.minutes);
    }
}
