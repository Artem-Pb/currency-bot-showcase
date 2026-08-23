package com.polybezev.currencybot.handler;

import com.polybezev.currencybot.formatter.BotMessages;
import com.polybezev.currencybot.formatter.MessageFormatter;
import com.polybezev.currencybot.model.*;
import com.polybezev.currencybot.service.*;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@AllArgsConstructor
@Slf4j
public class CommandHandler {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final CurrencyService currencyService;
    private final MessageFormatter formatter;
    private final CryptoService cryptoService;
    private final UserStateService userStateService;
    private final SubscriptionService subscriptionService;
    private final TaSignalService taSignalService;
    private final AiAnalysisService aiAnalysisService;
    private final NewsService newsService;
    private final MarketDataService marketDataService;
    private final TradeHandler tradeHandler;
    private final WatchlistService watchlistService;
    private final TradeService tradeService;

    // ==================== ENTRY POINTS ====================

    /**
     * Handles all slash-command messages (text starting with "/").
     * <p>
     * Arguments after the command name are parsed into {@code arg}:
     * {@code /curse USD} → cmd={@code /curse}, arg={@code USD}.
     * An empty {@code arg} starts the interactive FSM flow where applicable.
     *
     * @param text      full message text including the slash and any arguments
     * @param chatId    sender's chat ID
     * @param firstName sender's Telegram first name (used in /start greeting)
     * @param data      current FSM conversation state for this user
     * @param isAdmin   whether the caller is the admin user (affects /start keyboard)
     * @return response message to send back
     */
    public SendMessage handleCommand(String text, long chatId, String firstName,
                                     UserConversationData data, boolean isAdmin) {
        String[] parts = text.split(" ", 2);
        String cmd = parts[0].toLowerCase();
        String arg = parts.length > 1 ? parts[1].trim() : "";

        return switch (cmd) {
            case "/start" -> {
                data.setMode(UserMode.MAIN);
                if (watchlistService.get(chatId).isEmpty()) {
                    data.setState(ConversationState.AWAIT_WATCHLIST_SELECTION);
                    yield msg(chatId,
                            formatter.buildStartText(firstName) + BotMessages.WATCHLIST_ONBOARDING_PROMPT,
                            formatter.buildWatchlistKeyboard(Set.of()));
                }
                yield msg(chatId, formatter.buildStartText(firstName), formatter.buildMainKeyboard(isAdmin));
            }
            case "/help"    -> msg(chatId, formatter.buildHelpText());
            case "/list"    -> handleList(chatId);
            case "/curse"   -> arg.isEmpty() ? showCurseKeyboard(chatId) : handleCurrencyRequest(arg.toUpperCase(), chatId);
            case "/convert" -> arg.isEmpty() ? startConvertFsm(chatId, data) : handleConvert(arg, chatId);
            case "/btc"     -> handleBtc(chatId);
            case "/tier"    -> handleTier(chatId, isAdmin);
            case "/signal"  -> arg.isEmpty() ? handleSignal(chatId, data) : handleSignalForCoin(chatId, arg.toUpperCase());
            case "/lk"      -> enterLk(chatId, data);
            default         -> msg(chatId, BotMessages.UNKNOWN_COMMAND);
        };
    }

