package com.polybezev.currencybot.formatter;

public final class BotMessages {
    private BotMessages() {}

    // ==================== КНОПКИ МЕНЮ ====================
    // Используются и в сборщиках клавиатур (MessageFormatter), и в роутинге (CommandHandler/AdminCommandHandler).
    // Изменение строки здесь автоматически обновляет оба места.

    // Главное меню
    public static final String BTN_RATES     = "📊 Курсы";
    public static final String BTN_CONVERTER = "Конвертер";
    public static final String BTN_BTC_MAIN  = "₿ BTC";
    public static final String BTN_SIGNALS   = "📈 Сигналы";
    public static final String BTN_SUBSCRIBE = "💎 Подписка";
    public static final String BTN_LK        = "👤 ЛК";
    public static final String BTN_ADMIN     = "🔧 Админ";

    // Личный кабинет
    public static final String BTN_BALANCE   = "💰 Баланс";
    public static final String BTN_STARS_SUB = "⭐ Подписка";
    public static final String BTN_NEWS      = "📰 Новости";
    public static final String BTN_AI_DIGEST = "📊 AI-сводка";
    public static final String BTN_TRADE_BOT = "🤖 Торговый бот";
    public static final String BTN_HELP      = "❓ Помощь";
    public static final String BTN_BACK      = "◀️ Назад";

    // Админ-панель
    public static final String BTN_USERS = "👥 Пользователи";
    public static final String BTN_GRANT = "🔑 Выдать доступ";
    public static final String BTN_BANS  = "🚫 Баны";

    // ==================== CALLBACK DATA ====================

    /** Callback-data кнопки «Bitcoin» на inline-клавиатуре. */
    public static final String CALLBACK_BTC = "BTC";

    /** Префикс callback-data переключения кода в watchlist (например {@code WATCHLIST_TOGGLE_USD}). */
    public static final String CALLBACK_WATCHLIST_TOGGLE_PREFIX = "WATCHLIST_TOGGLE_";

    /** Callback-data кнопки «Готово» на клавиатуре выбора избранных валют. */
    public static final String CALLBACK_WATCHLIST_DONE = "WATCHLIST_DONE";

    /** Callback-data кнопки «Настроить избранное» под клавиатурой курсов. */
    public static final String CALLBACK_WATCHLIST_EDIT_OPEN = "WATCHLIST_EDIT_OPEN";

    // ==================== ОТМЕНА ВВОДА (универсальная) ====================
    // Единая точка выхода из любого FSM-шага, ожидающего свободный текст (конвертер,
    // admin-грант, настройка биржи TIER 3, вопрос ИИ по портфелю). Кнопка вешается inline
    // на сам промпт; обрабатывается централизованно в CurrencyBot.cancelActiveInput.

    /** Подпись inline-кнопки отмены, показываемой на всех промптах ожидания текста. */
    public static final String BTN_CANCEL_INLINE = "❌ Отмена";

    /** Callback-data кнопки {@link #BTN_CANCEL_INLINE}. */
    public static final String CALLBACK_CANCEL_INPUT = "CANCEL_INPUT";

    // ==================== ОПИСАНИЯ КОМАНД (SetMyCommands в BotInitializer) ====================

    public static final String CMD_START_DESC   = "Запустить бота";
    public static final String CMD_HELP_DESC    = "Все команды и помощь";
    public static final String CMD_CURSE_DESC   = "Курс валюты — пример: /curse USD";
    public static final String CMD_CONVERT_DESC = "Конвертер — пример: /convert 100 USD RUB";
    public static final String CMD_LIST_DESC    = "Список всех валют ЦБ РФ";
    public static final String CMD_BTC_DESC     = "Курс биткоина в ₽ и $";
    public static final String CMD_TIER_DESC    = "Подписки и возможности";
    public static final String CMD_SIGNAL_DESC  = "Торговые сигналы RSI/MACD (TIER 2)";

    // ==================== ПРИВЕТСТВИЕ ====================

