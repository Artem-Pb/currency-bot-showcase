package com.polybezev.currencybot.model;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.Date;

/**
 * Represents a single currency from the CBR (Central Bank of Russia) XML feed,
 * deserialized by Gson using the field names that appear in the CBR JSON response.
 * <p>
 * {@code getDiff()} and {@code getPercentChange()} are derived fields — they are computed
 * from {@code value} and {@code previous} rather than being part of the API response.
 * <p>
 * Note: the {@code date} field uses {@link java.util.Date} because Gson's default date
 * adapter targets that type. Migrating to {@link java.time.LocalDate} would require
 * a custom {@code TypeAdapter} in the Gson configuration.
 */
@Data
public class CurrencyModel {

    /** Internal CBR identifier (e.g. "R01235"). */
    @SerializedName("ID")
    private String id;

    /** ISO 4217 numeric code (e.g. "840" for USD). */
    @SerializedName("NumCode")
    private String numCode;

    /** ISO 4217 alpha-3 code (e.g. "USD"). */
    @SerializedName("CharCode")
    private String charCode;

    /**
     * Nominal unit count for the rate — typically 1, but some currencies use 10, 100, etc.
     * (e.g. Japanese Yen: 100 JPY = N RUB).
     */
    @SerializedName("Nominal")
    private Integer nominal;

    /** Full Russian name of the currency (e.g. "Доллар США"). */
    @SerializedName("Name")
    private String name;

    /** Current CBR rate: how many rubles equal {@link #nominal} units of this currency. */
    @SerializedName("Value")
    private Double value;

    /** CBR rate from the previous trading day — used to compute the daily change. */
    @SerializedName("Previous")
    private Double previous;

    /** Date of the CBR feed this rate belongs to. */
    @SerializedName("Date")
    private Date date;

    /**
     * Returns the absolute change in rate since the previous trading day
     * ({@code value − previous}), or {@code null} if either value is missing.
     *
     * @return daily rate change, or {@code null}
     */
    public Double getDiff() {
        if (value == null || previous == null) return null;
        return value - previous;
    }

    /**
     * Returns the relative rate change since the previous trading day as a percentage,
     * or {@code null} if either value is missing or {@code previous} is zero.
     *
     * @return daily rate change in percent, or {@code null}
     */
    public Double getPercentChange() {
        if (value == null || previous == null || previous == 0.0) return null;
        return ((value / previous) - 1) * 100;
    }
}
