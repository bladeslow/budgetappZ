package org.example.test;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.beans.property.SimpleIntegerProperty;
import java.util.TreeMap;
import java.util.ArrayList;
import java.util.List;
import java.sql.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javafx.beans.property.SimpleIntegerProperty;
// JavaFX imports
import javafx.application.Application;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

// AWT imports for image processing
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

// ZXing imports for QR code scanning
import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;

public class lolkek extends Application {
    private ObservableList<Transaction> transactions = FXCollections.observableArrayList();
    private ObservableList<Goal> goals = FXCollections.observableArrayList();
    private ObservableList<Debt> debts = FXCollections.observableArrayList();
    private ObservableList<String> incomeCategories = FXCollections.observableArrayList();
    private ObservableList<String> expenseCategories = FXCollections.observableArrayList();

    private double balance = 0.0;
    private double monthlyIncome = 0.0;
    private double monthlyExpenses = 0.0;
    private final ZoneId saratovZone = ZoneId.of("Europe/Saratov");
    private Stage primaryStage;
    private Label balanceLabel;
    private Label monthlyIncomeLabel;
    private Label monthlyExpensesLabel;
    private TableView<Transaction> table;
    private TabPane tabPane;

    private boolean darkTheme = false;
    private Scene currentScene;
    private java.util.List<Button> allButtons = new java.util.ArrayList<>();

    private static final String URL = "jdbc:postgresql://localhost:5432/budgetdb";
    private static final String USER = "budgetuser";
    private static final String PASSWORD = "123456";
    private Connection connection;

    // ==================== НОВЫЕ ПОЛЯ ====================
    // 1. Бюджет по категориям
    private Map<String, Double> categoryBudgets = new HashMap<>();
    private Map<String, Double> categorySpent = new HashMap<>();
    private TableView<BudgetRow> budgetTableView;
    private Label budgetWarningLabel;

    // 2. Календарь платежей (регулярные платежи)
    private ObservableList<RecurringPayment> recurringPayments = FXCollections.observableArrayList();
    private ListView<String> upcomingPaymentsList;

    // 3. График денежного потока
    private LineChart<String, Number> cashFlowChart;
    private ComboBox<String> chartPeriodCombo;

    public static void main(String[] args) {
        launch(args);
    }

    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        this.connectToDatabase();
        primaryStage.setTitle("💰 Личный бюджет");

        this.tabPane = new TabPane();

        Tab mainTab = new Tab("Главная");
        mainTab.setClosable(false);
        mainTab.setContent(this.createMainTabContent());

        Tab chartsTab = new Tab("Диаграммы");
        chartsTab.setClosable(false);
        chartsTab.setContent(this.createChartsTabContent());

        Tab goalsTab = new Tab("Цели");
        goalsTab.setClosable(false);
        goalsTab.setContent(this.createGoalsTabContent());

        Tab debtsTab = new Tab("Долги");
        debtsTab.setClosable(false);
        debtsTab.setContent(this.createDebtsTabContent());

        // НОВЫЕ ВКЛАДКИ
        Tab budgetTab = new Tab("📊 Бюджет");
        budgetTab.setClosable(false);
        budgetTab.setContent(this.createBudgetTabContent());

        Tab calendarTab = new Tab("📅 Платежи");
        calendarTab.setClosable(false);
        calendarTab.setContent(this.createCalendarTabContent());

        Tab cashFlowTab = new Tab("📈 Денежный поток");
        cashFlowTab.setClosable(false);
        cashFlowTab.setContent(this.createCashFlowTabContent());

        this.tabPane.getTabs().addAll(mainTab, chartsTab, goalsTab, debtsTab, budgetTab, calendarTab, cashFlowTab);

        // Добавляем кнопку переключения темы в правый верхний угол
        Button themeButton = createStyledButton(darkTheme ? "☀️ Светлая тема" : "🌙 Тёмная тема", "#3498db");
        themeButton.setOnAction(e -> toggleTheme());

        StackPane root = new StackPane();
        root.getChildren().add(this.tabPane);
        StackPane.setAlignment(themeButton, Pos.TOP_RIGHT);
        StackPane.setMargin(themeButton, new Insets(10, 10, 0, 0));
        root.getChildren().add(themeButton);

        this.currentScene = new Scene(root, 1400.0, 800.0);
        primaryStage.setScene(this.currentScene);
        primaryStage.setMinWidth(1200.0);
        primaryStage.setMinHeight(700.0);
        primaryStage.show();

        if (this.connection != null) {
            this.loadCategoriesFromDatabase();
            this.loadTransactionsFromDatabase();
            this.loadGoalsFromDatabase();
            this.loadDebtsFromDatabase();
            this.loadBudgetsFromDatabase();
            this.loadRecurringPayments();
        }

