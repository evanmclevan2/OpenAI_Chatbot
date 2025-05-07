
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.sql.*;
import java.util.*;

import org.json.JSONObject;
import io.github.cdimascio.dotenv.Dotenv;


public class Chatbot {

    private static OpenAiAssistantEngine assistant;
    private static final File USER_INFO_FILE = new File("user_info.txt");
    private static final File ACU_DATABASE_FILE = new File("acu_database1.txt");


    private static final String DB_URL =
    "jdbc:sqlite:" + ACU_DATABASE_FILE.getAbsolutePath();

    private static final List<String>  major_game_questions = List.of(
        "How many hours of sleep do you survive on each night?",
        "Do you run on caffeine, water, or existential dread?",
        "Would you rather sketch in a sketchbook, debug a stubborn bug, or solder a tricky circuit?",
        "Are you more into hoodies or high-vis lab coats?",
        "What's your favorite late-night ritual: scrolling Stack Overflow, binge-watching true-crime, or painting with pastels?",
        "When you hit an error, do you rubber-duck it, consult Freud, or write a prayer?",
        "Do you live by \"It works on my machine,\" \"Amen,\" or \"Show me the ROI?\"",
        "Would you choose a VR headset, a microscope, or a courtroom drama marathon?",
        "Are you a spreadsheet pivot-tablewizard or a vector-path manipulator?",
        "Do you collect resistors, paint swatches, or scripture verses?",
        "Would you rather have Reeves yell at you saying \"TYPE FASTER\" or enjoy an unstressful education?",
        "Do you have a shrine of Reeves memorabilia from ARCO or another movie obsession?",
        "Are you the kind of person who schedules coffee breaks down to the minute?",
        "When faced with a problem, do you “circle back” or \"firewall-reset\" first?",
        "Would you tweak shader code for hours or perfect your joystick skills?",
        "Do you ever look at a motherboard and feel emotions?",
        "Can you recite the periodic table like a rap battle?",
        "Would you rather write a 20-page research paper or fix one missing semicolon?",
        "Do you feel personally attacked by poorly kerned fonts?",
        "Have you ever reorganized your bookshelves by the Dewey Decimal System just for fun?",
        "When you see a microscope, do you whisper 'my precious'?",
        "Is your dream job somewhere between CSI and HGTV?",
        "Would you rather decode cryptic bug reports or argue with a toddler about logic?",
        "Does your idea of fun include pie charts and passive-aggressive emails?",
        "Do you roleplay as a lawyer when someone cuts in line at Starbucks?",
        "Do you make to-do lists for your to-do lists?",
        "Are your DMs full of memes, research studies, or conspiracy theories about fonts?",
        "Do you get emotionally attached to your PowerPoint transitions?",
        "Is your favorite smell that of solder smoke, book pages, or cold brew?",
        "Have you ever whispered sweet nothings to a robot or spreadsheet?"       
        );


        

    private static boolean promptGame(String userInput) {
                String lc = userInput.toLowerCase();
                return lc.contains("undecided")
                    || lc.contains("not sure")
                    || lc.contains("help picking")
                    || lc.contains("need help choosing");
           }