    /**
     * Handles plain text messages and reply-keyboard button presses.
     * <p>
     * Routing order:
     * <ol>
     *   <li>If the user is in LK mode — delegated to {@link #handleLkText}</li>
     *   <li>Main menu reply-keyboard buttons matched by {@link BotMessages} constants</li>
     *   <li>Free-text currency code recognition (3-letter code, or common Russian names)</li>
     *   <li>Unrecognised input — shows the unknown-input prompt</li>
     * </ol>
     *
     * @param text    message text
     * @param chatId  sender's chat ID
     * @param data    current FSM conversation state
     * @param isAdmin whether the caller is the admin user
     * @return response message to send back
     */
    public SendMessage handleText(String text, long chatId, UserConversationData data, boolean isAdmin) {
        if (data.getMode() == UserMode.LK) {
            return handleLkText(text, chatId, data, isAdmin);
        }
        if (data.getMode() == UserMode.SIGNALS) {
            return handleSignalsText(text, chatId, data, isAdmin);
        }
        if (data.getMode() == UserMode.TRADE_BOT) {
            return tradeHandler.handleTradeBotText(text, chatId, data);
        }

        if (text.equals(BotMessages.BTN_RATES))     return handleWatchlistRates(chatId);
        if (text.equals(BotMessages.BTN_CONVERTER)) return startConvertFsm(chatId, data);
        if (text.equals(BotMessages.BTN_BTC_MAIN))  return handleBtc(chatId);
        if (text.equals(BotMessages.BTN_SUBSCRIBE)) return handleTier(chatId, isAdmin);
        if (text.equals(BotMessages.BTN_LK))        return enterLk(chatId, data);
        if (text.equals(BotMessages.BTN_HELP))      return msg(chatId, formatter.buildHelpText());

        String upper = text.toUpperCase().trim();
        if (upper.matches("[A-Z]{3}"))   return handleCurrencyRequest(upper, chatId);
        if (upper.contains("ДОЛЛАР"))    return handleCurrencyRequest("USD", chatId);
        if (upper.contains("ЕВРО"))      return handleCurrencyRequest("EUR", chatId);
        if (upper.contains("ЮАНЬ"))      return handleCurrencyRequest("CNY", chatId);

        return msg(chatId, formatter.buildUnknownInputText());
    }

    // ==================== ЛК — Личный кабинет ====================

    /**
     * Switches the user to LK mode and sends the personal account menu.
     *
     * @param chatId sender's chat ID
     * @param data   FSM state; {@link UserMode} is set to {@link UserMode#LK}
     * @return LK header message with the LK reply keyboard
     */
    public SendMessage enterLk(long chatId, UserConversationData data) {
        data.setMode(UserMode.LK);
        Tier tier = subscriptionService.getActiveTier(chatId);
        return msg(chatId, BotMessages.LK_HEADER, formatter.buildLkKeyboard(tier));
    }

    /**
     * Routes reply-keyboard button presses while the user is in LK mode.
     *
     * @param text    button label pressed
     * @param chatId  sender's chat ID
     * @param data    FSM state; may be modified (mode set to MAIN on back button)
     * @param isAdmin whether the caller is the admin user
     * @return response message to send back
     */
    private SendMessage handleLkText(String text, long chatId, UserConversationData data, boolean isAdmin) {
        switch (text) {
            case BotMessages.BTN_BALANCE   -> { return handleLkBalance(chatId); }
            case BotMessages.BTN_STARS_SUB -> { return handleTier(chatId, isAdmin); }
            case BotMessages.BTN_NEWS      -> { return handleNews(chatId); }
            case BotMessages.BTN_AI_DIGEST -> { return handleAiDigest(chatId); }
            case BotMessages.BTN_SIGNALS   -> { return handleSignal(chatId, data); }
            case BotMessages.BTN_TRADE_BOT -> { return tradeHandler.enterTradeBotSection(chatId, data); }
            case BotMessages.BTN_HELP      -> { return msg(chatId, BotMessages.LK_HELP); }
            case BotMessages.BTN_BACK -> {
                data.setMode(UserMode.MAIN);
                return msg(chatId, formatter.buildStartText(null), formatter.buildMainKeyboard(isAdmin));
            }
            default -> { return msg(chatId, formatter.buildUnknownInputText()); }
        }
    }

