package com.binance.win;

import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.Side;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 每种条件因子对应的统计结果
 */

@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
@Data
@Builder
public class Factor {

    protected boolean isNight;

    protected String symbol;

    protected Side side;

    protected long joinTime;

    protected String joinDay;

    //成交量进场比例阈值
    protected int joinTurnover;
    //多空进场比例阈值
    protected int joinTakeTurnover;
    //成交量进场当前统计时间，单位秒
    protected int joinTurnoverTime;

    //成交量离场比例阈值
    protected int leaveTurnover;
    //多空离场比例阈值
    protected int leaveTakeTurnover;
    //价格平稳停留时间
    protected int leaveStableTime;


}