    // {name} — имя пользователя из Telegram
    public static final String START_TEXT =
            "👋 Привет, {name}!\n\n" +
            "Я — Currency Bot. Держу тебя в курсе рынка: курсы валют, " +
            "крипта, торговые сигналы и AI-аналитика — всё через кнопки.\n\n" +
            "━━━━━━━━━━━━━━━\n" +
            "🆓  Бесплатно\n" +
            "━━━━━━━━━━━━━━━\n" +
            "📊  Курсы 54 валют по ЦБ РФ — в один тап\n" +
            "💱  Конвертер: любая пара, в том числе BTC ↔ RUB\n" +
            "₿   Bitcoin в ₽ и $, изменение за 24ч\n\n" +
            "━━━━━━━━━━━━━━━\n" +
            "💎  Подписки (Telegram Stars)\n" +
            "━━━━━━━━━━━━━━━\n" +
            "⚡ TIER 1 · 200 ⭐\n" +
            "   Крипто-новости по запросу\n" +
            "   AI-сводка рынка каждое утро в 8:00 МСК\n\n" +
            "📈 TIER 2 · 600 ⭐\n" +
            "   Торговые сигналы RSI / MACD\n" +
            "   AI объясняет каждый сигнал простым языком\n" +
            "   Включает TIER 1\n\n" +
            "🤖 TIER 3 · 1500 ⭐\n" +
            "   Автоторговля через твой биржевой аккаунт\n" +
            "   Включает TIER 1 и TIER 2\n\n" +
            "━━━━━━━━━━━━━━━\n" +
            "Нажми 💎 Подписка — выбери свой уровень.\n" +
            "Или сразу пользуйся бесплатным 👇";

    // ==================== НАВИГАЦИЯ ====================

    public static final String HELP_TEXT =
            "📋 Как пользоваться ботом:\n\n" +
            "📊 Курсы — список всех валют ЦБ РФ + быстрый выбор\n" +
            "Конвертер — перевод любой суммы X → Y\n" +
            "₿ BTC — курс биткоина в ₽ и $\n" +
            "📈 Сигналы — RSI/MACD по крипте (TIER 2)\n" +
            "💎 Подписка — тиры и возможности\n" +
            "👤 ЛК — личный кабинет\n\n" +
            "Просто набери код валюты: USD, EUR, CNY, GBP — без команд.\n\n" +
            "Поддержка: напиши в ЛК → Помощь 👇";

    public static final String CURSE_KEYBOARD_PROMPT = "Выберите валюту:";

    // ==================== ИЗБРАННЫЕ ВАЛЮТЫ (WATCHLIST) ====================

    public static final String BTN_WATCHLIST_EDIT = "⭐ Настроить избранное";

    /** Показывается один раз на /start, пока watchlist пуст — сразу за приветствием. */
    public static final String WATCHLIST_ONBOARDING_PROMPT =
            "\n\n⭐ И ещё: какие монеты тебе интересны?\n" +
            "Отметь нужные — потом в один тап буду показывать именно их курсы.";

    public static final String WATCHLIST_PICK_PROMPT =
            "⭐ Выбери монеты, за которыми хочешь следить:\n\n" +
            "Нажимай, чтобы отметить/снять — затем жми «Готово».";

    // {list} — выбранные коды через запятую, либо "пока пусто"
    public static final String WATCHLIST_SAVED =
            "✅ Избранное сохранено: {list}\n\n" +
            "Изменить можно в любой момент: 📊 Курсы → ⭐ Настроить избранное.";

    // Заголовок панели курсов watchlist; строки монет собираются в MessageFormatter.buildWatchlistRates
    // в моноширинном <pre>-блоке. Требует ParseMode.HTML.
    public static final String WATCHLIST_RATES_HEADER = "⭐ <b>Твои избранные монеты</b>\n";

    public static final String WATCHLIST_EMPTY =
            "⭐ Список избранного пуст.\n\n" +
            "Нажми ⭐ Настроить избранное ниже — выбери монеты, за которыми хочешь следить.";

    // ==================== ОШИБКИ И ПОДСКАЗКИ ====================

