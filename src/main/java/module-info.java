module org.example.test {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;
    requires javafx.swing;
    requires java.desktop;
    requires java.sql;
    requires com.google.zxing;
    requires com.google.zxing.javase;
    opens org.example.test to javafx.fxml;
    exports org.example.test;
}