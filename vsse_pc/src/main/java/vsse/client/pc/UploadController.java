package vsse.client.pc;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Modality;
import javafx.stage.Stage;
import vsse.client.pc.ConnectionController.Connection;
import vsse.model.DocumentDTO;
import vsse.util.Counter;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class UploadController implements Initializable {
    private final CompletableFuture<Void> future = new CompletableFuture<>();
    private final Connection connection;
    @FXML
    private Button addBtn;
    @FXML
    private Button removeBtn;
    @FXML
    private Button uploadBtn;
    @FXML
    private Button cancelBtn;
    @FXML
    private TextArea contentArea;
    @FXML
    private TextField keywordsField;
    @FXML
    private TableView<DocumentDTO> documentTable;
    @FXML
    private TableColumn<DocumentDTO, Integer> idxCol;
    @FXML
    private TableColumn<DocumentDTO, String> contentCol;
    @FXML
    private TableColumn<DocumentDTO, String> keywordsCol;

    protected UploadController(Connection connection) {
        this.connection = connection;
    }

    public static Stage open(Connection connection) {
        FXMLLoader loader = new FXMLLoader();
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        loader.setController(new UploadController(connection).onClose(() -> {
            stage.close();
        }));
        try {
            Scene scene = new Scene(loader.load(SearchController.class.getResourceAsStream("/UploadPane.fxml")));
            stage.setScene(scene);
            stage.setTitle("Upload ...");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return stage;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        SimpleObjectProperty<DocumentDTO> currentObj = new SimpleObjectProperty<>();
        Counter id = new Counter();

        idxCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        contentCol.setCellValueFactory(new PropertyValueFactory<>("abstract"));
        keywordsCol.setCellValueFactory(
                d -> new ReadOnlyObjectWrapper<>(
                        Arrays.toString(d.getValue().getKeywords())));
        addBtn.setOnAction(ev -> {
            DocumentDTO d = new DocumentDTO();
            d.setId(id.inc());
            documentTable.getItems().add(d);
            documentTable.getSelectionModel().select(d);
        });

        keywordsField.focusedProperty().addListener((observable, o, n) -> {
            DocumentDTO d = currentObj.get();
            if (d != null && !n) {
                d.setKeywords(keywordsField.getText().split("[, ]+"));
            }
        });

        contentArea.focusedProperty().addListener((observable, o, n) -> {
            DocumentDTO d = currentObj.get();
            if (d != null && !n) {
                d.setContent(contentArea.getText());
            }
        });

        removeBtn.setOnAction(ev -> {
            DocumentDTO d = currentObj.get();
            if (d != null) {
                documentTable.getItems().remove(d);
            }
        });

        documentTable.getSelectionModel().selectedItemProperty().addListener((value, v1, v2) -> {
            currentObj.set(v2);
        });

        currentObj.addListener((observable, oldValue, newValue) -> {
            if (oldValue != null && getRow(oldValue) >= 0) {
                oldValue.setKeywords(keywordsField.getText().split("[, ]+"));
                oldValue.setContent(contentArea.getText());
            }

            documentTable.refresh();

            if (newValue != null && getRow(newValue) != -1) {

                contentArea.setText(newValue.getContent());
                keywordsField.setText(Arrays.stream(newValue.getKeywords())
                        .map(String::toLowerCase)
                        .collect(Collectors.joining(", ")));

            }
        });

        cancelBtn.setOnAction(ev -> future.complete(null));
        uploadBtn.setOnAction(ev -> {
            connection.upload(documentTable.getItems());
            future.complete(null);
        });
        addBtn.fire();
    }

    public UploadController onClose(Runnable r) {
        future.whenComplete((_a, ex) -> {
            r.run();
        });
        return this;
    }

    public int getRow(DocumentDTO documentDTO) {
        return documentTable.getItems().indexOf(documentDTO);
    }
}