    public static final String UNKNOWN_INPUT =
            "🤔 Не понял. Напиши код валюты: USD, EUR, CNY — или выбери действие в меню 👇";

    public static final String UNKNOWN_COMMAND =
            "Такой команды нет. Используй кнопки меню или нажми ❓ Помощь";

    // {code} — код, введённый пользователем
    public static final String CURRENCY_NOT_FOUND =
            "🔍 Валюта {code} не найдена.\n\n" +
            "Коды пишутся латиницей: USD, EUR, CNY.\n" +
            "Полный список — нажми 📊 Курсы";

    // ==================== КОНВЕРТЕР ====================

    public static final String CONVERT_FORMAT_ERROR =
            "Формат: сумма, затем FROM и TO.\nПример: 100 USD RUB";

    public static final String CONVERT_AMOUNT_ERROR =
            "Сумма должна быть числом. Пример: 100 USD RUB";

    public static final String CONVERT_AWAIT_AMOUNT =
            "💱 Конвертер\n\nВведите сумму:\nНапример: 100, 1500, 0.5";

    public static final String CONVERT_AMOUNT_INVALID =
            "💱 Конвертер\n\nНужно число — например, 100 или 1500.5\nПопробуйте ещё раз:";

    public static final String CONVERT_AWAIT_FROM =
            "💱 Конвертер\n\nИз какой валюты конвертируем?\nВыберите или введите код:";

    public static final String CONVERT_AWAIT_TO =
            "💱 Конвертер\n\nВ какую валюту переводим?\nВыберите или введите код:";

    public static final String CONVERT_ERROR =
            "⚠️ Не удалось получить курс — попробуйте чуть позже.\n" +
            "Или запустите конвертер заново через меню.";

    public static final String CONVERT_FSM_ERROR =
            "Что-то пошло не так. Начните заново — нажмите Конвертер";

    // ==================== КАРТОЧКИ (ФОРМАТИРОВАННЫЙ ВЫВОД) ====================

    // {from_amount} {from} — источник; {to_amount} {to} — результат; {source} — ЦБ РФ или
    // CoinGecko. Требует ParseMode.HTML.
    public static final String CONVERT_RESULT =
            "💱 <b>{from_amount} {from} = {to_amount} {to}</b>\n\nКурс: {source}";

    // Панельный стиль (2026-08-03): жирный заголовок, разделитель, поля в <pre>-блоке для
    // моноширинного выравнивания колонок. Требует ParseMode.HTML при отправке.
    // {flag} {code} {name} — заголовок; {value_line} — курс, уже с учётом номинала
    // (например "6.23 ₽ (за 10 JPY)"); {change_line} — стрелка+процент или "—"; {date} — дата ЦБ
    public static final String RATE_CARD =
            "{flag} <b>{code}</b> — {name}\n" +
            "▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔\n" +
            "<pre>Курс          {value_line}\n" +
            "Изменение     {change_line}\n" +
            "Обновлено     ЦБ РФ · {date}</pre>";

    // {symbol} — код монеты (BTC, ETH...); {rub} — цена в рублях; {usd} — цена в долларах;
    // {arrow} ▲/▼; {pct} — изменение за 24ч; {date} — дата. Требует ParseMode.HTML.
    public static final String CRYPTO_CARD =
            "₿ <b>{symbol}</b>\n" +
            "▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔\n" +
            "<pre>В рублях      {rub} ₽\n" +
            "В долларах    {usd} $\n" +
            "За 24ч        {arrow} {pct}%\n" +
            "Обновлено     CoinGecko · {date}</pre>";

    // ==================== ОШИБКИ API ====================

    public static final String BTC_ERROR =
            "⚠️ Не удалось загрузить курс биткоина — CoinGecko сейчас недоступен.\n" +
            "Попробуйте через пару минут.";

    public static final String LIST_ERROR =
            "⚠️ Не удалось загрузить список валют — ЦБ РФ сейчас не отвечает.\n" +
            "Попробуйте через минуту.";

    // ==================== СПИСОК ВАЛЮТ ====================

