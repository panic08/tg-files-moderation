package ru.marthastudios.tgsteamcookiegetter.bot;

import com.opencsv.CSVWriter;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.statement.delete.Delete;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Document;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.marthastudios.tgsteamcookiegetter.callback.AdminCallback;
import ru.marthastudios.tgsteamcookiegetter.callback.UserCallback;
import ru.marthastudios.tgsteamcookiegetter.callback.BackCallback;
import ru.marthastudios.tgsteamcookiegetter.enums.RequestStatus;
import ru.marthastudios.tgsteamcookiegetter.enums.UserRole;
import ru.marthastudios.tgsteamcookiegetter.model.Request;
import ru.marthastudios.tgsteamcookiegetter.model.User;
import ru.marthastudios.tgsteamcookiegetter.model.Withdrawal;
import ru.marthastudios.tgsteamcookiegetter.pojo.OperationBase;
import ru.marthastudios.tgsteamcookiegetter.pojo.Pair;
import ru.marthastudios.tgsteamcookiegetter.property.TelegramBotsProperty;
import ru.marthastudios.tgsteamcookiegetter.repository.RequestRepository;
import ru.marthastudios.tgsteamcookiegetter.repository.UserRepository;
import ru.marthastudios.tgsteamcookiegetter.repository.WithdrawalRepository;
import ru.marthastudios.tgsteamcookiegetter.util.UrlFileDownloaderUtil;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

@Component
@Slf4j
public class TelegramBot extends TelegramLongPollingBot {
    @Autowired
    private TelegramBotsProperty telegramBotsProperty;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RequestRepository requestRepository;
    @Autowired
    private WithdrawalRepository withdrawalRepository;

    @Autowired
    private UrlFileDownloaderUtil urlFileDownloaderUtil;
    @Value("${files.path}")
    private String filesPath;

    private final Set<Long> sendBaseSteps = new HashSet<>();

    private final Map<Long, OperationBase> handleAcceptSendBaseSteps = new HashMap<>();
    private final Map<Long, OperationBase> handleCancelSendBaseSteps = new HashMap<>();

    private final Map<Long, Pair<Integer, Long>> addBalanceSteps = new HashMap<>();
    private final Map<Long, Pair<Integer, Long>> removeBalanceSteps = new HashMap<>();

    private final Map<Long, Pair<Integer, Double>> withdrawalBalanceSteps = new HashMap<>();

    public TelegramBot(TelegramBotsProperty telegramBotsProperty) {
        this.telegramBotsProperty = telegramBotsProperty;
        List<BotCommand> listOfCommands = new ArrayList<>();

        listOfCommands.add(new BotCommand("/start", "\uD83D\uDD04 Перезапустить"));
        listOfCommands.add(new BotCommand("/sendbase", "\uD83D\uDCE4 Отправить базу"));
        listOfCommands.add(new BotCommand("/profile", "\uD83D\uDCBC Профиль"));
        listOfCommands.add(new BotCommand("/support", "\uD83E\uDD1D Поддержка"));
        listOfCommands.add(new BotCommand("/info", "ℹ\uFE0F Информация"));
        listOfCommands.add(new BotCommand("/howtosort", "\uD83D\uDD04 Как сортировать запрос"));

        try {
            this.execute(new SetMyCommands(listOfCommands, new BotCommandScopeDefault(), null));
        } catch (TelegramApiException e) {
            log.warn(e.getMessage());
        }
    }

    @Override
    public String getBotUsername() {
        return telegramBotsProperty.getName();
    }

    @Override
    public String getBotToken() {
        return telegramBotsProperty.getToken();
    }

