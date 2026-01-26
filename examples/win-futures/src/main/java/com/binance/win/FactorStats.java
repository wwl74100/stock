package com.binance.win;

import java.math.BigDecimal;
import lombok.Data;

import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.NewOrderResponse;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.Side;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 每种条件因子对应的统计结果
 */
@EqualsAndHashCode
@Data
public class FactorStats {

    boolean isNight;

    String symbol;

    Side side;

    long joinTime;
    //成交量进场比例阈值
    int joinTurnover;
    //多空进场比例阈值
    int joinTakeTurnover;
    //成交量进场当前统计时间，单位秒
    int joinTurnoverTime;

    //成交量离场比例阈值
    int leaveTurnover;
    //多空离场比例阈值
    int leaveTakeTurnover;
    //价格平稳停留时间
    int stableTime;
    String clientOrderId;

    BigDecimal joinPrice;
    BigDecimal basePrice;

    //成交量进场初始
    int baseTurnover;
    //成交量进场初始
    int calcTurnover;

    //成交量进场对比前一段时间周期，单位分钟
//    int joinPreviousPeriod;

    //成交量离场当前统计时间，单位秒
//    int leaveTurnoverTime;
    //成交量离场对比前一段时间周期，单位分钟
//    int leavePreviousPeriod;

    //价格平稳比例
//    BigDecimal stableScale;

    //离场盈亏比例
//    BigDecimal driftScale;

    NewOrderResponse newOrderResponse;

    long leaveTime;
    BigDecimal leavePrice;
    //实际盈亏
    BigDecimal actualProfit;

    long holdTime;
}