    /**
     * Builds and returns the LK balance card showing tier, expiry date, and Stars spent.
     *
     * @param chatId sender's chat ID
     * @return formatted balance message
     */
    private SendMessage handleLkBalance(long chatId) {
        Tier tier = subscriptionService.getActiveTier(chatId);
        String expires = subscriptionService.getActiveSubscription(chatId)
                .map(sub -> sub.getExpiresAt().format(DATE_FMT))
                .orElse("—");
        int paid = subscriptionService.getActiveSubscription(chatId)
                .map(sub -> sub.getAmountPaid().intValue())
                .orElse(0);
        return msgHtml(chatId, formatter.buildLkBalance(tier, expires, paid));
    }

    // ==================== TIER 1: Новости по запросу ====================

    /**
     * Fetches the latest crypto news and returns them with a Russian translation, a short
     * AI-generated analysis, and — where relevant — a personalised impact line, one block per
     * headline (phase 7). Requires TIER 1 access; returns an access-denied message otherwise.
     * <p>
     * The impact line is matched against the user's currently held TIER 3 exchange positions
     * if they have autotrading connected, or their watchlist otherwise — see
     * {@link #resolveNewsAssets}. Always fetches fresh headlines; translation/analysis per
     * item is cached by {@code AiAnalysisService} across all users.
     *
     * @param chatId sender's chat ID
     * @return news message or access-denied / unavailability message
     */
    public SendMessage handleNews(long chatId) {
        if (!subscriptionService.hasAccess(chatId, Tier.TIER_1)) {
            return msg(chatId, BotMessages.NEWS_TIER_REQUIRED);
        }
        List<NewsService.NewsItem> items = newsService.getTopNewsItems();
        if (items.isEmpty()) {
            return msg(chatId, BotMessages.NEWS_HEADER + BotMessages.NEWS_UNAVAILABLE);
        }

        Set<String> assets = resolveNewsAssets(chatId);

        StringBuilder sb = new StringBuilder(BotMessages.NEWS_HEADER);
        for (NewsService.NewsItem item : items) {
            AiAnalysisService.NewsAnalysis analysis =
                    aiAnalysisService.translateAndAnalyzeNews(item.guid(), item.title(), item.source());
            String impact = analysis == null ? null
                    : aiAnalysisService.generateNewsImpact(analysis.summary(), assets);
            sb.append(formatter.buildNewsItemBlock(item, analysis, impact));
        }
        return msg(chatId, sb.toString());
    }

    /**
     * Decides which asset codes the "📰 Новости" impact line should be matched against:
     * currently held TIER 3 exchange positions if the user has autotrading connected
     * (even if that happens to be an empty list right now), otherwise their watchlist.
     *
     * @param chatId sender's chat ID
     * @return uppercase asset codes; may be empty (impact line is simply omitted then)
     */
    private Set<String> resolveNewsAssets(long chatId) {
        if (tradeService.getCredentials(chatId).isPresent()) {
            return tradeService.getOpenPositions(chatId).stream()
                    .map(ExchangeService.Position::coin)
                    .collect(Collectors.toSet());
        }
        return watchlistService.get(chatId);
    }

    // ==================== TIER 2: AI-сводка по запросу ====================

    /**
     * Generates and returns an on-demand AI market digest.
     * Requires TIER 2 access; returns an access-denied message otherwise.
     *
     * @param chatId sender's chat ID
     * @return AI digest message or access-denied / error message
     */
    private SendMessage handleAiDigest(long chatId) {
        if (!subscriptionService.hasAccess(chatId, Tier.TIER_2)) {
            return msg(chatId, BotMessages.DIGEST_TIER_REQUIRED);
        }
        try {
            String marketData = marketDataService.getMarketSnapshot();
            String news = newsService.getTopNews();
            String digest = aiAnalysisService.generateMorningDigest(marketData, news);
            return msg(chatId, digest);
        } catch (Exception e) {
            log.error("On-demand digest error for user {}: {}", chatId, e.getMessage(), e);
            return msg(chatId, BotMessages.DIGEST_ERROR);
        }
    }

    // ==================== FEATURE HANDLERS ====================