    @Override
    public void onUpdateReceived(Update update) {
        new Thread(() -> {
            if (update.hasMessage() && update.getMessage().hasText()) {
                long userId = update.getMessage().getFrom().getId();
                long chatId = update.getMessage().getChatId();
                String text = update.getMessage().getText();

                User currentUser = userRepository.findByTelegramUserId(userId);

                if (currentUser == null) {
                    currentUser = userRepository.save(User.builder()
                            .telegramUserId(userId)
                            .role(UserRole.DEFAULT)
                            .registeredAt(System.currentTimeMillis())
                            .balance(0d)
                            .build());
                }

                switch (text) {
                    case "/start" -> {
                        createStartMessage(chatId, currentUser);

                        return;
                    }

                    case "/sendbase", "\uD83D\uDCE4 Отправить базу" -> {
                        createSendBaseMessage(chatId);

                        return;
                    }

                    case "/howtosort", "\uD83D\uDD04 Как сортировать запрос" -> {
                        createHowToSortMessage(chatId);

                        return;
                    }

                    case "/support", "\uD83E\uDD1D Поддержка" -> {
                        createSupportMessage(chatId);

                        return;
                    }

                    case "/profile", "\uD83D\uDCBC Профиль" -> {
                        createProfileMessage(chatId, currentUser, update.getMessage().getFrom().getFirstName());

                        return;
                    }

                    case "/info", "ℹ\uFE0F Информация" -> {
                        createInfoMessage(chatId);

                        return;
                    }

                    case "\uD83C\uDF9B Админ панель" -> {
                        createAdminMessage(chatId, currentUser);

                        return;
                    }
                }

                if (sendBaseSteps.contains(userId)) {
                    InlineKeyboardMarkup backToSendBaseKeyboardMarkup = new InlineKeyboardMarkup();

                    InlineKeyboardButton backToSendBaseButton = InlineKeyboardButton.builder()
                            .callbackData(BackCallback.BACK_TO_SEND_BASE_CALLBACK_DATA)
                            .text("↩\uFE0F Назад")
                            .build();

                    List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();

                    keyboardButtonsRow1.add(backToSendBaseButton);

                    List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

                    rowList.add(keyboardButtonsRow1);

                    backToSendBaseKeyboardMarkup.setKeyboard(rowList);

                    SendMessage sendMessage = SendMessage.builder()
                            .text("❌ Пожалуйста, отправьте <b>корректный</b> файл с вашей базы в формате <code>login:password</code>"
                            )
                            .chatId(chatId)
                            .replyMarkup(backToSendBaseKeyboardMarkup)
                            .parseMode("html")
                            .build();

                    sendMessage(sendMessage);
                    return;
                }

                if (handleAcceptSendBaseSteps.get(userId) != null) {
                    OperationBase operationBase = handleAcceptSendBaseSteps.get(userId);

                    handleAcceptSendBaseSteps.remove(userId);

                    double giveAmount = Double.parseDouble(text);

                    userRepository.updateBalanceByTelegramUserId(
                            userRepository.findBalanceByTelegramUserId(operationBase.getOtherUserId()) + giveAmount,
                            operationBase.getOtherUserId());

                    SendMessage sendMessage = SendMessage.builder()
                            .text("✅ <b>Ваш запрос с базой одобрен!</b>\n\n"
                                    + "Поздравляем! Ваш запрос с базой был успешно одобрен.\n\n"
                                    + "\uD83D\uDCB8 <b>Сумма одобрения:</b> " + giveAmount + " ₽\n\n"
                                    + "Если у вас есть дополнительные вопросы или нужна помощь, не стесняйтесь обращаться. Спасибо за использование наших услуг! \uD83D\uDE80\uD83D\uDCB0"
                            )
                            .chatId(operationBase.getOtherUserId())
                            .parseMode("html")
                            .build();

                    try {
                        execute(sendMessage);
                    } catch (TelegramApiException e) {
                        log.warn(e.getMessage());
                    }

                    File file = new File(requestRepository.findFilePathById(operationBase.getRequestId()));

                    file.delete();

                    requestRepository.updateStatusById(RequestStatus.SUCCESS, operationBase.getRequestId());
                    requestRepository.updateAmountById(giveAmount, operationBase.getRequestId());
                    requestRepository.updateFilePathById(null, operationBase.getRequestId());

                    DeleteMessage deleteMessage1 = DeleteMessage.builder()
                            .chatId(operationBase.getFirstChatId())
                            .messageId(operationBase.getFirstMessageId())
                            .build();

                    DeleteMessage deleteMessage2 = DeleteMessage.builder()
                            .chatId(operationBase.getSecondChatId())
                            .messageId(operationBase.getSecondMessageId())
                            .build();

                    DeleteMessage deleteMessage3 = DeleteMessage.builder()
                            .chatId(chatId)
                            .messageId(update.getMessage().getMessageId())
                            .build();

                    try {
                        execute(deleteMessage1);
                        execute(deleteMessage2);
                        execute(deleteMessage3);

                    } catch (TelegramApiException e) {
                        log.warn(e.getMessage());
                    }
                    return;
                }

                if (handleCancelSendBaseSteps.get(userId) != null) {
                    OperationBase operationBase = handleCancelSendBaseSteps.get(userId);

                    handleCancelSendBaseSteps.remove(userId);

                    SendMessage sendMessage = SendMessage.builder()
                            .text("❌ <b>Ваш запрос с базой отклонен</b>\n\n"
                            + "К сожалению, ваш запрос содержащий базу был отклонен.\n\n"
                            + "\uD83D\uDCC4 <b>Причина отклонения:</b>\n"
                            + text + "\n\n"
                            + "Если у вас есть вопросы или вам нужна дополнительная информация, не стесняйтесь обращаться. <b>Мы готовы вам помочь!</b> \uD83D\uDE80\uD83D\uDC94"
                            )
                            .chatId(operationBase.getOtherUserId())
                            .parseMode("html")
                            .build();

                    try {
                        execute(sendMessage);
                    } catch (TelegramApiException e) {
                        log.warn(e.getMessage());
                    }

                    File file = new File(requestRepository.findFilePathById(operationBase.getRequestId()));

                    file.delete();

                    requestRepository.deleteById(operationBase.getRequestId());

                    DeleteMessage deleteMessage1 = DeleteMessage.builder()
                            .chatId(operationBase.getFirstChatId())
                            .messageId(operationBase.getFirstMessageId())
                            .build();

                    DeleteMessage deleteMessage2 = DeleteMessage.builder()
                            .chatId(operationBase.getSecondChatId())
                            .messageId(operationBase.getSecondMessageId())
                            .build();

                    DeleteMessage deleteMessage3 = DeleteMessage.builder()
                            .chatId(chatId)
                            .messageId(update.getMessage().getMessageId())
                            .build();

                    try {
                        execute(deleteMessage1);
                        execute(deleteMessage2);
                        execute(deleteMessage3);

                    } catch (TelegramApiException e) {
                        log.warn(e.getMessage());
                    }
                    return;
                }

                if (addBalanceSteps.get(userId) != null) {
                    Pair<Integer, Long> stepTelegramUserIdPair =  addBalanceSteps.get(userId);

                    InlineKeyboardMarkup backToAdminKeyboardMarkup = new InlineKeyboardMarkup();

                    InlineKeyboardButton backToAdminButton = InlineKeyboardButton.builder()
                            .callbackData(BackCallback.BACK_TO_ADMIN_CALLBACK_DATA)
                            .text("↩\uFE0F Назад")
                            .build();

                    List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();

                    keyboardButtonsRow1.add(backToAdminButton);

                    List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

                    rowList.add(keyboardButtonsRow1);

                    backToAdminKeyboardMarkup.setKeyboard(rowList);

                    if (stepTelegramUserIdPair.getFirstValue().equals(1)) {
                        long telegramUserId = Long.parseLong(text);

                        if (userRepository.findByTelegramUserId(telegramUserId) == null) {
                            SendMessage sendMessage = SendMessage.builder()
                                    .text("❌ Пользователя с таким идентификатором <b>не существует</b>")
                                    .chatId(chatId)
                                    .parseMode("html")
                                    .replyMarkup(backToAdminKeyboardMarkup)
                                    .build();

                            sendMessage(sendMessage);

                            return;
                        }

                        stepTelegramUserIdPair.setFirstValue(2);
                        stepTelegramUserIdPair.setSecondValue(telegramUserId);

                        addBalanceSteps.put(userId, stepTelegramUserIdPair);

                        SendMessage sendMessage = SendMessage.builder()
                                .text("➕ Введите баланс, который вы хотите выдать пользователю")
                                .chatId(chatId)
                                .replyMarkup(backToAdminKeyboardMarkup)
                                .parseMode("html")
                                .build();

                        sendMessage(sendMessage);
                    } else if (stepTelegramUserIdPair.getFirstValue().equals(2)) {
                        long telegramUserId = stepTelegramUserIdPair.getSecondValue();
                        double balance = Double.parseDouble(text);

                        userRepository.updateBalanceByTelegramUserId(
                                userRepository.findBalanceByTelegramUserId(telegramUserId) + balance,
                                telegramUserId);

                        addBalanceSteps.remove(userId);

                        SendMessage sendMessage = SendMessage.builder()
                                .text("✅ Вы успешно выдали <b>" + balance + " ₽</b> пользователю с идентификатором <code>" + telegramUserId + "</code>")
                                .chatId(chatId)
                                .replyMarkup(backToAdminKeyboardMarkup)
                                .parseMode("html")
                                .build();

                        sendMessage(sendMessage);
                    }
                    return;
                }

                if (removeBalanceSteps.get(userId) != null) {
                    Pair<Integer, Long> stepTelegramUserIdPair =  removeBalanceSteps.get(userId);

                    InlineKeyboardMarkup backToAdminKeyboardMarkup = new InlineKeyboardMarkup();

                    InlineKeyboardButton backToAdminButton = InlineKeyboardButton.builder()
                            .callbackData(BackCallback.BACK_TO_ADMIN_CALLBACK_DATA)
                            .text("↩\uFE0F Назад")
                            .build();

                    List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();

                    keyboardButtonsRow1.add(backToAdminButton);

                    List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

                    rowList.add(keyboardButtonsRow1);

                    backToAdminKeyboardMarkup.setKeyboard(rowList);

                    if (stepTelegramUserIdPair.getFirstValue().equals(1)) {
                        long telegramUserId = Long.parseLong(text);

                        if (userRepository.findByTelegramUserId(telegramUserId) == null) {
                            SendMessage sendMessage = SendMessage.builder()
                                    .text("❌ Пользователя с таким идентификатором <b>не существует</b>")
                                    .chatId(chatId)
                                    .parseMode("html")
                                    .replyMarkup(backToAdminKeyboardMarkup)
                                    .build();

                            sendMessage(sendMessage);

                            return;
                        }

                        stepTelegramUserIdPair.setFirstValue(2);
                        stepTelegramUserIdPair.setSecondValue(telegramUserId);

                        removeBalanceSteps.put(userId, stepTelegramUserIdPair);

                        SendMessage sendMessage = SendMessage.builder()
                                .text("➖ Введите баланс, который вы хотите снять с пользователя")
                                .chatId(chatId)
                                .replyMarkup(backToAdminKeyboardMarkup)
                                .parseMode("html")
                                .build();

                        sendMessage(sendMessage);
                    } else if (stepTelegramUserIdPair.getFirstValue().equals(2)) {
                        long telegramUserId = stepTelegramUserIdPair.getSecondValue();
                        double balance = Double.parseDouble(text);

                        userRepository.updateBalanceByTelegramUserId(
                                userRepository.findBalanceByTelegramUserId(telegramUserId) - balance,
                                telegramUserId);

                        removeBalanceSteps.remove(userId);

                        SendMessage sendMessage = SendMessage.builder()
                                .text("❎ Вы успешно сняли <b>" + balance + " ₽</b> с пользователя с идентификатором <code>" + telegramUserId + "</code>")
                                .chatId(chatId)
                                .replyMarkup(backToAdminKeyboardMarkup)
                                .parseMode("html")
                                .build();

                        sendMessage(sendMessage);
                    }
                    return;
                }

                if (withdrawalBalanceSteps.get(userId) != null) {
                    Pair<Integer, Double> stepAmountPair = withdrawalBalanceSteps.get(userId);

                    InlineKeyboardMarkup backToProfileKeyboardMarkup = new InlineKeyboardMarkup();

                    InlineKeyboardButton backToProfileButton = InlineKeyboardButton.builder()
                            .callbackData(BackCallback.BACK_TO_PROFILE_CALLBACK_DATA)
                            .text("↩\uFE0F Назад")
                            .build();

                    List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();

                    keyboardButtonsRow1.add(backToProfileButton);

                    List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

                    rowList.add(keyboardButtonsRow1);

                    backToProfileKeyboardMarkup.setKeyboard(rowList);

                    if (stepAmountPair.getFirstValue().equals(1)) {
                        double amount = Double.parseDouble(text);

                        if (userRepository.findBalanceByTelegramUserId(userId) < amount) {
                            SendMessage sendMessage = SendMessage.builder()
                                    .text("❌ У вас <b>не хватает</b> баланса для вывода. Укажите сумму меньше")
                                    .chatId(chatId)
                                    .replyMarkup(backToProfileKeyboardMarkup)
                                    .parseMode("html")
                                    .build();

                            sendMessage(sendMessage);

                            return;
                        }

                        stepAmountPair.setFirstValue(2);
                        stepAmountPair.setSecondValue(amount);

                        withdrawalBalanceSteps.put(userId, stepAmountPair);

                        SendMessage sendMessage = SendMessage.builder()
                                .text("\uD83D\uDCB8 <b>Вывести баланс</b>\n\n"
                                + "Отлично! Теперь введите реквизиты для перевода средств. Пожалуйста, укажите метод перевода и необходимые реквизиты в одной строке, следуя формату:\n\n"
                                + "Пример: <code>Номер карты - 1234 5678 9012 3456</code>\n\n"
                                + "После ввода данных, мы обработаем ваш запрос и уведомим вас о статусе операции. <b>Спасибо за ваш выбор!</b>\n\n"
                                + "❓ Если у вас есть вопросы или нужна помощь, не стесняйтесь обращаться. <b>Мы готовы помочь вам!</b> \uD83D\uDE80\uD83D\uDCAC"
                                )
                                .chatId(chatId)
                                .replyMarkup(backToProfileKeyboardMarkup)
                                .parseMode("html")
                                .build();

                        sendMessage(sendMessage);
                    } else if (stepAmountPair.getFirstValue().equals(2)) {
                        double amount = stepAmountPair.getSecondValue();
                        String props = text;

                        withdrawalBalanceSteps.remove(userId);

                        userRepository.updateBalanceByTelegramUserId(userRepository.findBalanceByTelegramUserId(userId) - amount,
                                userId);
                        long withdrawalId = withdrawalRepository.save(Withdrawal.builder().userId(currentUser.getId()).build()).getId();

                        SendMessage sendMessage = SendMessage.builder()
                                .text("✅ <b>Заявка на вывод успешно опубликована!</b>\n\n"
                                        + "Ваш запрос на пополнение баланса на сумму <b>" + amount + " ₽</b> был успешно опубликован.\n\n"
                                        + "\uD83D\uDD10 <b>Ваши реквизиты для перевода:</b>\n"
                                        + "<code>" + props + "</code>\n\n"
                                        + "Теперь ожидайте обработки вашего запроса со стороны администраторов. <b>Мы уведомим вас о статусе операции.</b>\n\n"
                                        + "<b>Благодарим за ваше терпение и использование наших услуг!</b> \uD83D\uDE80\uD83D\uDCB3"
                                        )
                                .chatId(chatId)
                                .parseMode("html")
                                .build();

                        sendMessage(sendMessage);

                        List<Long> adminIdS = userRepository.findAllTelegramUserIdByRole(UserRole.ADMIN);

                        User finalCurrentUser = currentUser;
                        adminIdS.forEach(telegramUserId -> {
                            Date registeredAtDate = new Date(finalCurrentUser.getRegisteredAt());
                            SimpleDateFormat registeredDate = new SimpleDateFormat("dd.MM.yyyy");

                            String formattedRegisteredAtDate = registeredDate.format(registeredAtDate);

                            InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

                            InlineKeyboardButton acceptButton = InlineKeyboardButton.builder()
                                    .callbackData(AdminCallback.ACCEPT_WITHDRAWAL_CALLBACK_DATA + " " + withdrawalId + " " + chatId)
                                    .text("✅ Оплачено (одобрить)")
                                    .build();

                            InlineKeyboardButton ignoreButton = InlineKeyboardButton.builder()
                                    .callbackData(AdminCallback.IGNORE_WITHDRAWAL_CALLBACK_DATA + " " + withdrawalId + " " + chatId)
                                    .text("❌ Отклонить")
                                    .build();

                            List<InlineKeyboardButton> keyboardButtonsRow11 = new ArrayList<>();
                            List<InlineKeyboardButton> keyboardButtonsRow22 = new ArrayList<>();

                            keyboardButtonsRow11.add(acceptButton);
                            keyboardButtonsRow22.add(ignoreButton);

                            List<List<InlineKeyboardButton>> rowList1 = new ArrayList<>();

                            rowList1.add(keyboardButtonsRow11);
                            rowList1.add(keyboardButtonsRow22);

                            inlineKeyboardMarkup.setKeyboard(rowList1);

                            SendMessage withdrawalMessage = SendMessage.builder()
                                    .text("\uD83D\uDCE5 <b>Новая заявка на вывод средств!</b>\n\n"
                                    + "Получена новая заявка на вывод средств:\n\n"
                                    + "\uD83D\uDCB8 <b>Сумма вывода:</b> " + amount + " ₽\n"
                                    + "\uD83D\uDD10 <b>Реквизиты для перевода:</b> <code>" + props + "</code>\n"
                                    + "\uD83E\uDEAA <b>ID:</b> <code>" + finalCurrentUser.getTelegramUserId() + "</code>\n"
                                    + "\uD83D\uDC64 <b>Имя:</b> " + update.getMessage().getFrom().getFirstName() + "\n"
                                    + "\uD83D\uDD5C <b>Регистрация:</b> " + formattedRegisteredAtDate + "\n\n"
                                    + "Проверьте данные, выполните необходимые шаги и уведомите пользователя о статусе заявки.\n\n"
                                    + "<b>Спасибо за ваше внимание и оперативность!</b> \uD83D\uDE80\uD83D\uDC69\u200D\uD83D\uDCBB")
                                    .chatId(telegramUserId)
                                    .replyMarkup(inlineKeyboardMarkup)
                                    .parseMode("html")
                                    .build();

                            sendMessage(withdrawalMessage);
                        });
                    }

                    return;
                }

                sendMessage(SendMessage.builder().text("\uD83D\uDD04 Такой команды не существует. <b>Попробуйте еще раз!</b>")
                        .chatId(chatId).parseMode("html").replyMarkup(getDefaultReplyKeyboardMarkup(currentUser)).build());
            } else if (update.hasCallbackQuery()) {
                long userId = update.getCallbackQuery().getFrom().getId();
                long chatId = update.getCallbackQuery().getMessage().getChatId();
                int messageId = update.getCallbackQuery().getMessage().getMessageId();
                String callbackData = update.getCallbackQuery().getData();

                User currentUser = userRepository.findByTelegramUserId(userId);

                if (currentUser == null) {
                    currentUser = userRepository.save(User.builder()
                            .telegramUserId(userId)
                            .role(UserRole.DEFAULT)
                            .registeredAt(System.currentTimeMillis())
                            .balance(0d)
                            .build());
                }

                switch (callbackData) {
                    case "answer" -> {
                        AnswerCallbackQuery answerCallbackQuery = AnswerCallbackQuery.builder()
                                .callbackQueryId(update.getCallbackQuery().getId())
                                .build();

                        sendAnswerCallbackQuery(answerCallbackQuery);
                        return;
                    }
                    case UserCallback.SEND_BASE_CALLBACK_DATA -> {
                        sendBaseSteps.add(userId);

                        handleSendBaseMessage(chatId, messageId);

                        return;
                    }
                    case BackCallback.BACK_TO_SEND_BASE_CALLBACK_DATA -> {
                        sendBaseSteps.remove(userId);

                        editSendBaseMessage(chatId, messageId);

                        return;
                    }
                    case BackCallback.BACK_TO_FALL_FROM_HANDLE_SEND_BASE_CALLBACK_DATA -> {
                        handleAcceptSendBaseSteps.remove(userId);
                        handleCancelSendBaseSteps.remove(userId);

                        DeleteMessage deleteMessage = DeleteMessage.builder()
                                .chatId(chatId)
                                .messageId(messageId)
                                .build();

                        sendDeleteMessage(deleteMessage);

                        return;
                    }

                    case BackCallback.BACK_TO_PROFILE_CALLBACK_DATA -> {
                        withdrawalBalanceSteps.remove(userId);

                        editProfileMessage(chatId, messageId, currentUser, update.getCallbackQuery().getFrom().getFirstName());
                        return;
                    }

                    case BackCallback.BACK_TO_ADMIN_CALLBACK_DATA -> {
                        addBalanceSteps.remove(userId);
                        removeBalanceSteps.remove(userId);

                        editAdminMessage(chatId, messageId, currentUser);
                        return;
                    }

                    case AdminCallback.ADD_BALANCE_CALLBACK_DATA -> {
                        handleAddBalanceMessage(chatId, messageId, currentUser);
                        return;
                    }

                    case AdminCallback.REMOVE_BALANCE_CALLBACK_DATA -> {
                        handleRemoveBalanceMessage(chatId, messageId, currentUser);
                        return;
                    }

                    case AdminCallback.UNLOAD_DATABASE_CALLBACK_DATA -> {
                        handleUnloadDatabaseMessage(chatId, update.getCallbackQuery().getId(), currentUser);
                        return;
                    }
                    case UserCallback.SEND_WITHDRAWAL_CALLBACK_DATA -> {
                        editWithdrawalMessage(chatId, messageId, userId);
                        return;
                    }
                }

                if (callbackData.contains(AdminCallback.ACCEPT_BASE_CALLBACK_DATA)) {
                    String[] callbackDataSplit = callbackData.split(" ");

                    int userMessageId = Integer.parseInt(callbackDataSplit[4]);
                    long requestId = Long.parseLong(callbackDataSplit[5]);

                    AnswerCallbackQuery answerCallbackQuery = AnswerCallbackQuery.builder()
                            .callbackQueryId(update.getCallbackQuery().getId())
                            .build();

                    sendAnswerCallbackQuery(answerCallbackQuery);

                    InlineKeyboardMarkup backToFallFromHandleSendBaseKeyboardMarkup = new InlineKeyboardMarkup();

                    InlineKeyboardButton backToFallFromHandleSendBaseButton = InlineKeyboardButton.builder()
                            .callbackData(BackCallback.BACK_TO_FALL_FROM_HANDLE_SEND_BASE_CALLBACK_DATA)
                            .text("\uD83D\uDEAB Отменить")
                            .build();

                    List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();

                    keyboardButtonsRow1.add(backToFallFromHandleSendBaseButton);

                    List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

                    rowList.add(keyboardButtonsRow1);

                    backToFallFromHandleSendBaseKeyboardMarkup.setKeyboard(rowList);

                    Integer newMessageId = null;
                    SendMessage sendMessage = SendMessage.builder()
                            .text("✅ <b>Укажите стоимость</b> базы, на которую будет вознагражден пользователь")
                            .chatId(chatId)
                            .replyMarkup(backToFallFromHandleSendBaseKeyboardMarkup)
                            .parseMode("html")
                            .build();


                    try {
                        newMessageId = execute(sendMessage).getMessageId();
                    } catch (TelegramApiException e) {
                        log.warn(e.getMessage());
                    }

                    handleAcceptSendBaseSteps.put(userId, OperationBase.builder()
                            .firstChatId(chatId)
                            .firstMessageId(messageId)
                            .secondChatId(chatId)
                            .secondMessageId(newMessageId)
                            .otherUserId(userMessageId)
                            .requestId(requestId)
                            .build());
                    return;
                }

                if (callbackData.contains(AdminCallback.CANCEL_BASE_CALLBACK_DATA)) {
                    String[] callbackDataSplit = callbackData.split(" ");

                    int userMessageId = Integer.parseInt(callbackDataSplit[4]);
                    long requestId = Long.parseLong(callbackDataSplit[5]);

                    AnswerCallbackQuery answerCallbackQuery = AnswerCallbackQuery.builder()
                            .callbackQueryId(update.getCallbackQuery().getId())
                            .build();

                    sendAnswerCallbackQuery(answerCallbackQuery);

                    InlineKeyboardMarkup backToFallFromHandleSendBaseKeyboardMarkup = new InlineKeyboardMarkup();

                    InlineKeyboardButton backToFallFromHandleSendBaseButton = InlineKeyboardButton.builder()
                            .callbackData(BackCallback.BACK_TO_FALL_FROM_HANDLE_SEND_BASE_CALLBACK_DATA)
                            .text("\uD83D\uDEAB Отменить")
                            .build();

                    List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();

                    keyboardButtonsRow1.add(backToFallFromHandleSendBaseButton);

                    List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

                    rowList.add(keyboardButtonsRow1);

                    backToFallFromHandleSendBaseKeyboardMarkup.setKeyboard(rowList);

                    Integer newMessageId = null;
                    SendMessage sendMessage = SendMessage.builder()
                            .text("❌ <b>Укажите причину,</b> из-за которой вы отклонили запрос пользователя")
                            .chatId(chatId)
                            .replyMarkup(backToFallFromHandleSendBaseKeyboardMarkup)
                            .parseMode("html")
                            .build();

                    try {
                        newMessageId = execute(sendMessage).getMessageId();
                    } catch (TelegramApiException e) {
                        log.warn(e.getMessage());
                    }

                    handleCancelSendBaseSteps.put(userId, OperationBase.builder()
                            .firstChatId(chatId)
                            .firstMessageId(messageId)
                            .secondChatId(chatId)
                            .secondMessageId(newMessageId)
                            .otherUserId(userMessageId)
                            .requestId(requestId)
                            .build());
                    return;
                }

                if (callbackData.contains(UserCallback.SEND_BASE_AWAITING_CALLBACK_DATA)) {
                    String[] callBackDataSplit = callbackData.split(" ");

                    long id = Long.parseLong(callBackDataSplit[6]);
                    int page = Integer.parseInt(callBackDataSplit[5]);

                    editSendBaseAwaitingMessage(chatId, messageId, page ,id);
                    return;
                }

                if (callbackData.contains(AdminCallback.ACCEPT_WITHDRAWAL_CALLBACK_DATA)) {
                    String[] callBackDataSplit = callbackData.split(" ");

                    long withdrawalId = Long.parseLong(callBackDataSplit[4]);
                    long userChatId = Long.parseLong(callBackDataSplit[5]);

                    withdrawalRepository.deleteById(withdrawalId);

                    SendMessage sendMessage = SendMessage.builder()
                            .text("✅ <b>Ваша недавняя заявка на вывод была одобрена</b>")
                            .chatId(userChatId)
                            .parseMode("html")
                            .build();

                    sendMessage(sendMessage);

                    DeleteMessage deleteMessage = DeleteMessage.builder()
                            .chatId(chatId)
                            .messageId(messageId)
                            .build();

                    sendDeleteMessage(deleteMessage);
                    return;
                }

                if (callbackData.contains(AdminCallback.IGNORE_WITHDRAWAL_CALLBACK_DATA)) {
                    String[] callBackDataSplit = callbackData.split(" ");

                    long withdrawalId = Long.parseLong(callBackDataSplit[4]);
                    long userChatId = Long.parseLong(callBackDataSplit[5]);

                    withdrawalRepository.deleteById(withdrawalId);

                    SendMessage sendMessage = SendMessage.builder()
                            .text("❌ <b>Ваша недавняя заявка на вывод была отклонена</b>")
                            .chatId(userChatId)
                            .parseMode("html")
                            .build();

                    sendMessage(sendMessage);

                    DeleteMessage deleteMessage = DeleteMessage.builder()
                            .chatId(chatId)
                            .messageId(messageId)
                            .build();

                    sendDeleteMessage(deleteMessage);

                    return;
                }
            } else if (update.hasMessage() && update.getMessage().hasDocument()) {
                long userId = update.getMessage().getFrom().getId();
                long chatId = update.getMessage().getChatId();

                User currentUser = userRepository.findByTelegramUserId(userId);

                if (currentUser == null) {
                    currentUser = userRepository.save(User.builder()
                            .telegramUserId(userId)
                            .role(UserRole.DEFAULT)
                            .registeredAt(System.currentTimeMillis())
                            .balance(0d)
                            .build());
                }

                Document document = update.getMessage().getDocument();

                InlineKeyboardMarkup backToSendBaseKeyboardMarkup = new InlineKeyboardMarkup();

                InlineKeyboardButton backToSendBaseButton = InlineKeyboardButton.builder()
                        .callbackData(BackCallback.BACK_TO_SEND_BASE_CALLBACK_DATA)
                        .text("↩\uFE0F Назад")
                        .build();

                List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();

                keyboardButtonsRow1.add(backToSendBaseButton);

                List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

                rowList.add(keyboardButtonsRow1);

                backToSendBaseKeyboardMarkup.setKeyboard(rowList);

                if (!document.getFileName().endsWith(".txt")) {
                    SendMessage sendMessage = SendMessage.builder()
                            .text("❌ Пожалуйста, отправьте <b>корректный</b> файл с вашей базы в формате <code>login:password</code>"
                            )
                            .chatId(chatId)
                            .replyMarkup(backToSendBaseKeyboardMarkup)
                            .parseMode("html")
                            .build();

                    sendMessage(sendMessage);

                    return;
                }

                GetFile getFile = GetFile.builder().fileId(document.getFileId()).build();
                String FileURL = null;

                try {
                    FileURL = execute(getFile).getFileUrl(telegramBotsProperty.getToken());
                } catch (TelegramApiException e) {
                    log.warn(e.getMessage());
                }

                File txtFile = null;

                try {
                    txtFile = urlFileDownloaderUtil.downloadFile(FileURL, String.valueOf(System.currentTimeMillis()), ".txt", filesPath);
                } catch (IOException e) {
                    log.warn(e.getMessage());
                }

                Request request = Request.builder()
                        .amount(null)
                        .userId(currentUser.getId())
                        .status(RequestStatus.AWAIT)
                        .filePath(txtFile.getPath())
                        .createdAt(System.currentTimeMillis())
                        .build();

                request = requestRepository.save(request);

                sendBaseSteps.remove(userId);

                SendMessage sendMessage = SendMessage.builder()
                        .text("✅ <b>Файл получен!</b>\n\n"
                                + "Спасибо за отправку файла. Теперь мы обрабатываем ваш запрос. <b>Пожалуйста, ожидайте подтверждение завершения операции.</b>\n\n"
                                + "Если у вас есть дополнительные вопросы или вам нужна дополнительная информация, не стесняйтесь обращаться. <b>Мы уведомим вас, как только обработка будет завершена!</b> \uD83D\uDD04\uD83D\uDE80"
                                )
                        .chatId(chatId)
                        .replyMarkup(backToSendBaseKeyboardMarkup)
                        .parseMode("html")
                        .build();

                sendMessage(sendMessage);

                List<Long> adminTelegramUserIds = userRepository.findAllTelegramUserIdByRole(UserRole.ADMIN);

                File finalTxtFile = txtFile;
                User finalCurrentUser = currentUser;
                Request finalRequest = request;
                adminTelegramUserIds.forEach(c -> {
                    Date registeredAtDate = new Date(finalCurrentUser.getRegisteredAt());
                    SimpleDateFormat registeredDate = new SimpleDateFormat("dd.MM.yyyy");

                    String formattedRegisteredAtDate = registeredDate.format(registeredAtDate);

                    InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

                    InlineKeyboardButton acceptButton = InlineKeyboardButton.builder()
                            .callbackData(AdminCallback.ACCEPT_BASE_CALLBACK_DATA + " " + userId + " " + finalRequest.getId())
                            .text("✅ Одобрить запрос (указать стоимость)")
                            .build();

                    InlineKeyboardButton cancelButton = InlineKeyboardButton.builder()
                            .callbackData(AdminCallback.CANCEL_BASE_CALLBACK_DATA+ " " + userId + " " + finalRequest.getId())
                            .text("❌ Отклонить запрос (указать причину)")
                            .build();

                    List<InlineKeyboardButton> keyboardButtonsRow11 = new ArrayList<>();
                    List<InlineKeyboardButton> keyboardButtonsRow22 = new ArrayList<>();

                    keyboardButtonsRow11.add(acceptButton);

                    keyboardButtonsRow22.add(cancelButton);

                    List<List<InlineKeyboardButton>> rowList1 = new ArrayList<>();

                    rowList1.add(keyboardButtonsRow11);
                    rowList1.add(keyboardButtonsRow22);

                    inlineKeyboardMarkup.setKeyboard(rowList1);

                    SendDocument sendDocument = SendDocument.builder()
                            .document(new InputFile(finalTxtFile))
                            .caption("\uD83D\uDCE5 <b>Новая база от пользователя!</b>\n\n"
                            + "Получен новая база от пользователя:\n\n"
                            + "\uD83E\uDEAA <b>ID:</b> <code>" + finalCurrentUser.getTelegramUserId() + "</code>\n"
                            + "\uD83D\uDC64 <b>Имя:</b> " + update.getMessage().getFrom().getFirstName() + "\n"
                            + "\uD83D\uDD5C <b>Регистрация:</b> " + formattedRegisteredAtDate + "\n\n"
                            + "✉\uFE0F <b>Информация о файле:</b>\n"
                            + "   - <b>Имя файла:</b> " + finalTxtFile.getName() + "\n"
                            + "   - <b>Формат:</b> .txt\n\n"
                            + "\uD83D\uDE80 <b>Действия:</b>\n"
                            + "   1. Проверьте файл на корректность и безопасность.\n"
                            + "   2. Обработайте запрос пользователя.\n"
                            + "   3. Отправьте подтверждение обработки.")
                            .chatId(c)
                            .replyMarkup(inlineKeyboardMarkup)
                            .parseMode("html")
                            .build();

                    sendDocument(sendDocument);

                });
            } else if (update.hasMessage() && update.getMessage().hasPhoto()) {
                long userId = update.getMessage().getFrom().getId();
                long chatId = update.getMessage().getChatId();
                String caption = update.getMessage().getCaption();
                PhotoSize photoSize = update.getMessage().getPhoto().get(3);

                if (handleCancelSendBaseSteps.get(userId) != null) {
                    OperationBase operationBase = handleCancelSendBaseSteps.get(userId);

                    handleCancelSendBaseSteps.remove(userId);

                    GetFile getFile = GetFile.builder().fileId(photoSize.getFileId()).build();
                    String FileURL = null;

                    try {
                        FileURL = execute(getFile).getFileUrl(telegramBotsProperty.getToken());
                    } catch (TelegramApiException e) {
                        log.warn(e.getMessage());
                    }

                    File pngFile = null;

                    try {
                        pngFile = urlFileDownloaderUtil.downloadFileTemp(FileURL, String.valueOf(System.currentTimeMillis()), ".png");
                    } catch (IOException e) {
                        log.warn(e.getMessage());
                    }

                    SendPhoto sendPhoto = SendPhoto.builder()
                            .caption("❌ <b>Ваш запрос с базой отклонен</b>\n\n"
                                    + "К сожалению, ваш запрос содержащий базу был отклонен.\n\n"
                                    + "\uD83D\uDCC4 <b>Причина отклонения:</b>\n"
                                    + caption + "\n\n"
                                    + "\uD83D\uDCF7 <b>Фотография приложена к отказу</b>\n\n"
                                    + "Если у вас есть вопросы или вам нужна дополнительная информация, не стесняйтесь обращаться. <b>Мы готовы вам помочь!</b> \uD83D\uDE80\uD83D\uDC94"
                            )
                            .photo(new InputFile(pngFile))
                            .chatId(operationBase.getOtherUserId())
                            .parseMode("html")
                            .build();

                    try {
                        execute(sendPhoto);
                        pngFile.delete();
                    } catch (TelegramApiException e) {
                        log.warn(e.getMessage());
                    }

                    File file = new File(requestRepository.findFilePathById(operationBase.getRequestId()));

                    file.delete();

                    requestRepository.deleteById(operationBase.getRequestId());

                    DeleteMessage deleteMessage1 = DeleteMessage.builder()
                            .chatId(operationBase.getFirstChatId())
                            .messageId(operationBase.getFirstMessageId())
                            .build();

                    DeleteMessage deleteMessage2 = DeleteMessage.builder()
                            .chatId(operationBase.getSecondChatId())
                            .messageId(operationBase.getSecondMessageId())
                            .build();

                    DeleteMessage deleteMessage3 = DeleteMessage.builder()
                            .chatId(chatId)
                            .messageId(update.getMessage().getMessageId())
                            .build();

                    try {
                        execute(deleteMessage1);
                        execute(deleteMessage2);
                        execute(deleteMessage3);

                    } catch (TelegramApiException e) {
                        log.warn(e.getMessage());
                    }
                    return;
                }
            }

        }).start();

    }

