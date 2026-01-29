package com.binance.win;

import cn.hutool.core.thread.NamedThreadFactory;
import com.binance.connector.client.common.ApiException;
import com.binance.connector.client.common.ApiResponse;
import com.binance.connector.client.common.configuration.ClientConfiguration;
import com.binance.connector.client.common.configuration.SignatureConfiguration;
import com.binance.connector.client.common.websocket.configuration.WebSocketClientConfiguration;
import com.binance.connector.client.common.websocket.service.StreamBlockingQueueWrapper;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.DerivativesTradingUsdsFuturesRestApiUtil;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.api.DerivativesTradingUsdsFuturesRestApi;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.AutoCancelAllOpenOrdersRequest;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.AutoCancelAllOpenOrdersResponse;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.CancelOrderResponse;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.Interval;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.KlineCandlestickDataResponse;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.KlineCandlestickDataResponseItem;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.NewOrderRequest;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.NewOrderResponse;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.PositionInformationV3Response;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.PositionInformationV3ResponseInner;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.PositionSide;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.PriceMatch;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.QueryCurrentOpenOrderResponse;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.RecentTradesListResponse;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.Side;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.TimeInForce;
import com.binance.connector.client.derivatives_trading_usds_futures.websocket.stream.DerivativesTradingUsdsFuturesWebSocketStreamsUtil;
import com.binance.connector.client.derivatives_trading_usds_futures.websocket.stream.api.DerivativesTradingUsdsFuturesWebSocketStreams;
import com.binance.connector.client.derivatives_trading_usds_futures.websocket.stream.model.ContinuousContractKlineCandlestickStreamsRequest;
import com.binance.connector.client.derivatives_trading_usds_futures.websocket.stream.model.ContinuousContractKlineCandlestickStreamsResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.client.Socks5Proxy;

@Slf4j
public class OrderManager {

    public static DerivativesTradingUsdsFuturesWebSocketStreams webSocketStreams;


    public static DerivativesTradingUsdsFuturesWebSocketStreams getWebSocketStreams() {
        if (webSocketStreams == null) {
            WebSocketClientConfiguration clientConfiguration =
                    DerivativesTradingUsdsFuturesWebSocketStreamsUtil.getClientConfiguration();
            clientConfiguration.setWebSocketProxy(new Socks5Proxy("0.0.0.0",7891));
            webSocketStreams = new DerivativesTradingUsdsFuturesWebSocketStreams(clientConfiguration);
        }
        return webSocketStreams;
    }

    public static DerivativesTradingUsdsFuturesRestApi api;

    public static DerivativesTradingUsdsFuturesRestApi getApi() {

        if (api == null) {
            ClientConfiguration clientConfiguration =
                    DerivativesTradingUsdsFuturesRestApiUtil.getClientConfiguration();
            SignatureConfiguration signatureConfiguration = new SignatureConfiguration();
            signatureConfiguration.setApiKey("ybfbdsW8XNMJf3KgKSYdT2z0fkG1CyrQsrRJJOwFryoCsaURbFNoZiy0AkGBHeCE");
            signatureConfiguration.setPrivateKey(privatePath);
            clientConfiguration.setSignatureConfiguration(signatureConfiguration);
            api = new DerivativesTradingUsdsFuturesRestApi(clientConfiguration);
        }
        return api;
    }


    static ScheduledExecutorService scheduledExecutor;

    static {
        System.setProperty("socksProxyHost", "0.0.0.0");
        System.setProperty("socksProxyPort", "7891");
        getWebSocketStreams();
        getApi();
        /*//  定时刷新路由
        scheduledExecutor = Executors.newSingleThreadScheduledExecutor(
                new NamedThreadFactory("order-manager" + "-", true));
        scheduledExecutor.scheduleAtFixedRate(() -> {
            // TODO
        }, 10, 700, TimeUnit.MILLISECONDS);*/

    }


    public static final String dataPath = System.getProperty("path", "/home/");

    public static final String privatePath = System.getProperty("private", "/home/");
    public static final PersistentMap strategy = new PersistentMap(dataPath+"strategy.json");

//    public static final PersistentMap statistical = new PersistentMap("E:\\open_source\\stock\\examples\\data\\statistical-day.json");

    public static final Boolean real = Boolean.FALSE;

    public static final Side only = Side.BUY;
    public static final String side = System.getProperty("side", "both");

    public static final int onceMaxUsdt = getProperty("max.usdt", 300);

    public static final String symbol = System.getProperty("symbol", "ethusdt");

    public static final int joinTurnoverTime = getProperty("join.time", 30);

    public static final int joinTurnover = getProperty("join.turnover", 10);

    public static final int joinTakeTurnover = getProperty("join.take.turnover", 4);