    /**
     * Looks up a CBR exchange rate by currency code and returns the formatted rate card.
     * Returns a "not found" message if the code is unknown.
     *
     * @param code   currency code to look up (e.g. "USD", "EUR")
     * @param chatId sender's chat ID
     * @return rate card or not-found message
     */
    public SendMessage handleCurrencyRequest(String code, long chatId) {
        try {
            CurrencyModel currency = currencyService.getCurrency(code);
            return msgHtml(chatId, formatter.buildRateCard(currency));
        } catch (Exception e) {
            log.warn("Currency not found: {} for user {}", code, chatId);
            return msg(chatId, formatter.buildCurrencyNotFoundText(code));
        }
    }

    /**
     * Returns the full list of CBR currencies with an inline selection keyboard.
     * Bound to {@code /list} only — the "📊 Курсы" button uses {@link #handleWatchlistRates}
     * instead, showing just the user's favourites.
     *
     * @param chatId sender's chat ID
     * @return currency list message with inline keyboard, or an error message
     */
    private SendMessage handleList(long chatId) {
        try {
            return msg(chatId, formatter.buildCurrencyList(currencyService.getCurrencyList()),
                    formatter.buildRatesKeyboard());
        } catch (IOException e) {
            log.error("API unavailable for user {}: {}", chatId, e.getMessage(), e);
            return msg(chatId, BotMessages.LIST_ERROR);
        }
    }

    // ==================== WATCHLIST (ИЗБРАННЫЕ ВАЛЮТЫ) ====================

    /**
     * Returns the personalised "Курсы" view: live CoinGecko prices for the user's watchlist
     * coins, or a nudge to pick some via {@link BotMessages#BTN_WATCHLIST_EDIT} if the
     * watchlist is empty. Attaches {@link MessageFormatter#buildWatchlistRatesKeyboard()} —
     * just the favourites-editor button, not the unrelated fiat lookup grid.
     * <p>
     * Each coin is fetched independently (CoinGecko has no multi-coin variant of the endpoint
     * this bot already uses elsewhere) and cached per coin by {@code CryptoService}; a single
     * failing coin is skipped rather than failing the whole view.
     *
     * @param chatId sender's chat ID
     * @return watchlist rates card, empty-watchlist nudge, or an error message
     */
    public SendMessage handleWatchlistRates(long chatId) {
        Set<String> watchlist = watchlistService.get(chatId);
        if (watchlist.isEmpty()) {
            return msg(chatId, BotMessages.WATCHLIST_EMPTY, formatter.buildWatchlistRatesKeyboard());
        }
        List<CryptoPriceModel> rates = new ArrayList<>();
        for (String code : watchlist) {
            try {
                CryptoPriceModel model = cryptoService.getCryptoPrice(cryptoService.coinGeckoId(code));
                model.setSymbol(code);
                rates.add(model);
            } catch (Exception e) {
                log.warn("Watchlist coin {} unavailable for user {}: {}", code, chatId, e.getMessage());
            }
        }
        if (rates.isEmpty()) {
            return msg(chatId, BotMessages.LIST_ERROR, formatter.buildWatchlistRatesKeyboard());
        }
        return msgHtml(chatId, formatter.buildWatchlistRates(rates), formatter.buildWatchlistRatesKeyboard());
    }

    /**
     * Opens the favourites selection keyboard as a fresh message, pre-checked with the user's
     * current watchlist. Used by the "⭐ Настроить избранное" button — as opposed to the
     * onboarding prompt on {@code /start}, {@link ConversationState} is left at {@code IDLE}
     * here, so {@code CurrencyBot} knows "Готово" should just refresh the rates view.
     *
     * @param chatId sender's chat ID
     * @return favourites selection message
     */
    public SendMessage openWatchlistEditor(long chatId) {
        return msg(chatId, BotMessages.WATCHLIST_PICK_PROMPT,
                formatter.buildWatchlistKeyboard(watchlistService.get(chatId)));
    }