    public static final String LIST_HEADER = "Доступные валюты ЦБ РФ:\n\n";

    // {count} — кол-во валют; {date} — дата фида ЦБ
    public static final String LIST_FOOTER =
            "\nВсего: {count} валют\nДанные на: {date}\n\n" +
            "💡 Выбери валюту кнопкой или введи её код: USD, EUR...";

    // ==================== ПОДПИСКА ====================

    // {currentTierLabel} — название текущего тира пользователя. Требует ParseMode.HTML.
    public static final String TIER_CARD =
            "⭐ Ваша подписка: <b>{currentTierLabel}</b>\n" +
            "▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔\n\n" +
            "🆓 <b>FREE</b> — бесплатно\n" +
            "• Курсы валют по ЦБ РФ\n" +
            "• Конвертер валют\n" +
            "• Курс биткоина в ₽ и $\n\n" +
            "⚡ <b>TIER 1</b> — AI-прогнозы\n" +
            "• Новости по запросу (топ крипто)\n" +
            "• Утренняя AI-сводка рынка (8:00 МСК)\n" +
            "• Включает всё из FREE\n\n" +
            "📈 <b>TIER 2</b> — Торговые сигналы\n" +
            "• TA-сигналы RSI/MACD + AI-объяснение\n" +
            "• Включает всё из TIER 1\n\n" +
            "🤖 <b>TIER 3</b> — Автоторговля\n" +
            "• Автоматические сделки на бирже\n" +
            "• Безопасное хранение API-ключей\n" +
            "• Включает всё из TIER 2";

    // {requiredTier} — нужный тир (пример: TIER 1)
    public static final String ACCESS_DENIED =
            "🔒 Это доступно в {requiredTier}.\nНажми 💎 Подписка → выбери тир.";

    // ==================== ОПЛАТА — TELEGRAM STARS ====================

    // {tierName} — название тира (пример: TIER 1 — AI-прогнозы)
    public static final String STARS_SUCCESS =
            "✅ Оплата прошла — добро пожаловать в {tierName}!\n\n" +
            "Новые функции уже доступны. Открой 👤 ЛК → посмотри что появилось.";

    public static final String STARS_ERROR =
            "Оплата не прошла. Попробуйте ещё раз через 💎 Подписка.";

    /** Описание инвойса в Telegram Stars — показывается пользователю в окне оплаты. */
    public static final String INVOICE_DESCRIPTION = "Подписка на 30 дней";

    // ==================== ЛИЧНЫЙ КАБИНЕТ ====================

    public static final String LK_HEADER =
            "👤 Личный кабинет\n\nВыбери нужный раздел 👇";

    // Панельный стиль. {tier} — тир, {expires} — дата истечения или "—",
    // {paid} — уплачено Stars. Требует ParseMode.HTML.
    public static final String LK_BALANCE =
            "💰 <b>Баланс</b>\n" +
            "▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔\n" +
            "<pre>Подписка      {tier}\n" +
            "Действует до  {expires}\n" +
            "Уплачено      {paid} ⭐</pre>\n\n" +
            "Для продления или смены тира — 💎 Подписка";

    public static final String LK_HELP =
            "❓ Помощь\n\n" +
            "По всем вопросам пишите: @your_support_username\n\n" +
            "Или опишите проблему — и мы ответим в течение суток.";

    public static final String TRADE_BOT_COMING_SOON =
            "🤖 Торговый бот — в разработке. TIER 3 скоро.";

    // ==================== НОВОСТИ (TIER 1+) ====================

    public static final String NEWS_HEADER = "📰 Топ новости по крипте:\n\n";

    public static final String NEWS_TIER_REQUIRED =
            "📰 Новости по запросу доступны с TIER 1.\nНажми 💎 Подписка";

    public static final String NEWS_ERROR =
            "⚠️ Не удалось загрузить новости — попробуй позже.";

    /** Returned as the news content when NewsAPI is unavailable; used in digest fallback. */
    public static final String NEWS_UNAVAILABLE = "Новости временно недоступны.";