    /**
     * Разделитель
     */

    private void createStartMessage(long chatId, User currentUser) {
        SendMessage startMessage = SendMessage.builder()
                .text("<b>Привет!</b> \uD83E\uDD16\n\n"
                        + "<b>Я - бот StrzAccepter,</b> готовый помочь тебе в отработке баз Steam! \uD83C\uDFAE✨\n\n"
                        + "Что я могу для тебя сделать?")
                .chatId(chatId)
                .replyMarkup(getDefaultReplyKeyboardMarkup(currentUser))
                .parseMode("html")
                .build();

        sendMessage(startMessage);
    }

    private void createProfileMessage(long chatId, User currentUser, String firstName) {
        Date registeredAtDate = new Date(currentUser.getRegisteredAt());
        SimpleDateFormat registeredDate = new SimpleDateFormat("dd.MM.yyyy");

        String formattedRegisteredAtDate = registeredDate.format(registeredAtDate);

        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

        InlineKeyboardButton replenishmentButton = InlineKeyboardButton.builder()
                .callbackData(UserCallback.SEND_WITHDRAWAL_CALLBACK_DATA)
                .text("\uD83D\uDCB8 Вывести баланс")
                .build();

        InlineKeyboardButton baseAwaitingButton = InlineKeyboardButton.builder()
                .callbackData(UserCallback.SEND_BASE_AWAITING_CALLBACK_DATA + " " + 0 + " " + currentUser.getId())
                .text("\uD83D\uDDC3 Мои базы в ожидании")
                .build();

        List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();
        List<InlineKeyboardButton> keyboardButtonsRow2 = new ArrayList<>();

        keyboardButtonsRow1.add(replenishmentButton);

        keyboardButtonsRow2.add(baseAwaitingButton);

        List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

        rowList.add(keyboardButtonsRow1);
        rowList.add(keyboardButtonsRow2);

        inlineKeyboardMarkup.setKeyboard(rowList);


        SendMessage profileMessage = SendMessage.builder()
                .text("\uD83D\uDCBC <b>Профиль</b>\n\n"
                + "\uD83E\uDEAA <b>ID:</b> <code>" + currentUser.getTelegramUserId() + "</code>\n"
                + "\uD83D\uDC64 <b>Имя:</b> " + firstName + "\n"
                + "\uD83D\uDD5C <b>Регистрация:</b> " + formattedRegisteredAtDate + "\n\n"
                + "\uD83D\uDCB3 <b>Баланс:</b> " + currentUser.getBalance() + " ₽\n"
                + "\uD83D\uDCB0 <b>Заработано всего:</b> " + requestRepository.sumAmountByUserId(currentUser.getId()) + " ₽\n\n"
                + "\uD83D\uDC96 <b>Надеемся на хороший перфоманс и продолжение работы с вами!</b>")
                .chatId(chatId)
                .replyMarkup(inlineKeyboardMarkup)
                .parseMode("html")
                .build();

        sendMessage(profileMessage);
    }

