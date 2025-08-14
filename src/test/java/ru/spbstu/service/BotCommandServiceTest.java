package ru.spbstu.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import ru.spbstu.handler.CommandHandler;
import ru.spbstu.handler.HelpCommandHandler;
import ru.spbstu.handler.StartCommandHandler;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BotCommandServiceTest {

    @Mock
    private CommandHandler mockHandler1;
    
    @Mock
    private CommandHandler mockHandler2;
    
    private BotCommandService botCommandService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Настраиваем моки
        when(mockHandler1.getCommand()).thenReturn("/test1");
        when(mockHandler1.getDescription()).thenReturn("Test command 1");
        
        when(mockHandler2.getCommand()).thenReturn("/test2");
        when(mockHandler2.getDescription()).thenReturn("Test command 2");
        
        List<CommandHandler> handlers = Arrays.asList(mockHandler1, mockHandler2);
        botCommandService = new BotCommandService(handlers);
    }

    @Test
    void testGetAllCommands() {
        var commands = botCommandService.getAllCommands();
        
        assertEquals(2, commands.size());
        assertEquals("/test1", commands.get(0).getCommand());
        assertEquals("Test command 1", commands.get(0).getDescription());
        assertEquals("/test2", commands.get(1).getCommand());
        assertEquals("Test command 2", commands.get(1).getDescription());
    }

    @Test
    void testShouldShowInMenu() {
        // Тестируем с реальными обработчиками
        HelpCommandHandler helpHandler = new HelpCommandHandler();
        StartCommandHandler startHandler = new StartCommandHandler(null);
        
        List<CommandHandler> handlers = Arrays.asList(helpHandler, startHandler);
        BotCommandService service = new BotCommandService(handlers);
        
        // Проверяем, что команды возвращают правильные описания
        assertEquals("Показать справку по всем командам", helpHandler.getDescription());
        assertEquals("Запустить бота и получить приветствие", startHandler.getDescription());
    }
}
