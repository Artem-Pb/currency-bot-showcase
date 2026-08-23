package com.polybezev.currencybot.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.polybezev.currencybot.config.CacheConfig;
import com.polybezev.currencybot.model.CurrencyListData;
import com.polybezev.currencybot.model.CurrencyListEntry;
import com.polybezev.currencybot.model.CurrencyModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Fetches and parses currency rates from the CBR XML-daily feed
 * ({@code cbr-xml-daily.ru/daily_json.js}).
 * <p>
 * Both {@link #getCurrency} and {@link #getCurrencyList} are cache-backed; see
 * {@link CacheConfig} for TTL details.
 * <p>
 * Self-injection ({@code @Lazy CurrencyService self}) is required so that calls from
 * {@link #convertCurrency} go through the Spring proxy and hit the {@code @Cacheable} advice.
 * Direct calls within the same instance bypass the proxy and would skip the cache.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CurrencyService {

    // @Lazy breaks the self-referential circular dependency needed for @Cacheable proxy calls.
    @Autowired
    @Lazy
    private CurrencyService self;

    private final CryptoService cryptoService;

    private static final Gson GSON = new Gson();
    private static final String CBR_URL = "https://www.cbr-xml-daily.ru/daily_json.js";

    /**
     * Returns the CBR exchange rate for {@code currencyCode} against RUB.
     * <p>
     * Results are cached by currency code for {@link CacheConfig#CURRENCY_TTL_HOURS} hours
     * (cache name: {@link CacheConfig#CACHE_CURRENCY}).
     *
     * @param currencyCode ISO 4217 three-letter code, e.g. {@code "USD"}, {@code "EUR"}
     * @return parsed currency model including rate, nominal, name, previous value, and feed date
     * @throws IOException              if the CBR endpoint is unreachable
     * @throws IllegalArgumentException if {@code currencyCode} is not present in the CBR feed
     */
    @Cacheable(CacheConfig.CACHE_CURRENCY)
    public CurrencyModel getCurrency(String currencyCode) throws IOException {
        JsonObject root = parseRoot();

        String dateStr = root.get("Date").getAsString();
        JsonObject allValutes = root.getAsJsonObject("Valute");

        if (!allValutes.has(currencyCode)) {
            throw new IllegalArgumentException("Валюта '" + currencyCode + "' не найдена!");
        }

        CurrencyModel model = GSON.fromJson(allValutes.getAsJsonObject(currencyCode), CurrencyModel.class);

        try {
            model.setDate(Date.from(ZonedDateTime.parse(dateStr).toInstant()));
        } catch (Exception e) {
            log.warn("Failed to parse date from CBR response: {}", dateStr);
        }

        return model;
    }

    /**
     * Returns the full sorted list of currency codes and names available in the CBR feed.
     * <p>
     * Cached for {@link CacheConfig#CURRENCY_LIST_TTL_HOURS} hours
     * (cache name: {@link CacheConfig#CACHE_CURRENCY_LIST}).
     *
     * @return record containing the sorted entry list and the raw feed date string
     * @throws IOException if the CBR endpoint is unreachable
     */
    @Cacheable(CacheConfig.CACHE_CURRENCY_LIST)
    public CurrencyListData getCurrencyList() throws IOException {
        JsonObject root = parseRoot();

        String feedDate = root.get("Date").getAsString();
        JsonObject valutes = root.getAsJsonObject("Valute");

        List<String> codes = new ArrayList<>(valutes.keySet());
        Collections.sort(codes);

        List<CurrencyListEntry> entries = new ArrayList<>();
        for (String code : codes) {
            String name = valutes.getAsJsonObject(code).get("Name").getAsString();
            entries.add(new CurrencyListEntry(code, name));
        }

        return new CurrencyListData(entries, feedDate);
    }

    /**
     * Converts {@code amount} units of {@code from} currency to {@code to} currency
     * using the cached CBR rates and CoinGecko BTC price as intermediaries.
     * <p>
     * Both {@code from} and {@code to} may be {@code "RUB"} (identity) or {@code "BTC"}
     * (uses CoinGecko); all other codes are resolved via CBR.
     *
     * @param amount amount to convert
     * @param from   source currency code
     * @param to     target currency code
     * @return converted amount in {@code to} currency
     * @throws IOException if any upstream API call fails
     */
    public double convertCurrency(double amount, String from, String to) throws IOException {
        return fromRub(toRub(amount, from), to);
    }

    private double toRub(double amount, String currency) throws IOException {
        if (currency.equals("RUB")) return amount;
        if (currency.equals("BTC")) return amount * cryptoService.getCryptoPrice("bitcoin").getPriceRub();
        CurrencyModel rate = self.getCurrency(currency);
        return amount * rate.getValue() / rate.getNominal();
    }

    private double fromRub(double amountRub, String currency) throws IOException {
        if (currency.equals("RUB")) return amountRub;
        if (currency.equals("BTC")) return amountRub / cryptoService.getCryptoPrice("bitcoin").getPriceRub();
        CurrencyModel rate = self.getCurrency(currency);
        return amountRub / (rate.getValue() / rate.getNominal());
    }

    private JsonObject parseRoot() throws IOException {
        return JsonParser.parseReader(
                new InputStreamReader(new URL(CBR_URL).openStream())
        ).getAsJsonObject();
    }
}