    /**
     * Toggles {@code code} in the user's watchlist and returns the refreshed keyboard.
     * Called by {@code CurrencyBot} for every tap on the favourites keyboard; the caller
     * applies the result as an in-place keyboard-only edit, so the surrounding message text
     * (rates or onboarding prompt) is left untouched until "Готово" is pressed.
     *
     * @param chatId sender's chat ID
     * @param code   currency code to toggle (already uppercase)
     * @return refreshed favourites keyboard
     */
    public InlineKeyboardMarkup handleWatchlistToggle(long chatId, String code) {
        return formatter.buildWatchlistKeyboard(watchlistService.toggle(chatId, code));
    }

    /**
     * Builds the confirmation text for finishing the onboarding watchlist step.
     * The caller ({@code CurrencyBot}) applies this as an edit to the onboarding prompt
     * message, clearing its inline keyboard, then sends {@link #resumeMainMenu} separately
     * since a reply keyboard cannot be attached via message edit.
     *
     * @param chatId sender's chat ID
     * @return confirmation message with an empty inline keyboard
     */
    public SendMessage handleWatchlistDoneOnboarding(long chatId) {
        Set<String> selected = watchlistService.get(chatId);
        String list = selected.isEmpty() ? "пока пусто" : String.join(", ", selected);
        return msg(chatId, BotMessages.WATCHLIST_SAVED.replace("{list}", list), formatter.emptyKeyboard());
    }

    /**
     * Returns the main menu message (greeting + reply keyboard) — used to hand control back
     * to the user after the watchlist onboarding step finishes.
     *
     * @param chatId  sender's chat ID
     * @param isAdmin whether the caller is the admin user
     * @return main menu message
     */
    public SendMessage resumeMainMenu(long chatId, boolean isAdmin) {
        return msg(chatId, formatter.buildStartText(null), formatter.buildMainKeyboard(isAdmin));
    }

    /**
     * Handles a one-shot conversion command entered as {@code /convert 100 USD RUB}.
     * Parses amount, from-currency, and to-currency from the argument string.
     *
     * @param arg    the argument string after "/convert " (e.g. "100 USD RUB")
     * @param chatId sender's chat ID
     * @return conversion result or a format/error message
     */
    public SendMessage handleConvert(String arg, Long chatId) {
        String[] parts = arg.split("\\s+");
        if (parts.length != 3) return msg(chatId, BotMessages.CONVERT_FORMAT_ERROR);
        try {
            double amount = Double.parseDouble(parts[0]);
            String from = parts[1].toUpperCase();
            String to   = parts[2].toUpperCase();
            double result = currencyService.convertCurrency(amount, from, to);
            return msgHtml(chatId, formatter.buildConvertResult(amount, from, result, to));
        } catch (NumberFormatException e) {
            return msg(chatId, BotMessages.CONVERT_AMOUNT_ERROR);
        } catch (Exception e) {
            log.error("API unavailable for user {}: {}", chatId, e.getMessage(), e);
            return msg(chatId, BotMessages.CONVERT_ERROR);
        }
    }

    /**
     * Returns the current Bitcoin price card (price in RUB and USD, 24h change).
     *
     * @param chatId sender's chat ID
     * @return BTC price card or an error message
     */
    public SendMessage handleBtc(long chatId) {
        try {
            CryptoPriceModel model = cryptoService.getCryptoPrice("bitcoin");
            model.setSymbol(BotMessages.CALLBACK_BTC);
            return msgHtml(chatId, formatter.buildCryptoCard(model));
        } catch (IOException e) {
            log.error("API unavailable for user {}: {}", chatId, e.getMessage(), e);
            return msg(chatId, BotMessages.BTC_ERROR);
        }
    }

