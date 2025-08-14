package ru.spbstu.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.spbstu.handler.CommandHandler;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class BotCommandService {
    
    private final List<CommandHandler> commandHandlers;
    
    @Autowired
    public BotCommandService(List<CommandHandler> commandHandlers) {
        this.commandHandlers = commandHandlers;
    }
    
    /**
     * Устанавливает команды бота с подсказками
     * @param sender экземпляр бота для отправки команд
     */
    public void setBotCommands(AbsSender sender) {
        try {
            // Фильтруем только команды, которые должны отображаться в меню
            List<BotCommand> commands = commandHandlers.stream()
                    .filter(this::shouldShowInMenu)
                    .map(handler -> new BotCommand(handler.getCommand(), handler.getDescription()))
                    .collect(Collectors.toList());
            
            SetMyCommands setCommands = new SetMyCommands();
            setCommands.setCommands(commands);
            setCommands.setScope(new BotCommandScopeDefault());
            
            sender.execute(setCommands);
            
            System.out.println("The bot's commands have been successfully installed: " + commands.size() + " commands");
            
        } catch (TelegramApiException e) {
            System.err.println("Error when installing bot commands: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Определяет, должна ли команда отображаться в меню команд
     * @param handler обработчик команды
     * @return true, если команда должна отображаться в меню
     */
    private boolean shouldShowInMenu(CommandHandler handler) {
        String command = handler.getCommand();
        
        // Не показываем в меню служебные команды и команды без описания
        return !command.equals("/default") && 
               !command.equals("/start") &&
               !handler.getDescription().equals("Команда бота");
    }
    
    /**
     * Получает список всех доступных команд
     * @return список команд с описаниями
     */
    public List<BotCommand> getAllCommands() {
        return commandHandlers.stream()
                .map(handler -> new BotCommand(handler.getCommand(), handler.getDescription()))
                .collect(Collectors.toList());
    }
}
