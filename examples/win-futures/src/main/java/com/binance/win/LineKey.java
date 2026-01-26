package com.binance.win;

import lombok.Builder;
import lombok.Data;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * @description
 * @create on 2026-01-26 23:03
 */


@Data
@Builder
public class LineKey {

    String symbol;
    long endTime;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        LineKey lineKey = (LineKey) o;

        return new EqualsBuilder().append(endTime, lineKey.endTime).append(symbol, lineKey.symbol).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(symbol).append(endTime).toHashCode();
    }
}
