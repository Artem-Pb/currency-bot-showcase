package com.polybezev.currencybot.service;

import com.polybezev.currencybot.model.CryptoPriceModel;
import com.polybezev.currencybot.model.CurrencyModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * Assembles a compact market snapshot used as input to the morning AI digest prompt.
 * <p>
 * The snapshot is not cached here — caching is handled by the underlying
 * {@link CryptoService} and {@link CurrencyService} beans.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MarketDataService {

    private final CryptoService cryptoService;
    private final CurrencyService currencyService;

    /**
     * Fetches current BTC, USD, EUR, and CNY rates and formats them as a multi-line
     * plain-text snapshot suitable for inclusion in an AI prompt.
     * <p>
     * Example output:
     * <pre>
     * BTC: 7 000 000 ₽ / 78 000 $ (изменение за 24ч: +1.23%)
     * USD: 91.50 ₽
     * EUR: 99.20 ₽
     * CNY: 12.60 ₽
     * </pre>
     *
     * @return formatted market snapshot string
     * @throws IOException if any upstream API call fails
     */
    public String getMarketSnapshot() throws IOException {
        CryptoPriceModel btc = cryptoService.getCryptoPrice("bitcoin");
        CurrencyModel usd = currencyService.getCurrency("USD");
        CurrencyModel eur = currencyService.getCurrency("EUR");
        CurrencyModel cny = currencyService.getCurrency("CNY");

        return String.format(
                "BTC: %.0f ₽ / %.0f $ (изменение за 24ч: %+.2f%%)\n" +
                        "USD: %.2f ₽\n" +
                        "EUR: %.2f ₽\n" +
                        "CNY: %.2f ₽",
                btc.getPriceRub(), btc.getPriceUsd(), btc.getChange24h(),
                usd.getValue(),
                eur.getValue(),
                cny.getValue()
        );
    }
}
