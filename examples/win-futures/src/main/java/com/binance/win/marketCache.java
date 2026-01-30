package com.binance.win;

import cn.hutool.core.thread.ThreadUtil;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.CancelOrderResponse;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.KlineCandlestickDataResponseItem;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.NewOrderResponse;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.PositionInformationV3ResponseInner;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.PriceMatch;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.Side;
import com.binance.connector.client.derivatives_trading_usds_futures.websocket.stream.model.ContinuousContractKlineCandlestickStreamsResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.binance.win.FactorStats;
import com.binance.win.OrderManager;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import cn.hutool.core.thread.NamedThreadFactory;

@Slf4j
public class marketCache {

    public static final int INTERVAL_CACHE = 10 * 60 * 1000;
    public static final int joinTurnoverTime = 15;
    public static final int joinTurnoverTimeMax = 30;
    public static final int previousPeriod = 45;
    //最短持仓时间
    public static final int minHoldTime = 15;
    //最长持仓时间
    public static final int maxHoldTime = 5 * 60;
    //价格平稳停留时间
    public static final int stableTime = 5;
    public static final BigDecimal priceRange = new BigDecimal("0.001");

    public static final Map<LineKey, ContinuousContractKlineCandlestickStreamsResponse> secondLine = new ConcurrentHashMap<>();
    public static final Map<LineKey, BigDecimal> previousPeriodSum = new ConcurrentHashMap<>();
    public static final Map<LineKey, KlineCandlestickDataResponseItem> minuteLine = new ConcurrentHashMap<>();
    public static final Map<String, List<FactorStats>> openOrders = new ConcurrentHashMap<>();
    public static final Map<Factor, Boolean> filter = new ConcurrentHashMap<>();
    public static final PersistentMap statistical = new PersistentMap(OrderManager.dataPath+"statistical.json");
    public static final PersistentMap statisticalDay = new PersistentMap(OrderManager.dataPath+"statistical-day.json");

    static ScheduledExecutorService scheduledExecutor;
    static {
        //  定时刷新路由
        scheduledExecutor = Executors.newSingleThreadScheduledExecutor(
            new NamedThreadFactory("order-manager" + "-", true));
        scheduledExecutor.scheduleAtFixedRate(() -> {
            // TODO
            long millis = DateTime.now().minusSeconds(15).withMillisOfSecond(0).getMillis();
            for (String symbol : OrderManager.symbols) {
                if (secondLine.get(LineKey.builder().symbol(symbol.toUpperCase()).endTime(millis).build()) == null) {
                    log.info("restart continuousContractKline symbol={}", symbol);
                    System.exit(1111);
                }
            }
        }, 30, 15, TimeUnit.SECONDS);
    }

    public static void main(String[] args) {
        OrderManager.continuousContractKline(OrderManager.symbols);
        /*DateTime dateTime = DateTime.now().withHourOfDay(1).withMinuteOfHour(3).withSecondOfMinute(0).withMillisOfSecond(0);
        Long start = dateTime.getMillis();
        Long end = dateTime.plusMinutes(15).getMillis();
        List<KlineCandlestickDataResponseItem> items = OrderManager.klineCandlestickData("ethusdt", start, end);
        for (KlineCandlestickDataResponseItem item : items) {
            System.out.println(item);
        }*/
    }

    public static void putSecondLine(ContinuousContractKlineCandlestickStreamsResponse line) {
        // TODO
        if (!line.getkLowerCase().getxLowerCase()) {
            return;
        }
        secondLine.put(LineKey.builder().symbol(line.getPs()).endTime(line.getkLowerCase().gettLowerCase()).build(), line);
        secondLine.remove(LineKey.builder().symbol(line.getPs()).endTime(line.getkLowerCase().gettLowerCase() - INTERVAL_CACHE).build());
        secondLineTrigger(line.getPs(), line.getkLowerCase().gettLowerCase());
        secondLineLeave(line.getPs(), line.getkLowerCase().gettLowerCase());
    }

