package ru.spbstu.telegram.sender;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.polls.SendPoll;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;

@Component
public class MessageSender {
    private final ObjectProvider<AbsSender> senderProvider;

    public MessageSender(ObjectProvider<AbsSender> senderProvider) {
        this.senderProvider = senderProvider;
    }

    private AbsSender getSender() {
        return senderProvider.getIfAvailable();
    }

    public void sendMessage(Long chatId, String text) {
        SendMessage msg = SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .parseMode("Markdown")
                .build();
        execute(msg);
    }

    public void sendPlainMessage(Long chatId, String text) {
        SendMessage msg = SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .build();
        execute(msg);
    }

    public void sendPoll(Long chatId, String question, List<String> options,
                         int correctAnswerInd, String description) {
        SendPoll poll = new SendPoll();
        poll.setChatId(chatId.toString());
        poll.setQuestion(description + "\n" + question);
        poll.setOptions(options);
        poll.setCorrectOptionId(correctAnswerInd);
        poll.setType("quiz");
        poll.setOpenPeriod(30);
        poll.setIsAnonymous(false);
        execute(poll);
    }

    private void execute(BotApiMethod<?> method) {
        try {
            getSender().execute(method);
        } catch (TelegramApiException e) {
            throw new RuntimeException("Ошибка отправки сообщения: ", e);
        }
    }

    public String escapeTagForMarkdown(String tagName) {
        return tagName.replace("_", "\\_");
    }
}
