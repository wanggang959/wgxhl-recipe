package com.wgxhl.recipe.vehicle;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Consumption based on the published Xiaoxiong fuel-consumption algorithm. */
public final class FuelConsumptionCalculator {
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private FuelConsumptionCalculator() { }

    /** Records are supplied in ascending chronological order; missed means a fill was omitted before this record. */
    public static Result calculate(List<Reading> readings) {
        int size = readings.size();
        BigDecimal[] rates = new BigDecimal[size];
        String[] methods = new String[size];
        int lastFull = -1, lastLight = -1;
        for (int i = 0; i < size; i++) {
            Reading row = readings.get(i);
            if (i > 0 && !validPair(readings.get(i - 1), row)) { lastFull = -1; lastLight = -1; }
            if (!valid(row)) continue;
            if (row.full) {
                if (lastFull >= 0) fillPhase(readings, rates, methods, lastFull, i, true);
                lastFull = i;
            }
            if (row.light) {
                if (lastLight >= 0) fillPhase(readings, rates, methods, lastLight, i, false);
                lastLight = i;
            }
        }
        BigDecimal weighted = BigDecimal.ZERO, weight = BigDecimal.ZERO;
        List<Estimate> estimates = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            estimates.add(new Estimate(rates[i], methods[i]));
            Reading reading = readings.get(i);
            if (rates[i] != null && positive(reading.liters)) {
                weighted = weighted.add(rates[i].multiply(reading.liters));
                weight = weight.add(reading.liters);
            }
        }
        BigDecimal average = weight.signum() > 0 ? weighted.divide(weight, 6, RoundingMode.HALF_UP) : simpleAverage(readings);
        return new Result(estimates, average == null ? null : average.setScale(2, RoundingMode.HALF_UP), weight.signum() > 0 ? "WEIGHTED" : average == null ? null : "SIMPLE");
    }

    private static void fillPhase(List<Reading> rows, BigDecimal[] rates, String[] methods, int start, int end, boolean full) {
        BigDecimal liters = BigDecimal.ZERO;
        int first = full ? start + 1 : start;
        int last = full ? end : end - 1;
        for (int j = first; j <= last; j++) liters = liters.add(rows.get(j).liters);
        BigDecimal value = rate(liters, rows.get(end).mileage.subtract(rows.get(start).mileage));
        String method = full ? (end == start + 1 ? "FULL_SINGLE" : "FULL_PHASE") : (end == start + 1 ? "LIGHT_SINGLE" : "LIGHT_PHASE");
        for (int j = start; j < end; j++) if (rates[j] == null) set(rates, methods, j, value, method);
    }

    private static BigDecimal simpleAverage(List<Reading> rows) {
        if (rows.size() < 2) return null;
        BigDecimal liters = BigDecimal.ZERO;
        for (int i = 0; i < rows.size(); i++) {
            if (!valid(rows.get(i)) || (i > 0 && !validPair(rows.get(i - 1), rows.get(i)))) return null;
            if (i < rows.size() - 1) liters = liters.add(rows.get(i).liters);
        }
        return rate(liters, rows.get(rows.size() - 1).mileage.subtract(rows.get(0).mileage));
    }

    private static void set(BigDecimal[] values, String[] methods, int index, BigDecimal value, String method) { values[index] = value; methods[index] = method; }
    private static boolean positive(BigDecimal n) { return n != null && n.signum() > 0; }
    private static boolean valid(Reading r) { return r != null && r.mileage != null && r.mileage.signum() >= 0 && positive(r.liters); }
    private static boolean validPair(Reading a, Reading b) { return valid(a) && valid(b) && !b.missed && b.mileage.compareTo(a.mileage) > 0; }
    private static BigDecimal rate(BigDecimal liters, BigDecimal distance) { return liters.multiply(HUNDRED).divide(distance, 6, RoundingMode.HALF_UP); }

    public static final class Reading {
        private final BigDecimal mileage, liters;
        private final boolean full, light, missed;
        public Reading(BigDecimal mileage, BigDecimal liters, boolean full, boolean light, boolean missed) {
            this.mileage = mileage; this.liters = liters; this.full = full; this.light = light; this.missed = missed;
        }
    }
    public static final class Estimate {
        private final BigDecimal value;
        private final String method;
        private Estimate(BigDecimal value, String method) { this.value = value == null ? null : value.setScale(2, RoundingMode.HALF_UP); this.method = method; }
        public BigDecimal getValue() { return value; }
        public String getMethod() { return method; }
    }
    public static final class Result {
        private final List<Estimate> estimates;
        private final BigDecimal average;
        private final String averageMethod;
        private Result(List<Estimate> estimates, BigDecimal average, String averageMethod) { this.estimates = Collections.unmodifiableList(estimates); this.average = average; this.averageMethod = averageMethod; }
        public List<Estimate> getEstimates() { return estimates; }
        public BigDecimal getAverage() { return average; }
        public String getAverageMethod() { return averageMethod; }
    }
}
