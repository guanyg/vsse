package vsse.client.pc;
/**
 * Copyright (C) 2015 uphy.jp
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TitledPane;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.text.Text;
import javafx.util.Callback;

import java.util.HashSet;
import java.util.Set;


/**
 * @author Yuhi Ishikura
 */
public class ExpandableListView<E> extends ListView<E> {

    private ContentProvider<E> contentProvider = new ContentProvider<E>() {
        @Override
        public String getTitleOf(final E item) {
            return item.toString();
        }

        @Override
        public String getContentOf(final E item) {
            return getTitleOf(item);
        }
    };

    private final Set<E> expandedItems = new HashSet<E>();

    public ExpandableListView() {
        setSelectionModel(null);
        setCellFactory(new Callback<ListView<E>, ListCell<E>>() {
            @Override
            public ListCell<E> call(final ListView<E> param) {
                final TitledPane titledPane = new TitledPane();
                final Text contentArea = new Text();

                titledPane.setAnimated(false);
                titledPane.setCollapsible(true);
                titledPane.setExpanded(false);

                final BorderPane contentAreaWrapper = new BorderPane();
                contentAreaWrapper.setLeft(contentArea);
                titledPane.setContent(contentAreaWrapper);

                titledPane.addEventHandler(MouseEvent.MOUSE_CLICKED, new EventHandler<MouseEvent>() {
                    @Override
                    public void handle(final MouseEvent event) {
                        final boolean expanded = titledPane.isExpanded();
                        final E item = (E) titledPane.getUserData();
                        if (item == null) {
                            return;
                        }
                        if (expanded) {
                            expandedItems.add(item);
                        } else {
                            expandedItems.remove(item);
                        }
                    }
                });
                return new ListCell<E>() {

                    @Override
                    protected void updateItem(final E item, final boolean empty) {
                        super.updateItem(item, empty);
                        if (empty) {
                            titledPane.setText("");
                            contentArea.setText("");
                            setGraphic(null);
                            return;
                        }
                        final boolean expanded = isExpanded(item);
                        titledPane.setUserData(item);
                        titledPane.setExpanded(expanded);
                        titledPane.setText(contentProvider.getTitleOf(item));
                        contentArea.setText(contentProvider.getContentOf(item));
                        setGraphic(titledPane);
                    }
                };
            }
        });
        // getStylesheets().add(String.format("/%s/style.css", getClass().getPackage().getName().replaceAll("\\.", "/")));
    }

    public void setContentProvider(final ContentProvider<E> contentProvider) {
        this.contentProvider = contentProvider;
    }

    public void expand(E item) {
        expand(item, true);
    }

    public void collapse(E item) {
        expand(item, false);
    }

    private void expand(E item, boolean expand) {
        if (expand) {
            this.expandedItems.add(item);
        } else {
            this.expandedItems.remove(item);
        }

        ObservableList<E> o = getItems();
        setItems(null);
        setItems(o);
    }

    public boolean isExpanded(E item) {
        return this.expandedItems.contains(item);
    }

    public static interface ContentProvider<E> {

        String getTitleOf(E item);

        String getContentOf(E item);

    }

}