    private void editProfileMessage(long chatId, int messageId, User currentUser, String firstName) {
        Date registeredAtDate = new Date(currentUser.getRegisteredAt());
        SimpleDateFormat registeredDate = new SimpleDateFormat("dd.MM.yyyy");

        String formattedRegisteredAtDate = registeredDate.format(registeredAtDate);

        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

        InlineKeyboardButton replenishmentButton = InlineKeyboardButton.builder()
                .callbackData(UserCallback.SEND_WITHDRAWAL_CALLBACK_DATA)
                .text("\uD83D\uDCB8 Вывести баланс")
                .build();

        InlineKeyboardButton baseAwaitingButton = InlineKeyboardButton.builder()
                .callbackData(UserCallback.SEND_BASE_AWAITING_CALLBACK_DATA + " " + 0 + " " + currentUser.getId())
                .text("\uD83D\uDDC3 Мои базы в ожидании")
                .build();

        List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();
        List<InlineKeyboardButton> keyboardButtonsRow2 = new ArrayList<>();

        keyboardButtonsRow1.add(replenishmentButton);

        keyboardButtonsRow2.add(baseAwaitingButton);

        List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

        rowList.add(keyboardButtonsRow1);
        rowList.add(keyboardButtonsRow2);

        inlineKeyboardMarkup.setKeyboard(rowList);


        EditMessageText profileMessage = EditMessageText.builder()
                .text("\uD83D\uDCBC <b>Профиль</b>\n\n"
                        + "\uD83E\uDEAA <b>ID:</b> <code>" + currentUser.getTelegramUserId() + "</code>\n"
                        + "\uD83D\uDC64 <b>Имя:</b> " + firstName + "\n"
                        + "\uD83D\uDD5C <b>Регистрация:</b> " + formattedRegisteredAtDate + "\n\n"
                        + "\uD83D\uDCB3 <b>Баланс:</b> " + currentUser.getBalance() + " ₽\n"
                        + "\uD83D\uDCB0 <b>Заработано всего:</b> " + requestRepository.sumAmountByUserId(currentUser.getId()) + " ₽\n\n"
                        + "\uD83D\uDC96 <b>Надеемся на хороший перфоманс и продолжение работы с вами!</b>")
                .chatId(chatId)
                .messageId(messageId)
                .replyMarkup(inlineKeyboardMarkup)
                .parseMode("html")
                .build();

        editMessageText(profileMessage);
    }

