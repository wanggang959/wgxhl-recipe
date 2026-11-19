package com.wgxhl.recipe.vehicle;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class FuelConsumptionCalculatorTest {
    private FuelConsumptionCalculator.Reading r(String km, String liters, boolean full, boolean light, boolean missed) {
        return new FuelConsumptionCalculator.Reading(new BigDecimal(km), new BigDecimal(liters), full, light, missed);
    }
    @Test void fullTanksTakePrecedenceOverLights() {
        FuelConsumptionCalculator.Result result = FuelConsumptionCalculator.calculate(Arrays.asList(r("100", "4", true, true, false), r("200", "8", true, true, false)));
        assertEquals(new BigDecimal("8.00"), result.getEstimates().get(0).getValue());
        assertEquals("FULL_SINGLE", result.getEstimates().get(0).getMethod());
    }
    @Test void partialFillsUseFullTankPhase() {
        FuelConsumptionCalculator.Result result = FuelConsumptionCalculator.calculate(Arrays.asList(r("100", "4", true, false, false), r("150", "2", false, false, false), r("200", "3", true, false, false)));
        assertEquals(new BigDecimal("5.00"), result.getEstimates().get(0).getValue());
        assertEquals("FULL_PHASE", result.getEstimates().get(1).getMethod());
    }
    @Test void lightPhaseIncludesFirstFillAndExcludesLast() {
        FuelConsumptionCalculator.Result result = FuelConsumptionCalculator.calculate(Arrays.asList(r("100", "3", false, true, false), r("150", "2", false, false, false), r("200", "9", false, true, false)));
        assertEquals(new BigDecimal("5.00"), result.getEstimates().get(0).getValue());
        assertEquals("LIGHT_PHASE", result.getEstimates().get(1).getMethod());
    }
    @Test void missedFillInvalidatesSegment() {
        FuelConsumptionCalculator.Result result = FuelConsumptionCalculator.calculate(Arrays.asList(r("100", "4", true, false, false), r("150", "2", false, false, true), r("200", "3", true, false, false)));
        assertNull(result.getEstimates().get(0).getValue());
        assertNull(result.getAverage());
    }
    @Test void simpleFallbackAndWeightedAverage() {
        FuelConsumptionCalculator.Result simple = FuelConsumptionCalculator.calculate(Arrays.asList(r("100", "4", false, false, false), r("200", "8", false, false, false)));
        assertEquals(new BigDecimal("4.00"), simple.getAverage());
        assertEquals("SIMPLE", simple.getAverageMethod());
        FuelConsumptionCalculator.Result weighted = FuelConsumptionCalculator.calculate(Arrays.asList(r("100", "4", true, false, false), r("200", "8", true, false, false), r("300", "4", true, false, false)));
        assertEquals(new BigDecimal("5.33"), weighted.getAverage());
        assertEquals("WEIGHTED", weighted.getAverageMethod());
    }
    @Test void earlierLightPhaseSurvivesLaterFullTankPhase() {
        FuelConsumptionCalculator.Result result = FuelConsumptionCalculator.calculate(Arrays.asList(
                r("100", "4", true, true, false), r("150", "2", false, false, false),
                r("200", "3", false, true, false), r("300", "4", true, true, false)));
        assertEquals(new BigDecimal("6.00"), result.getEstimates().get(0).getValue());
        assertEquals("LIGHT_PHASE", result.getEstimates().get(1).getMethod());
        assertEquals(new BigDecimal("4.50"), result.getEstimates().get(2).getValue());
        assertEquals("FULL_PHASE", result.getEstimates().get(2).getMethod());
    }
}
