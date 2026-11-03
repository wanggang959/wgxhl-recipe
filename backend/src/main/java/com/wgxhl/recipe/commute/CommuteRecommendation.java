package com.wgxhl.recipe.commute;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public final class CommuteRecommendation {
    private static final int MIN_SAMPLES = 2;
    private static final int MAX_SAMPLES = 8;

    private CommuteRecommendation() { }

    public static Result calculateForRain(LocalDate date, LocalTime arrival, int bufferMinutes,
                                          int fallbackMinutes, String rain, List<Sample> samples) {
        List<Sample> valid=samples.stream().filter(s -> s!=null && s.date!=null && s.date.isBefore(date)
                && s.minutes>0 && s.minutes<=1440 && s.included && rain.equals(s.weatherGroup))
                .sorted(Comparator.comparing((Sample s)->s.date).reversed()).collect(Collectors.toList());
        List<Sample> sameDay=filter(valid,s->s.date.getDayOfWeek()==date.getDayOfWeek());
        List<Sample> workdays=filter(valid,s->s.date.getDayOfWeek().getValue()<=5);
        List<Sample> selected; String source;
        if(sameDay.size()>=MIN_SAMPLES){selected=sameDay;source="SAME_DAY_WEATHER";}
        else if(date.getDayOfWeek().getValue()<=5 && workdays.size()>=MIN_SAMPLES){selected=workdays;source="WORKDAY_WEATHER";}
        else {selected=new ArrayList<>();source="FALLBACK";}
        int count=Math.min(MAX_SAMPLES,selected.size());
        int minutes=count==0?fallbackMinutes:(int)Math.ceil(selected.subList(0,count).stream().mapToInt(s->s.minutes).average().orElse(fallbackMinutes));
        return new Result(date.atTime(arrival).minusMinutes(minutes+bufferMinutes),minutes,count,source);
    }

    public static Result calculate(LocalDate date, LocalTime arrival, int bufferMinutes,
                                   int fallbackMinutes, String weatherGroup, List<Sample> samples) {
        List<Sample> valid = samples.stream()
                .filter(s -> s != null && s.date != null && s.date.isBefore(date)
                        && s.minutes > 0 && s.minutes <= 1440 && s.included)
                .sorted(Comparator.comparing((Sample s) -> s.date).reversed())
                .collect(Collectors.toList());
        DayOfWeek weekday = date.getDayOfWeek();
        List<Sample> sameDay = filter(valid, s -> s.date.getDayOfWeek() == weekday);
        boolean hasWeather = weatherGroup != null && !weatherGroup.isEmpty() && !"UNKNOWN".equals(weatherGroup);
        List<Sample> sameWeatherDay = filter(sameDay, s -> hasWeather && weatherGroup.equals(s.weatherGroup));
        List<Sample> sameWeatherWorkdays = filter(valid, s -> hasWeather
                && s.date.getDayOfWeek().getValue() <= 5 && weatherGroup.equals(s.weatherGroup));
        List<Sample> selected;
        String source;
        if (sameWeatherDay.size() >= MIN_SAMPLES) {
            selected = sameWeatherDay; source = "SAME_DAY_WEATHER";
        } else if (weekday.getValue() <= 5 && sameWeatherWorkdays.size() >= MIN_SAMPLES) {
            selected = sameWeatherWorkdays; source = "WORKDAY_WEATHER";
        } else if (sameDay.size() >= MIN_SAMPLES) {
            selected = sameDay; source = "SAME_DAY";
        } else {
            selected = new ArrayList<>(); source = "FALLBACK";
        }
        int count = Math.min(MAX_SAMPLES, selected.size());
        int minutes = count == 0 ? fallbackMinutes : (int) Math.ceil(selected.subList(0, count).stream()
                .mapToInt(s -> s.minutes).average().orElse(fallbackMinutes));
        return new Result(date.atTime(arrival).minusMinutes(minutes + bufferMinutes), minutes, count, source);
    }

    private static List<Sample> filter(List<Sample> samples, java.util.function.Predicate<Sample> predicate) {
        return samples.stream().filter(predicate).collect(Collectors.toList());
    }

    public static final class Sample {
        public final LocalDate date;
        public final int minutes;
        public final String weatherGroup;
        public final boolean included;
        public Sample(LocalDate date, int minutes, String weatherGroup, boolean included) {
            this.date = date; this.minutes = minutes; this.weatherGroup = weatherGroup; this.included = included;
        }
    }

    public static final class Result {
        public final LocalDateTime departure;
        public final int minutes;
        public final int sampleCount;
        public final String source;
        Result(LocalDateTime departure, int minutes, int sampleCount, String source) {
            this.departure = departure; this.minutes = minutes; this.sampleCount = sampleCount; this.source = source;
        }
    }
}
