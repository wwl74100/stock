package com.binance.win;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @description
 * @create on 2026-01-26 23:02
 */


@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class JsonObj {

    Factor factor;

    FactorResult factorResult;

}
