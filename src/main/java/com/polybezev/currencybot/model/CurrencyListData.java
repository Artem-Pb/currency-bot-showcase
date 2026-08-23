package com.polybezev.currencybot.model;

import java.util.List;

/**
 * Snapshot of all currencies available from the CBR feed on a given date.
 *
 * @param currencies list of available currencies with their codes and names
 * @param feedDate   the date of the CBR feed this snapshot was built from (display string)
 */
public record CurrencyListData(List<CurrencyListEntry> currencies, String feedDate) {}
