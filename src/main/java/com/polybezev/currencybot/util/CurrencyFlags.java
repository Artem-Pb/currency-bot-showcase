package com.polybezev.currencybot.util;

/**
 * Maps ISO 4217 currency codes to their country flag emoji.
 * <p>
 * Covers all 54 currencies in the CBR XML-daily feed plus {@code XDR} (IMF Special Drawing Rights).
 * Unknown codes fall back to a bullet {@code •}.
 */
public final class CurrencyFlags {
    private CurrencyFlags() {}

    /**
     * Returns the flag emoji for the given currency code, or {@code "•"} if not mapped.
     *
     * @param code ISO 4217 three-letter currency code (e.g. {@code "USD"}, {@code "EUR"})
     * @return flag emoji string, never {@code null}
     */
    public static String getFlag(String code) {
        return switch (code) {
            case "USD" -> "🇺🇸";
            case "EUR" -> "🇪🇺";
            case "GBP" -> "🇬🇧";
            case "JPY" -> "🇯🇵";
            case "CNY" -> "🇨🇳";
            case "CHF" -> "🇨🇭";
            case "CAD" -> "🇨🇦";
            case "AUD" -> "🇦🇺";
            case "NZD" -> "🇳🇿";
            case "RUB" -> "🇷🇺";
            case "BYN" -> "🇧🇾";
            case "HKD" -> "🇭🇰";
            case "TRY" -> "🇹🇷";
            case "AED" -> "🇦🇪";
            case "KZT" -> "🇰🇿";
            case "AMD" -> "🇦🇲";
            case "AZN" -> "🇦🇿";
            case "BDT" -> "🇧🇩";
            case "BHD" -> "🇧🇭";
            case "BOB" -> "🇧🇴";
            case "BRL" -> "🇧🇷";
            case "CUP" -> "🇨🇺";
            case "CZK" -> "🇨🇿";
            case "DKK" -> "🇩🇰";
            case "DZD" -> "🇩🇿";
            case "EGP" -> "🇪🇬";
            case "ETB" -> "🇪🇹";
            case "GEL" -> "🇬🇪";
            case "HUF" -> "🇭🇺";
            case "IDR" -> "🇮🇩";
            case "INR" -> "🇮🇳";
            case "IRR" -> "🇮🇷";
            case "KGS" -> "🇰🇬";
            case "KRW" -> "🇰🇷";
            case "MDL" -> "🇲🇩";
            case "MMK" -> "🇲🇲";
            case "MNT" -> "🇲🇳";
            case "NGN" -> "🇳🇬";
            case "NOK" -> "🇳🇴";
            case "OMR" -> "🇴🇲";
            case "PLN" -> "🇵🇱";
            case "QAR" -> "🇶🇦";
            case "RON" -> "🇷🇴";
            case "RSD" -> "🇷🇸";
            case "SAR" -> "🇸🇦";
            case "SEK" -> "🇸🇪";
            case "SGD" -> "🇸🇬";
            case "THB" -> "🇹🇭";
            case "TJS" -> "🇹🇯";
            case "TMT" -> "🇹🇲";
            case "UAH" -> "🇺🇦";
            case "UZS" -> "🇺🇿";
            case "VND" -> "🇻🇳";
            case "ZAR" -> "🇿🇦";
            case "XDR" -> "🌐";
            default   -> "•";
        };
    }
}
