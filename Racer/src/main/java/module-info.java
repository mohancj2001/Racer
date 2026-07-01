module com.racer.app.racer {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.media;

    opens com.racer.app.racer to javafx.fxml;
    exports com.racer.app.racer;
}