    // {title}, {translation}, {summary}, {impact} — "🎯 ...\n" или "" (подставляется в Java),
    // {source}. Требует ParseMode.MARKDOWN не нужен — обычный текст.
    public static final String NEWS_ITEM_BLOCK =
            "📰 {title}\n" +
            "🇷🇺 {translation}\n\n" +
            "💡 {summary}\n" +
            "{impact}" +
            "— {source}\n\n";

    // {title}, {source} — показывается вместо NEWS_ITEM_BLOCK, если AI-разбор недоступен
    public static final String NEWS_ITEM_FALLBACK =
            "📰 {title}\n— {source} (AI-разбор временно недоступен)\n\n";

    // ==================== УТРЕННЯЯ СВОДКА (TIER 1+) ====================

    // {marketData} — рыночный снимок, {news} — заголовки новостей
    public static final String DIGEST_FALLBACK =
            "📊 Утренняя сводка\n\n" +
            "🏦 Рынок:\n{marketData}\n\n" +
            "📰 Новости:\n{news}\n\n" +
            "⚠️ AI-анализ временно недоступен.";

    public static final String DIGEST_TIER_REQUIRED =
            "📊 AI-сводка доступна с TIER 2.\nНажми 💎 Подписка";

    public static final String DIGEST_ERROR =
            "⚠️ Не удалось сформировать сводку. Попробуй позже.";

    /** Appended to every AI-generated digest. Requires ParseMode.MARKDOWN. */
    public static final String DIGEST_DISCLAIMER =
            "_⚠️ Не является инвестиционной рекомендацией. " +
            "Материал носит информационный характер._";

    // ==================== AI-ПРОГНОЗ ====================

    // Переменные: {pair}, {direction}, {confidence}, {reasoning}, {date}
    // Требует ParseMode.MARKDOWN при отправке
    public static final String AI_PREDICTION =
            "🤖 AI-прогноз: *{pair}*\n\n" +
            "Направление: {direction}\n" +
            "Уверенность: {confidence}\n\n" +
            "{reasoning}\n\n" +
            "_Дата: {date}_\n" +
            "_⚠️ Не является финансовым советом._";

    // ==================== TA-СИГНАЛ (TIER 2+) ====================

    public static final String SIGNAL_TIER_REQUIRED =
            "📊 Торговые сигналы доступны с TIER 2.\nНажми 💎 Подписка";

    public static final String SIGNAL_PROMPT =
            "📈 Выбери монету для анализа:\n\nRSI(14) + MACD + EMA · скоринг [-3…+3] · данные Binance · 1h свечи";

    public static final String SIGNAL_UNKNOWN_COIN =
            "❓ Монета не поддерживается.\nДоступны: BTC, ETH, SOL, BNB, XRP, DOGE, ADA, AVAX, DOT, LINK, TON, LTC";

    public static final String SIGNAL_ERROR =
            "⚠️ Не удалось получить данные сигнала. Попробуй позже.";

    // Переменные: {asset}, {signal}, {score}, {rsi}, {macd}, {macdSignal}, {ema20}, {ema50}, {aiExplanation}, {date}
    // Требует ParseMode.MARKDOWN при отправке
    public static final String TA_SIGNAL =
            "📈 Сигнал: *{asset}*\n\n" +
            "Итог: *{signal}* (балл: {score}/3)\n" +
            "RSI(14): `{rsi}`\n" +
            "MACD: `{macd}`\n" +
            "Сигн. линия: `{macdSignal}`\n" +
            "EMA20 / EMA50: `{ema20}` / `{ema50}`\n\n" +
            "{aiExplanation}\n\n" +
            "_Дата: {date}_\n" +
            "_⚠️ Не является финансовым советом._";

