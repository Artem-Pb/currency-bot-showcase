package com.polybezev.currencybot.formatter;

import com.polybezev.currencybot.model.CryptoPriceModel;
import com.polybezev.currencybot.model.CurrencyListData;
import com.polybezev.currencybot.model.CurrencyListEntry;
import com.polybezev.currencybot.model.CurrencyModel;
import com.polybezev.currencybot.model.Tier;
import com.polybezev.currencybot.service.AiAnalysisService;
import com.polybezev.currencybot.service.NewsService;
import com.polybezev.currencybot.service.TaSignalService;
import com.polybezev.currencybot.util.CurrencyFlags;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * Assembles all outgoing bot messages: reply keyboards, inline keyboards, and text cards.
 * <p>
 * All user-facing string literals live in {@link BotMessages} — this class only performs
 * placeholder substitution and object construction. Button labels used here are imported
 * from {@link BotMessages} so that keyboard builders and the text-based router in
 * {@code CommandHandler} always reference the same string.
 */
@Component
public class MessageFormatter {

    /** Thread-safe date formatter reused across all card builders. */
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    // ==================== REPLY KEYBOARDS ====================

    /**
     * Builds the main reply keyboard shown at the bottom of the chat.
     * An extra admin row is appended when {@code isAdmin} is {@code true}.
     *
     * @param isAdmin whether the current user is an administrator
     * @return configured {@link ReplyKeyboardMarkup}
     */
    public ReplyKeyboardMarkup buildMainKeyboard(boolean isAdmin) {
        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton(BotMessages.BTN_RATES));
        row1.add(new KeyboardButton(BotMessages.BTN_CONVERTER));
        row1.add(new KeyboardButton(BotMessages.BTN_BTC_MAIN));

        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton(BotMessages.BTN_SUBSCRIBE));
        row2.add(new KeyboardButton(BotMessages.BTN_LK));

        List<KeyboardRow> rows = new ArrayList<>(List.of(row1, row2));

        if (isAdmin) {
            KeyboardRow adminRow = new KeyboardRow();
            adminRow.add(new KeyboardButton(BotMessages.BTN_ADMIN));
            rows.add(adminRow);
        }

        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setKeyboard(rows);
        markup.setResizeKeyboard(true);
        return markup;
    }

    /**
     * Builds the personal account (ЛК) reply keyboard.
     * Rows are added progressively based on the user's tier:
     * TIER 1+ unlocks News; TIER 2+ adds AI digest and Signals; TIER 3+ adds the trading bot button.
     *
     * @param tier the user's current subscription tier
     * @return configured {@link ReplyKeyboardMarkup}
     */
    public ReplyKeyboardMarkup buildLkKeyboard(Tier tier) {
        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton(BotMessages.BTN_BALANCE));
        row1.add(new KeyboardButton(BotMessages.BTN_STARS_SUB));

        List<KeyboardRow> rows = new ArrayList<>(List.of(row1));

        if (tier.hasAccess(Tier.TIER_1)) {
            KeyboardRow newsRow = new KeyboardRow();
            newsRow.add(new KeyboardButton(BotMessages.BTN_NEWS));
            if (tier.hasAccess(Tier.TIER_2)) {
                newsRow.add(new KeyboardButton(BotMessages.BTN_AI_DIGEST));
                newsRow.add(new KeyboardButton(BotMessages.BTN_SIGNALS));
            }
            rows.add(newsRow);
        }

        if (tier.hasAccess(Tier.TIER_3)) {
            KeyboardRow botRow = new KeyboardRow();
            botRow.add(new KeyboardButton(BotMessages.BTN_TRADE_BOT));
            rows.add(botRow);
        }

        KeyboardRow last = new KeyboardRow();
        last.add(new KeyboardButton(BotMessages.BTN_HELP));
        last.add(new KeyboardButton(BotMessages.BTN_BACK));
        rows.add(last);

        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setKeyboard(rows);
        markup.setResizeKeyboard(true);
        return markup;
    }

    /**
     * Builds the admin panel reply keyboard with user management actions.
     *
     * @return configured {@link ReplyKeyboardMarkup}
     */
    public ReplyKeyboardMarkup buildAdminKeyboard() {
        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton(BotMessages.BTN_USERS));
        row1.add(new KeyboardButton(BotMessages.BTN_GRANT));

        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton(BotMessages.BTN_BANS));
        row2.add(new KeyboardButton(BotMessages.BTN_BACK));

        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setKeyboard(List.of(row1, row2));
        markup.setResizeKeyboard(true);
        return markup;
    }

    // ==================== INLINE KEYBOARDS ====================

    /**
     * Builds the currency selection keyboard for the converter FSM.
     * Includes the base currencies, an explicit RUB button, and a trailing cancel row.
     *
     * @return configured {@link InlineKeyboardMarkup}
     */
    public InlineKeyboardMarkup buildConvertKeyboard() {
        List<List<InlineKeyboardButton>> rows = baseCurrencyRows();
        rows.add(row(btn("🇷🇺 RUB", "RUB")));
        rows.add(row(btn(BotMessages.BTN_CANCEL_INLINE, BotMessages.CALLBACK_CANCEL_INPUT)));
        return markup(rows);
    }

    /**
     * Builds a single-button inline keyboard with the universal cancel action.
     * Attached to every free-text-awaiting prompt that has no other inline keyboard of its own
     * (converter amount, admin grant chatId, trade bot API key/secret, portfolio AI question) —
     * screens that already have one (currency grid, tier grid) get the button appended to that
     * keyboard instead, see {@link #buildConvertKeyboard()} and {@link #buildAdminGrantTierKeyboard()}.
     *
     * @return configured {@link InlineKeyboardMarkup}
     */
    public InlineKeyboardMarkup buildCancelKeyboard() {
        return markup(List.of(row(btn(BotMessages.BTN_CANCEL_INLINE, BotMessages.CALLBACK_CANCEL_INPUT))));
    }

    /**
     * Builds the currency selection keyboard for the full CBR fiat rate lookup ({@code /list},
     * {@code /curse}). Unrelated to the crypto watchlist — see {@link #buildWatchlistRatesKeyboard()}
     * for that screen's keyboard.
     *
     * @return configured {@link InlineKeyboardMarkup}
     */
    public InlineKeyboardMarkup buildRatesKeyboard() {
        return markup(baseCurrencyRows());
    }

    /**
     * Builds the keyboard for the personalised "📊 Курсы" (watchlist) screen: a single button
     * to open the favourites editor.
     * <p>
     * Split out from {@link #buildRatesKeyboard()} (2026-08-03, UX fix) — that keyboard's 13
     * fiat currency buttons had nothing to do with the crypto watchlist shown above them, and
     * buried the one relevant action (editing favourites) at the bottom of an unrelated grid,
     * making it look like there was no way to change or remove favourites at all.
     *
     * @return configured {@link InlineKeyboardMarkup}
     */
    public InlineKeyboardMarkup buildWatchlistRatesKeyboard() {
        return markup(List.of(row(btn(BotMessages.BTN_WATCHLIST_EDIT, BotMessages.CALLBACK_WATCHLIST_EDIT_OPEN))));
    }

    /**
     * Coins offered in the watchlist selection keyboard: the same 12 coins as
     * {@link TaSignalService#supportedCoins()} (same order as {@link #buildSignalReplyKeyboard}
     * for visual consistency), plus {@code USDT} — a global reference currency in its own
     * right, worth tracking even though it isn't a TA-signal coin.
     * <p>
     * Changed from an earlier fiat-currency pool (2026-08-02, live-testing feedback): crypto
     * news and TIER 3 positions are what this watchlist is actually matched against
     * (see {@code CommandHandler.resolveNewsAssets}), so a fiat pool never made sense here —
     * it was a leftover from watchlist reusing {@link #baseCurrencyRows()} at first draft.
     */
    private static final String[][] WATCHLIST_OPTIONS = {
            {"₿", "BTC"}, {"🪙", "ETH"}, {"🪙", "SOL"},
            {"🪙", "BNB"}, {"🪙", "XRP"}, {"🐶", "DOGE"},
            {"🪙", "ADA"}, {"🔺", "AVAX"}, {"⚫", "DOT"},
            {"🔗", "LINK"}, {"💎", "TON"}, {"🪙", "LTC"},
            {"💵", "USDT"},
    };

    /**
     * Returns the display icon for a watchlist coin code, or a generic coin icon if unknown.
     */
    private String watchlistIcon(String code) {
        for (String[] opt : WATCHLIST_OPTIONS) {
            if (opt[1].equals(code)) return opt[0];
        }
        return "🪙";
    }

    /**
     * Builds the multi-select favourites keyboard: one toggle button per currency (checked
     * with ✅ when present in {@code selected}), three per row, plus a trailing "Готово" row.
     * <p>
     * Reused in two places with different follow-up behaviour decided by the caller based on
     * {@link com.polybezev.currencybot.model.ConversationState}: the onboarding prompt on
     * {@code /start}, and the "⭐ Настроить избранное" editor opened from the rates view.
     *
     * @param selected currently selected coin codes (uppercase); never mutated
     * @return configured {@link InlineKeyboardMarkup}
     */
    public InlineKeyboardMarkup buildWatchlistKeyboard(Set<String> selected) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (int i = 0; i < WATCHLIST_OPTIONS.length; i += 3) {
            List<InlineKeyboardButton> currentRow = new ArrayList<>();
            for (int j = i; j < Math.min(i + 3, WATCHLIST_OPTIONS.length); j++) {
                String flag = WATCHLIST_OPTIONS[j][0];
                String code = WATCHLIST_OPTIONS[j][1];
                String mark = selected.contains(code) ? "✅ " : "";
                currentRow.add(btn(mark + flag + " " + code, BotMessages.CALLBACK_WATCHLIST_TOGGLE_PREFIX + code));
            }
            rows.add(currentRow);
        }
        rows.add(row(btn("✅ Готово", BotMessages.CALLBACK_WATCHLIST_DONE)));
        return markup(rows);
    }

    /**
     * Builds an inline keyboard with no buttons — used to clear a message's inline keyboard
     * via {@code EditMessageText}, which otherwise keeps whatever markup was already attached.
     *
     * @return empty {@link InlineKeyboardMarkup}
     */
    public InlineKeyboardMarkup emptyKeyboard() {
        return markup(new ArrayList<>());
    }

    /**
     * Builds the coin selection reply keyboard for the SIGNALS mode.
     * Coins are arranged 3-per-row; a back button occupies the last row.
     *
     * @return configured {@link ReplyKeyboardMarkup}
     */
    public ReplyKeyboardMarkup buildSignalReplyKeyboard() {
        KeyboardRow row1 = new KeyboardRow();
        row1.add("BTC"); row1.add("ETH"); row1.add("SOL");

        KeyboardRow row2 = new KeyboardRow();
        row2.add("BNB"); row2.add("XRP"); row2.add("DOGE");

        KeyboardRow row3 = new KeyboardRow();
        row3.add("ADA"); row3.add("AVAX"); row3.add("DOT");

        KeyboardRow row4 = new KeyboardRow();
        row4.add("LINK"); row4.add("TON"); row4.add("LTC");

        KeyboardRow back = new KeyboardRow();
        back.add(new KeyboardButton(BotMessages.BTN_BACK));

        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setKeyboard(List.of(row1, row2, row3, row4, back));
        markup.setResizeKeyboard(true);
        return markup;
    }

    /**
     * Builds the subscription purchase keyboard with a button per tier.
     * Each button sends a {@code BUY_<TIER>} callback.
     *
     * @return configured {@link InlineKeyboardMarkup}
     */
    public InlineKeyboardMarkup buildTierKeyboard() {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(row(btn("⚡ TIER 1 — 200 Stars", "BUY_TIER_1")));
        rows.add(row(btn("📈 TIER 2 — 600 Stars", "BUY_TIER_2")));
        rows.add(row(btn("🤖 TIER 3 — 1500 Stars", "BUY_TIER_3")));
        return markup(rows);
    }

    /**
     * Builds the tier selection keyboard used in the admin grant flow.
     * Each button sends an {@code ADMIN_GRANT_TIER_<TIER>} callback; a trailing row cancels
     * the grant flow.
     *
     * @return configured {@link InlineKeyboardMarkup}
     */
    public InlineKeyboardMarkup buildAdminGrantTierKeyboard() {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(row(btn("🆓 FREE",   "ADMIN_GRANT_TIER_FREE"),
                     btn("⚡ TIER 1", "ADMIN_GRANT_TIER_TIER_1")));
        rows.add(row(btn("📈 TIER 2", "ADMIN_GRANT_TIER_TIER_2"),
                     btn("🤖 TIER 3", "ADMIN_GRANT_TIER_TIER_3")));
        rows.add(row(btn(BotMessages.BTN_CANCEL_INLINE, BotMessages.CALLBACK_CANCEL_INPUT)));
        return markup(rows);
    }

    /**
     * Builds the per-user action keyboard in the admin user list.
     * Contains a tier grant button and a context-sensitive ban/unban button.
     *
     * @param targetChatId chat ID of the target user
     * @param isBanned     current ban state of the target user
     * @return configured {@link InlineKeyboardMarkup}
     */
    public InlineKeyboardMarkup buildAdminUserActionsKeyboard(long targetChatId, boolean isBanned) {
        String banLabel    = isBanned ? "✅ Разбанить" : "🚫 Забанить";
        String banCallback = isBanned ? "ADMIN_UNBAN_" + targetChatId : "ADMIN_BAN_" + targetChatId;
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(row(btn("🔑 Выдать тир", "ADMIN_START_GRANT_" + targetChatId), btn(banLabel, banCallback)));
        return markup(rows);
    }

    // ==================== TEXT BUILDERS ====================

    /**
     * Builds the /start welcome message with the user's first name substituted.
     *
     * @param name Telegram first name; if {@code null} an empty string is used
     * @return formatted welcome text from {@link BotMessages#START_TEXT}
     */
    public String buildStartText(String name) {
        return BotMessages.START_TEXT.replace("{name}", name != null ? name : "");
    }

    /**
     * Builds the help text listing all available features.
     *
     * @return help text from {@link BotMessages#HELP_TEXT}
     */
    public String buildHelpText() {
        return BotMessages.HELP_TEXT;
    }

    /**
     * Builds the "currency not found" error message with the unknown code substituted.
     *
     * @param code the currency code the user entered
     * @return formatted error text from {@link BotMessages#CURRENCY_NOT_FOUND}
     */
    public String buildCurrencyNotFoundText(String code) {
        return BotMessages.CURRENCY_NOT_FOUND.replace("{code}", code);
    }

    /**
     * Builds the unrecognised input error message.
     *
     * @return error text from {@link BotMessages#UNKNOWN_INPUT}
     */
    public String buildUnknownInputText() {
        return BotMessages.UNKNOWN_INPUT;
    }

    /**
     * Builds the subscription tier card showing the user's current tier and all tiers.
     * Requires {@code ParseMode.HTML} when sent.
     *
     * @param current the user's active {@link Tier}
     * @return formatted tier card from {@link BotMessages#TIER_CARD}
     */
    public String buildTierCard(Tier current) {
        return BotMessages.TIER_CARD.replace("{currentTierLabel}", current.label);
    }

    /**
     * Builds the personal account balance summary. Panel style (2026-08-03) — requires
     * {@code ParseMode.HTML} when sent.
     *
     * @param tier      the user's active tier
     * @param expires   human-readable expiry date, or "—" if not applicable
     * @param paidStars total Telegram Stars spent by this user
     * @return formatted balance text from {@link BotMessages#LK_BALANCE}
     */
    public String buildLkBalance(Tier tier, String expires, int paidStars) {
        return BotMessages.LK_BALANCE
                .replace("{tier}",    tier.label)
                .replace("{expires}", expires)
                .replace("{paid}",    String.valueOf(paidStars));
    }

    /**
     * Builds the Bitcoin price card from a {@link CryptoPriceModel}.
     * Panel style (2026-08-03) — requires {@code ParseMode.HTML} when sent.
     *
     * @param model price data for the coin (symbol, RUB price, USD price, 24h change)
     * @return formatted card text from {@link BotMessages#CRYPTO_CARD}
     */
    public String buildCryptoCard(CryptoPriceModel model) {
        return BotMessages.CRYPTO_CARD
                .replace("{symbol}", model.getSymbol())
                .replace("{rub}",    String.format("%.0f", model.getPriceRub()))
                .replace("{usd}",    String.format("%.0f", model.getPriceUsd()))
                .replace("{arrow}",  model.getChange24h() >= 0 ? "▲" : "▼")
                .replace("{pct}",    String.format("%.2f", model.getChange24h()))
                .replace("{date}",   LocalDate.now().format(DATE_FMT));
    }

    /**
     * Builds the CBR exchange rate card for a single currency.
     * Panel style (2026-08-03) — requires {@code ParseMode.HTML} when sent. The nominal is
     * folded into {@code {value_line}} (e.g. {@code "6.23 ₽ (за 10 JPY)"}) rather than a
     * separate placeholder, since only some currencies have one and the panel layout has no
     * room for a conditional prefix without breaking column alignment. {@code {name}} is
     * HTML-escaped since it comes from the CBR feed, not a fixed internal set like the code.
     *
     * @param c currency data including rate, nominal, diff, and CBR date
     * @return formatted rate card from {@link BotMessages#RATE_CARD}
     */
    public String buildRateCard(CurrencyModel c) {
        String flag = CurrencyFlags.getFlag(c.getCharCode());
        String valueLine = c.getNominal() != null && c.getNominal() > 1
                ? String.format("%.2f ₽ (за %d %s)", c.getValue(), c.getNominal(), c.getCharCode())
                : String.format("%.2f ₽", c.getValue());

        Double percent = c.getPercentChange();
        Double diff    = c.getDiff();
        String changeIcon = diff == null ? "" : (diff >= 0 ? "📈" : "📉");
        String changeLine = percent == null ? "—" : (changeIcon + " " + String.format("%+.2f%%", percent));

        return BotMessages.RATE_CARD
                .replace("{flag}",        flag)
                .replace("{code}",        c.getCharCode())
                .replace("{name}",        escapeHtml(c.getName()))
                .replace("{value_line}",  valueLine)
                .replace("{change_line}", changeLine)
                .replace("{date}",        formatDate(c.getDate()));
    }

    /**
     * Builds the currency conversion result message.
     * Source label is "CoinGecko" when either currency is BTC, otherwise "ЦБ РФ". Requires
     * {@code ParseMode.HTML} when sent; {@code from}/{@code to} are HTML-escaped since the
     * interactive converter FSM accepts them as free text, not just picked from the inline grid.
     *
     * @param amount source amount
     * @param from   source currency code
     * @param result converted amount
     * @param to     target currency code
     * @return formatted result text from {@link BotMessages#CONVERT_RESULT}
     */
    public String buildConvertResult(double amount, String from, double result, String to) {
        String source = (from.equals(BotMessages.CALLBACK_BTC) || to.equals(BotMessages.CALLBACK_BTC))
                ? "CoinGecko" : "ЦБ РФ";
        return BotMessages.CONVERT_RESULT
                .replace("{from_amount}", formatAmount(amount, from))
                .replace("{from}",        escapeHtml(from))
                .replace("{to_amount}",   formatAmount(result, to))
                .replace("{to}",          escapeHtml(to))
                .replace("{source}",      source);
    }

    /**
     * Builds the full currency list message from CBR data.
     *
     * @param data all available currencies with their codes, names, and feed date
     * @return formatted list using {@link BotMessages#LIST_HEADER} and {@link BotMessages#LIST_FOOTER}
     */
    public String buildCurrencyList(CurrencyListData data) {
        StringBuilder sb = new StringBuilder(BotMessages.LIST_HEADER);
        for (CurrencyListEntry entry : data.currencies()) {
            sb.append(String.format("%s %s — %s\n",
                    CurrencyFlags.getFlag(entry.code()), entry.code(), entry.name()));
        }
        sb.append(BotMessages.LIST_FOOTER
                .replace("{count}", String.valueOf(data.currencies().size()))
                .replace("{date}",  data.feedDate()));
        return sb.toString();
    }

    /**
     * Builds one news block for the "📰 Новости" view: original title, Russian translation,
     * general AI analysis, and — if relevant — a personalised impact line.
     * <p>
     * Falls back to {@link BotMessages#NEWS_ITEM_FALLBACK} (title + source only) when
     * {@code analysis} is {@code null}, i.e. the AI call failed and nothing was cached yet for
     * this item ({@code AiAnalysisService.translateAndAnalyzeNews}).
     *
     * @param item     the news item (title, source, link)
     * @param analysis shared translation + general summary, or {@code null} on AI failure
     * @param impact   personalised relevance line, or {@code null}/blank if not applicable
     * @return formatted block, always ending in a blank line so items concatenate cleanly
     */
    public String buildNewsItemBlock(NewsService.NewsItem item, AiAnalysisService.NewsAnalysis analysis, String impact) {
        if (analysis == null) {
            return BotMessages.NEWS_ITEM_FALLBACK
                    .replace("{title}", item.title())
                    .replace("{source}", item.source());
        }
        String impactLine = (impact == null || impact.isBlank()) ? "" : "🎯 " + impact + "\n";
        return BotMessages.NEWS_ITEM_BLOCK
                .replace("{title}", item.title())
                .replace("{translation}", analysis.translation())
                .replace("{summary}", analysis.summary())
                .replace("{impact}", impactLine)
                .replace("{source}", item.source());
    }

    /**
     * Builds the personalised "Курсы" view: one row per watchlist coin with its current
     * CoinGecko price (RUB/USD) and 24h direction arrow, in a monospaced {@code <pre>} block
     * so columns line up regardless of symbol length (BTC vs DOGE vs USDT). Panel style
     * (2026-08-03) — requires {@code ParseMode.HTML} when sent; column widths are computed
     * per call (via {@code String.format} padding) rather than hand-padded like the static
     * templates in {@link BotMessages}, since the row count and symbol lengths vary.
     *
     * @param rates watchlist coins already resolved to live {@link CryptoPriceModel}s, with
     *              {@code symbol} set to the coin code (codes that failed to resolve are
     *              skipped by the caller before this is called)
     * @return formatted list using {@link BotMessages#WATCHLIST_RATES_HEADER}
     */
    public String buildWatchlistRates(List<CryptoPriceModel> rates) {
        StringBuilder sb = new StringBuilder(BotMessages.WATCHLIST_RATES_HEADER);
        sb.append("<pre>\n");
        for (CryptoPriceModel c : rates) {
            String arrow = c.getChange24h() >= 0 ? "▲" : "▼";
            sb.append(String.format("%s %-5s %12.0f ₽  %10.2f $  %s%.2f%%%n",
                    watchlistIcon(c.getSymbol()), c.getSymbol(),
                    c.getPriceRub(), c.getPriceUsd(), arrow, Math.abs(c.getChange24h())));
        }
        sb.append("</pre>");
        return sb.toString();
    }

    /**
     * Builds the TA signal card combining technical indicator values and an optional AI explanation.
     *
     * @param r             signal result with all indicator values and composite score
     * @param aiExplanation AI-generated plain-language explanation, or {@code null} if unavailable
     * @return formatted signal card from {@link BotMessages#TA_SIGNAL}
     */
    public String buildSignalCard(TaSignalService.SignalResult r, String aiExplanation) {
        return BotMessages.TA_SIGNAL
                .replace("{asset}",         r.coin())
                .replace("{signal}",        r.signal())
                .replace("{score}",         String.valueOf(r.score()))
                .replace("{rsi}",           String.format("%.1f", r.rsi()))
                .replace("{macd}",          String.format("%.6f", r.macd()))
                .replace("{macdSignal}",    String.format("%.6f", r.macdSignal()))
                .replace("{ema20}",         String.format("%.2f", r.ema20()))
                .replace("{ema50}",         String.format("%.2f", r.ema50()))
                .replace("{aiExplanation}", aiExplanation != null ? aiExplanation : "")
                .replace("{date}",          LocalDate.now().format(DATE_FMT));
    }

    /**
     * Builds the signal change alert card sent by {@code SignalScheduler} when a coin's signal flips.
     * Includes the previous signal so the user can see the direction of the change.
     *
     * @param r             fresh signal result with all indicator values and composite score
     * @param prevSignal    the signal string that was active before this change (e.g. {@code "⚪ HOLD"});
     *                      {@code "—"} is substituted when the previous state is unknown (cold start)
     * @param aiExplanation AI-generated plain-language explanation, or {@code null} if unavailable
     * @return formatted alert card from {@link BotMessages#SIGNAL_CHANGE_CARD}
     */
    public String buildSignalChangeCard(TaSignalService.SignalResult r, String prevSignal, String aiExplanation) {
        return BotMessages.SIGNAL_CHANGE_CARD
                .replace("{asset}",         r.coin())
                .replace("{prevSignal}",    prevSignal.isEmpty() ? "—" : prevSignal)
                .replace("{signal}",        r.signal())
                .replace("{score}",         String.valueOf(r.score()))
                .replace("{rsi}",           String.format("%.1f", r.rsi()))
                .replace("{macd}",          String.format("%.6f", r.macd()))
                .replace("{macdSignal}",    String.format("%.6f", r.macdSignal()))
                .replace("{ema20}",         String.format("%.2f", r.ema20()))
                .replace("{ema50}",         String.format("%.2f", r.ema50()))
                .replace("{aiExplanation}", aiExplanation != null ? aiExplanation : "")
                .replace("{date}",          LocalDate.now().format(DATE_FMT));
    }

    // ==================== TRADE BOT ====================

    /**
     * Builds the trade bot status screen when the user has no credentials saved.
     * Requires {@code ParseMode.HTML} when sent.
     *
     * @return formatted no-keys message from {@link BotMessages#TRADE_BOT_NO_KEYS}
     */
    public String buildTradeBotNoKeys() {
        return BotMessages.TRADE_BOT_NO_KEYS;
    }

    /**
     * Builds the trade bot status screen for a user with credentials already saved.
     * Panel style (2026-08-03) — requires {@code ParseMode.HTML} when sent.
     *
     * @param exchangeName  display name of the connected exchange (e.g. {@code "Binance"})
     * @param tradingEnabled whether automated trading is currently active
     * @return formatted status message from {@link BotMessages#TRADE_BOT_STATUS}
     */
    public String buildTradeBotStatus(String exchangeName, boolean tradingEnabled) {
        String status = tradingEnabled ? "🟢 Включена" : "⏸ Приостановлена";
        return BotMessages.TRADE_BOT_STATUS
                .replace("{exchange}", exchangeName)
                .replace("{status}",   status);
    }

    /**
     * Builds the trade bot reply keyboard.
     * <p>
     * Layout depends on whether credentials exist and whether trading is enabled:
     * <ul>
     *   <li>No credentials: Connect Exchange + Back</li>
     *   <li>Credentials, trading off: Enable + History + Portfolio AI + Delete keys + Back</li>
     *   <li>Credentials, trading on: Pause + History + Portfolio AI + Delete keys + Back</li>
     * </ul>
     *
     * @param hasCredentials whether the user has connected an exchange
     * @param tradingEnabled whether automated trading is currently active
     * @return configured {@link ReplyKeyboardMarkup}
     */
    public ReplyKeyboardMarkup buildTradeBotKeyboard(boolean hasCredentials, boolean tradingEnabled) {
        List<KeyboardRow> rows = new ArrayList<>();

        if (!hasCredentials) {
            KeyboardRow row = new KeyboardRow();
            row.add(new KeyboardButton(BotMessages.BTN_CONNECT_EXCHANGE));
            rows.add(row);
        } else {
            KeyboardRow row1 = new KeyboardRow();
            row1.add(new KeyboardButton(
                    tradingEnabled ? BotMessages.BTN_DISABLE_TRADING : BotMessages.BTN_ENABLE_TRADING));
            rows.add(row1);

            KeyboardRow row2 = new KeyboardRow();
            row2.add(new KeyboardButton(BotMessages.BTN_TRADE_HISTORY));
            row2.add(new KeyboardButton(BotMessages.BTN_PORTFOLIO_AI));
            rows.add(row2);

            KeyboardRow row3 = new KeyboardRow();
            row3.add(new KeyboardButton(BotMessages.BTN_DELETE_KEYS));
            rows.add(row3);
        }

        KeyboardRow back = new KeyboardRow();
        back.add(new KeyboardButton(BotMessages.BTN_BACK));
        rows.add(back);

        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setKeyboard(rows);
        markup.setResizeKeyboard(true);
        return markup;
    }

    /**
     * Builds the inline keyboard for selecting an exchange during the setup flow.
     * Each button sends a {@code TRADE_EXCHANGE_<name>} callback.
     *
     * @return configured {@link InlineKeyboardMarkup}
     */
    public InlineKeyboardMarkup buildExchangeKeyboard() {
        return markup(List.of(
                row(btn("🟡 Binance", BotMessages.CALLBACK_TRADE_EXCHANGE_PREFIX + "BINANCE"),
                    btn("🟠 Bybit",   BotMessages.CALLBACK_TRADE_EXCHANGE_PREFIX + "BYBIT"))
        ));
    }

    /**
     * Builds the trade history text from a list of {@link com.polybezev.currencybot.entity.TradeOrder} rows.
     *
     * @param orders list of trade orders to display; must not be empty (caller should check first)
     * @return formatted history text from {@link BotMessages#TRADE_HISTORY_HEADER} and {@link BotMessages#TRADE_ORDER_ROW}
     */
    public String buildTradeHistory(List<com.polybezev.currencybot.entity.TradeOrder> orders) {
        StringBuilder sb = new StringBuilder(
                BotMessages.TRADE_HISTORY_HEADER.replace("{count}", String.valueOf(orders.size())));
        for (com.polybezev.currencybot.entity.TradeOrder o : orders) {
            String qty   = o.getQuantity() != null ? o.getQuantity().toPlainString() : "—";
            String price = o.getPrice()    != null ? o.getPrice().toPlainString()    : "—";
            sb.append(BotMessages.TRADE_ORDER_ROW
                    .replace("{side}",     o.getSide().name())
                    .replace("{coin}",     o.getCoin())
                    .replace("{quantity}", qty)
                    .replace("{price}",    price)
                    .replace("{status}",   o.getStatus().name())
                    .replace("{date}",     o.getCreatedAt().format(
                            java.time.format.DateTimeFormatter.ofPattern("dd.MM HH:mm")))
            ).append("\n");
        }
        return sb.toString();
    }

    /**
     * Builds the paginated user list text for the admin panel.
     *
     * @param userLines formatted lines, one per user
     * @param page      current page number (1-based)
     * @param totalPages total number of pages
     * @return formatted list from {@link BotMessages#ADMIN_USER_LIST}
     */
    public String buildAdminUserList(List<String> userLines, int page, int totalPages) {
        return BotMessages.ADMIN_USER_LIST
                .replace("{page}",  String.valueOf(page))
                .replace("{total}", String.valueOf(totalPages))
                .replace("{list}",  String.join("\n", userLines));
    }

    // ==================== PRIVATE HELPERS ====================

    /**
     * Escapes the characters with special meaning in Telegram's HTML parse mode
     * ({@code < > &}), so text sourced from outside the bot's own fixed enums/constants (CBR
     * currency names, free-typed converter currency codes) can't break message formatting or
     * be misread as markup when interpolated into an HTML-parsed card.
     *
     * @param s raw text to embed in an HTML-parsed message
     * @return {@code s} with {@code & < >} replaced by their HTML entities
     */
    private String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * Converts a {@link Date} from the CBR feed to a display string.
     * Returns "—" if the date is {@code null}.
     *
     * @param date CBR feed date, may be {@code null}
     * @return formatted date string (dd.MM.yyyy) or "—"
     */
    private String formatDate(Date date) {
        if (date == null) return "—";
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate().format(DATE_FMT);
    }

    /**
     * Formats a numeric amount with appropriate precision for the given currency.
     * BTC uses 10 decimal places; all other currencies use 2.
     *
     * @param value    the amount to format
     * @param currency currency code (e.g. "BTC", "USD")
     * @return formatted amount string
     */
    private String formatAmount(double value, String currency) {
        return BotMessages.CALLBACK_BTC.equals(currency)
                ? String.format("%.10f", value)
                : String.format("%.2f", value);
    }

    private InlineKeyboardMarkup markup(List<List<InlineKeyboardButton>> rows) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);
        return markup;
    }

    /**
     * Builds the shared base currency grid used by both the rates and converter keyboards.
     * The last row contains a BTC button since BTC conversions go through CoinGecko.
     *
     * @return mutable list of button rows (caller may append more rows)
     */
    private List<List<InlineKeyboardButton>> baseCurrencyRows() {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(row(btn("🇺🇸 USD", "USD"), btn("🇪🇺 EUR", "EUR"), btn("🇨🇳 CNY", "CNY")));
        rows.add(row(btn("🇬🇧 GBP", "GBP"), btn("🇯🇵 JPY", "JPY"), btn("🇨🇭 CHF", "CHF")));
        rows.add(row(btn("🇹🇷 TRY", "TRY"), btn("🇦🇪 AED", "AED"), btn("🇰🇿 KZT", "KZT")));
        rows.add(row(btn("🇧🇾 BYN", "BYN"), btn("🇨🇦 CAD", "CAD"), btn("🇭🇰 HKD", "HKD")));
        rows.add(row(btn("₿ BTC",   BotMessages.CALLBACK_BTC)));
        return rows;
    }

    private InlineKeyboardButton btn(String text, String callbackData) {
        InlineKeyboardButton btn = new InlineKeyboardButton();
        btn.setText(text);
        btn.setCallbackData(callbackData);
        return btn;
    }

    private List<InlineKeyboardButton> row(InlineKeyboardButton... buttons) {
        return new ArrayList<>(Arrays.asList(buttons));
    }
}