    public static final int leaveStableTime = getProperty("leave.stable.time", 30);

    public static final int leaveTurnover = getProperty("leave.turnover", 10);

    public static final int leaveTakeTurnover = getProperty("leave.take.turnover", 10);


    public static final Map<String, NewOrderResponse> openOrder = new ConcurrentHashMap<>();
    public static final Map<String, PositionInformationV3ResponseInner> openPosition = new ConcurrentHashMap<>();


    public static void main(String[] args) {
//        NewOrderResponse response = newLimitOrder("dogeusdt", Side.BUY, new BigDecimal("0.1"), "ethusdt-" + DateTime.now().withMillisOfSecond(0).getMillis());
//        newPriceMatchOrder("ethusdt", new BigDecimal("0.01"), Side.BUY, PriceMatch.OPPONENT_5, DateTime.now().withMillisOfSecond(0).getMillis());
//        newPriceMatchOrder("ethusdt", new BigDecimal("0.01"), Side.BUY, PriceMatch.OPPONENT_5, DateTime.now().withMillisOfSecond(0).getMillis());

//        queryCurrentOpenOrder("ethusdt", response.getClientOrderId());
//        cancelOrder("ethusdt", response.getClientOrderId());
//        getPosition("ethusdt");
        getHistoryTrades("solusdt");
    }