    // Переменные: {asset}, {prevSignal}, {signal}, {score}, {rsi}, {macd}, {macdSignal}, {ema20}, {ema50}, {aiExplanation}, {date}
    // Требует ParseMode.MARKDOWN при отправке. Используется SignalScheduler для push-уведомлений о смене сигнала.
    public static final String SIGNAL_CHANGE_CARD =
            "🔔 Смена сигнала: *{asset}*\n\n" +
            "{prevSignal} → *{signal}* (балл: {score}/3)\n" +
            "RSI(14): `{rsi}`\n" +
            "MACD: `{macd}`\n" +
            "Сигн. линия: `{macdSignal}`\n" +
            "EMA20 / EMA50: `{ema20}` / `{ema50}`\n\n" +
            "{aiExplanation}\n\n" +
            "_Дата: {date}_\n" +
            "_⚠️ Не является финансовым советом._";

    // ==================== ТОРГОВЫЙ БОТ (TIER 3) ====================

    // Кнопки торгового бота
    public static final String BTN_CONNECT_EXCHANGE = "🔑 Привязать биржу";
    public static final String BTN_ENABLE_TRADING   = "▶️ Включить торговлю";
    public static final String BTN_DISABLE_TRADING  = "⏸ Пауза";
    public static final String BTN_TRADE_HISTORY    = "📋 История сделок";
    public static final String BTN_PORTFOLIO_AI     = "🤖 Портфель (ИИ)";
    public static final String BTN_DELETE_KEYS      = "🗑 Удалить ключи";

    /** Prefix for trade bot exchange selection callbacks (e.g. {@code TRADE_EXCHANGE_BINANCE}). */
    public static final String CALLBACK_TRADE_EXCHANGE_PREFIX = "TRADE_EXCHANGE_";

    // Статус торгового бота — нет ключей. Требует ParseMode.HTML.
    public static final String TRADE_BOT_NO_KEYS =
            "🤖 <b>Торговый бот</b>\n" +
            "▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔\n" +
            "Биржевой аккаунт не привязан.\n\n" +
            "Нажми 🔑 Привязать биржу — введи API key и Secret.\n\n" +
            "⚠️ <b>Безопасность</b>\n" +
            "При создании ключа выбирай только права <b>Spot Trade</b>.\n" +
            "Withdraw (вывод) — никогда не включай: без прав на вывод деньги " +
            "не могут покинуть биржу, даже если ключ утечёт.\n\n" +
            "<i>Рекомендуем также включить IP-ограничение на ключ.</i>";

    // Панельный стиль. {exchange} — название биржи, {status} — 🟢 Включена / ⏸ Приостановлена.
    // Требует ParseMode.HTML.
    public static final String TRADE_BOT_STATUS =
            "🤖 <b>Торговый бот</b>\n" +
            "▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔\n" +
            "<pre>Биржа         {exchange}\n" +
            "Статус        {status}\n" +
            "Стратегия     RSI/MACD, каждые 4ч, балл ≥ 2</pre>";

    // Выбор биржи при настройке
    public static final String TRADE_SELECT_EXCHANGE =
            "🔑 Привязать биржу\n\nВыбери биржу:";

    // {exchange}
    public static final String TRADE_AWAIT_API_KEY =
            "🔑 {exchange}\n\n" +
            "Шаг 1/2 — Введи API Key:\n\n" +
            "_Создай ключ с правами Spot Trade. Withdraw — не включай._";

    public static final String TRADE_AWAIT_SECRET =
            "🔑 Шаг 2/2 — Введи Secret Key:";

    // {exchange}
    public static final String TRADE_SETUP_SUCCESS =
            "✅ Биржа {exchange} подключена!\n\n" +
            "Торговля пока отключена — нажми ▶️ Включить торговлю когда будешь готов.\n\n" +
            "💡 Совет: зайди на биржу и добавь IP-ограничение на ключ для дополнительной защиты.";

    public static final String TRADE_ENABLED  = "✅ Автоторговля включена. Бот будет торговать при сильных сигналах.";
    public static final String TRADE_DISABLED = "⏸ Торговля приостановлена.";
    public static final String TRADE_KEYS_DELETED = "🗑 Ключи удалены. Торговля отключена.";
    public static final String TRADE_NO_HISTORY = "📋 История сделок пуста — сделок пока не было.";

