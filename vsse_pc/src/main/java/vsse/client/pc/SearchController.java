package vsse.client.pc;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import vsse.proto.RequestOuterClass.SearchRequest.MsgCase;

import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static vsse.proto.ResponseOuterClass.Response.State.UP;

public class SearchController implements Initializable {

    private final ConnectionController.Connection connection;
    @FXML
    private Button copyUrlBtn;
    @FXML
    private Button uploadBtn;
    @FXML
    private Button disconnectBtn;
    @FXML
    private Button searchBtn;
    @FXML
    private ChoiceBox<String> typeSelector;
    @FXML
    private TextField keywordField;
    @FXML
    private ExpandableListView<String> resultList;

    public SearchController(ConnectionController.Connection connection) {
        this.connection = connection;

    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        copyUrlBtn.setOnAction(ev -> {
            String url = this.connection.getUrl();
            ClipboardContent cc = new ClipboardContent();
            cc.putString(url);
            Clipboard.getSystemClipboard().setContent(cc);
            new Alert(Alert.AlertType.INFORMATION, "URL has been copied.").show();
        });
        uploadBtn.setOnAction(ev -> {
            UploadController.open(connection).showAndWait();
            if (connection.getState() == UP) {
                disconnectBtn.fire();
            }

        });

        disconnectBtn.setOnAction(ev -> {
            connection.close();
            disconnectBtn.setDisable(true);
        });
        typeSelector.getItems().addAll("AND", "OR", "*", "?");
        typeSelector.setValue("AND");

        keywordField.requestFocus();
        searchBtn.setOnAction((ActionEvent ev) -> {
            searchBtn.setDisable(true);
            typeSelector.setDisable(true);
            keywordField.setEditable(false);
            resultList.getItems().clear();

            List<String> keywords = Arrays.stream(keywordField.getText().split("[, ]+"))
                    .collect(Collectors.toList());
            String type = typeSelector.getValue();
            CompletableFuture<List<String>> future = null;
            String keyword;
            switch (type) {
                case "AND":
                case "OR":
                    future = connection.search(MsgCase.valueOf(type),
                            keywords.toArray(new String[0]));
                    break;

                case "*":
                    if (keywords.size() != 1)
                        throw new UnsupportedOperationException("Wrong number of operands");
                    keyword = keywords.get(0);
                    future = connection.search(MsgCase.STAR, keyword.split("\\*")[0]);
                    break;

                case "?":
                    if (keywords.size() != 1)
                        throw new UnsupportedOperationException("Wrong number of operands");
                    keyword = keywords.get(0);
                    String[] keywordArr = keyword.split("\\?");
                    boolean a = keyword.startsWith("?");
                    boolean b = keyword.endsWith("?");

                    String prefix = a ? "" : keywordArr[0];
                    String suffix = b ? "" : keywordArr[1];
                    future = connection.search(MsgCase.Q,
                            prefix,
                            suffix);
                    break;
            }
            Objects.requireNonNull(future).whenComplete(
                    (response, throwable) ->
                            Platform.runLater(() -> {
                                typeSelector.setDisable(false);
                                keywordField.setEditable(true);
                                searchBtn.setDisable(false);
                                if (throwable != null) {
                                    new Alert(Alert.AlertType.ERROR, throwable.getLocalizedMessage()).showAndWait();
                                    return;
                                }
                                // response
                                resultList.getItems().addAll(response.stream()
                                        .map(String::new)
                                        .toArray(String[]::new));

                            }));
        });

        resultList.setContentProvider(new ExpandableListView.ContentProvider<String>() {
            @Override
            public String getTitleOf(String item) {
                String title = item.split("[\r\n]")[0];

                if (title.length() > 30)
                    return title.substring(0, 30) + "...";
                return title;
            }

            @Override
            public String getContentOf(String item) {
                return item;
            }
        });

        if (connection.getState() == UP) {
            uploadBtn.fire();
        }


    }
}
