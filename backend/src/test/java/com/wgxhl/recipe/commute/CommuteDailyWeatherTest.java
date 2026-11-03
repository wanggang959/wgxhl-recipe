package com.wgxhl.recipe.commute;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class CommuteDailyWeatherTest {
    @Test void selectsTargetDayAndFormatsProbability() throws Exception {
        String data="{\"days\":[{\"forecastStartTime\":\"2026-11-03T00:00+08:00\",\"temperatureMin\":{\"value\":16},\"temperatureMax\":{\"value\":22},\"daytime\":{\"condition\":{\"text\":\"小雨\",\"code\":\"305\"},\"precipitation\":{\"probability\":0.64,\"amount\":{\"value\":0.5}}}}]}";
        com.fasterxml.jackson.databind.JsonNode json=new ObjectMapper().readTree(data);
        CommuteService.Forecast found=CommuteService.parseDaily(json,"双流",LocalDate.of(2026,11,3));
        assertTrue(found.known);assertTrue(found.rain);assertTrue(found.text.contains("16～22℃"));assertTrue(found.text.contains("64%"));
        assertFalse(CommuteService.parseDaily(json,"双流",LocalDate.of(2026,11,4)).known);
    }
}