    private void createSendBaseMessage(long chatId) {
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

        InlineKeyboardButton sendBaseButton = InlineKeyboardButton.builder()
                .callbackData(UserCallback.SEND_BASE_CALLBACK_DATA)
                .text("\uD83D\uDCE4 Отправить базу")
                .build();

        List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();

        keyboardButtonsRow1.add(sendBaseButton);

        List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

        rowList.add(keyboardButtonsRow1);

        inlineKeyboardMarkup.setKeyboard(rowList);

        SendMessage sendMessage = SendMessage.builder()
                .text("\uD83D\uDCE4 <b>Отправить базу</b>\n\n"
                + "Чтобы отправить базу, следуйте этим шагам:\n\n"
                + "<i>1. Подготовьте базу в формате</i> <code>login:password</code>\n"
                + "<i>2. Нажмите кнопку</i> \"\uD83D\uDCE4 Отправить базу\"\n"
                + "<i>3. Прикрепите ваш файл с базой</i>\n"
                + "<i>4. Ожидайте подтверждение обработки</i>\n\n"
                + "<b>Мы гарантируем безопасность и конфиденциальность ваших данных. Если у вас есть какие-либо вопросы или нужна помощь, не стесняйтесь обращаться!</b> \uD83E\uDD1D"
                )
                .chatId(chatId)
                .replyMarkup(inlineKeyboardMarkup)
                .parseMode("html")
                .build();

        sendMessage(sendMessage);
    }

