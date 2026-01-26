package com.binance.win;

/**
 * @description
 * @create on 2026-01-26 23:01
 */


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * 每种条件因子对应的统计结果
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class FactorResult {


    //实际盈亏
    String actualProfit;
    int success;
    int fail;
    long successHoldTime;
    long failHoldTime;

}