    /**
     * Advances the multi-step converter FSM by one state.
     * <p>
     * State transitions:
     * {@code AWAIT_AMOUNT} → validates amount → {@code AWAIT_FROM}<br>
     * {@code AWAIT_FROM} → stores currency → {@code AWAIT_TO}<br>
     * {@code AWAIT_TO} → performs conversion → resets to IDLE, shows result
     *
     * @param text   user input for the current FSM step
     * @param chatId sender's chat ID
     * @param data   FSM state; mutated as the flow progresses
     * @return next prompt message or conversion result
     */
    public SendMessage handleFsmInput(String text, long chatId, UserConversationData data) {
        return switch (data.getState()) {
            case AWAIT_AMOUNT -> {
                try {
                    double amount = Double.parseDouble(text);
                    data.setAmount(amount);
                    data.setState(ConversationState.AWAIT_FROM);
                    yield msg(chatId, BotMessages.CONVERT_AWAIT_FROM, formatter.buildConvertKeyboard());
                } catch (NumberFormatException e) {
                    yield msg(chatId, BotMessages.CONVERT_AMOUNT_INVALID);
                }
            }
            case AWAIT_FROM -> {
                data.setFromCurrency(text);
                data.setState(ConversationState.AWAIT_TO);
                yield msg(chatId, BotMessages.CONVERT_AWAIT_TO, formatter.buildConvertKeyboard());
            }
            case AWAIT_TO -> {
                double amount = data.getAmount();
                String from   = data.getFromCurrency();
                try {
                    double result = currencyService.convertCurrency(amount, from, text);
                    userStateService.reset(chatId);
                    yield msgHtml(chatId, formatter.buildConvertResult(amount, from, result, text));
                } catch (IOException e) {
                    log.error("API unavailable for user {}: {}", chatId, e.getMessage(), e);
                    yield msg(chatId, BotMessages.CONVERT_ERROR);
                }
            }
            default -> msg(chatId, BotMessages.CONVERT_FSM_ERROR);
        };
    }

    /**
     * Enters the SIGNALS mode and shows the coin selection reply keyboard.
     * Requires TIER 2 access; returns an access-denied message otherwise.
     * <p>
     * The current mode is saved to {@link UserConversationData#getPreviousMode()} before
     * switching, so that the back button in {@link #handleSignalsText} can restore it correctly
     * regardless of whether the user entered from LK or the main menu.
     *
     * @param chatId sender's chat ID
     * @param data   FSM state; {@code previousMode} is saved, mode is set to {@link UserMode#SIGNALS}
     * @return signal prompt with coin reply keyboard, or access-denied message
     */
    public SendMessage handleSignal(long chatId, UserConversationData data) {
        if (!subscriptionService.hasAccess(chatId, Tier.TIER_2)) {
            return msg(chatId, BotMessages.SIGNAL_TIER_REQUIRED);
        }
        data.setPreviousMode(data.getMode());
        data.setMode(UserMode.SIGNALS);
        return msg(chatId, BotMessages.SIGNAL_PROMPT, formatter.buildSignalReplyKeyboard());
    }

    /**
     * Routes button presses while the user is in SIGNALS mode.
     * <p>
     * Any text matching a supported coin symbol triggers a TA analysis.
     * {@link BotMessages#BTN_BACK} restores the mode that was active before SIGNALS was entered
     * (either {@link UserMode#LK} or {@link UserMode#MAIN}) and shows the corresponding keyboard.
     *
     * @param text    button label pressed
     * @param chatId  sender's chat ID
     * @param data    FSM state; on back, mode is restored from {@code previousMode} and cleared
     * @param isAdmin whether the caller is the admin
     * @return signal card, restored keyboard, or unknown-coin message
     */
    public SendMessage handleSignalsText(String text, long chatId, UserConversationData data, boolean isAdmin) {
        if (text.equals(BotMessages.BTN_BACK)) {
            UserMode returnTo = data.getPreviousMode() != null ? data.getPreviousMode() : UserMode.MAIN;
            data.setMode(returnTo);
            data.setPreviousMode(null);
            if (returnTo == UserMode.LK) {
                Tier tier = subscriptionService.getActiveTier(chatId);
                return msg(chatId, BotMessages.LK_HEADER, formatter.buildLkKeyboard(tier));
            }
            return msg(chatId, formatter.buildStartText(null), formatter.buildMainKeyboard(isAdmin));
        }
        if (taSignalService.supportedCoins().contains(text.toUpperCase())) {
            return handleSignalForCoin(chatId, text.toUpperCase());
        }
        return msg(chatId, BotMessages.SIGNAL_UNKNOWN_COIN);
    }