        applyTheme();
        updateBudgetSpent();
    }

    private void toggleTheme() {
        darkTheme = !darkTheme;
        applyTheme();
    }

    private void applyTheme() {
        String globalStyle = darkTheme ? getDarkThemeCSS() : getLightThemeCSS();
        currentScene.getRoot().setStyle(globalStyle);

        primaryStage.setTitle(darkTheme ? "💰 Личный бюджет 🌙" : "💰 Личный бюджет ☀️");

        for (Button button : allButtons) {
            updateButtonStyle(button);
        }

        if (balanceLabel != null) {
            balanceLabel.setTextFill(darkTheme ? Color.web("#e4e6eb") : Color.web("#2c3e50"));
        }

        if (table != null) {
            table.refresh();
        }
    }

    private void updateLabelsStyle() {
        if (balanceLabel != null) {
            balanceLabel.setTextFill(darkTheme ? Color.web("#e0e0e0") : Color.web("#2c3e50"));
        }
        if (monthlyIncomeLabel != null) {
            monthlyIncomeLabel.setTextFill(Color.web("#27ae60"));
        }
        if (monthlyExpensesLabel != null) {
            monthlyExpensesLabel.setTextFill(Color.web("#e74c3c"));
        }
    }

    private void updateButtonStyle(Button button) {
        String text = button.getText();
        String color;

        if (text.contains("Тёмная") || text.contains("Светлая")) {
            color = "#0078d4";
        } else if (text.contains("Добавить") && (text.contains("цель") || text.contains("долг") || text.equals("Добавить") || text.equals("✅ Добавить"))) {
            color = "#0e7c32";
        } else if (text.contains("Удалить") || text.contains("🗑 Удалить")) {
            color = "#d32f2f";
        } else if (text.contains("Сканировать") && text.contains("чек")) {
            color = "#8e24aa";
        } else if (text.contains("Сканировать") && text.contains("QR")) {
            color = "#0288d1";
        } else if (text.contains("Очистить") || text.contains("🗑 Очистить")) {
            color = "#616161";
        } else if (text.contains("Погасить") || text.contains("✅ Погасить")) {
            color = "#0e7c32";
        } else if (text.contains("Новая категория") || text.contains("➕ Новая категория")) {
            color = "#0288d1";
        } else if (text.contains("💰 Добавить")) {
            color = "#0288d1";
        } else if (text.contains("Экспорт")) {
            color = "#3498db";
        } else {
            color = "#0078d4";
        }

        String finalColor = color;
        button.setStyle(String.format(
                "-fx-background-color: %s; " +
                        "-fx-text-fill: white; " +
                        "-fx-font-weight: bold; " +
                        "-fx-padding: 8 16; " +
                        "-fx-background-radius: 5; " +
                        "-fx-cursor: hand; " +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 3, 0, 0, 1);",
                finalColor
        ));

        button.setOnMouseEntered(null);
        button.setOnMouseExited(null);

        button.setOnMouseEntered(e -> button.setStyle(String.format(
                "-fx-background-color: derive(%s, -15%%); " +
                        "-fx-text-fill: white; " +
                        "-fx-font-weight: bold; " +
                        "-fx-padding: 8 16; " +
                        "-fx-background-radius: 5; " +
                        "-fx-cursor: hand; " +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.4), 5, 0, 0, 2);",
                finalColor)));

        button.setOnMouseExited(e -> button.setStyle(String.format(
                "-fx-background-color: %s; " +
                        "-fx-text-fill: white; " +
                        "-fx-font-weight: bold; " +
                        "-fx-padding: 8 16; " +
                        "-fx-background-radius: 5; " +
                        "-fx-cursor: hand; " +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 3, 0, 0, 1);",
                finalColor)));
    }

    private void updateComponentStyles(Node node) {
        if (node == null) return;

        if (node instanceof Button) {
            updateButtonStyle((Button) node);
        }

        if (node instanceof Label) {
            Label label = (Label) node;
            if (darkTheme) {
                label.setTextFill(Color.web("#e0e0e0"));
            } else {
                if (label.getText().contains("Доходы") || label.getText().contains("баланс")) {
                    // Сохраняем цвет для специальных меток
                } else {
                    label.setTextFill(Color.web("#2c3e50"));
                }
            }
        }

        if (node instanceof TextField || node instanceof TextArea ||
                node instanceof ComboBox || node instanceof DatePicker) {
            if (darkTheme) {
                node.setStyle("-fx-control-inner-background: #3c3c3c; -fx-text-fill: #e0e0e0; -fx-prompt-text-fill: #888888; -fx-background-color: #3c3c3c;");
            } else {
                node.setStyle("-fx-control-inner-background: white; -fx-text-fill: #2c3e50; -fx-prompt-text-fill: #888888; -fx-background-color: white;");
            }
        }

        if (node instanceof TableView) {
            TableView<?> tableView = (TableView<?>) node;
            tableView.setStyle(darkTheme ?
                    "-fx-background-color: #3c3c3c; -fx-border-color: #4a4a4a;" :
                    "-fx-background-color: white; -fx-border-color: #dee2e6;");
            tableView.refresh();
        }

        if (node instanceof Parent) {
            for (Node child : ((Parent) node).getChildrenUnmodifiable()) {
                updateComponentStyles(child);
            }
        }
    }

    private String getLightThemeCSS() {
        return """
        .root {
            -fx-base: #f8f9fa;
            -fx-background: #f0f2f5;
            -fx-control-inner-background: white;
            -fx-text-fill: #2c3e50;
            -fx-font-family: 'Segoe UI';
        }
        
        .tab-pane .tab-header-area {
            -fx-padding: 10 0 0 0;
        }
        
        .tab-pane .tab-header-background {
            -fx-background-color: #f0f2f5;
        }
        
        .tab-pane .tab {
            -fx-background-color: #e9ecef;
            -fx-padding: 8 20 8 20;
            -fx-background-radius: 5 5 0 0;
        }
        
        .tab-pane .tab:selected {
            -fx-background-color: #3498db;
        }
        
        .tab .tab-label {
            -fx-text-fill: #495057;
            -fx-font-weight: bold;
        }
        
        .tab:selected .tab-label {
            -fx-text-fill: white;
        }
        
        .table-view {
            -fx-background-color: transparent;
            -fx-border-color: #dee2e6;
            -fx-border-radius: 5;
        }
        
        .table-view .column-header {
            -fx-background-color: #e9ecef;
            -fx-border-color: #dee2e6;
        }
        
        .table-view .column-header .label {
            -fx-text-fill: #495057;
            -fx-font-weight: bold;
        }
        
        .table-row-cell {
            -fx-background-color: white;
            -fx-border-color: #f1f3f5;
        }
        
        .table-row-cell:odd {
            -fx-background-color: #f8f9fa;
        }
        
        .table-row-cell:selected {
            -fx-background-color: #3498db;
        }
        
        .table-row-cell:selected .text {
            -fx-fill: white;
        }
        
        .scroll-bar {
            -fx-background-color: #f8f9fa;
        }
        
        .scroll-bar .thumb {
            -fx-background-color: #cbd5e0;
        }
        
        .text-field, .text-area, .combo-box, .date-picker {
            -fx-control-inner-background: white;
            -fx-text-fill: #2c3e50;
            -fx-prompt-text-fill: #888888;
            -fx-background-color: white;
        }
        
        .check-box {
            -fx-text-fill: #2c3e50;
        }
        """;
    }

    private String getDarkThemeCSS() {
        return """
        .root {
            -fx-base: #1a1c23;
            -fx-background: #1a1c23;
            -fx-control-inner-background: #252833;
            -fx-text-fill: #e4e6eb;
            -fx-font-family: 'Segoe UI';
        }
        
        .tab-pane {
            -fx-background-color: #1a1c23;
        }
        
        .tab-pane .tab-header-background {
            -fx-background-color: #1a1c23;
        }
        
        .tab-pane .tab-content-area {
            -fx-background-color: #1a1c23;
        }
        
        .tab-pane .tab {
            -fx-background-color: #252833;
            -fx-padding: 8 20 8 20;
            -fx-background-radius: 8 8 0 0;
        }
        
        .tab-pane .tab:selected {
            -fx-background-color: #2d8cf0;
        }
        
        .tab .tab-label {
            -fx-text-fill: #a0a3b0;
            -fx-font-weight: bold;
        }
        
        .tab:selected .tab-label {
            -fx-text-fill: white;
        }
        
        .table-view {
            -fx-background-color: #1a1c23;
            -fx-border-color: #2d3038;
            -fx-border-radius: 8;
        }
        
        .table-view .column-header-background {
            -fx-background-color: #252833;
        }
        
        .table-view .column-header {
            -fx-background-color: #252833;
            -fx-border-color: #2d3038;
        }
        
        .table-view .column-header .label {
            -fx-text-fill: #e4e6eb;
            -fx-font-weight: bold;
        }
        
        .table-view .table-cell {
            -fx-text-fill: #c8cbd1;
            -fx-border-color: #2d3038;
        }
        
        .table-row-cell {
            -fx-background-color: #1a1c23;
        }
        
        .table-row-cell:odd {
            -fx-background-color: #20222b;
        }
        
        .table-row-cell:selected {
            -fx-background-color: #2d8cf0;
        }
        
        .table-row-cell:selected .table-cell {
            -fx-text-fill: white;
        }
        
        .text-field, .text-area, .combo-box, .date-picker {
            -fx-background-color: #252833;
            -fx-text-fill: #e4e6eb;
            -fx-prompt-text-fill: #6b6f7a;
            -fx-border-color: #2d3038;
            -fx-border-radius: 6;
            -fx-background-radius: 6;
        }
        
        .text-field:focused, .text-area:focused, .combo-box:focused {
            -fx-border-color: #2d8cf0;
        }
        
        .combo-box .list-cell {
            -fx-text-fill: #e4e6eb;
            -fx-background-color: #252833;
        }
        
        .combo-box-popup .list-view {
            -fx-background-color: #252833;
        }
        
        .combo-box-popup .list-cell {
            -fx-text-fill: #e4e6eb;
        }
        
        .combo-box-popup .list-cell:hover {
            -fx-background-color: #2d8cf0;
        }
        
        .check-box {
            -fx-text-fill: #e4e6eb;
        }
        
        .check-box .box {
            -fx-background-color: #252833;
            -fx-border-color: #2d8cf0;
            -fx-border-radius: 3;
        }
        
        .check-box:selected .mark {
            -fx-background-color: #2d8cf0;
        }
        
        .scroll-bar {
            -fx-background-color: #1a1c23;
        }
        
        .scroll-bar .thumb {
            -fx-background-color: #3a3e4a;
            -fx-background-radius: 10;
        }
        
        .scroll-bar .thumb:hover {
            -fx-background-color: #4a4e5c;
        }
        
        .label {
            -fx-text-fill: #e4e6eb;
        }
        
        .label:header {
            -fx-text-fill: #e4e6eb;
        }
        
        .date-picker-popup {
            -fx-background-color: #252833;
        }
        
        .date-picker-popup .month-year-pane {
            -fx-background-color: #252833;
        }
        
        .date-picker-popup .calendar-grid {
            -fx-background-color: #252833;
        }
        
        .date-picker-popup .day-cell {
            -fx-text-fill: #e4e6eb;
            -fx-background-color: #252833;
        }
        
        .date-picker-popup .day-cell:hover {
            -fx-background-color: #2d8cf0;
        }
        
        .date-picker-popup .day-cell.selected {
            -fx-background-color: #2d8cf0;
        }
        
        .dialog-pane {
            -fx-background-color: #252833;
        }
        
        .dialog-pane .label {
            -fx-text-fill: #e4e6eb;
        }
        
        .dialog-pane .header-panel {
            -fx-background-color: #1a1c23;
        }
        
        .dialog-pane .content {
            -fx-background-color: #252833;
        }
        """;
    }

    private BorderPane createMainTabContent() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(20.0));

        VBox headerBox = this.createHeaderBox();
        root.setTop(headerBox);

        this.table = this.createTransactionTable();
        VBox tableBox = this.createTableBox(this.table);
        root.setCenter(tableBox);

        VBox formBox = this.createFormBox(this.table);
        root.setLeft(formBox);

        return root;
    }

    // ==================== 1. БЮДЖЕТ ПО КАТЕГОРИЯМ ====================

    private VBox createBudgetTabContent() {
        VBox box = new VBox(15);
        box.setPadding(new Insets(20));
        box.setAlignment(Pos.TOP_CENTER);

        Label title = new Label("📊 Бюджет по категориям");
        title.setFont(Font.font("System", FontWeight.BOLD, 20));

        HBox form = new HBox(10);
        form.setAlignment(Pos.CENTER);

        ComboBox<String> catCombo = new ComboBox<>();
        catCombo.setPromptText("Категория");
        catCombo.setPrefWidth(200);
        catCombo.setItems(expenseCategories);

        TextField limitField = new TextField();
        limitField.setPromptText("Лимит ₽");
        limitField.setPrefWidth(150);

        Button setBtn = createStyledButton("Установить лимит", "#27ae60");

        form.getChildren().addAll(new Label("Категория:"), catCombo, new Label("Лимит:"), limitField, setBtn);

        budgetTableView = new TableView<>();
        budgetTableView.setPrefHeight(350);

        TableColumn<BudgetRow, String> catCol = new TableColumn<>("Категория");
        catCol.setCellValueFactory(new PropertyValueFactory<>("category"));
        catCol.setPrefWidth(200);

        TableColumn<BudgetRow, Double> limitCol = new TableColumn<>("Лимит ₽");
        limitCol.setCellValueFactory(new PropertyValueFactory<>("limit"));
        limitCol.setPrefWidth(120);

        TableColumn<BudgetRow, Double> spentCol = new TableColumn<>("Потрачено ₽");
        spentCol.setCellValueFactory(new PropertyValueFactory<>("spent"));
        spentCol.setPrefWidth(120);
        spentCol.setCellFactory(col -> new TableCell<BudgetRow, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (!empty && item != null) {
                    setText(String.format("%.2f", item));
                    BudgetRow row = getTableView().getItems().get(getIndex());
                    setTextFill(item > row.getLimit() ? Color.RED : Color.GREEN);
                } else {
                    setText(null);
                }
            }
        });

        TableColumn<BudgetRow, Double> remainCol = new TableColumn<>("Осталось ₽");
        remainCol.setCellValueFactory(new PropertyValueFactory<>("remaining"));
        remainCol.setPrefWidth(120);
        remainCol.setCellFactory(col -> new TableCell<BudgetRow, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (!empty && item != null) {
                    setText(String.format("%.2f", item));
                    setTextFill(item < 0 ? Color.RED : Color.GREEN);
                } else {
                    setText(null);
                }
            }
        });

        TableColumn<BudgetRow, Double> percentCol = new TableColumn<>("Прогресс");
        percentCol.setCellValueFactory(new PropertyValueFactory<>("percent"));
        percentCol.setPrefWidth(100);
        percentCol.setCellFactory(col -> new TableCell<BudgetRow, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (!empty && item != null) {
                    setText(String.format("%.1f%%", item));
                    if (item >= 100) setTextFill(Color.RED);
                    else if (item >= 80) setTextFill(Color.ORANGE);
                    else setTextFill(Color.GREEN);
                } else {
                    setText(null);
                }
            }
        });

        budgetWarningLabel = new Label();
        budgetWarningLabel.setFont(Font.font("System", FontWeight.BOLD, 14));

        budgetTableView.getColumns().addAll(catCol, limitCol, spentCol, remainCol, percentCol);

        setBtn.setOnAction(e -> {
            String cat = catCombo.getValue();
            if (cat == null) {
                showAlert("Ошибка", "Выберите категорию", "");
                return;
            }
            try {
                double limit = Double.parseDouble(limitField.getText());
                if (limit <= 0) throw new NumberFormatException();
                categoryBudgets.put(cat, limit);
                saveBudgetToDatabase(cat, limit);
                refreshBudgetTable();
                limitField.clear();
                showAlert("Успех", "Лимит установлен", cat + ": " + limit + " ₽");
            } catch (NumberFormatException ex) {
                showAlert("Ошибка", "Некорректная сумма", "");
            }
        });

        refreshBudgetTable();

        box.getChildren().addAll(title, form, budgetTableView, budgetWarningLabel);
        return box;
    }

    private void refreshBudgetTable() {
        ObservableList<BudgetRow> rows = FXCollections.observableArrayList();
        for (String cat : expenseCategories) {
            double limit = categoryBudgets.getOrDefault(cat, 0.0);
            double spent = categorySpent.getOrDefault(cat, 0.0);
            if (limit > 0) {
                rows.add(new BudgetRow(cat, limit, spent));
            }
        }
        budgetTableView.setItems(rows);
    }

    private void updateBudgetSpent() {
        categorySpent.clear();
        YearMonth current = YearMonth.now();
        for (Transaction t : transactions) {
            if (t.getAmount() < 0 && YearMonth.from(t.getDateTime()).equals(current)) {
                categorySpent.merge(t.getCategory(), Math.abs(t.getAmount()), Double::sum);
            }
        }
        boolean warn = false;
        for (Map.Entry<String, Double> e : categoryBudgets.entrySet()) {
            if (categorySpent.getOrDefault(e.getKey(), 0.0) > e.getValue()) {
                budgetWarningLabel.setText("⚠️ ПРЕВЫШЕН ЛИМИТ: " + e.getKey());
                budgetWarningLabel.setTextFill(Color.RED);
                warn = true;
                break;
            }
        }
        if (!warn && !categoryBudgets.isEmpty()) {
            budgetWarningLabel.setText("✅ Все лимиты в порядке");
            budgetWarningLabel.setTextFill(Color.GREEN);
        }
        refreshBudgetTable();
    }

    private void saveBudgetToDatabase(String category, double limit) {
        if (connection == null) return;
        String month = YearMonth.now().toString();
        String sql = "INSERT INTO category_budgets (category_name, limit_amount, month_year) VALUES (?,?,?) ON CONFLICT (category_name, month_year) DO UPDATE SET limit_amount=?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, category);
            stmt.setDouble(2, limit);
            stmt.setString(3, month);
            stmt.setDouble(4, limit);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Бюджет ошибка: " + e.getMessage());
        }
    }

    private void loadBudgetsFromDatabase() {
        if (connection == null) return;
        String month = YearMonth.now().toString();
        String sql = "SELECT category_name, limit_amount FROM category_budgets WHERE month_year = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, month);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                categoryBudgets.put(rs.getString("category_name"), rs.getDouble("limit_amount"));
            }
        } catch (SQLException e) {
            System.out.println("Загрузка бюджетов: " + e.getMessage());
        }
    }

    // ==================== 2. ЭКСПОРТ В CSV ====================

    private void exportToCSV() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Сохранить отчёт");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV файлы", "*.csv"));
        fileChooser.setInitialFileName("transactions_" + LocalDate.now() + ".csv");

        File file = fileChooser.showSaveDialog(primaryStage);
        if (file != null) {
            try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
                pw.println("Дата;Тип;Категория;Сумма;От кого/Куда;Описание");

                for (Transaction t : transactions) {
                    pw.printf("%s;%s;%s;%.2f;%s;%s%n",
                            t.getFormattedDateTime(),
                            t.getType(),
                            t.getCategory(),
                            Math.abs(t.getAmount()),
                            t.getFrom(),
                            t.getDescription().replace(";", ",")
                    );
                }

                pw.println();
                pw.println("ИТОГИ;;;");
                double totalIncome = transactions.stream().filter(t -> t.getAmount() > 0).mapToDouble(Transaction::getAmount).sum();
                double totalExpense = transactions.stream().filter(t -> t.getAmount() < 0).mapToDouble(t -> Math.abs(t.getAmount())).sum();
                pw.printf("Общий доход;;;%.2f%n", totalIncome);
                pw.printf("Общий расход;;;%.2f%n", totalExpense);
                pw.printf("Баланс;;;%.2f%n", balance);

                showAlert("Успех", "Экспорт завершён", "Файл: " + file.getName());
            } catch (Exception e) {
                showAlert("Ошибка", "Не удалось сохранить файл", e.getMessage());
            }
        }
    }

    // ==================== 3. КАЛЕНДАРЬ ПЛАТЕЖЕЙ ====================

    private VBox createCalendarTabContent() {
        VBox box = new VBox(15);
        box.setPadding(new Insets(20));
        box.setAlignment(Pos.TOP_CENTER);

        Label title = new Label("📅 Регулярные платежи");
        title.setFont(Font.font("System", FontWeight.BOLD, 20));

        HBox form = new HBox(10);
        form.setAlignment(Pos.CENTER);

        TextField nameField = new TextField();
        nameField.setPromptText("Название");
        nameField.setPrefWidth(150);

        TextField amountField = new TextField();
        amountField.setPromptText("Сумма");
        amountField.setPrefWidth(100);

        ComboBox<String> catCombo = new ComboBox<>();
        catCombo.setPromptText("Категория");
        catCombo.setPrefWidth(120);
        catCombo.setItems(expenseCategories);

        Spinner<Integer> daySpinner = new Spinner<>(1, 31, 1);
        daySpinner.setPrefWidth(80);
        daySpinner.setEditable(true);

        Button addBtn = createStyledButton("➕ Добавить", "#27ae60");

        form.getChildren().addAll(nameField, amountField, catCombo, new Label("Число:"), daySpinner, addBtn);

        TableView<RecurringPayment> table = new TableView<>();
        table.setPrefHeight(250);

        TableColumn<RecurringPayment, String> nameCol = new TableColumn<>("Платёж");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setPrefWidth(180);

        TableColumn<RecurringPayment, Double> amountCol = new TableColumn<>("Сумма");
        amountCol.setCellValueFactory(new PropertyValueFactory<>("amount"));
        amountCol.setPrefWidth(120);
        amountCol.setCellFactory(col -> new TableCell<RecurringPayment, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : String.format("%.2f ₽", item));
            }
        });

        TableColumn<RecurringPayment, Integer> dayCol = new TableColumn<>("День месяца");
        dayCol.setCellValueFactory(new PropertyValueFactory<>("dayOfMonth"));
        dayCol.setPrefWidth(100);

        TableColumn<RecurringPayment, String> catCol2 = new TableColumn<>("Категория");
        catCol2.setCellValueFactory(new PropertyValueFactory<>("category"));
        catCol2.setPrefWidth(150);

        TableColumn<RecurringPayment, Void> actionsCol = new TableColumn<>("Действия");
        actionsCol.setPrefWidth(80);
        actionsCol.setCellFactory(col -> new TableCell<RecurringPayment, Void>() {
            private final Button deleteBtn = createStyledButton("🗑", "#e74c3c");
            {
                deleteBtn.setOnAction(e -> {
                    RecurringPayment p = getTableView().getItems().get(getIndex());
                    deleteRecurringPayment(p);
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                setGraphic(empty ? null : deleteBtn);
            }
        });

        table.getColumns().addAll(nameCol, amountCol, dayCol, catCol2, actionsCol);
        table.setItems(recurringPayments);

        upcomingPaymentsList = new ListView<>();
        upcomingPaymentsList.setPrefHeight(200);

        addBtn.setOnAction(e -> {
            try {
                String name = nameField.getText();
                double amount = Double.parseDouble(amountField.getText());
                String cat = catCombo.getValue();
                int day = daySpinner.getValue();

                if (name.isEmpty() || cat == null) {
                    showAlert("Ошибка", "Заполните все поля", "");
                    return;
                }

                RecurringPayment p = new RecurringPayment(name, amount, day, cat);
                recurringPayments.add(p);
                saveRecurringPaymentToDatabase(p);

                nameField.clear();
                amountField.clear();
                updateUpcomingPayments();
                showAlert("Успех", "Платёж добавлен", "");
            } catch (NumberFormatException ex) {
                showAlert("Ошибка", "Некорректная сумма", "");
            }
        });

        updateUpcomingPayments();

        box.getChildren().addAll(title, form, new Label("📋 Мои платежи:"), table, new Label("📅 Предстоящие в этом месяце:"), upcomingPaymentsList);
        return box;
    }

    private void updateUpcomingPayments() {
        upcomingPaymentsList.getItems().clear();
        int currentDay = LocalDate.now().getDayOfMonth();

        java.util.List<RecurringPayment> upcoming = recurringPayments.stream()
                .filter(p -> p.getDayOfMonth() >= currentDay)
                .sorted(java.util.Comparator.comparingInt(RecurringPayment::getDayOfMonth))
                .collect(java.util.stream.Collectors.toList());

        for (RecurringPayment p : upcoming) {
            upcomingPaymentsList.getItems().add(
                    String.format("%d числа — %s: %.2f ₽ (%s)",
                            p.getDayOfMonth(), p.getName(), p.getAmount(), p.getCategory())
            );
        }

        if (upcoming.isEmpty()) {
            upcomingPaymentsList.getItems().add("✅ На этот месяц больше нет запланированных платежей");
        }
    }

    private void saveRecurringPaymentToDatabase(RecurringPayment p) {
        if (connection == null) return;
        String sql = "INSERT INTO recurring_payments (name, amount, day_of_month, category) VALUES (?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, p.getName());
            stmt.setDouble(2, p.getAmount());
            stmt.setInt(3, p.getDayOfMonth());
            stmt.setString(4, p.getCategory());
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Ошибка сохранения платежа: " + e.getMessage());
        }
    }

    private void loadRecurringPayments() {
        if (connection == null) return;
        String sql = "SELECT * FROM recurring_payments ORDER BY day_of_month";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            recurringPayments.clear();
            while (rs.next()) {
                recurringPayments.add(new RecurringPayment(
                        rs.getString("name"),
                        rs.getDouble("amount"),
                        rs.getInt("day_of_month"),
                        rs.getString("category")
                ));
            }
            updateUpcomingPayments();
        } catch (SQLException e) {
            System.out.println("Ошибка загрузки платежей: " + e.getMessage());
        }
    }

    private void deleteRecurringPayment(RecurringPayment p) {
        if (connection != null) {
            String sql = "DELETE FROM recurring_payments WHERE name = ? AND amount = ? AND day_of_month = ?";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, p.getName());
                stmt.setDouble(2, p.getAmount());
                stmt.setInt(3, p.getDayOfMonth());
                stmt.executeUpdate();
            } catch (SQLException e) {
                System.out.println("Ошибка удаления: " + e.getMessage());
            }
        }
        recurringPayments.remove(p);
        updateUpcomingPayments();
    }

    // ==================== 4. ГРАФИК ДЕНЕЖНОГО ПОТОКА ====================

    private VBox createCashFlowTabContent() {
        VBox box = new VBox(15);
        box.setPadding(new Insets(20));
        box.setAlignment(Pos.TOP_CENTER);

        Label title = new Label("📈 Денежный поток");
        title.setFont(Font.font("System", FontWeight.BOLD, 20));

        HBox controls = new HBox(10);
        controls.setAlignment(Pos.CENTER);

        chartPeriodCombo = new ComboBox<>();
        chartPeriodCombo.getItems().addAll("Дни", "Недели", "Месяцы");
        chartPeriodCombo.setValue("Дни");

        Button refreshBtn = createStyledButton("🔄 Обновить", "#3498db");
        refreshBtn.setOnAction(e -> updateCashFlowChart());

        controls.getChildren().addAll(new Label("Период:"), chartPeriodCombo, refreshBtn);

        // Исправлено: используем CategoryAxis для строковых меток
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        xAxis.setLabel("Период");
        yAxis.setLabel("Сумма (₽)");

        cashFlowChart = new LineChart<>(xAxis, yAxis);
        cashFlowChart.setTitle("Динамика доходов и расходов");
        cashFlowChart.setPrefHeight(500);

        updateCashFlowChart();

        box.getChildren().addAll(title, controls, cashFlowChart);
        return box;
    }

    private void updateCashFlowChart() {
        cashFlowChart.getData().clear();

        String period = chartPeriodCombo.getValue();
        YearMonth currentMonth = YearMonth.now();

        XYChart.Series<String, Number> incomeSeries = new XYChart.Series<>();
        incomeSeries.setName("📈 Доходы");

        XYChart.Series<String, Number> expenseSeries = new XYChart.Series<>();
        expenseSeries.setName("📉 Расходы");

        if ("Дни".equals(period)) {
            int days = currentMonth.lengthOfMonth();
            Map<Integer, Double> dailyIncome = new HashMap<>();
            Map<Integer, Double> dailyExpense = new HashMap<>();

            for (int i = 1; i <= days; i++) {
                dailyIncome.put(i, 0.0);
                dailyExpense.put(i, 0.0);
            }

            for (Transaction t : transactions) {
                if (YearMonth.from(t.getDateTime()).equals(currentMonth)) {
                    int day = t.getDateTime().getDayOfMonth();
                    if (t.getAmount() > 0) {
                        dailyIncome.merge(day, t.getAmount(), Double::sum);
                    } else {
                        dailyExpense.merge(day, Math.abs(t.getAmount()), Double::sum);
                    }
                }
            }

            for (int i = 1; i <= days; i++) {
                incomeSeries.getData().add(new XYChart.Data<>(String.valueOf(i), dailyIncome.get(i)));
                expenseSeries.getData().add(new XYChart.Data<>(String.valueOf(i), dailyExpense.get(i)));
            }
        }
        else if ("Недели".equals(period)) {
            Map<Integer, Double> weeklyIncome = new HashMap<>();
            Map<Integer, Double> weeklyExpense = new HashMap<>();

            for (int i = 1; i <= 5; i++) {
                weeklyIncome.put(i, 0.0);
                weeklyExpense.put(i, 0.0);
            }

            for (Transaction t : transactions) {
                if (YearMonth.from(t.getDateTime()).equals(currentMonth)) {
                    int week = (t.getDateTime().getDayOfMonth() - 1) / 7 + 1;
                    if (t.getAmount() > 0) {
                        weeklyIncome.merge(week, t.getAmount(), Double::sum);
                    } else {
                        weeklyExpense.merge(week, Math.abs(t.getAmount()), Double::sum);
                    }
                }
            }

            for (int i = 1; i <= 5; i++) {
                incomeSeries.getData().add(new XYChart.Data<>("Неделя " + i, weeklyIncome.get(i)));
                expenseSeries.getData().add(new XYChart.Data<>("Неделя " + i, weeklyExpense.get(i)));
            }
        }
        else if ("Месяцы".equals(period)) {
            Map<YearMonth, Double> monthlyIncome = new TreeMap<>();
            Map<YearMonth, Double> monthlyExpense = new TreeMap<>();

            for (Transaction t : transactions) {
                YearMonth ym = YearMonth.from(t.getDateTime());
                if (t.getAmount() > 0) {
                    monthlyIncome.merge(ym, t.getAmount(), Double::sum);
                } else {
                    monthlyExpense.merge(ym, Math.abs(t.getAmount()), Double::sum);
                }
            }

            List<YearMonth> last12 = new ArrayList<>();
            for (int i = 11; i >= 0; i--) {
                last12.add(YearMonth.now().minusMonths(i));
            }

            for (YearMonth ym : last12) {
                String label = ym.format(DateTimeFormatter.ofPattern("MMM yy"));
                incomeSeries.getData().add(new XYChart.Data<>(label, monthlyIncome.getOrDefault(ym, 0.0)));
                expenseSeries.getData().add(new XYChart.Data<>(label, monthlyExpense.getOrDefault(ym, 0.0)));
            }
        }

        cashFlowChart.getData().addAll(incomeSeries, expenseSeries);
    }

    // ==================== ОСТАЛЬНЫЕ МЕТОДЫ (ВАШИ, БЕЗ ИЗМЕНЕНИЙ) ====================

    private VBox createDebtsTabContent() {
        VBox debtsBox = new VBox(20.0);
        debtsBox.setPadding(new Insets(20.0));
        debtsBox.setAlignment(Pos.TOP_CENTER);

        Label titleLabel = new Label("💰 Управление долгами");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 20.0));
        titleLabel.setTextFill(darkTheme ? Color.web("#e0e0e0") : Color.web("#2c3e50"));

        HBox summaryBox = new HBox(20.0);
        summaryBox.setAlignment(Pos.CENTER);

        Label totalDebtsLabel = new Label();
        Label totalLoansLabel = new Label();
        summaryBox.getChildren().addAll(totalDebtsLabel, totalLoansLabel);

        VBox formBox = new VBox(10.0);
        formBox.setPadding(new Insets(20.0));
        formBox.setPrefWidth(400.0);

        if (darkTheme) {
            formBox.setStyle("-fx-background-color: #252833; -fx-border-color: #2d3038; -fx-border-radius: 10; -fx-background-radius: 10;");
        } else {
            formBox.setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #dee2e6; -fx-border-radius: 10; -fx-background-radius: 10;");
        }

        Label formTitle = new Label("Новый долг");
        formTitle.setFont(Font.font("System", FontWeight.BOLD, 16.0));

        ComboBox<String> debtTypeCombo = new ComboBox<>();
        debtTypeCombo.getItems().addAll("Я должен", "Должны мне");
        debtTypeCombo.setValue("Я должен");
        debtTypeCombo.setPrefWidth(300.0);

        TextField personField = new TextField();
        personField.setPromptText("Кому/От кого должны");
        personField.setPrefWidth(300.0);

        TextField amountField = new TextField();
        amountField.setPromptText("Сумма долга");
        amountField.setPrefWidth(300.0);

        TextField descriptionField = new TextField();
        descriptionField.setPromptText("Описание");
        descriptionField.setPrefWidth(300.0);

        DatePicker dueDatePicker = new DatePicker();
        dueDatePicker.setPromptText("Дата возврата (опционально)");
        dueDatePicker.setPrefWidth(300.0);

        Button addDebtButton = createStyledButton("➕ Добавить долг", "#27ae60");

        formBox.getChildren().addAll(formTitle, debtTypeCombo, personField,
                amountField, descriptionField, dueDatePicker, addDebtButton);

        TableView<Debt> debtsTable = this.createDebtsTable();
        VBox tableBox = new VBox(10.0);
        tableBox.getChildren().addAll(new Label("Мои долги"), debtsTable);

        HBox contentBox = new HBox(20.0);
        contentBox.setAlignment(Pos.TOP_CENTER);
        contentBox.getChildren().addAll(formBox, tableBox);

        Runnable updateStats = () -> {
            double totalDebt = debts.stream().filter(d -> d.getType().equals("Я должен")).mapToDouble(Debt::getAmount).sum();
            double totalLoan = debts.stream().filter(d -> d.getType().equals("Должны мне")).mapToDouble(Debt::getAmount).sum();
            totalDebtsLabel.setText(String.format("💸 Я должен: %.2f ₽", totalDebt));
            totalDebtsLabel.setTextFill(Color.web("#e74c3c"));
            totalLoansLabel.setText(String.format("💰 Должны мне: %.2f ₽", totalLoan));
            totalLoansLabel.setTextFill(Color.web("#27ae60"));
        };

        updateStats.run();

        addDebtButton.setOnAction(e -> {
            try {
                String type = debtTypeCombo.getValue();
                String person = personField.getText();
                double amount = Double.parseDouble(amountField.getText());
                String description = descriptionField.getText();
                LocalDate dueDate = dueDatePicker.getValue();

                if (person.isEmpty()) {
                    showAlert("Ошибка", "Пустое поле", "Введите имя человека");
                    return;
                }

                Debt debt = new Debt(type, person, amount, description, dueDate);

                if (connection != null) {
                    String sql = "INSERT INTO debts (debt_type, person_name, amount, description, due_date) VALUES (?, ?, ?, ?, ?)";
                    try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                        pstmt.setString(1, type);
                        pstmt.setString(2, person);
                        pstmt.setDouble(3, amount);
                        pstmt.setString(4, description);
                        pstmt.setDate(5, dueDate != null ? Date.valueOf(dueDate) : null);
                        pstmt.executeUpdate();
                    }
                }

                debts.add(debt);
                personField.clear();
                amountField.clear();
                descriptionField.clear();
                dueDatePicker.setValue(null);
                updateStats.run();
                debtsTable.refresh();

            } catch (NumberFormatException ex) {
                showAlert("Ошибка", "Некорректная сумма", "Введите числовое значение");
            } catch (SQLException ex) {
                System.out.println("Ошибка сохранения долга: " + ex.getMessage());
            }
        });

        debtsBox.getChildren().addAll(titleLabel, summaryBox, contentBox);
        return debtsBox;
    }

    private TableView<Debt> createDebtsTable() {
        TableView<Debt> table = new TableView<>();
        table.setPlaceholder(new Label("Нет долгов для отображения"));
        table.setPrefSize(600.0, 400.0);

        TableColumn<Debt, String> typeCol = new TableColumn<>("Тип");
        typeCol.setCellValueFactory(new PropertyValueFactory<>("type"));
        typeCol.setPrefWidth(100.0);
        typeCol.setCellFactory(column -> new TableCell<Debt, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (!empty && item != null) {
                    setText(item);
                    if ("Я должен".equals(item)) {
                        setTextFill(Color.web("#e74c3c"));
                    } else {
                        setTextFill(Color.web("#27ae60"));
                    }
                } else {
                    setText(null);
                }
            }
        });

        TableColumn<Debt, String> personCol = new TableColumn<>("Кому/От кого");
        personCol.setCellValueFactory(new PropertyValueFactory<>("person"));
        personCol.setPrefWidth(150.0);

        TableColumn<Debt, Double> amountCol = new TableColumn<>("Сумма");
        amountCol.setCellValueFactory(new PropertyValueFactory<>("amount"));
        amountCol.setPrefWidth(120.0);
        amountCol.setCellFactory(column -> new TableCell<Debt, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (!empty && item != null) {
                    setText(String.format("%.2f ₽", item));
                } else {
                    setText(null);
                }
            }
        });

        TableColumn<Debt, String> descriptionCol = new TableColumn<>("Описание");
        descriptionCol.setCellValueFactory(new PropertyValueFactory<>("description"));
        descriptionCol.setPrefWidth(150.0);

        TableColumn<Debt, String> dueDateCol = new TableColumn<>("Дата возврата");
        dueDateCol.setCellValueFactory(new PropertyValueFactory<>("dueDateFormatted"));
        dueDateCol.setPrefWidth(120.0);
        dueDateCol.setCellFactory(column -> new TableCell<Debt, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (!empty && item != null) {
                    setText(item);
                    if (item.contains("Просрочен")) {
                        setTextFill(Color.web("#e74c3c"));
                        setStyle("-fx-font-weight: bold;");
                    }
                } else {
                    setText(null);
                }
            }
        });

        TableColumn<Debt, Void> actionsCol = new TableColumn<>("Действия");
        actionsCol.setPrefWidth(150.0);
        actionsCol.setCellFactory(param -> new TableCell<Debt, Void>() {
            private final Button repayButton = createStyledButton("✅ Погасить", "#27ae60");
            private final Button deleteButton = createStyledButton("🗑 Удалить", "#e74c3c");

            {
                repayButton.setOnAction(event -> {
                    Debt debt = getTableView().getItems().get(getIndex());

                    Alert alert = new Alert(AlertType.CONFIRMATION);
                    alert.setTitle("Погашение долга");
                    alert.setHeaderText("Погасить долг");
                    alert.setContentText("Вы уверены, что хотите отметить этот долг как погашенный?");

                    alert.showAndWait().ifPresent(response -> {
                        if (response == ButtonType.OK) {
                            if (connection != null) {
                                String sql = "DELETE FROM debts WHERE person_name = ? AND amount = ? AND description = ?";
                                try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                                    pstmt.setString(1, debt.getPerson());
                                    pstmt.setDouble(2, debt.getAmount());
                                    pstmt.setString(3, debt.getDescription());
                                    pstmt.executeUpdate();
                                } catch (SQLException e) {
                                    System.out.println("Ошибка удаления: " + e.getMessage());
                                }
                            }
                            debts.remove(debt);
                            getTableView().refresh();
                        }
                    });
                });

                deleteButton.setOnAction(event -> {
                    Debt debt = getTableView().getItems().get(getIndex());
                    if (connection != null) {
                        String sql = "DELETE FROM debts WHERE person_name = ? AND amount = ? AND description = ?";
                        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                            pstmt.setString(1, debt.getPerson());
                            pstmt.setDouble(2, debt.getAmount());
                            pstmt.setString(3, debt.getDescription());
                            pstmt.executeUpdate();
                        } catch (SQLException e) {
                            System.out.println("Ошибка удаления: " + e.getMessage());
                        }
                    }
                    debts.remove(debt);
                    getTableView().refresh();
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    HBox buttons = new HBox(5.0, repayButton, deleteButton);
                    setGraphic(buttons);
                }
            }
        });

        table.getColumns().addAll(typeCol, personCol, amountCol, descriptionCol, dueDateCol, actionsCol);
        table.setItems(debts);
        return table;
    }

    private void loadDebtsFromDatabase() {
        if (connection != null) {
            String sql = "SELECT * FROM debts ORDER BY created_at DESC";
            try (PreparedStatement pstmt = connection.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {
                debts.clear();
                while (rs.next()) {
                    String type = rs.getString("debt_type");
                    String person = rs.getString("person_name");
                    double amount = rs.getDouble("amount");
                    String description = rs.getString("description");
                    LocalDate dueDate = rs.getDate("due_date") != null ? rs.getDate("due_date").toLocalDate() : null;
                    debts.add(new Debt(type, person, amount, description, dueDate));
                }
                System.out.println("✅ Загружено долгов: " + debts.size());
            } catch (SQLException e) {
                System.out.println("Ошибка загрузки долгов: " + e.getMessage());
            }
        }
    }

    private VBox createHeaderBox() {
        Label titleLabel = new Label("Личный бюджет");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 24.0));
        titleLabel.setTextFill(darkTheme ? Color.web("#e0e0e0") : Color.web("#2c3e50"));

        this.balanceLabel = new Label(String.format("Баланс: %.2f ₽", this.balance));
        this.balanceLabel.setFont(Font.font("System", FontWeight.BOLD, 20.0));
        this.updateBalanceLabel();

        this.monthlyIncomeLabel = new Label(String.format("Доходы за месяц: +%.2f ₽", this.monthlyIncome));
        this.monthlyIncomeLabel.setFont(Font.font("System", FontWeight.BOLD, 16.0));
        this.monthlyIncomeLabel.setTextFill(Color.web("#27ae60"));

        this.monthlyExpensesLabel = new Label(String.format("Расходы за месяц: -%.2f ₽", this.monthlyExpenses));
        this.monthlyExpensesLabel.setFont(Font.font("System", FontWeight.BOLD, 16.0));
        this.monthlyExpensesLabel.setTextFill(Color.web("#e74c3c"));

        VBox headerBox = new VBox(10.0, titleLabel, this.balanceLabel,
                this.monthlyIncomeLabel, this.monthlyExpensesLabel);
        headerBox.setAlignment(Pos.CENTER);
        headerBox.setPadding(new Insets(0.0, 0.0, 20.0, 0.0));
        return headerBox;
    }

    private VBox createFormBox(TableView<Transaction> table) {
        Label formTitle = new Label("Новая транзакция");
        formTitle.setFont(Font.font("System", FontWeight.BOLD, 16.0));

        ComboBox<String> typeCombo = new ComboBox<>();
        typeCombo.getItems().addAll("Доход", "Расход");
        typeCombo.setValue("Расход");
        typeCombo.setPrefWidth(200.0);

        ComboBox<String> categoryCombo = new ComboBox<>();
        categoryCombo.setPrefWidth(200.0);

        TextField amountField = new TextField();
        amountField.setPromptText("Сумма");
        amountField.setPrefWidth(200.0);

        TextField fromField = new TextField();
        fromField.setPromptText("От кого/Куда");
        fromField.setPrefWidth(200.0);

        TextArea descriptionArea = new TextArea();
        descriptionArea.setPromptText("Описание...");
        descriptionArea.setPrefWidth(200.0);
        descriptionArea.setPrefHeight(60.0);
        descriptionArea.setWrapText(true);

        DatePicker datePicker = new DatePicker();
        datePicker.setValue(LocalDate.now());
        datePicker.setPrefWidth(200.0);
        datePicker.setPromptText("Дата транзакции");

        CheckBox backDateCheck = new CheckBox("Задним числом");
        backDateCheck.setPrefWidth(200.0);

        // КНОПКА ЭКСПОРТА
        Button exportButton = createStyledButton("📎 Экспорт в CSV", "#3498db");
        exportButton.setOnAction(e -> exportToCSV());

        updateCategoryCombo(typeCombo, categoryCombo, "Расход");

        Button addCategoryButton = createStyledButton("➕ Новая категория", "#3498db");
        addCategoryButton.setPrefWidth(200.0);

        Button scanReceiptButton = createStyledButton("📷 Сканировать чек", "#9b59b6");
        scanReceiptButton.setPrefWidth(200.0);

        Button scanQRButton = createStyledButton("📱 Сканировать QR код чека", "#3498db");
        scanQRButton.setPrefWidth(200.0);

        Button addButton = createStyledButton("✅ Добавить", "#27ae60");
        Button clearButton = createStyledButton("🗑 Очистить", "#95a5a6");

        typeCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            fromField.setDisable(!"Доход".equals(newVal));
            fromField.setPromptText("Доход".equals(newVal) ? "От кого" : "Куда");
            updateCategoryCombo(typeCombo, categoryCombo, newVal);
        });

        backDateCheck.selectedProperty().addListener((obs, oldVal, newVal) ->
                datePicker.setDisable(!newVal));

        addCategoryButton.setOnAction(e -> {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("Новая категория");
            dialog.setHeaderText("Добавление категории для " + typeCombo.getValue());
            dialog.setContentText("Введите название категории:");

            Optional<String> result = dialog.showAndWait();
            result.ifPresent(categoryName -> {
                if (!categoryName.trim().isEmpty()) {
                    String type = typeCombo.getValue();

                    if ("Доход".equals(type)) {
                        if (!incomeCategories.contains(categoryName)) {
                            incomeCategories.add(categoryName);
                            saveCategoryToDatabase(type, categoryName);
                        }
                    } else {
                        if (!expenseCategories.contains(categoryName)) {
                            expenseCategories.add(categoryName);
                            saveCategoryToDatabase(type, categoryName);
                        }
                    }

                    updateCategoryCombo(typeCombo, categoryCombo, type);
                    categoryCombo.setValue(categoryName);

                    Alert successAlert = new Alert(AlertType.INFORMATION);
                    successAlert.setTitle("Успех");
                    successAlert.setHeaderText("Категория добавлена");
                    successAlert.setContentText("Категория \"" + categoryName + "\" добавлена");
                    successAlert.showAndWait();
                }
            });
        });

        scanReceiptButton.setOnAction(e -> scanReceipt(typeCombo, categoryCombo, amountField,
                fromField, descriptionArea, datePicker));

        scanQRButton.setOnAction(e -> scanQRCode(typeCombo, categoryCombo, amountField,
                fromField, descriptionArea, datePicker));

        addButton.setOnAction(e -> this.addTransaction(typeCombo, categoryCombo,
                amountField, fromField, descriptionArea, datePicker, backDateCheck, table));

        clearButton.setOnAction(e -> {
            amountField.clear();
            fromField.clear();
            descriptionArea.clear();
            datePicker.setValue(LocalDate.now());
            backDateCheck.setSelected(false);
        });

        VBox buttonBox = new VBox(10.0, addCategoryButton, scanReceiptButton, scanQRButton);
        HBox actionButtons = new HBox(10.0, addButton, clearButton);
        actionButtons.setAlignment(Pos.CENTER);

        VBox formBox = new VBox(15.0);
        formBox.getChildren().addAll(formTitle, typeCombo, categoryCombo,
                amountField, fromField, descriptionArea, backDateCheck, datePicker, exportButton, buttonBox, actionButtons);
        formBox.setPadding(new Insets(20.0));
        formBox.setPrefWidth(250.0);
        formBox.setMaxWidth(250.0);

        if (darkTheme) {
            formBox.setStyle("-fx-background-color: #252833; -fx-border-color: #2d3038; -fx-border-radius: 10; -fx-background-radius: 10;");
            formTitle.setTextFill(Color.web("#e4e6eb"));
        } else {
            formBox.setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #dee2e6; -fx-border-radius: 10; -fx-background-radius: 10;");
            formTitle.setTextFill(Color.web("#34495e"));
        }

        return formBox;
    }

    private void addNewCategory(ComboBox<String> typeCombo, ComboBox<String> categoryCombo) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Новая категория");
        dialog.setHeaderText("Добавление новой категории для " + typeCombo.getValue());
        dialog.setContentText("Введите название категории:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(categoryName -> {
            if (!categoryName.trim().isEmpty()) {
                String type = typeCombo.getValue();

                if (connection != null) {
                    String tableName = "Доход".equals(type) ? "income_categories" : "expense_categories";
                    String sql = "INSERT INTO " + tableName + " (category_name) VALUES (?)";
                    try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                        pstmt.setString(1, categoryName);
                        pstmt.executeUpdate();

                        if ("Доход".equals(type)) {
                            incomeCategories.add(categoryName);
                        } else {
                            expenseCategories.add(categoryName);
                        }

                        updateCategoryCombo(typeCombo, categoryCombo, type);

                        Alert successAlert = new Alert(AlertType.INFORMATION);
                        successAlert.setTitle("Успех");
                        successAlert.setHeaderText("Категория добавлена");
                        successAlert.setContentText("Категория \"" + categoryName + "\" успешно добавлена");
                        successAlert.showAndWait();
                    } catch (SQLException e) {
                        if (e.getMessage().contains("unique")) {
                            showAlert("Ошибка", "Категория уже существует",
                                    "Категория с таким названием уже существует");
                        } else {
                            showAlert("Ошибка", "Не удалось добавить категорию", e.getMessage());
                        }
                    }
                } else {
                    if ("Доход".equals(type)) {
                        incomeCategories.add(categoryName);
                    } else {
                        expenseCategories.add(categoryName);
                    }
                    updateCategoryCombo(typeCombo, categoryCombo, type);

                    Alert successAlert = new Alert(AlertType.INFORMATION);
                    successAlert.setTitle("Успех");
                    successAlert.setHeaderText("Категория добавлена");
                    successAlert.setContentText("Категория \"" + categoryName + "\" успешно добавлена (оффлайн режим)");
                    successAlert.showAndWait();
                }
            }
        });
    }

    private void scanReceipt(ComboBox<String> typeCombo, ComboBox<String> categoryCombo,
                             TextField amountField, TextField fromField,
                             TextArea descriptionArea, DatePicker datePicker) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Выберите изображение чека");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.bmp")
        );

        File selectedFile = fileChooser.showOpenDialog(primaryStage);
        if (selectedFile != null) {
            try {
                Alert infoAlert = new Alert(AlertType.INFORMATION);
                infoAlert.setTitle("Сканирование чека");
                infoAlert.setHeaderText("Выберите тип операции");

                ButtonType incomeType = new ButtonType("Доход", ButtonData.YES);
                ButtonType expenseType = new ButtonType("Расход", ButtonData.NO);
                ButtonType cancelType = new ButtonType("Отмена", ButtonData.CANCEL_CLOSE);

                infoAlert.getButtonTypes().setAll(incomeType, expenseType, cancelType);

                Optional<ButtonType> result = infoAlert.showAndWait();

                if (result.isPresent() && result.get() != cancelType) {
                    boolean isIncome = result.get() == incomeType;
                    typeCombo.setValue(isIncome ? "Доход" : "Расход");

                    TextInputDialog sumDialog = new TextInputDialog("1000");
                    sumDialog.setTitle("Сумма чека");
                    sumDialog.setHeaderText("Введите сумму чека");
                    sumDialog.setContentText("Сумма:");

                    Optional<String> sumResult = sumDialog.showAndWait();
                    sumResult.ifPresent(sum -> {
                        try {
                            amountField.setText(sum);
                            descriptionArea.setText("Автоматически добавлен из чека: " + selectedFile.getName());

                            Alert successAlert = new Alert(AlertType.INFORMATION);
                            successAlert.setTitle("Успех");
                            successAlert.setHeaderText("Чек успешно отсканирован");
                            successAlert.setContentText("Данные чека добавлены в форму. Проверьте и нажмите 'Добавить'.");
                            successAlert.showAndWait();
                        } catch (NumberFormatException ex) {
                            showAlert("Ошибка", "Неверная сумма", ex.getMessage());
                        }
                    });
                }
            } catch (Exception ex) {
                showAlert("Ошибка", "Не удалось распознать чек", ex.getMessage());
            }
        }
    }

    private void scanQRCode(ComboBox<String> typeCombo, ComboBox<String> categoryCombo,
                            TextField amountField, TextField fromField,
                            TextArea descriptionArea, DatePicker datePicker) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Выберите изображение с QR-кодом чека");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.bmp")
        );

        File selectedFile = fileChooser.showOpenDialog(primaryStage);
        if (selectedFile != null) {
            try {
                BufferedImage bufferedImage = ImageIO.read(selectedFile);
                LuminanceSource source = new BufferedImageLuminanceSource(bufferedImage);
                BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
                Result result = new MultiFormatReader().decode(bitmap);
                String qrText = result.getText();

                Pattern amountPattern = Pattern.compile("s=([0-9.,]+)");
                Pattern datePattern = Pattern.compile("t=([0-9T]+)");

                Matcher amountMatcher = amountPattern.matcher(qrText);
                Matcher dateMatcher = datePattern.matcher(qrText);

                if (amountMatcher.find()) {
                    String amountStr = amountMatcher.group(1).replace(",", ".");
                    double amount = Double.parseDouble(amountStr);
                    amountField.setText(String.valueOf(amount));
                    typeCombo.setValue("Расход");
                    categoryCombo.setValue("Еда");

                    if (dateMatcher.find()) {
                        String dateStr = dateMatcher.group(1);
                        try {
                            LocalDate receiptDate = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"));
                            datePicker.setValue(receiptDate);
                        } catch (Exception e) {
                            datePicker.setValue(LocalDate.now());
                        }
                    }

                    descriptionArea.setText("QR код чека: " + qrText.substring(0, Math.min(100, qrText.length())));

                    Alert successAlert = new Alert(AlertType.INFORMATION);
                    successAlert.setTitle("Успех");
                    successAlert.setHeaderText("QR код успешно распознан");
                    successAlert.setContentText("Сумма: " + amount + " ₽\nДанные добавлены в форму.");
                    successAlert.showAndWait();
                } else {
                    showAlert("Ошибка", "QR код не содержит информации о сумме", "Не удалось распознать сумму в QR коде");
                }

            } catch (NotFoundException e) {
                showAlert("Ошибка", "QR код не найден", "На изображении не обнаружен QR код");
            } catch (Exception e) {
                showAlert("Ошибка", "Не удалось распознать QR код", e.getMessage());
            }
        }
    }

    private void updateCategoryCombo(ComboBox<String> typeCombo, ComboBox<String> categoryCombo, String type) {
        categoryCombo.getItems().clear();

        if ("Доход".equals(type)) {
            if (incomeCategories.isEmpty()) {
                incomeCategories.addAll(java.util.Arrays.asList("Зарплата", "Подарок", "Возврат", "Инвестиции", "Другое"));
            }
            categoryCombo.getItems().addAll(incomeCategories);
        } else {
            if (expenseCategories.isEmpty()) {
                expenseCategories.addAll(java.util.Arrays.asList("Еда", "Транспорт", "Жильё", "Развлечения", "Здоровье", "Другое"));
            }
            categoryCombo.getItems().addAll(expenseCategories);
        }

        if (!categoryCombo.getItems().isEmpty()) {
            categoryCombo.setValue(categoryCombo.getItems().get(0));
        }
    }

    private VBox createTableBox(TableView<Transaction> table) {
        Label tableTitle = new Label("История транзакций");
        tableTitle.setFont(Font.font("System", FontWeight.BOLD, 16.0));
        tableTitle.setTextFill(darkTheme ? Color.web("#e0e0e0") : Color.web("#34495e"));

        Button deleteButton = createStyledButton("Удалить выбранное", "#e74c3c");
        deleteButton.setOnAction(e -> this.deleteSelectedTransaction(table));

        TextField searchField = new TextField();
        searchField.setPromptText("Поиск по описанию...");
        searchField.setPrefWidth(300.0);
        searchField.textProperty().addListener((obs, oldVal, newVal) ->
                this.filterTransactions(newVal));

        HBox controlsBox = new HBox(10.0, tableTitle, new Region(), searchField, deleteButton);
        controlsBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(controlsBox.getChildren().get(1), Priority.ALWAYS);

        VBox tableBox = new VBox(15.0);
        tableBox.getChildren().addAll(controlsBox, table);
        tableBox.setPadding(new Insets(0.0, 0.0, 0.0, 20.0));

        return tableBox;
    }

    private VBox createChartsTabContent() {
        VBox chartsBox = new VBox(20.0);
        chartsBox.setPadding(new Insets(20.0));
        chartsBox.setAlignment(Pos.TOP_CENTER);

        Label titleLabel = new Label("Диаграммы доходов и расходов");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 20.0));
        titleLabel.setTextFill(darkTheme ? Color.web("#e0e0e0") : Color.web("#2c3e50"));

        HBox monthSelectorBox = new HBox(10.0);
        monthSelectorBox.setAlignment(Pos.CENTER);

        Label monthLabel = new Label("Выберите месяц:");
        monthLabel.setFont(Font.font("System", FontWeight.BOLD, 14.0));

        ComboBox<YearMonth> monthCombo = new ComboBox<>();
        monthCombo.setPrefWidth(200.0);

        YearMonth currentMonth = YearMonth.now();
        for (int i = 0; i < 12; i++) {
            monthCombo.getItems().add(currentMonth.minusMonths(i));
        }
        monthCombo.setValue(currentMonth);

        monthSelectorBox.getChildren().addAll(monthLabel, monthCombo);

        VBox expensesChartBox = new VBox(10.0);
        expensesChartBox.setAlignment(Pos.CENTER);
        Label expensesTitle = new Label("Расходы по категориям");
        expensesTitle.setFont(Font.font("System", FontWeight.BOLD, 16.0));

        PieChart expensesChart = new PieChart();
        expensesChart.setPrefSize(500.0, 400.0);
        expensesChart.setTitle("Расходы по категориям");

        VBox incomeChartBox = new VBox(10.0);
        incomeChartBox.setAlignment(Pos.CENTER);
        Label incomeTitle = new Label("Доходы по категориям");
        incomeTitle.setFont(Font.font("System", FontWeight.BOLD, 16.0));

        PieChart incomeChart = new PieChart();
        incomeChart.setPrefSize(500.0, 400.0);
        incomeChart.setTitle("Доходы по категориям");

        HBox chartsContainer = new HBox(20.0);
        chartsContainer.setAlignment(Pos.CENTER);
        VBox.setVgrow(chartsContainer, Priority.ALWAYS);

        VBox leftChart = new VBox(10.0, expensesTitle, expensesChart);
        VBox rightChart = new VBox(10.0, incomeTitle, incomeChart);
        chartsContainer.getChildren().addAll(leftChart, rightChart);

        monthCombo.valueProperty().addListener((obs, oldVal, newVal) ->
                this.updateCharts(expensesChart, incomeChart, newVal));

        this.updateCharts(expensesChart, incomeChart, currentMonth);

        chartsBox.getChildren().addAll(titleLabel, monthSelectorBox, chartsContainer);
        return chartsBox;
    }

    private VBox createGoalsTabContent() {
        VBox goalsBox = new VBox(20.0);
        goalsBox.setPadding(new Insets(20.0));
        goalsBox.setAlignment(Pos.TOP_CENTER);

        Label titleLabel = new Label("Финансовые цели");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 20.0));
        titleLabel.setTextFill(darkTheme ? Color.web("#e0e0e0") : Color.web("#2c3e50"));

        VBox formBox = new VBox(10.0);
        formBox.setPadding(new Insets(20.0));
        formBox.setStyle(darkTheme ?
                "-fx-background-color: #3c3c3c; -fx-border-color: #4a4a4a; -fx-border-radius: 10; -fx-background-radius: 10;" :
                "-fx-background-color: #f8f9fa; -fx-border-color: #dee2e6; -fx-border-radius: 10; -fx-background-radius: 10;");
        formBox.setPrefWidth(400.0);

        Label formTitle = new Label("Новая цель");
        formTitle.setFont(Font.font("System", FontWeight.BOLD, 16.0));

        TextField goalNameField = new TextField();
        goalNameField.setPromptText("Название цели");
        goalNameField.setPrefWidth(300.0);

        TextField targetAmountField = new TextField();
        targetAmountField.setPromptText("Целевая сумма");
        targetAmountField.setPrefWidth(300.0);

        TextField currentAmountField = new TextField();
        currentAmountField.setPromptText("Текущая сумма (по умолчанию 0)");
        currentAmountField.setPrefWidth(300.0);

        Button addGoalButton = createStyledButton("Добавить цель", "#27ae60");
        addGoalButton.setOnAction(e -> this.addGoal(goalNameField, targetAmountField, currentAmountField));

        formBox.getChildren().addAll(formTitle, goalNameField, targetAmountField,
                currentAmountField, addGoalButton);

        TableView<Goal> goalsTable = this.createGoalsTable();

        VBox tableBox = new VBox(10.0);
        tableBox.getChildren().addAll(new Label("Мои цели"), goalsTable);

        HBox contentBox = new HBox(20.0);
        contentBox.setAlignment(Pos.TOP_CENTER);
        contentBox.getChildren().addAll(formBox, tableBox);

        goalsBox.getChildren().addAll(titleLabel, contentBox);
        return goalsBox;
    }

    private TableView<Goal> createGoalsTable() {
        TableView<Goal> table = new TableView<>();
        table.setPlaceholder(new Label("Нет целей для отображения"));
        table.setPrefSize(500.0, 400.0);

        TableColumn<Goal, String> nameCol = new TableColumn<>("Название");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setPrefWidth(150.0);

        TableColumn<Goal, Double> currentCol = new TableColumn<>("Текущая сумма");
        currentCol.setCellValueFactory(new PropertyValueFactory<>("currentAmount"));
        currentCol.setPrefWidth(120.0);
        currentCol.setCellFactory(column -> new TableCell<Goal, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (!empty && item != null) {
                    this.setText(String.format("%.2f ₽", item));
                } else {
                    this.setText(null);
                }
            }
        });

        TableColumn<Goal, Double> targetCol = new TableColumn<>("Целевая сумма");
        targetCol.setCellValueFactory(new PropertyValueFactory<>("targetAmount"));
        targetCol.setPrefWidth(120.0);
        targetCol.setCellFactory(column -> new TableCell<Goal, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (!empty && item != null) {
                    this.setText(String.format("%.2f ₽", item));
                } else {
                    this.setText(null);
                }
            }
        });

        TableColumn<Goal, Double> progressCol = new TableColumn<>("Прогресс");
        progressCol.setCellValueFactory(new PropertyValueFactory<>("progress"));
        progressCol.setPrefWidth(100.0);
        progressCol.setCellFactory(column -> new TableCell<Goal, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (!empty && item != null) {
                    this.setText(String.format("%.1f%%", item));
                    if (item >= 100.0) {
                        this.setTextFill(Color.web("#27ae60"));
                    } else if (item >= 50.0) {
                        this.setTextFill(Color.web("#f39c12"));
                    } else {
                        this.setTextFill(Color.web("#e74c3c"));
                    }
                } else {
                    this.setText(null);
                }
            }
        });

        TableColumn<Goal, Void> actionsCol = new TableColumn<>("Действия");
        actionsCol.setPrefWidth(150.0);
        actionsCol.setCellFactory(param -> new TableCell<Goal, Void>() {
            private final Button addMoneyButton = createStyledButton("💰 Добавить", "#3498db");
            private final Button deleteButton = createStyledButton("🗑 Удалить", "#e74c3c");

            {
                this.addMoneyButton.setOnAction(event -> {
                    Goal goal = this.getTableView().getItems().get(this.getIndex());
                    showAddMoneyDialog(goal);
                });
                this.deleteButton.setOnAction(event -> {
                    Goal goal = this.getTableView().getItems().get(this.getIndex());
                    deleteGoal(goal);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    this.setGraphic(null);
                } else {
                    HBox buttons = new HBox(5.0, this.addMoneyButton, this.deleteButton);
                    this.setGraphic(buttons);
                }
            }
        });

        table.getColumns().addAll(nameCol, currentCol, targetCol, progressCol, actionsCol);
        table.setItems(this.goals);
        return table;
    }

    private void showAddMoneyDialog(Goal goal) {
        Dialog<Double> dialog = new Dialog<>();
        dialog.setTitle("Добавить деньги к цели");
        dialog.setHeaderText("Добавить деньги к цели: " + goal.getName());

        ButtonType addButtonType = new ButtonType("Добавить", ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10.0);
        grid.setVgap(10.0);
        grid.setPadding(new Insets(20.0, 150.0, 10.0, 10.0));

        TextField amountField = new TextField();
        amountField.setPromptText("Сумма");

        grid.add(new Label("Сумма:"), 0, 0);
        grid.add(amountField, 1, 0);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == addButtonType) {
                try {
                    return Double.parseDouble(amountField.getText());
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            return null;
        });

        dialog.showAndWait().ifPresent(amount -> {
            if (amount > 0.0) {
                goal.setCurrentAmount(goal.getCurrentAmount() + amount);
                this.updateGoalInDatabase(goal);
                this.addGoalContribution(goal, amount);
                this.refreshGoalsTable();
            }
        });
    }

    private Button createStyledButton(String text, String color) {
        Button button = new Button(text);
        String style = String.format(
                "-fx-background-color: %s; " +
                        "-fx-text-fill: white; " +
                        "-fx-font-weight: bold; " +
                        "-fx-padding: 8 16; " +
                        "-fx-background-radius: 6; " +
                        "-fx-cursor: hand;",
                color
        );
        button.setStyle(style);

        button.setOnMouseEntered(e -> button.setStyle(String.format(
                "-fx-background-color: derive(%s, -15%%); " +
                        "-fx-text-fill: white; " +
                        "-fx-font-weight: bold; " +
                        "-fx-padding: 8 16; " +
                        "-fx-background-radius: 6; " +
                        "-fx-cursor: hand;",
                color)));

        button.setOnMouseExited(e -> button.setStyle(style));

        allButtons.add(button);
        return button;
    }

    private TableView<Transaction> createTransactionTable() {
        TableView<Transaction> table = new TableView<>();
        table.setPlaceholder(new Label("Нет транзакций для отображения"));

        TableColumn<Transaction, String> dateTimeCol = new TableColumn<>("Дата/Время");
        dateTimeCol.setCellValueFactory(new PropertyValueFactory<>("formattedDateTime"));
        dateTimeCol.setPrefWidth(150.0);

        TableColumn<Transaction, String> typeCol = new TableColumn<>("Тип");
        typeCol.setCellValueFactory(new PropertyValueFactory<>("type"));
        typeCol.setPrefWidth(100.0);
        typeCol.setCellFactory(column -> new TableCell<Transaction, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (!empty && item != null) {
                    this.setText(item);
                    if ("Доход".equals(item)) {
                        this.setTextFill(Color.web("#27ae60"));
                    } else {
                        this.setTextFill(Color.web("#e74c3c"));
                    }
                } else {
                    this.setText(null);
                }
            }
        });

        TableColumn<Transaction, String> categoryCol = new TableColumn<>("Категория");
        categoryCol.setCellValueFactory(new PropertyValueFactory<>("category"));
        categoryCol.setPrefWidth(120.0);

        TableColumn<Transaction, Double> amountCol = new TableColumn<>("Сумма");
        amountCol.setCellValueFactory(new PropertyValueFactory<>("amount"));
        amountCol.setPrefWidth(120.0);
        amountCol.setCellFactory(column -> new TableCell<Transaction, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (!empty && item != null) {
                    this.setText(String.format("%.2f ₽", Math.abs(item)));
                    if (item >= 0.0) {
                        this.setTextFill(Color.web("#27ae60"));
                    } else {
                        this.setTextFill(Color.web("#e74c3c"));
                    }
                } else {
                    this.setText(null);
                }
            }
        });

        TableColumn<Transaction, String> fromCol = new TableColumn<>("От кого/Куда");
        fromCol.setCellValueFactory(new PropertyValueFactory<>("from"));
        fromCol.setPrefWidth(150.0);

        TableColumn<Transaction, String> descriptionCol = new TableColumn<>("Описание");
        descriptionCol.setCellValueFactory(new PropertyValueFactory<>("description"));
        descriptionCol.setPrefWidth(200.0);

        table.getColumns().addAll(dateTimeCol, typeCol, categoryCol, amountCol, fromCol, descriptionCol);
        table.setItems(this.transactions);

        return table;
    }

    private void filterTransactions(String searchText) {
        if (searchText != null && !searchText.isEmpty()) {
            ObservableList<Transaction> filtered = FXCollections.observableArrayList();
            String lowerCaseFilter = searchText.toLowerCase();

            for (Transaction transaction : this.transactions) {
                if (transaction.getDescription().toLowerCase().contains(lowerCaseFilter) ||
                        transaction.getFrom().toLowerCase().contains(lowerCaseFilter) ||
                        transaction.getCategory().toLowerCase().contains(lowerCaseFilter)) {
                    filtered.add(transaction);
                }
            }
            this.table.setItems(filtered);
        } else {
            this.table.setItems(this.transactions);
        }
    }

    private void addTransaction(ComboBox<String> typeCombo, ComboBox<String> categoryCombo,
                                TextField amountField, TextField fromField, TextArea descriptionArea,
                                DatePicker datePicker, CheckBox backDateCheck, TableView<Transaction> table) {
        try {
            double amount = Double.parseDouble(amountField.getText());
            String type = typeCombo.getValue();
            if (type.equals("Расход")) {
                amount = -amount;
            }

            ZonedDateTime transactionDateTime;
            if (backDateCheck.isSelected() && datePicker.getValue() != null) {
                transactionDateTime = datePicker.getValue().atStartOfDay(this.saratovZone);
            } else {
                transactionDateTime = ZonedDateTime.now(this.saratovZone);
            }

            if (this.connection != null) {
                String sql = "INSERT INTO transactions (transaction_type, category_name, amount, " +
                        "from_person, description, transaction_date, is_backdated) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?)";

                try (PreparedStatement pstmt = this.connection.prepareStatement(sql)) {
                    pstmt.setString(1, type);
                    pstmt.setString(2, categoryCombo.getValue());
                    pstmt.setDouble(3, Math.abs(amount));
                    pstmt.setString(4, fromField.getText());
                    pstmt.setString(5, descriptionArea.getText());
                    pstmt.setTimestamp(6, Timestamp.from(transactionDateTime.toInstant()));
                    pstmt.setBoolean(7, backDateCheck.isSelected());
                    pstmt.executeUpdate();
                }
            }

            Transaction transaction = new Transaction(transactionDateTime, type,
                    categoryCombo.getValue(), amount, fromField.getText(), descriptionArea.getText());
            this.transactions.add(0, transaction);
            this.balance += amount;
            this.updateMonthlyStats();
            this.updateBalanceLabel();
            this.updateBudgetSpent();

            amountField.clear();
            fromField.clear();
            descriptionArea.clear();
            datePicker.setValue(LocalDate.now());
            backDateCheck.setSelected(false);

        } catch (NumberFormatException e) {
            this.showAlert("Ошибка", "Некорректная сумма",
                    "Пожалуйста, введите числовое значение для суммы.");
        } catch (SQLException e) {
            System.out.println("Ошибка сохранения в БД: " + e.getMessage());
        }
    }

    private void deleteSelectedTransaction(TableView<Transaction> table) {
        Transaction selected = table.getSelectionModel().getSelectedItem();
        if (selected != null) {
            if (this.connection != null) {
                String sql = "DELETE FROM transactions WHERE transaction_date = ? AND amount = ? AND transaction_type = ?";

                try (PreparedStatement pstmt = this.connection.prepareStatement(sql)) {
                    pstmt.setTimestamp(1, Timestamp.from(selected.getDateTime().toInstant()));
                    pstmt.setDouble(2, Math.abs(selected.getAmount()));
                    pstmt.setString(3, selected.getType());
                    pstmt.executeUpdate();
                } catch (SQLException e) {
                    System.out.println("Ошибка удаления из БД: " + e.getMessage());
                }
            }

            this.transactions.remove(selected);
            this.balance -= selected.getAmount();
            this.updateMonthlyStats();
            this.updateBalanceLabel();
            this.updateBudgetSpent();
        } else {
            this.showAlert("Внимание", "Не выбрана транзакция",
                    "Пожалуйста, выберите транзакцию для удаления.");
        }
    }

    private void updateBalanceLabel() {
        if (this.balanceLabel != null) {
            this.balanceLabel.setText(String.format("Баланс: %.2f ₽", this.balance));
            this.balanceLabel.setTextFill(this.balance >= 0.0 ?
                    Color.web("#27ae60") : Color.web("#e74c3c"));
        }
    }

    private void updateMonthlyStats() {
        this.monthlyIncome = 0.0;
        this.monthlyExpenses = 0.0;
        YearMonth currentMonth = YearMonth.now();

        for (Transaction transaction : this.transactions) {
            YearMonth transactionMonth = YearMonth.from(transaction.getDateTime());
            if (transactionMonth.equals(currentMonth)) {
                if (transaction.getAmount() >= 0.0) {
                    this.monthlyIncome += transaction.getAmount();
                } else {
                    this.monthlyExpenses += Math.abs(transaction.getAmount());
                }
            }
        }

        if (this.monthlyIncomeLabel != null) {
            this.monthlyIncomeLabel.setText(String.format("Доходы за месяц: +%.2f ₽", this.monthlyIncome));
        }

        if (this.monthlyExpensesLabel != null) {
            this.monthlyExpensesLabel.setText(String.format("Расходы за месяц: -%.2f ₽", this.monthlyExpenses));
        }
    }

    private void updateCharts(PieChart expensesChart, PieChart incomeChart, YearMonth selectedMonth) {
        Map<String, Double> expensesByCategory = new HashMap<>();
        Map<String, Double> incomeByCategory = new HashMap<>();

        for (Transaction transaction : this.transactions) {
            YearMonth transactionMonth = YearMonth.from(transaction.getDateTime());
            if (transactionMonth.equals(selectedMonth)) {
                String category = transaction.getCategory();
                double amount = Math.abs(transaction.getAmount());
                if (transaction.getAmount() < 0.0) {
                    expensesByCategory.put(category,
                            expensesByCategory.getOrDefault(category, 0.0) + amount);
                } else {
                    incomeByCategory.put(category,
                            incomeByCategory.getOrDefault(category, 0.0) + amount);
                }
            }
        }

        expensesChart.getData().clear();
        for (Map.Entry<String, Double> entry : expensesByCategory.entrySet()) {
            expensesChart.getData().add(
                    new PieChart.Data(entry.getKey() + String.format(" (%.2f₽)", entry.getValue()),
                            entry.getValue()));
        }

        incomeChart.getData().clear();
        for (Map.Entry<String, Double> entry : incomeByCategory.entrySet()) {
            incomeChart.getData().add(
                    new PieChart.Data(entry.getKey() + String.format(" (%.2f₽)", entry.getValue()),
                            entry.getValue()));
        }
    }

    private void addGoal(TextField nameField, TextField targetField, TextField currentField) {
        try {
            String name = nameField.getText();
            double target = Double.parseDouble(targetField.getText());
            double current = currentField.getText().isEmpty() ? 0.0 :
                    Double.parseDouble(currentField.getText());

            if (name.isEmpty()) {
                this.showAlert("Ошибка", "Пустое название",
                        "Пожалуйста, введите название цели.");
                return;
            }

            if (target <= 0.0) {
                this.showAlert("Ошибка", "Некорректная сумма",
                        "Целевая сумма должна быть больше 0.");
                return;
            }

            Goal goal = new Goal(name, current, target);

            if (this.connection != null) {
                String sql = "INSERT INTO goals (goal_name, target_amount, current_amount) " +
                        "VALUES (?, ?, ?)";

                try (PreparedStatement pstmt = this.connection.prepareStatement(sql)) {
                    pstmt.setString(1, name);
                    pstmt.setDouble(2, target);
                    pstmt.setDouble(3, current);
                    pstmt.executeUpdate();
                }
            }

            this.goals.add(goal);
            nameField.clear();
            targetField.clear();
            currentField.clear();

        } catch (NumberFormatException e) {
            this.showAlert("Ошибка", "Некорректная сумма",
                    "Пожалуйста, введите числовые значения для сумм.");
        } catch (SQLException e) {
            System.out.println("Ошибка сохранения цели в БД: " + e.getMessage());
        }
    }

    private void deleteGoal(Goal goal) {
        if (this.connection != null) {
            String sql = "DELETE FROM goals WHERE goal_name = ? AND target_amount = ?";

            try (PreparedStatement pstmt = this.connection.prepareStatement(sql)) {
                pstmt.setString(1, goal.getName());
                pstmt.setDouble(2, goal.getTargetAmount());
                pstmt.executeUpdate();
            } catch (SQLException e) {
                System.out.println("Ошибка удаления цели из БД: " + e.getMessage());
            }
        }

        this.goals.remove(goal);
    }

    private void updateGoalInDatabase(Goal goal) {
        if (this.connection != null) {
            String sql = "UPDATE goals SET current_amount = ? WHERE goal_name = ? AND target_amount = ?";

            try (PreparedStatement pstmt = this.connection.prepareStatement(sql)) {
                pstmt.setDouble(1, goal.getCurrentAmount());
                pstmt.setString(2, goal.getName());
                pstmt.setDouble(3, goal.getTargetAmount());
                pstmt.executeUpdate();
            } catch (SQLException e) {
                System.out.println("Ошибка обновления цели в БД: " + e.getMessage());
            }
        }
    }

    private void addGoalContribution(Goal goal, double amount) {
        if (this.connection != null) {
            String sql = "INSERT INTO goal_contributions (goal_id, amount, description) " +
                    "VALUES ((SELECT goal_id FROM goals WHERE goal_name = ? AND target_amount = ?), ?, ?)";

            try (PreparedStatement pstmt = this.connection.prepareStatement(sql)) {
                pstmt.setString(1, goal.getName());
                pstmt.setDouble(2, goal.getTargetAmount());
                pstmt.setDouble(3, amount);
                pstmt.setString(4, "Пополнение из приложения");
                pstmt.executeUpdate();
            } catch (SQLException e) {
                System.out.println("Ошибка сохранения пополнения цели: " + e.getMessage());
            }
        }
    }

    private void refreshGoalsTable() {
        for (Tab tab : this.tabPane.getTabs()) {
            if ("Цели".equals(tab.getText())) {
                VBox goalsTabContent = (VBox) tab.getContent();
                HBox contentBox = (HBox) goalsTabContent.getChildren().get(1);
                VBox tableBox = (VBox) contentBox.getChildren().get(1);
                TableView<Goal> goalsTable = (TableView<Goal>) tableBox.getChildren().get(1);
                goalsTable.refresh();
                break;
            }
        }
    }

    private void connectToDatabase() {
        try {
            System.out.println("Пытаемся подключиться к базе: " + URL);
            System.out.println("Пользователь: " + USER);
            this.connection = DriverManager.getConnection(URL, USER, PASSWORD);
            this.createTablesIfNotExists();
            System.out.println("✅ Подключение к БД установлено успешно!");
        } catch (SQLException e) {
            System.out.println("❌ Ошибка подключения: " + e.getMessage());
            this.showAlert("Режим без БД", "База данных недоступна",
                    "Работаем в демонстрационном режиме.\nОшибка: " + e.getMessage());
        }
    }

    private void createTablesIfNotExists() {
        if (this.connection != null) {
            String[] createTables = {
                    "CREATE TABLE IF NOT EXISTS income_categories (" +
                            "    category_id SERIAL PRIMARY KEY," +
                            "    category_name VARCHAR(50) NOT NULL UNIQUE," +
                            "    color VARCHAR(20) DEFAULT '#27ae60'," +
                            "    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP" +
                            ")",
                    "CREATE TABLE IF NOT EXISTS expense_categories (" +
                            "    category_id SERIAL PRIMARY KEY," +
                            "    category_name VARCHAR(50) NOT NULL UNIQUE," +
                            "    color VARCHAR(20) DEFAULT '#e74c3c'," +
                            "    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP" +
                            ")",
                    "CREATE TABLE IF NOT EXISTS transactions (" +
                            "    transaction_id SERIAL PRIMARY KEY," +
                            "    transaction_type VARCHAR(10) NOT NULL CHECK (transaction_type IN ('Доход', 'Расход'))," +
                            "    category_name VARCHAR(50) NOT NULL," +
                            "    amount DECIMAL(10,2) NOT NULL CHECK (amount > 0)," +
                            "    from_person VARCHAR(100)," +
                            "    description TEXT," +
                            "    transaction_date TIMESTAMP WITH TIME ZONE NOT NULL," +
                            "    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP," +
                            "    is_backdated BOOLEAN DEFAULT FALSE," +
                            "    confirmed BOOLEAN DEFAULT TRUE" +
                            ")",
                    "CREATE TABLE IF NOT EXISTS goals (" +
                            "    goal_id SERIAL PRIMARY KEY," +
                            "    goal_name VARCHAR(100) NOT NULL," +
                            "    target_amount DECIMAL(10,2) NOT NULL CHECK (target_amount > 0)," +
                            "    current_amount DECIMAL(10,2) DEFAULT 0," +
                            "    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP" +
                            ")",
                    "CREATE TABLE IF NOT EXISTS goal_contributions (" +
                            "    contribution_id SERIAL PRIMARY KEY," +
                            "    goal_id INTEGER NOT NULL," +
                            "    amount DECIMAL(10,2) NOT NULL CHECK (amount > 0)," +
                            "    contribution_date TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP," +
                            "    description TEXT," +
                            "    FOREIGN KEY (goal_id) REFERENCES goals(goal_id) ON DELETE CASCADE" +
                            ")",
                    "CREATE TABLE IF NOT EXISTS monthly_stats (" +
                            "    stat_id SERIAL PRIMARY KEY," +
                            "    stat_year INTEGER NOT NULL," +
                            "    stat_month INTEGER NOT NULL CHECK (stat_month >= 1 AND stat_month <= 12)," +
                            "    total_income DECIMAL(10,2) DEFAULT 0," +
                            "    total_expenses DECIMAL(10,2) DEFAULT 0," +
                            "    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP," +
                            "    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP," +
                            "    UNIQUE(stat_year, stat_month)" +
                            ")",
                    "CREATE TABLE IF NOT EXISTS debts (" +
                            "    debt_id SERIAL PRIMARY KEY," +
                            "    debt_type VARCHAR(20) NOT NULL CHECK (debt_type IN ('Я должен', 'Должны мне'))," +
                            "    person_name VARCHAR(100) NOT NULL," +
                            "    amount DECIMAL(10,2) NOT NULL CHECK (amount > 0)," +
                            "    description TEXT," +
                            "    due_date DATE," +
                            "    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP," +
                            "    is_paid BOOLEAN DEFAULT FALSE" +
                            ")",
                    // НОВЫЕ ТАБЛИЦЫ
                    "CREATE TABLE IF NOT EXISTS category_budgets (" +
                            "    budget_id SERIAL PRIMARY KEY," +
                            "    category_name VARCHAR(50) NOT NULL," +
                            "    limit_amount DECIMAL(10,2) NOT NULL," +
                            "    month_year VARCHAR(7) NOT NULL," +
                            "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                            "    UNIQUE(category_name, month_year)" +
                            ")",
                    "CREATE TABLE IF NOT EXISTS recurring_payments (" +
                            "    payment_id SERIAL PRIMARY KEY," +
                            "    name VARCHAR(100) NOT NULL," +
                            "    amount DECIMAL(10,2) NOT NULL," +
                            "    day_of_month INTEGER NOT NULL," +
                            "    category VARCHAR(50) NOT NULL," +
                            "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                            ")"
            };

            try (Statement stmt = this.connection.createStatement()) {
                for (String sql : createTables) {
                    stmt.execute(sql);
                }
                System.out.println("✅ Таблицы созданы/проверены");
                insertDefaultCategories();
            } catch (SQLException e) {
                System.out.println("Ошибка создания таблиц: " + e.getMessage());
            }
        }
    }

    private void insertDefaultCategories() throws SQLException {
        String[] incomeCats = {"Зарплата", "Подарок", "Возврат", "Инвестиции", "Другое"};
        for (String cat : incomeCats) {
            String sql = "INSERT INTO income_categories (category_name) " +
                    "SELECT ? WHERE NOT EXISTS " +
                    "(SELECT 1 FROM income_categories WHERE category_name = ?)";
            try (PreparedStatement pstmt = this.connection.prepareStatement(sql)) {
                pstmt.setString(1, cat);
                pstmt.setString(2, cat);
                pstmt.executeUpdate();
            }
        }

        String[] expenseCats = {"Еда", "Транспорт", "Жильё", "Развлечения", "Здоровье", "Другое"};
        for (String cat : expenseCats) {
            String sql = "INSERT INTO expense_categories (category_name) " +
                    "SELECT ? WHERE NOT EXISTS " +
                    "(SELECT 1 FROM expense_categories WHERE category_name = ?)";
            try (PreparedStatement pstmt = this.connection.prepareStatement(sql)) {
                pstmt.setString(1, cat);
                pstmt.setString(2, cat);
                pstmt.executeUpdate();
            }
        }
    }

    private void loadCategoriesFromDatabase() {
        if (this.connection != null) {
            try {
                String incomeSql = "SELECT category_name FROM income_categories ORDER BY category_name";
                try (Statement stmt = this.connection.createStatement();
                     ResultSet rs = stmt.executeQuery(incomeSql)) {
                    incomeCategories.clear();
                    while (rs.next()) {
                        incomeCategories.add(rs.getString("category_name"));
                    }
                }

                String expenseSql = "SELECT category_name FROM expense_categories ORDER BY category_name";
                try (Statement stmt = this.connection.createStatement();
                     ResultSet rs = stmt.executeQuery(expenseSql)) {
                    expenseCategories.clear();
                    while (rs.next()) {
                        expenseCategories.add(rs.getString("category_name"));
                    }
                }
            } catch (SQLException e) {
                System.out.println("Ошибка загрузки категорий: " + e.getMessage());
            }
        }

        if (incomeCategories.isEmpty()) {
            incomeCategories.addAll(java.util.Arrays.asList("Зарплата", "Подарок", "Возврат", "Инвестиции", "Другое"));
        }
        if (expenseCategories.isEmpty()) {
            expenseCategories.addAll(java.util.Arrays.asList("Еда", "Транспорт", "Жильё", "Развлечения", "Здоровье", "Другое"));
        }

        System.out.println("✅ Категории доходов: " + incomeCategories);
        System.out.println("✅ Категории расходов: " + expenseCategories);
    }

    private void loadTransactionsFromDatabase() {
        if (this.connection != null) {
            String sql = "SELECT * FROM transactions ORDER BY transaction_date DESC";

            try (PreparedStatement pstmt = this.connection.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {

                this.transactions.clear();
                this.balance = 0.0;

                while (rs.next()) {
                    ZonedDateTime dateTime = rs.getTimestamp("transaction_date")
                            .toInstant().atZone(this.saratovZone);
                    String type = rs.getString("transaction_type");
                    String category = rs.getString("category_name");
                    double amount = rs.getDouble("amount");
                    String from = rs.getString("from_person");
                    String description = rs.getString("description");

                    double signedAmount = "Доход".equals(type) ? amount : -amount;

                    Transaction transaction = new Transaction(dateTime, type,
                            category, signedAmount, from, description);
                    this.transactions.add(transaction);
                    this.balance += signedAmount;
                }

                System.out.println("✅ Загружено транзакций: " + this.transactions.size());
                this.updateMonthlyStats();
                this.updateBalanceLabel();
                this.updateBudgetSpent();

            } catch (SQLException e) {
                System.out.println("Ошибка загрузки транзакций: " + e.getMessage());
            }
        }
    }

    private void loadGoalsFromDatabase() {
        if (this.connection != null) {
            String sql = "SELECT * FROM goals ORDER BY created_at DESC";

            try (PreparedStatement pstmt = this.connection.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {

                this.goals.clear();

                while (rs.next()) {
                    String name = rs.getString("goal_name");
                    double currentAmount = rs.getDouble("current_amount");
                    double targetAmount = rs.getDouble("target_amount");

                    Goal goal = new Goal(name, currentAmount, targetAmount);
                    this.goals.add(goal);
                }

                System.out.println("✅ Загружено целей: " + this.goals.size());

            } catch (SQLException e) {
                System.out.println("Ошибка загрузки целей: " + e.getMessage());
            }
        }
    }

    public void stop() {
        if (this.connection != null) {
            try {
                this.connection.close();
                System.out.println("Соединение с БД закрыто");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private void showAlert(String title, String header, String content) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void saveCategoryToDatabase(String type, String categoryName) {
        if (connection != null) {
            String tableName = "Доход".equals(type) ? "income_categories" : "expense_categories";
            String sql = "INSERT INTO " + tableName + " (category_name) VALUES (?) ON CONFLICT (category_name) DO NOTHING";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, categoryName);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                System.out.println("Ошибка сохранения категории: " + e.getMessage());
            }
        }
    }

    private void updateFormStyle(VBox formBox, Label formTitle) {
        if (darkTheme) {
            formBox.setStyle("-fx-background-color: #2d2d2d; -fx-border-color: #3a3a3a; -fx-border-radius: 10; -fx-background-radius: 10;");
            formTitle.setTextFill(Color.web("#e0e0e0"));
        } else {
            formBox.setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #dee2e6; -fx-border-radius: 10; -fx-background-radius: 10;");
            formTitle.setTextFill(Color.web("#34495e"));
        }
    }

    // ==================== ВНУТРЕННИЕ КЛАССЫ ====================

    public static class Transaction {
        private final SimpleObjectProperty<ZonedDateTime> dateTime;
        private final SimpleStringProperty type;
        private final SimpleStringProperty category;
        private final SimpleDoubleProperty amount;
        private final SimpleStringProperty from;
        private final SimpleStringProperty description;
        private final SimpleStringProperty formattedDateTime;

        public Transaction(ZonedDateTime dateTime, String type, String category,
                           double amount, String from, String description) {
            this.dateTime = new SimpleObjectProperty<>(dateTime);
            this.type = new SimpleStringProperty(type);
            this.category = new SimpleStringProperty(category);
            this.amount = new SimpleDoubleProperty(amount);
            this.from = new SimpleStringProperty(from);
            this.description = new SimpleStringProperty(description);
            this.formattedDateTime = new SimpleStringProperty(
                    dateTime.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")));
        }

        public ZonedDateTime getDateTime() { return dateTime.get(); }
        public String getType() { return type.get(); }
        public String getCategory() { return category.get(); }
        public double getAmount() { return amount.get(); }
        public String getFrom() { return from.get(); }
        public String getDescription() { return description.get(); }
        public String getFormattedDateTime() { return formattedDateTime.get(); }

        public SimpleObjectProperty<ZonedDateTime> dateTimeProperty() { return dateTime; }
        public SimpleStringProperty typeProperty() { return type; }
        public SimpleStringProperty categoryProperty() { return category; }
        public SimpleDoubleProperty amountProperty() { return amount; }
        public SimpleStringProperty fromProperty() { return from; }
        public SimpleStringProperty descriptionProperty() { return description; }
        public SimpleStringProperty formattedDateTimeProperty() { return formattedDateTime; }
    }

    public static class Goal {
        private final SimpleStringProperty name;
        private final SimpleDoubleProperty currentAmount;
        private final SimpleDoubleProperty targetAmount;
        private final SimpleDoubleProperty progress;

        public Goal(String name, double currentAmount, double targetAmount) {
            this.name = new SimpleStringProperty(name);
            this.currentAmount = new SimpleDoubleProperty(currentAmount);
            this.targetAmount = new SimpleDoubleProperty(targetAmount);
            this.progress = new SimpleDoubleProperty(currentAmount / targetAmount * 100.0);
        }

        public String getName() { return name.get(); }
        public double getCurrentAmount() { return currentAmount.get(); }
        public double getTargetAmount() { return targetAmount.get(); }
        public double getProgress() { return progress.get(); }

        public void setCurrentAmount(double amount) {
            currentAmount.set(amount);
            progress.set(amount / targetAmount.get() * 100.0);
        }

        public SimpleStringProperty nameProperty() { return name; }
        public SimpleDoubleProperty currentAmountProperty() { return currentAmount; }
        public SimpleDoubleProperty targetAmountProperty() { return targetAmount; }
        public SimpleDoubleProperty progressProperty() { return progress; }
    }

    public static class Debt {
        private final SimpleStringProperty type;
        private final SimpleStringProperty person;
        private final SimpleDoubleProperty amount;
        private final SimpleStringProperty description;
        private final SimpleObjectProperty<LocalDate> dueDate;

        public Debt(String type, String person, double amount, String description, LocalDate dueDate) {
            this.type = new SimpleStringProperty(type);
            this.person = new SimpleStringProperty(person);
            this.amount = new SimpleDoubleProperty(amount);
            this.description = new SimpleStringProperty(description);
            this.dueDate = new SimpleObjectProperty<>(dueDate);
        }

        public String getType() { return type.get(); }
        public String getPerson() { return person.get(); }
        public double getAmount() { return amount.get(); }
        public String getDescription() { return description.get(); }
        public LocalDate getDueDate() { return dueDate.get(); }
        public String getDueDateFormatted() {
            if (dueDate.get() == null) return "Не указана";
            String formatted = dueDate.get().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
            if (dueDate.get().isBefore(LocalDate.now())) {
                formatted += " ⚠️ Просрочен!";
            }
            return formatted;
        }

        public SimpleStringProperty typeProperty() { return type; }
        public SimpleStringProperty personProperty() { return person; }
        public SimpleDoubleProperty amountProperty() { return amount; }
        public SimpleStringProperty descriptionProperty() { return description; }
        public SimpleObjectProperty<LocalDate> dueDateProperty() { return dueDate; }
    }

    // НОВЫЙ ВНУТРЕННИЙ КЛАСС ДЛЯ БЮДЖЕТА
    public static class BudgetRow {
        private final SimpleStringProperty category;
        private final SimpleDoubleProperty limit;
        private final SimpleDoubleProperty spent;
        private final SimpleDoubleProperty remaining;
        private final SimpleDoubleProperty percent;

        public BudgetRow(String category, double limit, double spent) {
            this.category = new SimpleStringProperty(category);
            this.limit = new SimpleDoubleProperty(limit);
            this.spent = new SimpleDoubleProperty(spent);
            this.remaining = new SimpleDoubleProperty(limit - spent);
            this.percent = new SimpleDoubleProperty(limit > 0 ? spent / limit * 100 : 0);
        }

        public String getCategory() { return category.get(); }
        public double getLimit() { return limit.get(); }
        public double getSpent() { return spent.get(); }
        public double getRemaining() { return remaining.get(); }
        public double getPercent() { return percent.get(); }

        public SimpleStringProperty categoryProperty() { return category; }
        public SimpleDoubleProperty limitProperty() { return limit; }
        public SimpleDoubleProperty spentProperty() { return spent; }
        public SimpleDoubleProperty remainingProperty() { return remaining; }
        public SimpleDoubleProperty percentProperty() { return percent; }
    }

    // НОВЫЙ ВНУТРЕННИЙ КЛАСС ДЛЯ РЕГУЛЯРНЫХ ПЛАТЕЖЕЙ
    public static class RecurringPayment {
        private final SimpleStringProperty name;
        private final SimpleDoubleProperty amount;
        private final SimpleIntegerProperty dayOfMonth;
        private final SimpleStringProperty category;

        public RecurringPayment(String name, double amount, int dayOfMonth, String category) {
            this.name = new SimpleStringProperty(name);
            this.amount = new SimpleDoubleProperty(amount);
            this.dayOfMonth = new SimpleIntegerProperty(dayOfMonth);
            this.category = new SimpleStringProperty(category);
        }

        public String getName() { return name.get(); }
        public double getAmount() { return amount.get(); }
        public int getDayOfMonth() { return dayOfMonth.get(); }
        public String getCategory() { return category.get(); }

        public SimpleStringProperty nameProperty() { return name; }
        public SimpleDoubleProperty amountProperty() { return amount; }
        public SimpleIntegerProperty dayOfMonthProperty() { return dayOfMonth; }
        public SimpleStringProperty categoryProperty() { return category; }
    }
}