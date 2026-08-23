package com.polybezev.currencybot.model;

/**
 * A single row in the CBR currency list: the ISO 4217 code and the Russian name of the currency.
 *
 * @param code 3-letter ISO 4217 currency code (e.g. "USD", "EUR")
 * @param name full Russian name of the currency (e.g. "Доллар США")
 */
public record CurrencyListEntry(String code, String name) {}
