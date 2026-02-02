package com.binance.win;


import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import java.util.HashMap;

@Slf4j
public class PersistentMap {

    private final Path FILE_PATH;
    private final ConcurrentHashMap<Factor, FactorResult> map = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper().setPolymorphicTypeValidator(
            BasicPolymorphicTypeValidator.builder()
                    .allowIfBaseType("com.binance.connector.client.derivatives_trading_usds_futures.win") // 你的业务包
                    .allowIfBaseType(FactorResult.class)
                    .allowIfBaseType(Factor.class)
                    .allowIfBaseType(JsonObj.class)
                    .allowIfBaseType(List.class)
                    .allowIfBaseType("java.util")
                    .allowIfSubTypeIsArray()
                    .build()
    ).enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL);
    private final TypeReference<List<JsonObj>> TYPE_REF =
            new TypeReference<List<JsonObj>>() {
            };


    public PersistentMap(String path) {
        FILE_PATH = Path.of(path);
        mapper.activateDefaultTyping(
                mapper.getPolymorphicTypeValidator(),           // 2.10+ 安全写法
                ObjectMapper.DefaultTyping.NON_FINAL,           // 最常用
                JsonTypeInfo.As.PROPERTY                        // 字段方式最常用，也可 WRAPPER_ARRAY
                // JsonTypeInfo.As.WRAPPER_ARRAY  // 包一层数组，更安全但体积稍大
        );
        load();
        // 优雅关机时保存（Ctrl+C、kill -15、systemd stop 等大多数情况有效）
        Runtime.getRuntime().addShutdownHook(new Thread(this::save));
    }

    @SuppressWarnings("unchecked")
    private void load() {
        if (!Files.exists(FILE_PATH)) {
            return;
        }
        try {
            String json = Files.readString(FILE_PATH);
            List<JsonObj> loaded = mapper.readValue(json, TYPE_REF);
            for (JsonObj result : loaded) {

                map.put(result.getFactor(), result.getFactorResult());
            }
            log.info("Loaded {} entries from disk path {}", map.size(), FILE_PATH);
        } catch (Exception e) {
            log.error("Failed to load map: {}", e);
        }
    }

    private void save() {
        try {
            List<Entry<Factor, FactorResult>> sort = sort();
            // 原子写：先写临时文件再改名，防止写一半崩溃
            Path temp = FILE_PATH.resolveSibling(FILE_PATH.getFileName() + ".tmp");
            List<JsonObj> jsonObjList = new ArrayList<>();
            for (Entry<Factor, FactorResult> entry : sort) {
                jsonObjList.add(JsonObj.builder().factor(entry.getKey()).factorResult(entry.getValue()).build());
            }
            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonObjList);
            Files.writeString(temp, json);
            Files.move(temp, FILE_PATH, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            log.info("Saved map to disk ({} entries) path {}", map.size(), FILE_PATH);
        } catch (Exception e) {
            log.error("Failed to save map: {}", e);
        }
    }

    // 对外暴露的操作接口
    public FactorResult get(Factor key) {
        return map.get(key);
    }

    public FactorResult put(Factor key, FactorResult value) {
        return map.put(key, value);
    }

    public FactorResult remove(Factor key) {
        return map.remove(key);
    }

    public boolean containsKey(Factor key) {
        return map.containsKey(key);
    }

    public Map<Factor, FactorResult> getMap() {
        return map;
    }

    public List<Entry<Factor, FactorResult>> sort() {
        Map<String, List<Entry<Factor, FactorResult>>> sort = new HashMap<>();
        List<Entry<Factor, FactorResult>> list = new ArrayList<>(map.entrySet());
        list.forEach((v) -> {
            sort.compute(v.getKey().getSymbol(), (key, value) -> {
                if (value == null) {
                    value = new ArrayList<>();
                } else {
                    value.add(v);
                }
                return value;
            });
        });

        try {
            // 原子写：先写临时文件再改名，防止写一半崩溃
            Path temp = FILE_PATH.resolveSibling(FILE_PATH.getFileName() + "-top" + ".tmp");
            List<JsonObj> jsonObjList = new ArrayList<>();
            for (List<Entry<Factor, FactorResult>> value : sort.values()) {
                for (int i = 0; i < 30; i++) {
                    log.info("top sorted key={}---value={}", value.get(i).getKey(), value.get(i).getValue());
                    jsonObjList.add(JsonObj.builder().factor(value.get(i).getKey()).factorResult(value.get(i).getValue()).build());
                }
                if (value.size() > 60) {
                    for (int i = value.size() - 30; i < value.size(); i++) {
                        log.info("fail sorted key={}---value={}", value.get(i).getKey(), value.get(i).getValue());
                    }
                }
            }
            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonObjList);
            Files.writeString(temp, json);
            log.info("Saved map to disk ({} entries) path {}", map.size(), temp);
        } catch (Exception e) {
            log.error("Failed to save map: {}", e);
        }

        list.sort(Comparator.comparing((Map.Entry<Factor, FactorResult> e) -> new BigDecimal(e.getValue().getActualProfit()), Comparator.reverseOrder())
                .thenComparing(e -> e.getValue().getSuccess(), Comparator.reverseOrder())
                .thenComparing(e -> e.getValue().getFail(), Comparator.reverseOrder())
                .thenComparing(e -> e.getValue().getSuccessHoldTime()).reversed()
                .thenComparing(e -> e.getValue().getFailHoldTime()).reversed()
        );
        return list;

    }


}