    // {side} BUY/SELL, {coin}, {quantity}, {price}, {status}, {date}
    public static final String TRADE_ORDER_ROW =
            "{side} {coin} · {quantity} @ {price} USDT · {status} · {date}";

    // {count} — число сделок в истории
    public static final String TRADE_HISTORY_HEADER = "📋 История сделок (последние {count}):\n\n";

    // {coin}, {side}, {exchange}
    public static final String TRADE_ALERT_FILLED =
            "✅ Сделка исполнена\n\n" +
            "{side} {coin} на {exchange}\n" +
            "Кол-во: {quantity}\nЦена: {price} USDT";

    // {coin}, {reason}
    public static final String TRADE_ALERT_FAILED =
            "❌ Сделка не исполнена\n\n" +
            "Монета: {coin}\nПричина: {reason}";

    public static final String TRADE_BOT_TIER_REQUIRED =
            "🤖 Торговый бот доступен с TIER 3.\nНажми 💎 Подписка";

    public static final String PORTFOLIO_AI_AWAIT_QUESTION =
            "🤖 Портфель (ИИ)\n\n" +
            "Задай вопрос о своём портфеле и истории сделок — например:\n" +
            "«как дела с моими сделками?» или «стоит ли включить торговлю снова?»";

    public static final String PORTFOLIO_AI_UNAVAILABLE =
            "🤖 ИИ-аналитик сейчас недоступен. Попробуй позже.";

    public static final String PORTFOLIO_AI_DISCLAIMER =
            "\n\n_⚠️ Не является финансовым советом._";

    // ==================== АДМИН-ПАНЕЛЬ ====================

    public static final String ADMIN_HEADER =
            "🔧 Административная панель\n\nВыбери действие:";

    public static final String ADMIN_GRANT_ASK_ID =
            "🔑 Выдать доступ\n\nВведи Telegram chatId пользователя:";

    public static final String ADMIN_GRANT_ASK_TIER =
            "🔑 Выдать доступ\n\nПользователь: {targetId}\n\nВыбери тир:";

    // {targetId}, {tier}
    public static final String ADMIN_GRANT_SUCCESS =
            "✅ {tier} выдан пользователю {targetId}";

    public static final String ADMIN_GRANT_ID_ERROR =
            "❌ chatId должен быть числом. Попробуй снова:";

    public static final String ADMIN_BAN_SUCCESS =
            "🚫 Пользователь {chatId} заблокирован";

    public static final String ADMIN_UNBAN_SUCCESS =
            "✅ Пользователь {chatId} разблокирован";

    // {page}, {total}, {list}
    public static final String ADMIN_USER_LIST =
            "👥 Пользователи (стр. {page}/{total}):\n\n{list}\n\nНажми кнопки для управления";

    public static final String ADMIN_DIGEST_TRIGGERED = "✅ Дайджест запущен";
    public static final String ADMIN_UNKNOWN_CMD       = "Неизвестная команда";
    public static final String ADMIN_PANEL_PROMPT      = "Выбери действие из меню";
    public static final String ADMIN_FSM_UNEXPECTED    = "Неожиданное состояние. Начни заново.";
    public static final String ADMIN_GRANT_TARGET_LOST = "Ошибка: потерян targetId. Начни заново.";

    public static final String ADMIN_GRANT_FORMAT =
            "Формат: /grant <chatId> <TIER_1|TIER_2|TIER_3>";
    public static final String ADMIN_GRANT_UNKNOWN_TIER =
            "Неизвестный тир. Доступны: TIER_1, TIER_2, TIER_3";
    public static final String ADMIN_BAN_FORMAT = "Формат: /ban <chatId>";

    // {reason} — сообщение исключения
    public static final String ADMIN_GRANT_EXEC_ERROR = "Ошибка выдачи: {reason}";

    public static final String ADMIN_BAN_NONE = "🚫 Забаненных пользователей нет.";

    // {count} — число заблокированных; {list} — строки с пользователями
    public static final String ADMIN_BAN_LIST = "🚫 Заблокированные ({count}):\n\n{list}";
}