    private void editSendBaseMessage(long chatId, int messageId) {
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

        InlineKeyboardButton sendBaseButton = InlineKeyboardButton.builder()
                .callbackData(UserCallback.SEND_BASE_CALLBACK_DATA)
                .text("\uD83D\uDCE4 Отправить базу")
                .build();

        List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();

        keyboardButtonsRow1.add(sendBaseButton);

        List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

        rowList.add(keyboardButtonsRow1);

        inlineKeyboardMarkup.setKeyboard(rowList);

        EditMessageText editMessageText = EditMessageText.builder()
                .text("\uD83D\uDCE4 <b>Отправить базу</b>\n\n"
                        + "Чтобы отправить базу, следуйте этим шагам:\n\n"
                        + "<i>1. Подготовьте базу в формате</i> <code>login:password</code>\n"
                        + "<i>2. Нажмите кнопку</i> \"\uD83D\uDCE4 Отправить базу\"\n"
                        + "<i>3. Прикрепите ваш файл с базой</i>\n"
                        + "<i>4. Ожидайте подтверждение обработки</i>\n\n"
                        + "<b>Мы гарантируем безопасность и конфиденциальность ваших данных. Если у вас есть какие-либо вопросы или нужна помощь, не стесняйтесь обращаться!</b> \uD83E\uDD1D"
                )
                .chatId(chatId)
                .messageId(messageId)
                .replyMarkup(inlineKeyboardMarkup)
                .parseMode("html")
                .build();

        editMessageText(editMessageText);
    }