    /**
     * Runs a full TA analysis for the given coin symbol and returns the signal card
     * with an AI-generated plain-language explanation.
     * <p>
     * Requires TIER 2 access. If AI is unavailable, the signal card is still returned
     * without the explanation (graceful degradation in {@code AiAnalysisService}).
     *
     * @param chatId sender's chat ID
     * @param symbol coin symbol to analyse (e.g. "BTC", "ETH")
     * @return signal card with Markdown parse mode, or access-denied / error message
     */
    public SendMessage handleSignalForCoin(long chatId, String symbol) {
        if (!subscriptionService.hasAccess(chatId, Tier.TIER_2)) {
            return msg(chatId, BotMessages.SIGNAL_TIER_REQUIRED);
        }
        try {
            TaSignalService.SignalResult result = taSignalService.analyzeBySymbol(symbol);
            String aiExplanation = aiAnalysisService.generateSignalExplanation(result);
            SendMessage m = msg(chatId, formatter.buildSignalCard(result, aiExplanation));
            m.setParseMode("Markdown");
            return m;
        } catch (IllegalArgumentException e) {
            return msg(chatId, BotMessages.SIGNAL_UNKNOWN_COIN);
        } catch (Exception e) {
            log.error("TA signal error for user {}: {}", chatId, e.getMessage(), e);
            return msg(chatId, BotMessages.SIGNAL_ERROR);
        }
    }

    /**
     * Returns the subscription tier card showing the user's current tier and all available tiers.
     *
     * @param chatId  sender's chat ID
     * @param isAdmin unused here, kept for signature symmetry with other handlers
     * @return tier card with purchase keyboard
     */
    private SendMessage handleTier(long chatId, boolean isAdmin) {
        Tier tier = subscriptionService.getActiveTier(chatId);
        return msgHtml(chatId, formatter.buildTierCard(tier), formatter.buildTierKeyboard());
    }

    // ==================== BUILDERS ====================

    private SendMessage msg(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        return message;
    }

    private SendMessage msg(long chatId, String text, ReplyKeyboard keyboard) {
        SendMessage message = msg(chatId, text);
        message.setReplyMarkup(keyboard);
        return message;
    }

    /**
     * Like {@link #msg(long, String)}, but sets {@code ParseMode.HTML} — used for the panel-style
     * cards ({@link MessageFormatter#buildRateCard}, {@code buildCryptoCard}, etc.) that rely on
     * {@code <b>}/{@code <pre>} tags for their layout.
     */
    private SendMessage msgHtml(long chatId, String text) {
        SendMessage message = msg(chatId, text);
        message.setParseMode("HTML");
        return message;
    }

    private SendMessage msgHtml(long chatId, String text, ReplyKeyboard keyboard) {
        SendMessage message = msgHtml(chatId, text);
        message.setReplyMarkup(keyboard);
        return message;
    }

    private SendMessage startConvertFsm(long chatId, UserConversationData data) {
        data.setState(ConversationState.AWAIT_AMOUNT);
        return msg(chatId, BotMessages.CONVERT_AWAIT_AMOUNT, formatter.buildCancelKeyboard());
    }

    private SendMessage showCurseKeyboard(long chatId) {
        return msg(chatId, BotMessages.CURSE_KEYBOARD_PROMPT, formatter.buildRatesKeyboard());
    }
}
