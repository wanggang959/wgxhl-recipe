package com.wgxhl.recipe.commute;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AmapShareParserTest {
    @Test void parsesEncodedPoiLocation() throws Exception {
        AmapShareParser.Location location = AmapShareParser.parseRedirect("https://wb.amap.com/?p=B0FFLJ7WL0%2C30.557585%2C104.045%2C%E5%9C%B0%E7%82%B9");
        assertEquals("地点", location.name);
        assertEquals(30.557585, location.latitude, 0.000001);
        assertEquals(104.045, location.longitude, 0.000001);
    }
    @Test void rejectsArbitraryNetworkLocations() {
        assertThrows(IllegalArgumentException.class, () -> AmapShareParser.resolve("https://127.0.0.1/a"));
        assertThrows(IllegalArgumentException.class, () -> AmapShareParser.resolve("https://surl.amap.com.evil.test/a"));
        assertThrows(IllegalArgumentException.class, () -> AmapShareParser.parseRedirect("https://evil.test/?p=id,30,104,name"));
        assertThrows(IllegalArgumentException.class, () -> AmapShareParser.parseRedirect("https://wb.amap.com/?p=id,NaN,104,name"));
        assertThrows(IllegalArgumentException.class, () -> AmapShareParser.parseRedirect("https://wb.amap.com/?p=id,30,104,name&p=id,31,105,name"));
    }
}
