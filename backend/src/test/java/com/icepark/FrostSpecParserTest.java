package com.icepark;

import com.icepark.exception.BusinessValidationException;
import com.icepark.util.FrostSpecParser;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class FrostSpecParserTest {

    @Test
    void parsesCommonSpecs() {
        assertEquals(0, new BigDecimal("-30").compareTo(FrostSpecParser.parseLowerLimit("耐低温 -30℃")));
        assertEquals(0, new BigDecimal("-20").compareTo(
                FrostSpecParser.parseLowerLimit("适用温度 -20°C ~ 10°C")));
        assertEquals(0, new BigDecimal("-15").compareTo(FrostSpecParser.parseLowerLimit("耐寒 -15度以上")));
        assertEquals(0, new BigDecimal("-25.5").compareTo(FrostSpecParser.parseLowerLimit("极限 -25.5 ℃")));
        // 全角负号
        assertEquals(0, new BigDecimal("-18").compareTo(FrostSpecParser.parseLowerLimit("－18℃")));
    }

    @Test
    void blankOrMissingTemperature_isRejected() {
        assertThrows(BusinessValidationException.class, () -> FrostSpecParser.parseLowerLimit(""));
        assertThrows(BusinessValidationException.class, () -> FrostSpecParser.parseLowerLimit("普通规格无温度"));
    }
}