    private void sendMessage(SendMessage message) {
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.warn(e.getMessage());
        }
    }

    private void editMessageText(EditMessageText editMessageText) {
        try {
            execute(editMessageText);
        } catch (TelegramApiException e) {
            log.warn(e.getMessage());
        };
    }

    private ReplyKeyboardMarkup getDefaultReplyKeyboardMarkup(User currentUser) {
        ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();

        replyKeyboardMarkup.setSelective(true);
        replyKeyboardMarkup.setResizeKeyboard(true);
        replyKeyboardMarkup.setOneTimeKeyboard(false);

        KeyboardButton sendDataBaseButton = new KeyboardButton("\uD83D\uDCE4 Отправить базу");
        KeyboardButton profileButton = new KeyboardButton("\uD83D\uDCBC Профиль");
        KeyboardButton supportButton = new KeyboardButton("\uD83E\uDD1D Поддержка");
        KeyboardButton howToSortRequestButton = new KeyboardButton("\uD83D\uDD04 Как сортировать запрос");
        KeyboardButton infoButton = new KeyboardButton("ℹ\uFE0F Информация");

        KeyboardRow keyboardRow1 = new KeyboardRow();
        KeyboardRow keyboardRow2 = new KeyboardRow();
        KeyboardRow keyboardRow3 = new KeyboardRow();

        keyboardRow1.add(sendDataBaseButton);

        keyboardRow2.add(profileButton);
        keyboardRow2.add(supportButton);

        keyboardRow3.add(infoButton);
        keyboardRow3.add(howToSortRequestButton);

        List<KeyboardRow> keyboardRows = new ArrayList<>();

        keyboardRows.add(keyboardRow1);
        keyboardRows.add(keyboardRow2);
        keyboardRows.add(keyboardRow3);

        if (currentUser.getRole().equals(UserRole.ADMIN)) {
            KeyboardButton adminButton = new KeyboardButton("\uD83C\uDF9B Админ панель");

            KeyboardRow keyboardRow4 = new KeyboardRow();

            keyboardRow4.add(adminButton);

            keyboardRows.add(keyboardRow4);
        }

        replyKeyboardMarkup.setKeyboard(keyboardRows);

        return replyKeyboardMarkup;
    }

    private void handleSendBaseMessage(long chatId, int messageId) {
        InlineKeyboardMarkup backToSendBaseKeyboardMarkup = new InlineKeyboardMarkup();

        InlineKeyboardButton backToSendBaseButton = InlineKeyboardButton.builder()
                .callbackData(BackCallback.BACK_TO_SEND_BASE_CALLBACK_DATA)
                .text("↩\uFE0F Назад")
                .build();

        List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();

        keyboardButtonsRow1.add(backToSendBaseButton);

        List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

        rowList.add(keyboardButtonsRow1);

        backToSendBaseKeyboardMarkup.setKeyboard(rowList);

        EditMessageText editMessageText = EditMessageText.builder()
                .text("\uD83D\uDCE4 <b>Отправить базу</b>\n\n"
                + "Отлично! Теперь, пожалуйста, отправьте файл с вашей базой в формате <code>login:password</code>. Убедитесь, что файл имеет расширение <b>.txt</b>\n\n"
                + "Если у вас есть какие-либо вопросы или нужна помощь, не стесняйтесь спрашивать. <b>Мы готовы вам помочь!</b> \uD83E\uDD16\uD83D\uDE80"
                )
                .chatId(chatId)
                .messageId(messageId)
                .replyMarkup(backToSendBaseKeyboardMarkup)
                .parseMode("html")
                .build();

        editMessageText(editMessageText);
    }

    private void sendDocument(SendDocument sendDocument) {
        try {
            execute(sendDocument);
        } catch (TelegramApiException e) {
            log.warn(e.getMessage());
        }
    }
    private void sendAnswerCallbackQuery(AnswerCallbackQuery answerCallbackQuery) {
        try {
            execute(answerCallbackQuery);
        } catch (TelegramApiException e) {
            log.warn(e.getMessage());
        }
    }

    private void sendDeleteMessage(DeleteMessage deleteMessage) {
        try {
            execute(deleteMessage);
        } catch (TelegramApiException e) {
            log.warn(e.getMessage());
        }
    }

    private void editSendBaseAwaitingMessage(long chatId, int messageId, int page, long userId) {
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

        List<Request> requests = requestRepository.findAllByUserIdAndStatusWithOffsetLimit(userId, RequestStatus.AWAIT,
                page * 8, 8);
        long requestsCount = requestRepository.count();

        List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();
        List<InlineKeyboardButton> keyboardButtonsRow2 = new ArrayList<>();

        if (page != 0) {
            InlineKeyboardButton prevPageBaseButton = InlineKeyboardButton.builder()
                    .callbackData(UserCallback.SEND_BASE_AWAITING_CALLBACK_DATA + " " + (page - 1) + " " + userId)
                    .text("⏪ Предыдущая")
                    .build();

            keyboardButtonsRow1.add(prevPageBaseButton);
        }

        InlineKeyboardButton currentPageBaseButton = InlineKeyboardButton.builder()
                .callbackData("answer")
                .text("Страница " + (page + 1) + "/" + Math.round((double) requestsCount / 8))
                .build();

        keyboardButtonsRow1.add(currentPageBaseButton);

        if (requests.size() == 8) {
            InlineKeyboardButton nextPageBaseButton = InlineKeyboardButton.builder()
                    .callbackData(UserCallback.SEND_BASE_AWAITING_CALLBACK_DATA + " " + (page + 1) + " " + userId)
                    .text("⏩ Следующая")
                    .build();

            keyboardButtonsRow1.add(nextPageBaseButton);
        }

        InlineKeyboardButton backToSendBaseButton = InlineKeyboardButton.builder()
                .callbackData(BackCallback.BACK_TO_PROFILE_CALLBACK_DATA)
                .text("↩\uFE0F Назад")
                .build();

        keyboardButtonsRow2.add(backToSendBaseButton);

        List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

        for (Request request : requests) {
            List<InlineKeyboardButton> newKeyboardButtonsRow = new ArrayList<>();

            Date createdAtDate = new Date(request.getCreatedAt());
            SimpleDateFormat createdDate = new SimpleDateFormat("dd.MM.yyyy");

            String formattedCreatedAtDate = createdDate.format(createdAtDate);

            InlineKeyboardButton newRequestButton = InlineKeyboardButton.builder()
                    .callbackData("answer")
                    .text("#" + request.getId() + " | " + formattedCreatedAtDate)
                    .build();

            newKeyboardButtonsRow.add(newRequestButton);

            rowList.add(newKeyboardButtonsRow);
        }

        rowList.add(keyboardButtonsRow1);
        rowList.add(keyboardButtonsRow2);

        inlineKeyboardMarkup.setKeyboard(rowList);

        EditMessageText editMessageText = EditMessageText.builder()
                .text("\uD83D\uDDC3 <b>Мои базы в ожидании</b>")
                .chatId(chatId)
                .messageId(messageId)
                .parseMode("html")
                .replyMarkup(inlineKeyboardMarkup)
                .build();

        editMessageText(editMessageText);
    }

    private void createHowToSortMessage(long chatId) {
        SendMessage sendMessage = SendMessage.builder()
                .text("\uD83D\uDD04 <b>Как сортировать запрос</b>\n\n"
                + "Для оптимальной сортировки запросов, воспользуйтесь любым сортировщиком логов и выделите два следующих запроса:\n\n"
                + "<i>1.</i> steamcommunity.com \n"
                + "<i>2.</i> steampowered.com\n\n"
                + "Формат строк обязателен и должен соответствовать: <code>login:password</code>.\n\n"
                + "<b>Пожалуйста, помните:</b> нам не нужны ваши логи, только строки. Отправьте их нам для более эффективной обработки. Спасибо за понимание! \uD83D\uDE80\uD83D\uDD0D"
                )
                .chatId(chatId)
                .parseMode("html")
                .build();

        sendMessage(sendMessage);
    }

    private void createSupportMessage(long chatId) {
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

        InlineKeyboardButton supportButton = InlineKeyboardButton.builder()
                .url("https://t.me/strzwork")
                .text("\uD83E\uDD1D Обратиться")
                .build();

        List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();

        keyboardButtonsRow1.add(supportButton);

        List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

        rowList.add(keyboardButtonsRow1);

        inlineKeyboardMarkup.setKeyboard(rowList);

        SendMessage sendMessage = SendMessage.builder()
                .text("\uD83E\uDD1D <b>Поддержка</b>\n\n"
                + "Остались вопросы или нужна помощь? Не стесняйтесь обращаться!\n\n"
                + "\uD83D\uDCEC <b>Контакт:</b>\n"
                + "@strzwork\n\n"
                + "<b>Мы готовы помочь вам в любое время.</b> Спасибо за выбор наших услуг! \uD83D\uDE80\uD83D\uDCAC"
                )
                .chatId(chatId)
                .replyMarkup(inlineKeyboardMarkup)
                .parseMode("html")
                .build();

        sendMessage(sendMessage);
    }

    private void createInfoMessage(long chatId) {
        SendMessage sendMessage = SendMessage.builder()
                .text("ℹ\uFE0F <b>Информация</b>\n\n"
                + "Добро пожаловать в StrzAccepter - автоматический бот принятия ваших баз на Steam!\n\n"
                + "\uD83D\uDE80 <b>Преимущества:</b>\n"
                + "- Мгновенные выплаты после проверки вашей базы.\n"
                + "- Нет необходимости ждать продажи каждого аккаунта.\n\n"
                + "\uD83D\uDD0D <b>Как отправить базу:</b>\n"
                + "- Принимаем запросы с ваших логов steamcommunity.com и steampowered.com.\n"
                + "- Любое качество и возраст строк - мы готовы принять даже самые старые строки!\n\n"
                + "\uD83D\uDD04 <b>Процесс обработки:</b>\n"
                + "- После отправки базы она проходит проверку с использованием нашего личного Анти-Паблика.\n"
                + "- Работа начинается с 1000 уникальных строк!\n\n"
                + "<b>Не теряйте времени,</b> отправляйте свои базы и наслаждайтесь мгновенными выплатами! \uD83D\uDCBC\uD83D\uDCB0 \n\n"
                + "Если у вас есть вопросы, обращайтесь. <b>Спасибо, что выбрали наши услуги!</b> \uD83E\uDD16\uD83C\uDF1F"
                )
                .chatId(chatId)
                .parseMode("html")
                .build();

        sendMessage(sendMessage);
    }

    private void createAdminMessage(long chatId, User currentUser) {
        if (!currentUser.getRole().equals(UserRole.ADMIN)) {
            return;
        }

        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

        InlineKeyboardButton unloadButton = InlineKeyboardButton.builder()
                .callbackData(AdminCallback.UNLOAD_DATABASE_CALLBACK_DATA)
                .text("\uD83D\uDCE4 Выкачать базу пользователей")
                .build();

        InlineKeyboardButton addBalanceButton = InlineKeyboardButton.builder()
                .callbackData(AdminCallback.ADD_BALANCE_CALLBACK_DATA)
                .text("➕ Выдать баланс")
                .build();

        InlineKeyboardButton removeBalanceButton = InlineKeyboardButton.builder()
                .callbackData(AdminCallback.REMOVE_BALANCE_CALLBACK_DATA)
                .text("➖Снять баланс")
                .build();

        List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();
        List<InlineKeyboardButton> keyboardButtonsRow2 = new ArrayList<>();

        keyboardButtonsRow1.add(unloadButton);

        keyboardButtonsRow2.add(addBalanceButton);
        keyboardButtonsRow2.add(removeBalanceButton);

        List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

        rowList.add(keyboardButtonsRow1);
        rowList.add(keyboardButtonsRow2);

        inlineKeyboardMarkup.setKeyboard(rowList);

        SendMessage sendMessage = SendMessage.builder()
                .text("\uD83C\uDF9B <b>Админ панель</b>")
                .chatId(chatId)
                .replyMarkup(inlineKeyboardMarkup)
                .parseMode("html")
                .build();

        sendMessage(sendMessage);
    }

    private void editAdminMessage(long chatId, int messageId, User currentUser) {
        if (!currentUser.getRole().equals(UserRole.ADMIN)) {
            return;
        }

        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

        InlineKeyboardButton unloadButton = InlineKeyboardButton.builder()
                .callbackData(AdminCallback.UNLOAD_DATABASE_CALLBACK_DATA)
                .text("\uD83D\uDCE4 Выкачать базу пользователей")
                .build();

        InlineKeyboardButton addBalanceButton = InlineKeyboardButton.builder()
                .callbackData(AdminCallback.ADD_BALANCE_CALLBACK_DATA)
                .text("➕ Выдать баланс")
                .build();

        InlineKeyboardButton removeBalanceButton = InlineKeyboardButton.builder()
                .callbackData(AdminCallback.REMOVE_BALANCE_CALLBACK_DATA)
                .text("➖Снять баланс")
                .build();

        List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();
        List<InlineKeyboardButton> keyboardButtonsRow2 = new ArrayList<>();

        keyboardButtonsRow1.add(unloadButton);

        keyboardButtonsRow2.add(addBalanceButton);
        keyboardButtonsRow2.add(removeBalanceButton);

        List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

        rowList.add(keyboardButtonsRow1);
        rowList.add(keyboardButtonsRow2);

        inlineKeyboardMarkup.setKeyboard(rowList);

        EditMessageText editMessageText = EditMessageText.builder()
                .text("\uD83C\uDF9B <b>Админ панель</b>")
                .chatId(chatId)
                .messageId(messageId)
                .replyMarkup(inlineKeyboardMarkup)
                .parseMode("html")
                .build();

        editMessageText(editMessageText);
    }

    private void handleUnloadDatabaseMessage(long chatId, String callbackQueryId, User currentUser) {
        if (!currentUser.getRole().equals(UserRole.ADMIN)) {
            return;
        }

        AnswerCallbackQuery answerCallbackQuery = new AnswerCallbackQuery(callbackQueryId);

        sendAnswerCallbackQuery(answerCallbackQuery);

        SendMessage sendMessage = SendMessage.builder()
                .text("⏳ <b>Загрузка...</b>")
                .chatId(chatId)
                .parseMode("html")
                .build();

        Integer sendMessageId = null;

        try {
            sendMessageId = execute(sendMessage).getMessageId();
        } catch (TelegramApiException e) {
            log.warn(e.getMessage());
        }

        try {
            Iterable<User> userList = userRepository.findAll();

            File csvTempFile = File.createTempFile("csvTempFile", ".csv");
            FileWriter fileWriter = new FileWriter(csvTempFile.getPath());

            CSVWriter csvWriter = new CSVWriter(fileWriter);

            String[] header = {"ID", "Кол-во баз", "Профит", "Дата регистрации"};

            csvWriter.writeNext(header);

            for (User user : userList) {
                List<Request> requests = requestRepository.findAllByUserIdAndStatus(user.getId(), RequestStatus.SUCCESS);
                long baseCount = requestRepository.countByUserId(user.getId());
                double profit = 0;

                Date registeredAtDate = new Date(user.getRegisteredAt());
                SimpleDateFormat registeredDate = new SimpleDateFormat("dd.MM.yyyy");

                String formattedRegisteredAtDate = registeredDate.format(registeredAtDate);

                for (Request request : requests) {
                    profit += request.getAmount();
                }

                String[] row = {String.valueOf(user.getTelegramUserId()), String.valueOf(baseCount), String.valueOf(profit),
                        formattedRegisteredAtDate};

                csvWriter.writeNext(row);
            }

            csvWriter.close();

            sendDeleteMessage(DeleteMessage.builder().messageId(sendMessageId).chatId(chatId).build());

            SendDocument sendDocument = SendDocument.builder()
                    .caption("\uD83D\uDCE4 Выгруженная база пользователей в формате <b>.csv</b>")
                    .chatId(chatId)
                    .document(new InputFile(csvTempFile))
                    .parseMode("html")
                    .build();


            sendDocument(sendDocument);

            csvTempFile.delete();
        } catch (IOException e) {
            log.warn(e.getMessage());
        }
    }

    private void handleAddBalanceMessage(long chatId, int messageId, User currentUser) {
        if (!currentUser.getRole().equals(UserRole.ADMIN)) {
            return;
        }

        addBalanceSteps.put(chatId, new Pair<>(1, null));

        InlineKeyboardMarkup backToAdminKeyboardMarkup = new InlineKeyboardMarkup();

        InlineKeyboardButton backToAdminButton = InlineKeyboardButton.builder()
                .callbackData(BackCallback.BACK_TO_ADMIN_CALLBACK_DATA)
                .text("↩\uFE0F Назад")
                .build();

        List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();

        keyboardButtonsRow1.add(backToAdminButton);

        List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

        rowList.add(keyboardButtonsRow1);

        backToAdminKeyboardMarkup.setKeyboard(rowList);

        EditMessageText editMessageText = EditMessageText.builder()
                .text("➕ Введите идентификатор пользователя, которому вы хотите выдать баланс")
                .chatId(chatId)
                .replyMarkup(backToAdminKeyboardMarkup)
                .messageId(messageId)
                .parseMode("html")
                .build();

        editMessageText(editMessageText);
    }

    private void handleRemoveBalanceMessage(long chatId, int messageId, User currentUser) {
        if (!currentUser.getRole().equals(UserRole.ADMIN)) {
            return;
        }

        removeBalanceSteps.put(chatId, new Pair<>(1, null));

        InlineKeyboardMarkup backToAdminKeyboardMarkup = new InlineKeyboardMarkup();

        InlineKeyboardButton backToAdminButton = InlineKeyboardButton.builder()
                .callbackData(BackCallback.BACK_TO_ADMIN_CALLBACK_DATA)
                .text("↩\uFE0F Назад")
                .build();

        List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();

        keyboardButtonsRow1.add(backToAdminButton);

        List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

        rowList.add(keyboardButtonsRow1);

        backToAdminKeyboardMarkup.setKeyboard(rowList);

        EditMessageText editMessageText = EditMessageText.builder()
                .text("➖ Введите идентификатор пользователя, с которого вы хотите снять баланс")
                .chatId(chatId)
                .messageId(messageId)
                .replyMarkup(backToAdminKeyboardMarkup)
                .parseMode("html")
                .build();

        editMessageText(editMessageText);
    }

    private void editWithdrawalMessage(long chatId, int messageId, long userId) {
        InlineKeyboardMarkup backToProfileKeyboardMarkup = new InlineKeyboardMarkup();

        InlineKeyboardButton backToProfileButton = InlineKeyboardButton.builder()
                .callbackData(BackCallback.BACK_TO_PROFILE_CALLBACK_DATA)
                .text("↩\uFE0F Назад")
                .build();

        List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();

        keyboardButtonsRow1.add(backToProfileButton);

        List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

        rowList.add(keyboardButtonsRow1);

        backToProfileKeyboardMarkup.setKeyboard(rowList);

        withdrawalBalanceSteps.put(userId, new Pair<>(1, null));

        EditMessageText editMessageText = EditMessageText.builder()
                .text("\uD83D\uDCB8 <b>Вывести баланс</b>\n\n"
                + "Для начала процесса вывода средств, введите сумму в рублях, которую вы хотите вывести. <b>Убедитесь, что указали сумму корректно.</b>\n\n"
                + "❓ Если у вас есть вопросы или нужна помощь, не стесняйтесь обращаться. <b>Мы здесь, чтобы помочь вам!</b> \uD83D\uDE80\uD83D\uDCAC")
                .chatId(chatId)
                .replyMarkup(backToProfileKeyboardMarkup)
                .messageId(messageId)
                .parseMode("html")
                .build();

        editMessageText(editMessageText);
    }
}