    private static void playGame(BufferedReader reader) throws IOException, SQLException {

        Map<String,Integer> scores = new HashMap<>();

        System.out.println("Welcome to the \"Pick Your Major\" game!");
        try (Connection conn = DriverManager.getConnection(DB_URL)){
            for (String q : major_game_questions) {
                System.out.println("\nGame: " + q);
                String answer = reader.readLine().trim().toLowerCase();
                if (answer.equals("stop")) {
                    System.out.println("Game: You chose to stop the game early. Goodbye!");
                    return;
                }
                for (String token : answer.split("\\W+")) {
                    if (token.isBlank()) continue;

                    String sql = "SELECT majorID from major_keywords WHERE lower(keyword) LIKE ?";

                    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                        pstmt.setString(1, "%" + token + "%");
                        ResultSet rs = pstmt.executeQuery();
                        while (rs.next()) {
                            String major = rs.getString("majorID");
                            scores.put(major, scores.getOrDefault(major, 0) + 1);
                        }
                    } catch (SQLException e) {
                        System.out.println("Error executing SQL query: " + e.getMessage());
                    }
                }
            }
        }
        String highestMatch = scores.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(null);
        if (highestMatch != null) {
            System.out.println("Game: Based on your answers, you might be interested in the " + highestMatch + " major!");
        } else {
            System.out.println("Game: Sorry, we couldn't find a match for your answers.");
        }
        System.out.println("Game: Thanks for playing! Remember, this is just a fun way to explore your options. Good luck with your decision!");
    }

    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.load();
        String APIKEY = dotenv.get("MY_API_KEY");
        System.out.println("API Key: " + APIKEY);


        assistant = new OpenAiAssistantEngine(APIKEY);
        System.out.println("-------------------------");
        System.out.println("Setting up AI Academic Advisor...");

        String assistantId = setupAssistant();
        if (assistantId == null) {
            return;
        }

        startInteractiveChat(assistantId);
    }

    private static String setupAssistant() {
        String assistantId = assistant.createAssistant(
                "gpt-3.5-turbo",
                "Personal AI Academic Advisor",
                null, // i dont think this is really needed
                "You are a real-time chat AI Academic Advisor for Abilene Christian University. Address the student by their first and last name based on the user info provided in the user_info.txt file. Provide information about the student's academic journey, courses, and other academic-related topics.",
                null, //not supported by this specific model
                List.of("file_search"),
                null, // we will add this later with the vector store
                0.5,
                0.5,
                null // we will add these later
        );

        if (assistantId == null) {
            System.out.println("Failed to create assistant");
            return null;
        }

        String userInfoFileID = assistant.uploadFile(USER_INFO_FILE, "assistants");
        String acuDatabaseFileID = assistant.uploadFile(ACU_DATABASE_FILE, "assistants");

        if (userInfoFileID == null || acuDatabaseFileID == null) {
            System.out.println("Failed to upload one or more files");
            return null;
        }

        Map<String, String> fileMetadata = new HashMap<>();
        fileMetadata.put(userInfoFileID, "This fileID (user_info.txt) is associated with the user info");
        fileMetadata.put(acuDatabaseFileID, "This fileID (acu_database.txt) is associated with the ACU database");

        String vectorStoreId = assistant.createVectorStore(
                "User Files",
                Arrays.asList(userInfoFileID, acuDatabaseFileID),
                null,
                null,
                fileMetadata
        );

        if (vectorStoreId == null) {
            System.out.println("Failed to create vector store");
            return null;
        }

        Map<String, Object> toolResources = new HashMap<>();
        Map<String, List<String>> fileSearch = new HashMap<>();
        fileSearch.put("vector_store_ids", List.of(vectorStoreId));
        toolResources.put("file_search", fileSearch);

        boolean updateSuccess = assistant.modifyAssistant(
                assistantId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                toolResources,
                null,
                null
        );

        if (!updateSuccess) {
            System.out.println("Failed to update assistant with vector store");
            return null;
        }

        System.out.println("Assistant setup successfully with ID: " + assistantId);
        return assistantId;
    }

    private static void startInteractiveChat(String assistantId) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String threadId = null;

        System.out.println("\n=== ACU AI Academic Advisor Chat ===");
        System.out.println("If you don't know your major or are unsure about which major to choose, type 'game' to play the \"Pick Your Major\" game");
        System.out.println("Type 'exit' to end the conversation");

        try {
            String userInput;
            while (true) {
                System.out.print("\nYou: ");
                userInput = reader.readLine().trim();

                if (userInput.equalsIgnoreCase("exit")) {
                    break;
                }
                if (userInput.isEmpty()) {
                    continue;
                }

                if (promptGame(userInput)) {
                    try {
                        playGame(reader);
                    } catch (SQLException | IOException e) {
                        System.out.println("Game error: " + e.getMessage());
                    }
                    continue;
                }

                try {
                    if (threadId == null) {
                        List<JSONObject> messages = List.of(
                            new JSONObject().put("role", "user").put("content", userInput)
                        );
                        threadId = assistant.createThread(messages, null, null);
                        if (threadId == null) {
                            System.out.println("Failed to create thread. Please try again.");
                            continue;
                        }
                    } else {
                        String messageId = assistant.addMessageToThread(threadId, userInput);
                        if (messageId == null) {
                            System.out.println("Failed to send message. Please try again.");
                            continue;
                        }
                    }

                    String runId = assistant.createRun(
                        threadId, assistantId,
                        null, null, null, null,
                        null, null, null, null,
                        null, null, null, null,
                        null, null, null, null
                    );

                    if (runId == null) {
                        System.out.println("Failed to create run. Please try again.");
                        continue;
                    }

                    boolean completed = assistant.waitForRunCompletion(threadId, runId, 60, 1000);
                    if (!completed) {
                        System.out.println("The assistant encountered an issue. Please try again.");
                        continue;
                    }

                    List<String> retrievedMessages = assistant.listMessages(threadId, runId);
                    if (retrievedMessages != null && !retrievedMessages.isEmpty()) {
                        System.out.println("\nAdvisor: " + retrievedMessages.get(0));
                    } else {
                        System.out.println("No response received. Please try again.");
                    }
                } catch (Exception e) {
                    System.out.println("Chat error: " + e.getMessage());
                }
            }

            if (threadId != null) {
                assistant.deleteResource("threads", threadId);
            }
        } catch (IOException e) {
            System.out.println("Error reading input: " + e.getMessage());
        }
    }
}
