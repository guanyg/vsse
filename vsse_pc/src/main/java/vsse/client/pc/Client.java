package vsse.client.pc;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import vsse.client.pc.ConnectionController.Connection;

import java.io.IOException;

public class Client extends Application {
    private static final String APP_NAME = "VSSE Client";
    private static Connection conn;
    private Stage stage;

    public static void main(String[] args) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (conn != null) conn.shutdown();
            System.out.println("Shutdown");
        }, "Shutdown_Hook"));
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        primaryStage.setTitle(APP_NAME);

        this.stage = primaryStage;
        stage.setOnCloseRequest(ev -> System.exit(0));

        switchConnection();
    }

    private void switchConnection() {
        if (stage.isShowing()) {
            stage.hide();
        }

        ConnectionController.openConnection().whenComplete((conn, ex) -> {
            if (ex != null) {
                new Alert(Alert.AlertType.ERROR, ex.getLocalizedMessage()).showAndWait();
                switchConnection();
            } else {
                Client.conn = conn;
                conn.onInactive(this::switchConnection);
                FXMLLoader loader = new FXMLLoader();
                loader.setController(new SearchController(conn));
                try {
                    stage.setScene(new Scene(loader.load(Client.class.getResourceAsStream("/SearchPane.fxml"))));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if (!stage.isShowing()) {
                    stage.show();
                }
            }
        });
    }
}