    public static int getProperty(String key, int def) {
        String value = System.getProperty(key);
        if (value == null) {
            return def;
        }
        value = value.trim().toLowerCase();
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            // ignored
            throw new RuntimeException(ignored);
        }
    }

    public static String covertUSDC(String symbol) {
        if (symbol.startsWith("eth") || symbol.startsWith("sol") || symbol.startsWith("ETH") || symbol.startsWith("SOL")) {
            return symbol.replace("usdt", "usdc").replace("USDT", "USDC");
        } else {
            return symbol;
        }
    }

    public static Double UsdtCovertQty(BigDecimal price) {
        if (price.compareTo(new BigDecimal(1500)) > 0) {
            return new BigDecimal(onceMaxUsdt).divide(price, 3, RoundingMode.DOWN).doubleValue();
        } else if (price.compareTo(new BigDecimal(500)) > 0) {
            return new BigDecimal(onceMaxUsdt).divide(price, 2, RoundingMode.DOWN).doubleValue();
        } else if (price.compareTo(new BigDecimal(10)) > 0) {
            return new BigDecimal(onceMaxUsdt).divide(price, 1, RoundingMode.DOWN).doubleValue();
        } else {
            return new BigDecimal(onceMaxUsdt).divide(price, 0, RoundingMode.DOWN).doubleValue();
        }
    }

    public static RecentTradesListResponse getHistoryTrades(String symbol) {
        symbol = covertUSDC(symbol);
        RecentTradesListResponse data = getApi().recentTradesList(symbol, 5L).getData();
        if (!data.isEmpty()) {
            log.info("getHistoryTrades={}", data);
        }
        return data;
    }

    public static PositionInformationV3ResponseInner getPosition(String symbol) {
        symbol = covertUSDC(symbol);
        PositionInformationV3Response data = getApi().positionInformationV3(symbol, null).getData();
        if (!data.isEmpty()) {
            log.info("getPosition={}", data);
        }
        PositionInformationV3ResponseInner position = data.get(0);
        openPosition.put(symbol, data.get(0));
        return data.get(0);
    }

    public static CancelOrderResponse cancelOrder(String symbol, String origClientOrderId) {
        symbol = covertUSDC(symbol);
        try {
            CancelOrderResponse data = getApi().cancelOrder(symbol, null, origClientOrderId, null).getData();
            if (data != null) {
                log.info("cancelOrder={}", data);
            }
            return data;
        } catch (ApiException e) {
            log.error("cancelOrder fail", e);
        }
        return null;
    }

    public static AutoCancelAllOpenOrdersResponse autoCancelAllOpenOrders(String symbol) {
        symbol = covertUSDC(symbol);
        try {
            AutoCancelAllOpenOrdersRequest autoCancel = new AutoCancelAllOpenOrdersRequest();
            autoCancel.setSymbol(symbol);
            autoCancel.setCountdownTime(5000L);
            AutoCancelAllOpenOrdersResponse data = getApi().autoCancelAllOpenOrders(autoCancel).getData();
            if (data != null) {
                log.info("autoCancelAllOpenOrders={}", data);
            }
            return data;
        } catch (ApiException e) {
            log.error("autoCancelAllOpenOrders fail", e);
        }
        return null;
    }


    public static QueryCurrentOpenOrderResponse getOrder(String symbol, String origClientOrderId) {
        symbol = covertUSDC(symbol);
        QueryCurrentOpenOrderResponse data = getApi().queryCurrentOpenOrder(symbol, null, origClientOrderId, null).getData();
        if (data != null) {
            log.info("queryCurrentOpenOrder={}", data);
        }
        return data;
    }

    public static NewOrderResponse newClosePositionOrder(String symbol, Side side, PriceMatch priceMatch, String clientOrderId) {
        symbol = covertUSDC(symbol);
        NewOrderRequest order = new NewOrderRequest();
        order.positionSide(PositionSide.LONG);
        order.setSymbol(symbol);
        order.setSide(side);
        order.type("stop");
        order.setTimeInForce(TimeInForce.GTX);
        order.setPriceMatch(priceMatch);
        order.closePosition("true");
        order.setNewClientOrderId(clientOrderId);
        log.info("newPriceMatchOrder request={}", order);
        try {
            NewOrderResponse data = getApi().newOrder(order).getData();
            log.info("newPriceMatchOrder={}", data);
            if (data != null) {
                openOrder.put(symbol, data);
            }
            return data;
        } catch (ApiException e) {
            log.error("newPriceMatchOrder fail", e);
        }
        return null;
    }

    public static NewOrderResponse newPriceMatchOrder(String symbol, Side side, BigDecimal price, PriceMatch priceMatch, String clientOrderId) {
        symbol = covertUSDC(symbol);
        NewOrderRequest order = new NewOrderRequest();
        order.positionSide(PositionSide.LONG);
        order.setSymbol(symbol);
        order.setSide(side);
        order.type("limit");
        order.setTimeInForce(TimeInForce.GTX);
        order.setPriceMatch(priceMatch);
        order.setQuantity(UsdtCovertQty(price));
        order.setNewClientOrderId(clientOrderId);
        log.info("newPriceMatchOrder request={}", order);
        try {
            NewOrderResponse data = getApi().newOrder(order).getData();
            log.info("newPriceMatchOrder={}", data);
            if (data != null) {
                openOrder.put(symbol, data);
            }
            return data;
        } catch (ApiException e) {
            log.error("newPriceMatchOrder fail", e);
        }
        return null;
    }

    public static NewOrderResponse newLimitOrder(String symbol, Side side, BigDecimal price, String clientOrderId) {
        symbol = covertUSDC(symbol);
        NewOrderRequest order = new NewOrderRequest();
        order.positionSide(PositionSide.LONG);
        order.setSymbol(symbol);
        order.setSide(side);
        order.type("limit");
        order.setTimeInForce(TimeInForce.GTX);
        order.setPrice(price.doubleValue());
        order.setQuantity(UsdtCovertQty(price));
        order.setNewClientOrderId(clientOrderId);
        log.info("newLimitOrder request={}", order);
        try {
            NewOrderResponse data = getApi().newOrder(order).getData();
            log.info("newLimitOrder={}", data);
            if (data != null) {
                openOrder.put(symbol, data);
            }
            return data;
        } catch (ApiException e) {
            log.error("newLimitOrder fail", e);
        }
        return null;
    }


    @SneakyThrows
    public static void continuousContractKline(String... symbols) {
        for (String symbol : symbols) {
            ContinuousContractKlineCandlestickStreamsRequest
                    continuousContractKlineCandlestickStreamsRequest =
                    new ContinuousContractKlineCandlestickStreamsRequest();
            continuousContractKlineCandlestickStreamsRequest.pair(symbol);
            continuousContractKlineCandlestickStreamsRequest.contractType("perpetual");
            continuousContractKlineCandlestickStreamsRequest.interval("1s");
            StreamBlockingQueueWrapper<ContinuousContractKlineCandlestickStreamsResponse> response =
                    getWebSocketStreams().continuousContractKlineCandlestickStreams(continuousContractKlineCandlestickStreamsRequest);
            Thread thread = new Thread(() -> {
                while (true) {
                    try {
                        ContinuousContractKlineCandlestickStreamsResponse take = response.take();
                        com.binance.win.marketCache.putSecondLine(take);
//                        log.info("continuousContractKlineCandlestickStreams take={}", take);
                    } catch (Exception e) {
                        log.warn("continuousContractKline fail", e);
                    }
                }
            });
            thread.setDaemon(true);
            thread.setName("put-line-" + symbol);
            thread.start();
        }

    }

    @SneakyThrows
    public static List<KlineCandlestickDataResponseItem> klineCandlestickData(String symbol, Long startTime, Long endTime) {
        ObjectMapper mapper = new ObjectMapper();
        Interval interval = Interval.INTERVAL_1m;
        Long limit = 1440L;
        //log.info("klineCandlestickData symbol={},endTime={}", symbol, endTime);
        ApiResponse<KlineCandlestickDataResponse> response = getApi().klineCandlestickData(symbol, interval, startTime, endTime, limit);
        return response.getData().stream().toList();
    }


}