    public static void secondLineTrigger(String symbol, Long endTime) {
        long timeMillis = System.currentTimeMillis();
        BigDecimal base = getPreviousPeriod(symbol, endTime);
        BigDecimal calc = BigDecimal.ZERO;
        BigDecimal calcBuy = BigDecimal.ZERO;
        for (int i = 0; i <= joinTurnoverTimeMax; i++) {
            LineKey lineKey = LineKey.builder().symbol(symbol).endTime(endTime - i * 1000).build();
            ContinuousContractKlineCandlestickStreamsResponse endLine = secondLine.get(lineKey);
            if (endLine == null) {
                return;
            }
            calc = calc.add(new BigDecimal(endLine.getkLowerCase().getvLowerCase()));
            calcBuy = calcBuy.add(new BigDecimal(endLine.getkLowerCase().getV()));
            if (i < joinTurnoverTime || i % 3 != 0) {
                continue;
            }
            BigDecimal calcTurnover = calc.divide(new BigDecimal(i + 1), 4, RoundingMode.DOWN);
            BigDecimal calcSell = calc.subtract(calcBuy);
            if (base.compareTo(BigDecimal.ZERO) <= 0 || calc.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            
            int joinTurnover = calcTurnover.divide(base.multiply(new BigDecimal(3)), 2, RoundingMode.DOWN).intValue();
            if (symbol.equalsIgnoreCase("ethusdt") || symbol.equalsIgnoreCase("solusdt")) {
                if (joinTurnover < 3) {
                    continue;
                }
            } else {
                if (joinTurnover < 6) {
                    continue;
                }
            }
            int takeTurnover;
            Side side;
            if (calcBuy.compareTo(calcSell) > 0) {
                side = Side.BUY;
                takeTurnover = calcBuy.multiply(new BigDecimal(10)).divide(calc, 4, RoundingMode.DOWN).intValue();
            } else {
                side = Side.SELL;
                takeTurnover = calcSell.multiply(new BigDecimal(10)).divide(calc, 4, RoundingMode.DOWN).intValue();
            }
            if (takeTurnover < 7) {
                continue;
            }
            Side recentlySide = recentlySide(symbol, endTime);
            if (recentlySide != null) {
                return;
            }
            BigDecimal basePrice = new BigDecimal(endLine.getkLowerCase().getcLowerCase());
            BigDecimal joinPrice = new BigDecimal(secondLine.get(LineKey.builder().symbol(symbol).endTime(endTime).build()).getkLowerCase().getcLowerCase());
            BigDecimal basePrice5 = new BigDecimal(minuteLine.get(LineKey.builder().symbol(symbol)
                .endTime(new DateTime(endTime).withSecondOfMinute(0).withMillisOfSecond(0).minusMinutes(5).getMillis()).build()).get(4));
            if (side.equals(Side.BUY)) {
                if (joinPrice.divide(basePrice, 6, RoundingMode.DOWN).subtract(BigDecimal.ONE).compareTo(priceRange) < 0) {
                    log.info("price changes too small symbol={},basePrice={},joinPrice={},side={}", symbol, basePrice, joinPrice, side);
                    continue;
                }
                if (joinPrice.divide(basePrice5, 6, RoundingMode.DOWN).subtract(BigDecimal.ONE)
                    .compareTo(priceRange.multiply(new BigDecimal(0.2 * joinTurnover))) > 0) {
                    log.info("already {} symbol={},basePrice5={},joinPrice={}", side, symbol, basePrice5, joinPrice);
                    return;
                }
            } else {
                if (basePrice.divide(joinPrice, 6, RoundingMode.DOWN).subtract(BigDecimal.ONE).compareTo(priceRange) < 0) {
                    log.info("price changes too small symbol={},basePrice={},joinPrice={},side={}", symbol, basePrice, joinPrice, side);
                    continue;
                }
                if (basePrice5.divide(joinPrice, 6, RoundingMode.DOWN).subtract(BigDecimal.ONE)
                    .compareTo(priceRange.multiply(new BigDecimal(0.2 * joinTurnover))) > 0) {
                    log.info("already {} symbol={},basePrice5={},joinPrice={}", side, symbol, basePrice5, joinPrice);
                    return;
                }
            }
            if (isTurn(symbol, side, endTime, i, takeTurnover)) {
                continue;
            }
            int finalI = i;
            FactorStats factor = new FactorStats();
            String clientOrderId = symbol + "-" + endTime;
            int hourOfDay = new DateTime(endTime).getHourOfDay();
            Boolean isNight = hourOfDay < 8 || hourOfDay > 18;
            if (OrderManager.real && OrderManager.openPosition.get(symbol) == null) {
                OrderManager.strategy.getMap().forEach((k, v) -> {
                    if (k.isNight() == isNight && k.getSymbol().equals(symbol) && k.getSide().equals(side)
                            && k.getJoinTurnoverTime() == finalI
                            && k.getJoinTurnover() <= (joinTurnover) && k.getJoinTakeTurnover() <= takeTurnover) {
                        long orderTime = System.currentTimeMillis();
                        while (System.currentTimeMillis() - orderTime < 500) {
                            if (OrderManager.getPosition(symbol) != null) {
                                break;
                            }
                            NewOrderResponse newOrderResponse = OrderManager.newPriceMatchOrder(symbol, side, joinPrice, PriceMatch.QUEUE, clientOrderId);
                            factor.setClientOrderId(clientOrderId);
                            factor.setNewOrderResponse(newOrderResponse);
                            ThreadUtil.safeSleep(150);
                            OrderManager.cancelOrder(symbol, clientOrderId);
                        }
                    }
                });
            }

            openOrders.compute(symbol, (key, oldValue) -> {
                factor.setSymbol(symbol);
                factor.setSide(side);
                factor.setBaseTurnover(base.intValue());
                factor.setCalcTurnover(calcTurnover.intValue());
                factor.setJoinTurnover(joinTurnover);
                factor.setJoinTakeTurnover(takeTurnover);
                factor.setJoinTurnoverTime(finalI);
                factor.setJoinTime(endTime);
                factor.setJoinPrice(joinPrice);
                factor.setBasePrice(basePrice);

                if (oldValue == null) {
                    oldValue = new ArrayList<>();
                }
                oldValue.add(factor);
                return oldValue;
            });
//            log.info("secondLineTriggerMock symbol={} cost={}ms delay={}", symbol, System.currentTimeMillis() - timeMillis, System.currentTimeMillis() - endTime);
        }
    }

    public static void secondLineLeave(String symbol, Long endTime) {
        long timeMillis = System.currentTimeMillis();
        List<FactorStats> factorStatsList = openOrders.get(symbol);
        if (factorStatsList == null) {
            return;
        }
        Iterator<FactorStats> iterator = factorStatsList.iterator();
        while (iterator.hasNext()) {
            FactorStats factorStats = iterator.next();
            if (minHoldTime * 1000 + factorStats.getJoinTime() > endTime) {
                continue;
            }
            //过期删除
            if (maxHoldTime * 1000 + factorStats.getJoinTime() < endTime) {
                long orderTime = System.currentTimeMillis();
                while (System.currentTimeMillis() - orderTime < 500) {
                    if (OrderManager.openPosition.get(symbol) == null) {
                        break;
                    }
                    Side colseSide = factorStats.getNewOrderResponse().getSide().equals(Side.BUY) ? Side.SELL : Side.BUY;
                    OrderManager.newClosePositionOrder(symbol, colseSide, PriceMatch.QUEUE, factorStats.getClientOrderId());
                    ThreadUtil.safeSleep(150);
                    OrderManager.cancelOrder(symbol, factorStats.getClientOrderId());
                }
                iterator.remove();
                continue;
            }
            long holdTime = (endTime - factorStats.getJoinTime()) / 1000;

            for (int j = stableTime; j < 15; j++) {
                if (j % 2 == 0) {
                    continue;
                }
                BigDecimal base = BigDecimal.ZERO;
                for (int i = j; i < holdTime; i++) {
                    base = base.add(new BigDecimal(secondLine.get(LineKey.builder().symbol(symbol).endTime(endTime - i * 1000).build()).getkLowerCase().getvLowerCase()));
                }
                base = base.divide(new BigDecimal(holdTime - j), 6, RoundingMode.DOWN);
                BigDecimal calc = BigDecimal.ZERO;
                BigDecimal calcBuy = BigDecimal.ZERO;
                for (int i = 0; i < j; i++) {
                    calc = calc.add(new BigDecimal(secondLine.get(LineKey.builder().symbol(symbol).endTime(endTime - i * 1000).build()).getkLowerCase().getvLowerCase()));
                    calcBuy = calcBuy.add(new BigDecimal(secondLine.get(LineKey.builder().symbol(symbol).endTime(endTime - i * 1000).build()).getkLowerCase().getV()));
                }
                calc = calc.divide(new BigDecimal(j), 6, RoundingMode.DOWN);

                calcBuy = calcBuy.divide(new BigDecimal(j), 6, RoundingMode.DOWN);
                BigDecimal calcSell = calc.subtract(calcBuy);
                if (base.compareTo(BigDecimal.ZERO) <= 0 || calcSell.compareTo(BigDecimal.ZERO) <= 0
                        || calcBuy.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                int leaveTurnover = calc.multiply(new BigDecimal(10)).divide(base, 2, RoundingMode.DOWN).intValue();

                int takeTurnover;
                if (factorStats.getSide().equals(Side.BUY)) {
                    takeTurnover = calcBuy.multiply(new BigDecimal(10)).divide(calc, 4, RoundingMode.DOWN).intValue();
                } else {
                    takeTurnover = calcSell.multiply(new BigDecimal(10)).divide(calc, 4, RoundingMode.DOWN).intValue();
                }
                closePosition(symbol, j, factorStats, leaveTurnover, takeTurnover);

                if (leaveTurnover < 5 || leaveTurnover > 15) {
                    continue;
                }
                if (takeTurnover < 5 || leaveTurnover >= 8) {
                    continue;
                }
                factorStats.setLeaveTurnover(leaveTurnover);
                factorStats.setLeaveTakeTurnover(takeTurnover);
                factorStats.setLeaveTime(DateTime.now().withMillisOfSecond(0).getMillis());
                factorStats.setLeavePrice(new BigDecimal(secondLine.get(LineKey.builder().symbol(symbol).endTime(endTime).build()).getkLowerCase().getcLowerCase()));
                BigDecimal actualProfit;
                if (factorStats.getSide().equals(Side.BUY)) {
                    actualProfit = factorStats.getLeavePrice().subtract(factorStats.getJoinPrice())
                            .multiply(new BigDecimal("10000")).divide(factorStats.getJoinPrice(), 2, RoundingMode.DOWN);
                } else {
                    actualProfit = factorStats.getJoinPrice().subtract(factorStats.getLeavePrice())
                            .multiply(new BigDecimal("10000")).divide(factorStats.getLeavePrice(), 2, RoundingMode.DOWN);
                }
                factorStats.setStableTime(j);
                factorStats.setHoldTime(holdTime);
                factorStats.setActualProfit(actualProfit);
                Factor factor2 = Factor.builder().symbol(factorStats.getSymbol()).side(factorStats.getSide())
                        .joinTime(factorStats.getJoinTime()).joinTakeTurnover(factorStats.getJoinTakeTurnover())
                        .joinTurnover(factorStats.getJoinTurnover()).joinTurnoverTime(factorStats.getJoinTurnoverTime())
                        .leaveTurnover(leaveTurnover).leaveTakeTurnover(takeTurnover).leaveStableTime(j)
                        .build();
                Boolean b = filter.get(factor2);
                if (b == null) {
                    log.info("factorStats={}", factorStats);
                    filter.put(factor2, Boolean.TRUE);

                    {
                        Factor factor = Factor.builder()
                                .isNight(factorStats.isNight())
                                .symbol(factorStats.getSymbol()).side(factorStats.getSide())
                                .joinTakeTurnover(factorStats.getJoinTakeTurnover())
                                .joinTurnover(factorStats.getJoinTurnover()).joinTurnoverTime(factorStats.getJoinTurnoverTime())
                                .leaveTurnover(leaveTurnover).leaveTakeTurnover(takeTurnover).leaveStableTime(j)
                                .build();
                        FactorResult result = statistical.get(factor);
                        if (result == null) {
                            result = FactorResult.builder().actualProfit(actualProfit.stripTrailingZeros().toPlainString()).build();
                            statistical.put(factor, result);
                        } else {
                            BigDecimal bigDecimal = new BigDecimal(result.getActualProfit());
                            bigDecimal.add(actualProfit);
                            result.setActualProfit(bigDecimal.stripTrailingZeros().toPlainString());

                        }
                        if (actualProfit.compareTo(BigDecimal.ZERO) > 0) {
                            result.setSuccess(result.getSuccess() + 1);
                            result.setSuccessHoldTime(result.getSuccessHoldTime() + holdTime);
                        } else {
                            result.setFail(result.getFail() + 1);
                            result.setFailHoldTime(result.getFailHoldTime() + holdTime);
                        }
//                        log.info("statistical key={}", factor);
//                        log.info("statistical value={}", result);
                    }
                    {
                        Factor factor = Factor.builder()
                                .isNight(factorStats.isNight())
                                .symbol(factorStats.getSymbol()).side(factorStats.getSide())
                                .joinDay(DateTime.now().toString("yyyyMMdd"))
                                .joinTakeTurnover(factorStats.getJoinTakeTurnover())
                                .joinTurnover(factorStats.getJoinTurnover()).joinTurnoverTime(factorStats.getJoinTurnoverTime())
                                .leaveTurnover(leaveTurnover).leaveTakeTurnover(takeTurnover).leaveStableTime(j)
                                .build();
                        FactorResult result = statisticalDay.get(factor);
                        if (result == null) {
                            result = FactorResult.builder().actualProfit(actualProfit.stripTrailingZeros().toPlainString()).build();
                            statisticalDay.put(factor, result);
                        } else {
                            BigDecimal bigDecimal = new BigDecimal(result.getActualProfit());
                            bigDecimal.add(actualProfit);
                            result.setActualProfit(bigDecimal.stripTrailingZeros().toPlainString());
                        }
                        if (actualProfit.compareTo(BigDecimal.ZERO) > 0) {
                            result.setSuccess(result.getSuccess() + 1);
                            result.setSuccessHoldTime(result.getSuccessHoldTime() + holdTime);
                        } else {
                            result.setFail(result.getFail() + 1);
                            result.setFailHoldTime(result.getFailHoldTime() + holdTime);
                        }
//                        log.info("statistical key={}", factor);
//                        log.info("statistical value={}", result);
                    }
                }
            }
        }
//        log.info("secondLineLeaveMock symbol={} cost={}ms delay={}", symbol, System.currentTimeMillis() - timeMillis, System.currentTimeMillis() - endTime);
    }

    private static void closePosition(String symbol, int j, FactorStats factorStats, int leaveTurnover, int takeTurnover) {
        PositionInformationV3ResponseInner position = OrderManager.openPosition.get(symbol);
        if (OrderManager.real && position != null) {
            int finalJ = j;
            OrderManager.strategy.getMap().forEach((k, v) -> {
                if (k.isNight() == factorStats.isNight() && k.getSymbol().equals(symbol)
                        && k.getSide().equals(factorStats.getSide()) && k.getLeaveStableTime() == finalJ
                        && (k.getLeaveTurnover() >= leaveTurnover || k.getLeaveTakeTurnover() >= takeTurnover)) {
                    long orderTime = System.currentTimeMillis();
                    while (System.currentTimeMillis() - orderTime < 500) {
                        if (OrderManager.getPosition(symbol) == null) {
                            break;
                        }
                        Side colseSide = factorStats.getNewOrderResponse().getSide().equals(Side.BUY) ? Side.SELL : Side.BUY;
                        OrderManager.newClosePositionOrder(symbol, colseSide, PriceMatch.QUEUE, factorStats.getClientOrderId());
                        ThreadUtil.safeSleep(150);
                        OrderManager.cancelOrder(symbol, factorStats.getClientOrderId());
                    }
                }
            });
        }
    }

    public static BigDecimal getPreviousPeriod(String symbol, Long endTime) {
        DateTime dateTime = new DateTime(endTime).withSecondOfMinute(0).withMillisOfSecond(0);
        Long end = dateTime.getMillis();
        Long start = dateTime.minusMinutes(previousPeriod - 1).getMillis();
        LineKey lineKey = LineKey.builder().symbol(symbol).endTime(end).build();
        BigDecimal computed = previousPeriodSum.computeIfAbsent(lineKey, k -> {
            BigDecimal base = BigDecimal.ZERO;
            List<KlineCandlestickDataResponseItem> items = OrderManager.klineCandlestickData(symbol, start, end);
            for (KlineCandlestickDataResponseItem item : items) {
                minuteLine.put(LineKey.builder().symbol(symbol).endTime(Long.parseLong(item.get(0))).build(), item);
                base = base.add(new BigDecimal(item.get(5)));
            }
            base = base.divide(new BigDecimal(items.size() * 60), 4, RoundingMode.DOWN);
            return base;
        });
        return computed;
    }

    public static Side recentlySide(String symbol, Long endTime) {
        Side result = null;
        DateTime dateTime = new DateTime(endTime).withSecondOfMinute(0).withMillisOfSecond(0);
        BigDecimal base = BigDecimal.ZERO;
        BigDecimal buy = BigDecimal.ZERO;
        for (int i = 1; i < 9; i++) {
            Long end = dateTime.minusMinutes(i).getMillis();
            base = base.add(new BigDecimal(minuteLine.get(LineKey.builder().symbol(symbol).endTime(end).build()).get(5)));
            buy = buy.add(new BigDecimal(minuteLine.get(LineKey.builder().symbol(symbol).endTime(end).build()).get(9)));
        }
        BigDecimal sell = base.subtract(buy);
        if (buy.compareTo(sell) > 0) {
            if (buy.divide(sell, 2, RoundingMode.DOWN).compareTo(new BigDecimal(1.7)) > 0) {
                log.info("symbol={},endTime={},recentlySide={}", symbol, endTime, Side.BUY);
                result = Side.BUY;
            }
        } else {
            if (sell.divide(buy, 2, RoundingMode.DOWN).compareTo(new BigDecimal(1.7)) > 0) {
                log.info("symbol={},endTime={},recentlySide={}", symbol, endTime, Side.SELL);
                result = Side.SELL;
            }
        }
        return result;
    }

    public static boolean isTurn(String symbol, Side side, Long endTime, int continues, int takeTurnover) {
        BigDecimal base = BigDecimal.ZERO;
        BigDecimal buy = BigDecimal.ZERO;
        for (int i = continues; i < continues * 2; i++) {
            LineKey lineKey = LineKey.builder().symbol(symbol).endTime(endTime - i * 1000).build();
            buy = buy.add(new BigDecimal(secondLine.get(lineKey).getkLowerCase().getV()));
            base = base.add(new BigDecimal(secondLine.get(lineKey).getkLowerCase().getvLowerCase()));
        }
        BigDecimal sell = base.subtract(buy);
        if (side.equals(Side.BUY)) {
            if (buy.multiply(new BigDecimal(13)).divide(base, 2, RoundingMode.DOWN).compareTo(new BigDecimal(takeTurnover)) > 0) {
                return true;
            }
        } else {
            if (sell.multiply(new BigDecimal(13)).divide(base, 2, RoundingMode.DOWN).compareTo(new BigDecimal(takeTurnover)) > 0) {
                return true;
            }
        }
        log.info("isTurn=false,symbol={},side={},endTime={}", symbol, side, endTime);
        return false;
    }

}
