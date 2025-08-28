# QuizBot - Telegram Quiz Application

A comprehensive Telegram bot application built with Java and Spring Framework that allows users to create, manage, and participate in quizzes. The bot supports multiple timezones, scheduled question delivery, and comprehensive quiz management features.

## 🚀 Features

### Core Functionality
- **Quiz Management**: Create, edit, and delete quiz questions with multiple choice options
- **Tag System**: Organize questions by categories using tags
- **User Management**: User registration, timezone settings, and role-based access
- **Scheduled Quizzes**: Automated delivery of random questions at scheduled intervals
- **Score Tracking**: Monitor user performance and track scores by tag categories

### Bot Commands
- `/start` - Initialize the bot and set timezone
- `/help` - Display available commands and features
- Question management commands (add, edit, delete)
- Tag management commands
- Quiz participation commands
- Score and statistics viewing

### Technical Features
- **Multi-timezone Support**: Supports 11 Russian timezones (MSC-1 to MSC+9)
- **Session Management**: Stateful conversation handling for complex operations
- **Database Persistence**: PostgreSQL database with JPA/Hibernate
- **Scheduled Jobs**: Quartz scheduler for automated question delivery
- **Comprehensive Logging**: Logback integration for debugging and monitoring

## 🛠️ Technology Stack

- **Java 21** - Core programming language
- **Spring Framework 6.1.0** - Dependency injection and configuration
- **Spring Data JPA 3.5.0** - Data access layer
- **Hibernate 6.4.0** - Object-relational mapping
- **PostgreSQL** - Database
- **Telegram Bots API 6.9.7.1** - Telegram bot integration
- **Quartz 2.5.0** - Job scheduling
- **Gradle** - Build tool and dependency management
- **JUnit 5** - Testing framework
- **TestContainers** - Integration testing

## 📋 Prerequisites

- Java 21 or higher
- PostgreSQL database
- Telegram Bot Token (obtain from [@BotFather](https://t.me/botfather))
- Gradle (or use the included wrapper)

## ⚙️ Installation & Setup

### 1. Clone the Repository
```bash
git clone <repository-url>
cd QuizBot
```

### 2. Database Setup
Create a PostgreSQL database and update the connection settings in `src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/quizbot
spring.datasource.username=your_username
spring.datasource.password=your_password
```

### 3. Telegram Bot Configuration
Update the bot configuration in `src/main/resources/application.properties`:

```properties
telegram.bot.token=YOUR_BOT_TOKEN
telegram.bot.username=@YOUR_BOT_USERNAME
```

### 4. Build and Run
```bash
# Build the project
./gradlew build

# Run the application
./gradlew run

# Or build and run the JAR
./gradlew jar
java -jar build/libs/quiz-bot-1.0-SNAPSHOT.jar
```

## 🏗️ Project Structure

```
src/main/java/ru/spbstu/
├── config/                 # Spring configuration
├── dto/                   # Data transfer objects
├── model/                 # Entity models
├── repository/            # Data access layer
├── service/               # Business logic services
├── telegram/              # Telegram bot implementation
│   ├── bot/              # Bot core classes
│   ├── handler/          # Command handlers
│   ├── job/              # Scheduled jobs
│   ├── sender/           # Message sending utilities
│   ├── session/          # User session management
│   └── utils/            # Utility classes
└── Main.java             # Application entry point
```

## 🔧 Configuration

### Application Properties
The main configuration file `application.properties` contains:
- Database connection settings
- Telegram bot credentials
- JPA/Hibernate configuration
- Logging settings

### Logging
Logback configuration is provided in `src/main/resources/logback.xml` for comprehensive logging.

## 🧪 Testing

The project includes comprehensive testing with:
- Unit tests using JUnit 5 and Mockito
- Integration tests with TestContainers
- Spring test support

Run tests with:
```bash
./gradlew test
```

## 📦 Building

### Create Distribution Packages
```bash
./gradlew distTar    # Creates .tar distribution
./gradlew distZip    # Creates .zip distribution
```

### Create Executable JAR
```bash
./gradlew jar
```

## 🚀 Deployment

### Production Deployment
1. Build the project: `./gradlew build`
2. Copy the JAR file to your server
3. Ensure PostgreSQL is running and accessible
4. Set environment variables for database credentials
5. Run: `java -jar quiz-bot-1.0-SNAPSHOT.jar`

### Docker Deployment (Recommended)
Create a `Dockerfile` and `docker-compose.yml` for easy deployment:

```dockerfile
FROM openjdk:21-jre-slim
COPY build/libs/quiz-bot-1.0-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

## 📱 Usage Examples

### Starting the Bot
1. Send `/start` to your bot
2. Select your timezone (e.g., "MSC+4")
3. Use `/help` to see available commands

### Creating Questions
- Use the question management commands to create quiz questions
- Assign tags to organize questions by category
- Set up scheduled delivery for automated quizzes

### Taking Quizzes
- Participate in scheduled quizzes
- View your scores and statistics
- Track performance by tag categories

---

**Note**: This bot is designed with Russian timezone support but can be easily extended for other regions by modifying the timezone configuration in the `StartCommandHandler`.
