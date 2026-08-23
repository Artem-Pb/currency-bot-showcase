package com.polybezev.currencybot.handler;

import com.polybezev.currencybot.formatter.BotMessages;
import com.polybezev.currencybot.model.Tier;
import com.polybezev.currencybot.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.invoices.SendInvoice;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.payments.LabeledPrice;
import org.telegram.telegrambots.meta.api.objects.payments.SuccessfulPayment;
import org.telegram.telegrambots.meta.exceptions.TelegramApiValidationException;

import java.util.List;

/**
 * Handles Telegram Stars payment flow: invoice creation and successful-payment processing.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentHandler {

    private final SubscriptionService subscriptionService;

    /**
     * Builds a Telegram Stars invoice for the given subscription tier.
     * <p>
     * The {@code validate()} method is overridden to skip the {@code providerToken} check —
     * Telegram Stars (currency "XTR") does not require a provider token, but the default
     * {@link SendInvoice#validate()} implementation would reject an empty token.
     *
     * @param chatId recipient's chat ID
     * @param tier   subscription tier to charge for
     * @return configured invoice ready to execute
     */
    public SendInvoice sendInvoice(long chatId, Tier tier) {
        SendInvoice invoice = new SendInvoice() {
            @Override
            public void validate() throws TelegramApiValidationException {
                if (getChatId() == null)
                    throw new TelegramApiValidationException("ChatId empty", this);
                if (getTitle() == null)
                    throw new TelegramApiValidationException("Title empty", this);
                if (getPayload() == null)
                    throw new TelegramApiValidationException("Payload empty", this);
                if (getPrices() == null || getPrices().isEmpty())
                    throw new TelegramApiValidationException("Prices empty", this);
                // providerToken is not validated — XTR (Telegram Stars) does not require one
            }
        };
        invoice.setChatId(chatId);
        invoice.setTitle(tier.label);
        invoice.setDescription(BotMessages.INVOICE_DESCRIPTION);
        invoice.setPayload(tier.name());
        invoice.setProviderToken("");
        invoice.setCurrency("XTR");
        invoice.setPrices(List.of(new LabeledPrice(tier.label, tier.starsPrice)));
        return invoice;
    }

    /**
     * Activates the purchased subscription and returns the success message.
     * <p>
     * The tier is recovered from {@link SuccessfulPayment#getInvoicePayload()},
     * which was set to {@link Tier#name()} when the invoice was created.
     *
     * @param chatId  payer's chat ID
     * @param payment Telegram's successful payment object
     * @return confirmation message with the tier name
     */
    public SendMessage handleSuccessfulPayment(long chatId, SuccessfulPayment payment) {
        Tier tier = Tier.valueOf(payment.getInvoicePayload());
        subscriptionService.activateSubscription(chatId, tier, payment.getTotalAmount());
        log.info("Payment confirmed for user {}: {} ({} XTR)", chatId, tier, payment.getTotalAmount());
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(BotMessages.STARS_SUCCESS.replace("{tierName}", tier.label));
        return message;
    }
}
