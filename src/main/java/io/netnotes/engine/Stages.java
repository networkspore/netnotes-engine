package io.netnotes.engine;

import java.awt.FontFormatException;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ExecutorService;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import io.netnotes.engine.apps.AppConstants;
import io.netnotes.javafxsvg.SvgImageLoaderFactory;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import org.ergoplatform.sdk.SecretString;

public class Stages {
    public static Font openSansTxt;
    public static Font openSansSmall;
    public static Font mainFont;
    public static Font txtFont;
    public static Font titleFont;
    public static Font smallFont;
    

    public static Color txtColor = Color.web("#cdd4da");
    public static Color altColor = Color.web("#777777");
    public static Color formFieldColor = new Color(.8, .8, .8, .9);
    
    public final static int DEFAULT_RGBA = 0x00000000;

    public final static String ASSETS_DIRECTORY = "/assets";
    public final static String FONTS_DIRECTORY = "/fonts";
    public final static String DEFAULT_CSS = "/css/startWindow.css";

    public static final String WAITING_IMG = ASSETS_DIRECTORY + "/spinning.gif";
    public static final String OPEN_IMG = ASSETS_DIRECTORY + "/open-outline-white-20.png";
    public static final String DISK_IMG = ASSETS_DIRECTORY + "/save-outline-white-20.png";
    public static final String ADD_IMG = ASSETS_DIRECTORY + "/add-outline-white-40.png";

    public static final String PRIMARY_FONT = FONTS_DIRECTORY + "/OCRAEXT.TTF";
    public static final String PRIMARY_FONT_FAMILY = "OCR A Extended";

    public static final String EMOJI_FONT = FONTS_DIRECTORY + "/OpenSansEmoji.ttf";
    public static final String EMOJI_FONT_FAMILY = "OpenSansEmoji, Regular";

    public static final String NETNOTES_LOGO_256 = ASSETS_DIRECTORY + "/icon256.png";
    public static final String NETNOTES_LOGO_WHITE = ASSETS_DIRECTORY + "/icon.png";

    public static final String UNKNOWN_IMAGE_PATH = ASSETS_DIRECTORY + "/unknown-unit.png";

    public static Image icon = new Image(ASSETS_DIRECTORY + "/icon15.png");
    public static Image logo = new Image(NETNOTES_LOGO_256);
    public static Image closeImg = new Image(ASSETS_DIRECTORY + "/close-outline-white.png");
    public static Image minimizeImg = new Image(ASSETS_DIRECTORY + "/minimize-white-20.png");
    public static Image globeImg = new Image(ASSETS_DIRECTORY + "/globe-outline-white-120.png");
    public static Image globeImage30 = new Image(ASSETS_DIRECTORY + "/globe-outline-white-30.png");
    public static Image settingsImg = new Image(ASSETS_DIRECTORY + "/settings-outline-white-120.png");
    public static Image unknownImg = new Image(UNKNOWN_IMAGE_PATH);

    public final static double STAGE_WIDTH = 450;
    public final static double STAGE_HEIGHT = 250;
    
    public final static double SMALL_STAGE_WIDTH = 500;
    public final static double DEFAULT_STAGE_WIDTH = 700;
    public final static double DEFAULT_STAGE_HEIGHT = 500;

    public static final String CHECK_MARK = "🗸";
    public static final String PLAY = "▶";
    public static final String STOP = "⏹";
    public static final String CIRCLE = "○";
    public static final String RADIO_BTN = "◉";

    public final static double MENU_BAR_IMAGE_WIDTH = 18;
    public final static int VIEWPORT_HEIGHT_OFFSET = 5;
    public final static int VIEWPORT_WIDTH_OFFSET = 5;
    public static final int ROW_HEIGHT = 27;
    public static final int MAX_ROW_HEIGHT = 20;
    public static final int COL_WIDTH = 160;

    public static void initStages(){
        SvgImageLoaderFactory.install();

        Font.loadFont(Stages.class.getResource(PRIMARY_FONT).toExternalForm(),16);
        Font.loadFont(Stages.class.getResource(EMOJI_FONT).toExternalForm(),20);

        openSansTxt = Font.font(EMOJI_FONT_FAMILY, FontWeight.BOLD, 16);
        openSansSmall = Font.font(EMOJI_FONT_FAMILY , FontWeight.BOLD, 12);
        mainFont =  Font.font(PRIMARY_FONT_FAMILY, FontWeight.BOLD, 20);
        txtFont = Font.font(PRIMARY_FONT_FAMILY, 18);
        titleFont = Font.font(PRIMARY_FONT_FAMILY, FontWeight.BOLD, 16);
        smallFont = Font.font(PRIMARY_FONT_FAMILY, 14);

        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();

        try( 
            InputStream stream = Stages.class.getResource(PRIMARY_FONT).openStream(); 
        ) {
            
            java.awt.Font ocrFont = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, stream).deriveFont(48f);
            ge.registerFont(ocrFont);
           
        } catch (FontFormatException | IOException e) {
           
            try {
                Files.writeString(AppConstants.LOG_FILE.toPath(), "\nError registering font: " + e.toString(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e1) {
            
            }
            
        } 
    }

    public static ImageView highlightedImageView(Image image) {

        ImageView imageView = new ImageView(image);

        ColorAdjust colorAdjust = new ColorAdjust();
        colorAdjust.setBrightness(-0.5);

        imageView.addEventFilter(MouseEvent.MOUSE_ENTERED, e -> {

            imageView.setEffect(colorAdjust);

        });
        imageView.addEventFilter(MouseEvent.MOUSE_EXITED, e -> {
            imageView.setEffect(null);
        });

        return imageView;
    }

    public static HBox createTopBar(Image iconImage, Button fillRightBtn, Button maximizeBtn, Button closeBtn, Stage theStage) {

        ImageView barIconView = new ImageView(iconImage);
        barIconView.setFitWidth(20);
        barIconView.setPreserveRatio(true);

        // Rectangle2D logoRect = new Rectangle2D(30,30,30,30);
        Region spacer = new Region();

        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label newTitleLbl = new Label(theStage.titleProperty().get());
        newTitleLbl.setFont(titleFont);
        newTitleLbl.setTextFill(txtColor);
        newTitleLbl.setPadding(new Insets(0, 0, 0, 10));
        newTitleLbl.textProperty().bind(theStage.titleProperty());

        //  HBox.setHgrow(titleLbl2, Priority.ALWAYS);
        ImageView closeImage = highlightedImageView(closeImg);
        closeImage.setFitHeight(20);
        closeImage.setFitWidth(20);
        closeImage.setPreserveRatio(true);

        closeBtn.setGraphic(closeImage);
        closeBtn.setPadding(new Insets(0, 5, 0, 3));
        closeBtn.setId("closeBtn");

        ImageView minimizeImage = highlightedImageView(minimizeImg);
        minimizeImage.setFitHeight(20);
        minimizeImage.setFitWidth(20);
        minimizeImage.setPreserveRatio(true);

        Button minimizeBtn = new Button();
        minimizeBtn.setId("toolBtn");
        minimizeBtn.setGraphic(minimizeImage);
        minimizeBtn.setPadding(new Insets(0, 2, 1, 2));
        minimizeBtn.setOnAction(minEvent -> {
            theStage.setIconified(true);
        });

        maximizeBtn.setId("toolBtn");
        maximizeBtn.setGraphic(IconButton.getIconView(new Image(AppConstants.MAXIMIZE_ICON), 20));
        maximizeBtn.setPadding(new Insets(0, 3, 0, 3));

        fillRightBtn.setId("toolBtn");
        fillRightBtn.setGraphic(IconButton.getIconView(new Image(AppConstants.FILL_RIGHT_ICON), 20));
        fillRightBtn.setPadding(new Insets(0, 3, 0, 3));

        HBox newTopBar = new HBox(barIconView, newTitleLbl, spacer, minimizeBtn, fillRightBtn, maximizeBtn, closeBtn);
        newTopBar.setAlignment(Pos.CENTER_LEFT);
        newTopBar.setPadding(new Insets(7, 8, 10, 10));
        newTopBar.setId("topBar");

        Delta dragDelta = new Delta();

        newTopBar.setOnMousePressed(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent mouseEvent) {
                // record a delta distance for the drag and drop operation.
                dragDelta.x = theStage.getX() - mouseEvent.getScreenX();
                dragDelta.y = theStage.getY() - mouseEvent.getScreenY();
            }
        });
        newTopBar.setOnMouseDragged(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent mouseEvent) {
                theStage.setX(mouseEvent.getScreenX() + dragDelta.x);
                theStage.setY(mouseEvent.getScreenY() + dragDelta.y);
            }
        });

        return newTopBar;
    }


    static class Delta {
        double x, y;
    }

    public static HBox createTopBar(Image iconImage, Button maximizeBtn, Button closeBtn, Stage theStage) {

        ImageView barIconView = new ImageView(iconImage);
        barIconView.setFitWidth(20);
        barIconView.setPreserveRatio(true);

        // Rectangle2D logoRect = new Rectangle2D(30,30,30,30);
        Region spacer = new Region();

        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label newTitleLbl = new Label(theStage.titleProperty().get());
        newTitleLbl.setFont(titleFont);
        newTitleLbl.setTextFill(txtColor);
        newTitleLbl.setPadding(new Insets(0, 0, 0, 10));
        newTitleLbl.textProperty().bind(theStage.titleProperty());

        //  HBox.setHgrow(titleLbl2, Priority.ALWAYS);
        ImageView closeImage = highlightedImageView(closeImg);
        closeImage.setFitHeight(20);
        closeImage.setFitWidth(20);
        closeImage.setPreserveRatio(true);

        closeBtn.setGraphic(closeImage);
        closeBtn.setPadding(new Insets(0, 5, 0, 3));
        closeBtn.setId("closeBtn");

        ImageView minimizeImage = highlightedImageView(minimizeImg);
        minimizeImage.setFitHeight(20);
        minimizeImage.setFitWidth(20);
        minimizeImage.setPreserveRatio(true);

        Button minimizeBtn = new Button();
        minimizeBtn.setId("toolBtn");
        minimizeBtn.setGraphic(minimizeImage);
        minimizeBtn.setPadding(new Insets(0, 2, 1, 2));
        minimizeBtn.setOnAction(minEvent -> {
            theStage.setIconified(true);
        });

        maximizeBtn.setId("toolBtn");
        maximizeBtn.setGraphic(IconButton.getIconView(new Image(AppConstants.MAXIMIZE_ICON), 20));
        maximizeBtn.setPadding(new Insets(0, 3, 0, 3));

        HBox newTopBar = new HBox(barIconView, newTitleLbl, spacer, minimizeBtn, maximizeBtn, closeBtn);
        newTopBar.setAlignment(Pos.CENTER_LEFT);
        newTopBar.setPadding(new Insets(7, 8, 3, 10));
        newTopBar.setId("topBar");

        Delta dragDelta = new Delta();

        newTopBar.setOnMousePressed(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent mouseEvent) {
                // record a delta distance for the drag and drop operation.
                dragDelta.x = theStage.getX() - mouseEvent.getScreenX();
                dragDelta.y = theStage.getY() - mouseEvent.getScreenY();
            }
        });
        newTopBar.setOnMouseDragged(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent mouseEvent) {
                theStage.setX(mouseEvent.getScreenX() + dragDelta.x);
                theStage.setY(mouseEvent.getScreenY() + dragDelta.y);
            }
        });

        return newTopBar;
    }

    

    public static HBox createTopBar(Image iconImage, String titleString, Button maximizeBtn, Button closeBtn, Stage theStage) {

        ImageView barIconView = new ImageView(iconImage);
        barIconView.setFitWidth(20);
        barIconView.setPreserveRatio(true);

        // Rectangle2D logoRect = new Rectangle2D(30,30,30,30);
        Region spacer = new Region();

        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label newTitleLbl = new Label(titleString);
        newTitleLbl.setFont(titleFont);
        newTitleLbl.setTextFill(txtColor);
        newTitleLbl.setPadding(new Insets(0, 0, 0, 10));

        //  HBox.setHgrow(titleLbl2, Priority.ALWAYS);
        ImageView closeImage = highlightedImageView(closeImg);
        closeImage.setFitHeight(20);
        closeImage.setFitWidth(20);
        closeImage.setPreserveRatio(true);

        closeBtn.setGraphic(closeImage);
        closeBtn.setPadding(new Insets(0, 5, 0, 3));
        closeBtn.setId("closeBtn");

        ImageView minimizeImage = highlightedImageView(minimizeImg);
        minimizeImage.setFitHeight(20);
        minimizeImage.setFitWidth(20);
        minimizeImage.setPreserveRatio(true);

        Button minimizeBtn = new Button();
        minimizeBtn.setId("toolBtn");
        minimizeBtn.setGraphic(minimizeImage);
        minimizeBtn.setPadding(new Insets(0, 2, 1, 2));
        minimizeBtn.setOnAction(minEvent -> {
            theStage.setIconified(true);
        });

        maximizeBtn.setId("toolBtn");
        maximizeBtn.setGraphic(IconButton.getIconView(new Image(AppConstants.MAXIMIZE_ICON), 20));
        maximizeBtn.setPadding(new Insets(0, 3, 0, 3));

        HBox newTopBar = new HBox(barIconView, newTitleLbl, spacer, minimizeBtn, maximizeBtn, closeBtn);
        newTopBar.setAlignment(Pos.CENTER_LEFT);
        newTopBar.setPadding(new Insets(7, 8, 10, 10));
        newTopBar.setId("topBar");

        Delta dragDelta = new Delta();

        newTopBar.setOnMousePressed(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent mouseEvent) {
                // record a delta distance for the drag and drop operation.
                dragDelta.x = theStage.getX() - mouseEvent.getScreenX();
                dragDelta.y = theStage.getY() - mouseEvent.getScreenY();
            }
        });
        newTopBar.setOnMouseDragged(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent mouseEvent) {
                theStage.setX(mouseEvent.getScreenX() + dragDelta.x);
                theStage.setY(mouseEvent.getScreenY() + dragDelta.y);
            }
        });

        return newTopBar;
    }

    public static HBox createTopBar(Image iconImage, String titleString, Button closeBtn, Stage theStage) {

        ImageView barIconView = new ImageView(iconImage);
        barIconView.setFitWidth(20);
        barIconView.setPreserveRatio(true);

        // Rectangle2D logoRect = new Rectangle2D(30,30,30,30);
        Region spacer = new Region();

        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label newTitleLbl = new Label(titleString);
        newTitleLbl.setFont(titleFont);
        newTitleLbl.setTextFill(txtColor);
        newTitleLbl.setPadding(new Insets(0, 0, 0, 10));

        //  HBox.setHgrow(titleLbl2, Priority.ALWAYS);
        ImageView closeImage = highlightedImageView(closeImg);
        closeImage.setFitHeight(20);
        closeImage.setFitWidth(20);
        closeImage.setPreserveRatio(true);

        closeBtn.setGraphic(closeImage);
        closeBtn.setPadding(new Insets(0, 5, 0, 3));
        closeBtn.setId("closeBtn");

        ImageView minimizeImage = highlightedImageView(minimizeImg);
        minimizeImage.setFitHeight(20);
        minimizeImage.setFitWidth(20);
        minimizeImage.setPreserveRatio(true);

        Button minimizeBtn = new Button();
        minimizeBtn.setId("toolBtn");
        minimizeBtn.setGraphic(minimizeImage);
        minimizeBtn.setPadding(new Insets(0, 2, 1, 2));
        minimizeBtn.setOnAction(minEvent -> {
            theStage.setIconified(true);
        });

        HBox newTopBar = new HBox(barIconView, newTitleLbl, spacer, minimizeBtn, closeBtn);
        newTopBar.setAlignment(Pos.CENTER_LEFT);
        newTopBar.setPadding(new Insets(7, 8, 5, 10));
        newTopBar.setId("topBar");

        Delta dragDelta = new Delta();

        newTopBar.setOnMousePressed(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent mouseEvent) {
                // record a delta distance for the drag and drop operation.
                dragDelta.x = theStage.getX() - mouseEvent.getScreenX();
                dragDelta.y = theStage.getY() - mouseEvent.getScreenY();
            }
        });
        newTopBar.setOnMouseDragged(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent mouseEvent) {
                theStage.setX(mouseEvent.getScreenX() + dragDelta.x);
                theStage.setY(mouseEvent.getScreenY() + dragDelta.y);
            }
        });

        return newTopBar;
    }


    public static HBox createTopBar(Button extraBtn, Image iconImage, String titleString,  Button closeBtn, Stage theStage) {

        ImageView barIconView = new ImageView(iconImage);
        barIconView.setFitWidth(20);
        barIconView.setPreserveRatio(true);

        // Rectangle2D logoRect = new Rectangle2D(30,30,30,30);
        Region spacer = new Region();

        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label newTitleLbl = new Label(titleString);
        newTitleLbl.setFont(titleFont);
        newTitleLbl.setTextFill(txtColor);
        newTitleLbl.setPadding(new Insets(0, 0, 0, 10));

        //  HBox.setHgrow(titleLbl2, Priority.ALWAYS);
        ImageView closeImage = highlightedImageView(closeImg);
        closeImage.setFitHeight(20);
        closeImage.setFitWidth(20);
        closeImage.setPreserveRatio(true);


        closeBtn.setGraphic(closeImage);
        closeBtn.setPadding(new Insets(0, 5, 0, 3));
        closeBtn.setId("closeBtn");

        ImageView minimizeImage = highlightedImageView(minimizeImg);
        minimizeImage.setFitHeight(20);
        minimizeImage.setFitWidth(20);
        minimizeImage.setPreserveRatio(true);

        Button minimizeBtn = new Button();
        minimizeBtn.setId("toolBtn");
        minimizeBtn.setGraphic(minimizeImage);
        minimizeBtn.setPadding(new Insets(0, 2, 1, 2));
        minimizeBtn.setOnAction(minEvent -> {
            theStage.setIconified(true);
        });

        HBox newTopBar = new HBox(barIconView, newTitleLbl, spacer, extraBtn,  minimizeBtn, closeBtn);
        newTopBar.setAlignment(Pos.CENTER_LEFT);
        newTopBar.setPadding(new Insets(7, 8, 5, 10));
        newTopBar.setId("topBar");

        Delta dragDelta = new Delta();

        newTopBar.setOnMousePressed(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent mouseEvent) {
                // record a delta distance for the drag and drop operation.
                dragDelta.x = theStage.getX() - mouseEvent.getScreenX();
                dragDelta.y = theStage.getY() - mouseEvent.getScreenY();
            }
        });
        newTopBar.setOnMouseDragged(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent mouseEvent) {
                theStage.setX(mouseEvent.getScreenX() + dragDelta.x);
                theStage.setY(mouseEvent.getScreenY() + dragDelta.y);
            }
        });

        return newTopBar;
    }

    public static HBox createTopBar(Image iconImage, Label newTitleLbl, Button closeBtn, Stage theStage) {

        ImageView barIconView = new ImageView(iconImage);
        barIconView.setFitWidth(20);
        barIconView.setPreserveRatio(true);

        // Rectangle2D logoRect = new Rectangle2D(30,30,30,30);
        Region spacer = new Region();

        HBox.setHgrow(spacer, Priority.ALWAYS);

        newTitleLbl.setFont(titleFont);
        newTitleLbl.setTextFill(txtColor);
        newTitleLbl.setPadding(new Insets(0, 0, 0, 10));

        //  HBox.setHgrow(titleLbl2, Priority.ALWAYS);
        ImageView closeImage = highlightedImageView(closeImg);
        closeImage.setFitHeight(20);
        closeImage.setFitWidth(20);
        closeImage.setPreserveRatio(true);

        closeBtn.setGraphic(closeImage);
        closeBtn.setPadding(new Insets(0, 5, 0, 3));
        closeBtn.setId("closeBtn");

        ImageView minimizeImage = highlightedImageView(minimizeImg);
        minimizeImage.setFitHeight(20);
        minimizeImage.setFitWidth(20);
        minimizeImage.setPreserveRatio(true);

        Button minimizeBtn = new Button();
        minimizeBtn.setId("toolBtn");
        minimizeBtn.setGraphic(minimizeImage);
        minimizeBtn.setPadding(new Insets(0, 2, 1, 2));
        minimizeBtn.setOnAction(minEvent -> {
            theStage.setIconified(true);
        });
        
        HBox newTopBar = new HBox(barIconView, newTitleLbl, spacer, minimizeBtn, closeBtn);
        newTopBar.setAlignment(Pos.CENTER_LEFT);
        newTopBar.setPadding(new Insets(7, 8, 10, 10));
        newTopBar.setId("topBar");

        Delta dragDelta = new Delta();

        newTopBar.setOnMousePressed(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent mouseEvent) {
                // record a delta distance for the drag and drop operation.
                dragDelta.x = theStage.getX() - mouseEvent.getScreenX();
                dragDelta.y = theStage.getY() - mouseEvent.getScreenY();
            }
        });
        newTopBar.setOnMouseDragged(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent mouseEvent) {
                theStage.setX(mouseEvent.getScreenX() + dragDelta.x);
                theStage.setY(mouseEvent.getScreenY() + dragDelta.y);
            }
        });

        return newTopBar;
    }

    public static Button createImageButton(Image image, String name) {
        ImageView btnImageView = new ImageView(image);
        btnImageView.setFitHeight(100);
        btnImageView.setPreserveRatio(true);

        Button imageBtn = new Button(name);
        imageBtn.setGraphic(btnImageView);
        imageBtn.setId("startImageBtn");
        imageBtn.setFont(mainFont);
        imageBtn.setContentDisplay(ContentDisplay.TOP);

        return imageBtn;
    }

    public static Scene getFileProgressScene(Image icon, String headingString, SimpleStringProperty titleContextString, String fileName, ProgressBar progressBar, Stage stage, Button closeBtn) {

        double defaultRowHeight = 40;
       


        Text fileNameProgressText = new Text(fileName + " (" + String.format("%.1f", progressBar.getProgress() * 100) + "%)");
        fileNameProgressText.setFill(txtColor);
        fileNameProgressText.setFont(txtFont);

        Label titleBoxLabel = new Label();
        titleBoxLabel.setTextFill(txtColor);
        titleBoxLabel.setFont(txtFont);
        titleBoxLabel.textProperty().bind(fileNameProgressText.textProperty());

        HBox titleBox = createTopBar(icon, titleBoxLabel, closeBtn, stage);

        Text headingText = new Text(headingString);
        headingText.setFont(txtFont);
        headingText.setFill(txtColor);

        HBox headingBox = new HBox(headingText);
        headingBox.prefHeight(defaultRowHeight);
        headingBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(headingBox, Priority.ALWAYS);
        headingBox.setPadding(new Insets(10, 10, 10, 10));
        headingBox.setId("headingBox");

        HBox headingPaddingBox = new HBox(headingBox);

        headingPaddingBox.setPadding(new Insets(5, 0, 2, 0));

        VBox headerBox = new VBox(titleBox, headingPaddingBox);

        headerBox.setPadding(new Insets(0, 5, 0, 5));

        progressBar.prefWidthProperty().bind(stage.widthProperty().multiply(0.7));


        HBox progressAlignmentBox = new HBox(progressBar);
        progressAlignmentBox.setAlignment(Pos.CENTER);
        HBox.setHgrow(progressAlignmentBox,Priority.ALWAYS);
       

        progressBar.progressProperty().addListener((obs, oldVal, newVal) -> {
            fileNameProgressText.setText(fileName + " (" + String.format("%.1f", newVal.doubleValue() * 100) + "%)");
        });

        stage.titleProperty().bind(Bindings.concat(fileNameProgressText.textProperty(), " - ", titleContextString));

        HBox fileNameProgressBox = new HBox(fileNameProgressText);
        fileNameProgressBox.setAlignment(Pos.CENTER);
        fileNameProgressBox.setPadding(new Insets(20, 0, 0,  0));

        VBox colorBox = new VBox(progressAlignmentBox, fileNameProgressBox);
        colorBox.setId("bodyBox");
        HBox.setHgrow(colorBox, Priority.ALWAYS);
        colorBox.setPadding(new Insets(40, 0, 15, 0));

        VBox bodyBox = new VBox(colorBox);
        bodyBox.setId("bodyBox");
        bodyBox.setPadding(new Insets(15));
        bodyBox.setAlignment(Pos.CENTER);

        VBox bodyPaddingBox = new VBox(bodyBox);
        bodyPaddingBox.setPadding(new Insets(5, 5, 5, 5));

        Region footerSpacer = new Region();
        footerSpacer.setMinHeight(5);

        VBox footerBox = new VBox(footerSpacer);
        VBox layoutBox = new VBox(headerBox, bodyPaddingBox, footerBox);
        Scene scene = new Scene(layoutBox, STAGE_WIDTH, STAGE_HEIGHT);
        scene.setFill(null);
        scene.getStylesheets().add("/css/startWindow.css");

        // bodyTopRegion.minHeightProperty().bind(stage.heightProperty().subtract(30).divide(2).subtract(progressAlignmentBox.heightProperty()).subtract(fileNameProgressBox.heightProperty().divide(2)));
        bodyBox.prefHeightProperty().bind(stage.heightProperty().subtract(headerBox.heightProperty()).subtract(footerBox.heightProperty()).subtract(10));
        return scene;
    }

    public static Stage getStatusStage(String title, String statusMessage) {
        Stage statusStage = new Stage();
        statusStage.setResizable(false);
        statusStage.initStyle(StageStyle.UNDECORATED);
        
        statusStage.getIcons().add(logo);

        statusStage.setTitle(title);

        Label newTitleLbl = new Label(title);
        newTitleLbl.setFont(titleFont);
        newTitleLbl.setTextFill(txtColor);
        newTitleLbl.setPadding(new Insets(0, 0, 0, 10));

        ImageView barIconView = new ImageView(icon);
        barIconView.setFitHeight(20);
        barIconView.setPreserveRatio(true);

        HBox newTopBar = new HBox(barIconView, newTitleLbl);
        newTopBar.setAlignment(Pos.CENTER_LEFT);
        newTopBar.setPadding(new Insets(10, 8, 10, 10));
        newTopBar.setId("topBar");

        ImageView waitingView = new ImageView(logo);
        waitingView.setFitHeight(135);
        waitingView.setPreserveRatio(true);

        HBox imageBox = new HBox(waitingView);
        HBox.setHgrow(imageBox, Priority.ALWAYS);
        imageBox.setAlignment(Pos.CENTER);

        Text statusTxt = new Text(statusMessage);
        statusTxt.setFill(txtColor);
        statusTxt.setFont(txtFont);

        VBox bodyVBox = new VBox(imageBox, statusTxt);

        VBox.setVgrow(bodyVBox, Priority.ALWAYS);
        VBox.setMargin(bodyVBox, new Insets(0, 20, 20, 20));

        VBox layoutVBox = new VBox(newTopBar, bodyVBox);

        Scene statusScene = new Scene(layoutVBox, 420, 215);
        statusScene.setFill(null);
        statusScene.getStylesheets().add("/css/startWindow.css");
        
        statusStage.setScene(statusScene);


        return statusStage;
    }


    public static Stage getFileLocationStage(String title, Image logo, Button closeBtn, String statusMessage, File file) {
        Stage statusStage = new Stage();
        statusStage.setResizable(false);
        statusStage.initStyle(StageStyle.UNDECORATED);
        
        statusStage.getIcons().add(logo);

        statusStage.setTitle(title);


        Label newTitleLbl = new Label(title);
        newTitleLbl.setFont(titleFont);
        newTitleLbl.setTextFill(txtColor);
        newTitleLbl.setPadding(new Insets(0, 0, 0, 10));

        ImageView barIconView = new ImageView(logo);
        barIconView.setFitHeight(20);
        barIconView.setPreserveRatio(true);

        HBox newTopBar = createTopBar(logo, newTitleLbl, closeBtn, statusStage);

        HBox titleBox = new HBox(createImageButton(logo,statusMessage));
        HBox.setHgrow(titleBox, Priority.ALWAYS);
        titleBox.setAlignment(Pos.CENTER);

        Text fileText = new Text("Location");
        fileText.setFill(txtColor);
        fileText.setFont(txtFont);

        TextField fileField = new TextField(file.getAbsolutePath());
        HBox.setHgrow(fileField, Priority.ALWAYS);
        fileField.setEditable(false);


        Tooltip openFileLocationTip = new Tooltip("Open Folder");
        openFileLocationTip.setShowDelay( javafx.util.Duration.millis(100));

        Button openFileLocationBtn = new Button(" 🗁");
        openFileLocationBtn.setId("lblBtn");
        openFileLocationBtn.setOnAction(e->{
            try {
                Utils.open(file.isFile() ? file.getParentFile() : file);
                closeBtn.fire();
            } catch (Exception e1) {
                Alert a = new Alert(AlertType.NONE, e1.toString(), ButtonType.OK);
                a.setTitle("Error");
                a.setHeaderText("Error");
                a.show();
            }
        });

        HBox fileFieldBox = new HBox(fileField);
        fileFieldBox.setId("bodyBox");
        HBox.setHgrow(fileFieldBox,Priority.ALWAYS);
        fileFieldBox.setAlignment(Pos.CENTER_LEFT);

        HBox fileBox = new HBox(fileText, fileFieldBox, openFileLocationBtn);
        HBox.setHgrow(fileBox, Priority.ALWAYS);
        fileBox.setAlignment(Pos.CENTER_LEFT);
        fileBox.setPadding(new Insets(10,0,0,15));

        VBox bodyVBox = new VBox(titleBox, fileBox);

        VBox.setVgrow(bodyVBox, Priority.ALWAYS);
        VBox.setMargin(bodyVBox, new Insets(0,5,20,5));

        VBox layoutVBox = new VBox(newTopBar, bodyVBox);

        Scene statusScene = new Scene(layoutVBox, 420, 230);
        statusScene.setFill(null);
        statusScene.getStylesheets().add("/css/startWindow.css");
        
        statusStage.setScene(statusScene);


        return statusStage;
    }

    public static Scene getWaitngScene(Label statusLabel, Button cancelBtn, Stage theStage) {


        Button closeBtn = new Button();
        Label topLabel = new Label();
        topLabel.textProperty().bind(statusLabel.textProperty());

        HBox topBar = createTopBar(icon, topLabel, closeBtn, theStage); 
        

        ImageView waitingView = new ImageView(logo);
        waitingView.setFitHeight(135);
        waitingView.setPreserveRatio(true);

        HBox imageBox = new HBox(waitingView);
        HBox.setHgrow(imageBox, Priority.ALWAYS);
        imageBox.setAlignment(Pos.CENTER);

        closeBtn.setOnAction(e->cancelBtn.fire());
        ProgressBar progressBar = new ProgressBar(ProgressBar.INDETERMINATE_PROGRESS);

        HBox progressAlignmentBox = new HBox(progressBar);
        progressAlignmentBox.setAlignment(Pos.CENTER);
        progressAlignmentBox.setPadding(new Insets(0, 0, 0, 0));

       
        HBox statusLabelBox = new HBox(statusLabel);

        statusLabelBox.setPadding(new Insets(5, 0, 0, 15));

   
        HBox cancelBtnBox = new HBox(cancelBtn);
        HBox.setHgrow(cancelBtnBox, Priority.ALWAYS);
        cancelBtnBox.setAlignment(Pos.CENTER);
        cancelBtnBox.setPadding(new Insets(10,0,10,0));


        VBox progressPaddingBox = new VBox(imageBox, progressAlignmentBox, statusLabelBox, cancelBtnBox);

        HBox.setHgrow(progressPaddingBox, Priority.ALWAYS);


        VBox bodyBox = new VBox(progressPaddingBox);
        bodyBox.setAlignment(Pos.CENTER);

        VBox bodyPaddingBox = new VBox(topBar, bodyBox);
        bodyPaddingBox.setPadding(new Insets(0, 5, 5, 5));

        Region footerSpacer = new Region();
        footerSpacer.setMinHeight(5);

        VBox footerBox = new VBox(footerSpacer);
        VBox layoutBox = new VBox(bodyPaddingBox, footerBox);
        Scene scene = new Scene(layoutBox, 420, 250);
        scene.setFill(null);
        scene.getStylesheets().add("/css/startWindow.css");

        progressBar.prefWidthProperty().bind(scene.widthProperty().multiply(0.7));

        bodyBox.prefHeightProperty().bind(scene.heightProperty().subtract(footerBox.heightProperty()).subtract(10));

        theStage.setOnCloseRequest(e->cancelBtn.fire());
        return scene;
    }

    public static void showMagnifyingStage(String title, String magnifyString) {
        Stage magnifyingStage = new Stage();
        magnifyingStage.setResizable(false);
        magnifyingStage.initStyle(StageStyle.UNDECORATED);
        magnifyingStage.setTitle(title);
        magnifyingStage.getIcons().add(logo);

        Label topLabel = new Label(title);
        Button closeBtn = new Button();

        HBox topBar = createTopBar(logo, topLabel, closeBtn, magnifyingStage);

        TextArea largeField = new TextArea(magnifyString);
        HBox.setHgrow(largeField, Priority.ALWAYS);
        VBox.setVgrow(largeField, Priority.ALWAYS);
        largeField.setId("largeField");
        largeField.setWrapText(true);
        largeField.setEditable(false);

        VBox largeFieldBox = new VBox(largeField);
        VBox.setVgrow(largeFieldBox, Priority.ALWAYS);
        VBox.setMargin(largeFieldBox, new Insets(0, 10, 10, 10));
        largeFieldBox.setAlignment(Pos.CENTER);
      

         Tooltip copiedTooltip = new Tooltip("Copied");
        
        Button copyBtn = new Button("⧉  Copy");

        copyBtn.setOnAction(e->{
            e.consume();
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(largeField.getText());
            clipboard.setContent(content);

            Point2D p = copyBtn.localToScene(0.0, 0.0);
         

            copiedTooltip.show(
                copyBtn,  
                p.getX() + copyBtn.getScene().getX() + copyBtn.getScene().getWindow().getX(), 
                (p.getY()+ copyBtn.getScene().getY() + copyBtn.getScene().getWindow().getY())-copyBtn.getLayoutBounds().getHeight()
            );
            PauseTransition pt = new PauseTransition(javafx.util.Duration.millis(1600));
            pt.setOnFinished(ptE->{
                copiedTooltip.hide();
            });
            pt.play();
        });

        HBox copyBtnBox = new HBox(copyBtn);
        HBox.setHgrow(copyBtnBox, Priority.ALWAYS);
        copyBtnBox.setAlignment(Pos.CENTER_RIGHT);

        VBox layoutVBox = new VBox(topBar, largeFieldBox, copyBtnBox);
        VBox.setVgrow(layoutVBox, Priority.ALWAYS);
        
        Scene statusScene = new Scene(layoutVBox, 800, 150);
        statusScene.setFill(null);
        statusScene.getStylesheets().add("/css/startWindow.css");
        
        magnifyingStage.setScene(statusScene);
        magnifyingStage.show();
        closeBtn.setOnAction(e->{
            magnifyingStage.close();
        });
    }


 
    public static void showStatusStage(Stage statusStage, String title, String statusMessage) {
       
        statusStage.setTitle(title);

        Label newTitleLbl = new Label(title);
        newTitleLbl.setFont(Stages.titleFont);
        newTitleLbl.setTextFill(Stages.txtColor);
        newTitleLbl.setPadding(new Insets(0, 0, 0, 10));

        ImageView barIconView = new ImageView(Stages.icon);
        barIconView.setFitHeight(20);
        barIconView.setPreserveRatio(true);

        HBox newTopBar = new HBox(barIconView, newTitleLbl);
        newTopBar.setAlignment(Pos.CENTER_LEFT);
        newTopBar.setPadding(new Insets(10, 8, 10, 10));
        newTopBar.setId("topBar");

        ImageView waitingView = new ImageView(Stages.logo);
        waitingView.setFitHeight(135);
        waitingView.setPreserveRatio(true);

        HBox imageBox = new HBox(waitingView);
        HBox.setHgrow(imageBox, Priority.ALWAYS);
        imageBox.setAlignment(Pos.CENTER);

        Text statusTxt = new Text(statusMessage);
        statusTxt.setFill(Stages.txtColor);
        statusTxt.setFont(Stages.txtFont);

        VBox bodyVBox = new VBox(imageBox, statusTxt);

        VBox.setVgrow(bodyVBox, Priority.ALWAYS);
        VBox.setMargin(bodyVBox, new Insets(0, 20, 20, 20));

        VBox layoutVBox = new VBox(newTopBar, bodyVBox);

        Scene statusScene = new Scene(layoutVBox, 420, 215);
        statusScene.setFill(null);
        statusScene.getStylesheets().add("/css/startWindow.css");

        statusStage.setScene(statusScene);
        statusStage.show();
    }

    public static File getFile(String title, Stage owner, FileChooser.ExtensionFilter... extensionFilters) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(title);
        //  fileChooser.setInitialFileName(initialFileName);

        fileChooser.getExtensionFilters().addAll(extensionFilters);
        File file = fileChooser.showOpenDialog(owner);
        return file;
    }

    

    public static void createPassword(Stage passwordStage, String topTitle, Image windowLogo, Image mainLogo, Button closeBtn, ExecutorService execService, EventHandler<WorkerStateEvent> onSucceeded) {
        
        passwordStage.setTitle(topTitle);

      
        HBox titleBox = createTopBar(Stages.icon, topTitle, closeBtn, passwordStage);

        Button imageBtn = createImageButton(mainLogo, "Password");
        imageBtn.setGraphicTextGap(20);
        HBox imageBox = new HBox(imageBtn);
        imageBox.setAlignment(Pos.CENTER);

        Text passwordTxt = new Text("Create password:");
        passwordTxt.setFill(Stages.txtColor);
        passwordTxt.setFont(Stages.txtFont);

        PasswordField passwordField = new PasswordField();

        passwordField.setFont(Stages.txtFont);
        passwordField.setId("passField");
        HBox.setHgrow(passwordField, Priority.ALWAYS);

        PasswordField createPassField2 = new PasswordField();
        HBox.setHgrow(createPassField2, Priority.ALWAYS);
        createPassField2.setId("passField");

        Button enterButton = new Button("[enter]");
        enterButton.setId("toolBtn");

        HBox passwordBox = new HBox(passwordTxt, passwordField);
        passwordBox.setAlignment(Pos.CENTER_LEFT);
        passwordBox.setPadding(new Insets(0, 10,0,0));



        VBox bodyBox = new VBox(passwordBox);
        VBox.setMargin(bodyBox, new Insets(5, 10, 0, 20));
        VBox.setVgrow(bodyBox, Priority.ALWAYS);

        Platform.runLater(() -> passwordField.requestFocus());

        VBox passwordVBox = new VBox(titleBox, imageBox, bodyBox);

        Scene passwordScene = new Scene(passwordVBox, STAGE_WIDTH, STAGE_HEIGHT);
        passwordScene.setFill(null);
        passwordScene.getStylesheets().add("/css/startWindow.css");
        passwordStage.setScene(passwordScene);

        closeBtn.setOnAction(e -> {
            passwordField.setText("");
            createPassField2.setText("");
            passwordStage.close();
        });

        Text reenterTxt = new Text("Confirm password:");
        reenterTxt.setFill(Stages.txtColor);
        reenterTxt.setFont(Stages.txtFont);

  

        Button enter2 = new Button("[enter]");
        enter2.setId("toolBtn");

        HBox secondPassBox = new HBox(reenterTxt, createPassField2);
        secondPassBox.setAlignment(Pos.CENTER_LEFT);
        secondPassBox.setPadding(new Insets(0,10,0,0));

        enterButton.setOnAction(e->{

           // String passStr = passwordField.getText();
            // createPassField.setText("");

            bodyBox.getChildren().remove(passwordBox);


            bodyBox.getChildren().add(secondPassBox);

            createPassField2.requestFocus();
            
        });

        passwordField.setOnKeyPressed(e->{
            if(passwordField.getPromptText().length() > 0){
                passwordField.setPromptText("");
            }
        });

        passwordField.textProperty().addListener((obs,oldval,newval) -> {
            
            if(passwordField.getText().length() == 0){
                if(passwordBox.getChildren().contains(enterButton)){
                    passwordBox.getChildren().remove(enterButton);
                }
            }else{
                if(!passwordBox.getChildren().contains(enterButton)){
                    passwordBox.getChildren().add(enterButton);
                }
            }
        });

        createPassField2.textProperty().addListener((obs,oldval,newval)->{
            if(createPassField2.getText().length() == 0){
                if(secondPassBox.getChildren().contains(enter2)){
                    secondPassBox.getChildren().remove(enter2);
                }
            }else{
                if(!secondPassBox.getChildren().contains(enter2)){
                    secondPassBox.getChildren().add(enter2);
                }
            }
        });

        passwordField.setOnAction(e->enterButton.fire());
        
        Tooltip errorToolTip = new Tooltip("Password mis-match");
        

        enter2.setOnAction(e->{
            if (passwordField.getText().equals(createPassField2.getText())) {

                Utils.returnObject(SecretString.create(passwordField.getText()),execService, onSucceeded);
                createPassField2.setText("");
                passwordField.setText("");
            } else {
                bodyBox.getChildren().clear();
                createPassField2.setText("");
                passwordField.setText("");
                
                
    
                bodyBox.getChildren().add(passwordBox);
                passwordField.requestFocus();

                Point2D p = passwordBox.localToScene(0.0, 0.0);
       

                errorToolTip.show(
                    passwordBox,  
                    p.getX() + passwordBox.getScene().getX() + passwordBox.getScene().getWindow().getX() + passwordBox.getLayoutBounds().getWidth()-150, 
                    (p.getY()+ passwordBox.getScene().getY() + passwordBox.getScene().getWindow().getY())-passwordBox.getLayoutBounds().getHeight()
                );
                
                PauseTransition pt = new PauseTransition(javafx.util.Duration.millis(1600));
                pt.setOnFinished(ptE->{
                    errorToolTip.hide();
                });
                pt.play();
            }
        });

        createPassField2.setOnAction(e->{
            enter2.fire();
        });
        
    }


    public static Scene createPasswordScene(Image mainLogo, HBox topBar, ExecutorService execService, EventHandler<WorkerStateEvent> onSucceeded) {
        
        HBox titleBox = topBar;

        Button imageBtn = createImageButton(mainLogo, "Password");
        imageBtn.setGraphicTextGap(20);
        HBox imageBox = new HBox(imageBtn);
        imageBox.setAlignment(Pos.CENTER);

        Text passwordTxt = new Text("Create password:");
        passwordTxt.setFill(Stages.txtColor);
        passwordTxt.setFont(Stages.txtFont);

        PasswordField passwordField = new PasswordField();

        passwordField.setFont(Stages.txtFont);
        passwordField.setId("passField");
        HBox.setHgrow(passwordField, Priority.ALWAYS);

        PasswordField createPassField2 = new PasswordField();
        HBox.setHgrow(createPassField2, Priority.ALWAYS);
        createPassField2.setId("passField");

        Button enterButton = new Button("[enter]");
        enterButton.setId("toolBtn");

        HBox passwordBox = new HBox(passwordTxt, passwordField);
        passwordBox.setAlignment(Pos.CENTER_LEFT);
        passwordBox.setPadding(new Insets(0, 10,0,0));



        VBox bodyBox = new VBox(passwordBox);
        VBox.setMargin(bodyBox, new Insets(5, 10, 0, 20));
        VBox.setVgrow(bodyBox, Priority.ALWAYS);

        Platform.runLater(() -> passwordField.requestFocus());

        VBox passwordVBox = new VBox(titleBox, imageBox, bodyBox);

        Scene passwordScene = new Scene(passwordVBox, STAGE_WIDTH, STAGE_HEIGHT);
        passwordScene.setFill(null);
        passwordScene.getStylesheets().add(DEFAULT_CSS);
   
        Text reenterTxt = new Text("Confirm password:");
        reenterTxt.setFill(Stages.txtColor);
        reenterTxt.setFont(Stages.txtFont);

  

        Button enter2 = new Button("[enter]");
        enter2.setId("toolBtn");

        HBox secondPassBox = new HBox(reenterTxt, createPassField2);
        secondPassBox.setAlignment(Pos.CENTER_LEFT);
        secondPassBox.setPadding(new Insets(0,10,0,0));



        passwordField.setOnKeyPressed(e->{
            if(passwordField.getPromptText().length() > 0){
                passwordField.setPromptText("");
            }
        });

        passwordField.textProperty().addListener((obs,oldval,newval) -> {
            
            if(passwordField.getText().length() == 0){
                if(passwordBox.getChildren().contains(enterButton)){
                    passwordBox.getChildren().remove(enterButton);
                }
            }else{
                if(!passwordBox.getChildren().contains(enterButton)){
                    passwordBox.getChildren().add(enterButton);
                }
            }
        });

        createPassField2.textProperty().addListener((obs,oldval,newval)->{
            if(createPassField2.getText().length() == 0){
                if(secondPassBox.getChildren().contains(enter2)){
                    secondPassBox.getChildren().remove(enter2);
                }
            }else{
                if(!secondPassBox.getChildren().contains(enter2)){
                    secondPassBox.getChildren().add(enter2);
                }
            }
        });

        passwordField.setOnAction(e->enterButton.fire());
        
        Tooltip errorToolTip = new Tooltip();
        

        enter2.setOnAction(e->{
            if (passwordField.getText().equals(createPassField2.getText())) {
                Utils.returnObject(SecretString.create( passwordField.getText()), execService, onSucceeded);
                passwordField.setText("");
                createPassField2.setText("");
            } else {
                bodyBox.getChildren().clear();
                createPassField2.setText("");
                passwordField.setText("");
                
                
    
                bodyBox.getChildren().add(passwordBox);
                passwordField.requestFocus();

                Point2D p = passwordBox.localToScene(0.0, 0.0);
                
                errorToolTip.setText("Password mis-match");
                errorToolTip.show(
                    passwordBox,  
                    p.getX() + passwordBox.getScene().getX() + passwordBox.getScene().getWindow().getX() + passwordBox.getLayoutBounds().getWidth()-150, 
                    (p.getY()+ passwordBox.getScene().getY() + passwordBox.getScene().getWindow().getY())-passwordBox.getLayoutBounds().getHeight()
                );
                
                PauseTransition pt = new PauseTransition(javafx.util.Duration.millis(1600));
                pt.setOnFinished(ptE->{
                    errorToolTip.hide();
                });
                pt.play();
            }
        });

        enterButton.setOnAction(e->{

            if(passwordField.getText().length() > 3){

                bodyBox.getChildren().remove(passwordBox);


                bodyBox.getChildren().add(secondPassBox);

                createPassField2.requestFocus();
            }else{
                Point2D p = passwordBox.localToScene(0.0, 0.0);
       
                errorToolTip.setText("Password too short");
                errorToolTip.show(
                    passwordBox,  
                    p.getX() + passwordBox.getScene().getX() + passwordBox.getScene().getWindow().getX() + passwordBox.getLayoutBounds().getWidth()-150, 
                    (p.getY()+ passwordBox.getScene().getY() + passwordBox.getScene().getWindow().getY())-passwordBox.getLayoutBounds().getHeight()
                );
                
                PauseTransition pt = new PauseTransition(javafx.util.Duration.millis(1600));
                pt.setOnFinished(ptE->{
                    errorToolTip.hide();
                });
                pt.play();
            }
            
        });

        createPassField2.setOnAction(e->{
            enter2.fire();
        });
        
        return passwordScene;
    }

    public static void enterPassword(String title, AppData appData, Stage appStage, EventHandler<ActionEvent> closeEvent, EventHandler<ActionEvent> enterEvent){
        appStage.setTitle(title);

        Button closeBtn = new Button();

        HBox titleBox = Stages.createTopBar(Stages.icon, title, closeBtn, appStage);

     
        ImageView btnImageView = new ImageView(Stages.logo);
        btnImageView.setFitHeight(100);
        btnImageView.setPreserveRatio(true);

        Button imageButton = new Button("Netnotes");
        imageButton.setGraphic(btnImageView);
        imageButton.setId("startImageBtn");
        imageButton.setFont(Stages.mainFont);
        imageButton.setContentDisplay(ContentDisplay.TOP);

        HBox imageBox = new HBox(imageButton);
        imageBox.setAlignment(Pos.CENTER);

        Text passwordTxt = new Text("Enter password:");
        passwordTxt.setFill(Stages.txtColor);
        passwordTxt.setFont(Stages.txtFont);

        PasswordField passwordField = new PasswordField();
        passwordField.setFont(Stages.txtFont);
        passwordField.setId("passField");
        HBox.setHgrow(passwordField, Priority.ALWAYS);

        Platform.runLater(() -> passwordField.requestFocus());

        HBox passwordBox = new HBox(passwordTxt, passwordField);
        passwordBox.setAlignment(Pos.CENTER_LEFT);
        passwordBox.setPadding(new Insets(20, 0, 0, 0));

        Button clickRegion = new Button();
        clickRegion.setPrefWidth(Double.MAX_VALUE);
        clickRegion.setId("transparentColor");
        clickRegion.setPrefHeight(500);

        clickRegion.setOnAction(e -> {
            passwordField.requestFocus();

        });

        VBox.setMargin(passwordBox, new Insets(5, 10, 0, 20));

        VBox layoutVBox = new VBox(titleBox, imageBox, passwordBox, clickRegion);
        VBox.setVgrow(layoutVBox, Priority.ALWAYS);

        Scene passwordScene = new Scene(layoutVBox, Stages.STAGE_WIDTH, Stages.STAGE_HEIGHT);
        passwordScene.setFill(null);
        passwordScene.getStylesheets().add("/css/startWindow.css");
        appStage.setScene(passwordScene);


        closeBtn.setOnAction(closeEvent);

       

        passwordField.setOnAction(enterEvent);
       
    

 
       Platform.runLater(()->passwordField.requestFocus());
      
       
     
       passwordScene.focusOwnerProperty().addListener((obs, oldval, newval)->{
            
            Platform.runLater(()->passwordField.requestFocus());
            
        });
      

        appStage.show();
        appStage.centerOnScreen();
    }


    public static void showGetTextInput(String prompt, String title, Image img, TextField textField, Button closeBtn, Stage textInputStage) {
        
        textInputStage.setTitle(title);
  

        HBox titleBox = createTopBar(Stages.icon, title, closeBtn, textInputStage);

        Button imageButton = createImageButton(img, title);

        HBox imageBox = new HBox(imageButton);
        imageBox.setAlignment(Pos.CENTER);

        Text promptTxt = new Text("" + prompt + ":");
        promptTxt.setFill(Stages.txtColor);
        promptTxt.setFont(Stages.txtFont);


        textField.setFont(Stages.txtFont);
        textField.setId("textField");



        HBox.setHgrow(textField, Priority.ALWAYS);

        Platform.runLater(() -> textField.requestFocus());

        HBox passwordBox = new HBox(promptTxt, textField);
        passwordBox.setAlignment(Pos.CENTER_LEFT);

    
        VBox.setMargin(passwordBox, new Insets(5, 10, 0, 20));

        VBox layoutVBox = new VBox(titleBox, imageBox, passwordBox);
        VBox.setVgrow(layoutVBox, Priority.ALWAYS);

        Scene textInputScene = new Scene(layoutVBox, STAGE_WIDTH, STAGE_HEIGHT);
        textInputScene.setFill(null);
        textInputScene.getStylesheets().add("/css/startWindow.css");

        textInputStage.setScene(textInputScene);

        textInputScene.focusOwnerProperty().addListener((obs, oldVal,newVal)->{
            if(!(newVal instanceof TextField)){
                Platform.runLater(()->textField.requestFocus());
            }
        });

        textInputStage.show();


    }


    public static void enterPassword(String topTitle,Image windowLogo, Image smallLogo, String windowSubTitle, ExecutorService execService, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed) {

        
        Stage passwordStage = new Stage();

        passwordStage.setTitle(topTitle);

        passwordStage.getIcons().add(windowLogo);
        passwordStage.setResizable(false);
        passwordStage.initStyle(StageStyle.UNDECORATED);

        Button closeBtn = new Button();

        HBox titleBox = createTopBar(smallLogo, topTitle, closeBtn, passwordStage);

        Button imageButton = createImageButton(windowLogo, windowSubTitle);

        HBox imageBox = new HBox(imageButton);
        imageBox.setAlignment(Pos.CENTER);

        Text passwordTxt = new Text("Enter password:");
        passwordTxt.setFill(Stages.txtColor);
        passwordTxt.setFont(Stages.txtFont);

        PasswordField passwordField = new PasswordField();
        passwordField.setFont(Stages.txtFont);
        passwordField.setId("passField");
        HBox.setHgrow(passwordField, Priority.ALWAYS);

        Platform.runLater(() -> passwordField.requestFocus());

        HBox passwordBox = new HBox(passwordTxt, passwordField);
        passwordBox.setAlignment(Pos.CENTER_LEFT);

        Button clickRegion = new Button();
        clickRegion.setMaxWidth(Double.MAX_VALUE);
        clickRegion.setId("transparentColor");
        clickRegion.setPrefHeight(Double.MAX_VALUE);

        clickRegion.setOnAction(e -> {
            passwordField.requestFocus();
        });

        VBox bodyBox = new VBox(passwordBox, clickRegion);
        VBox.setMargin(bodyBox, new Insets(5, 10, 0, 20));
        VBox.setVgrow(bodyBox, Priority.ALWAYS);

        VBox layoutVBox = new VBox(titleBox, imageBox, bodyBox);
        

        Scene passwordScene = new Scene(layoutVBox, STAGE_WIDTH, STAGE_HEIGHT);
        passwordScene.setFill(null);
        passwordScene.getStylesheets().add("/css/startWindow.css");
        passwordStage.setScene(passwordScene);

        closeBtn.setOnAction(e -> {
            passwordBox.getChildren().remove(passwordField);
            passwordField.setDisable(true);
            passwordField.setText("");
            Utils.returnException(NoteConstants.STATUS_SHUTDOWN, execService, onFailed);
            passwordStage.close();
        });

        passwordStage.setOnCloseRequest(e->{
            Utils.returnException(NoteConstants.STATUS_SHUTDOWN, execService, onFailed);
        });

        passwordField.setOnAction(e -> {
            if(passwordField.getText().length() > 0){
                Utils.returnObject(SecretString.create( passwordField.getText()), execService, onSucceeded);
                passwordStage.close();
            }
        });

        passwordStage.show();

    }

    public void createAutorunKeyDialog(Stage appStage,EventHandler<ActionEvent> closeEvent, boolean isNewKey, Runnable newKey, Runnable disableAutorun){

        TextField inpuTextField = new TextField();
        Button closeBtn = new Button();
        Stages.showGetTextInput(!isNewKey ? "Autrun key invalid. " : "" + "Create autorun key? (Y/n)", "Autorun - Setup", Stages.logo, inpuTextField, closeBtn, appStage);
        closeBtn.setOnAction(closeEvent);

        inpuTextField.setOnKeyPressed(e -> {

            KeyCode keyCode = e.getCode();

            if (keyCode == KeyCode.ENTER || keyCode == KeyCode.Y) {
                newKey.run();
            }else{
                if(keyCode == KeyCode.N){
                    disableAutorun.run();
                }else{
                    inpuTextField.setText("");
                }
            }
        });
      
    }

    public static Scene getAuthorizationScene(Stage txStage,  String title, Button closeBtn, PasswordField passwordField, JsonObject dataObject, String locationString, double rowHeight, double lblCol){

        JsonParametersBox parametersBox = new JsonParametersBox(dataObject, lblCol);
        parametersBox.openAll();


        Label locationLbl = new Label("Location:");
        locationLbl.setMinWidth(lblCol);
        locationLbl.setFont(Stages.txtFont);

        TextField locationField = new TextField(locationString);
        locationField.setEditable(false);
        HBox.setHgrow(locationField, Priority.ALWAYS);
        locationField.setFont(Stages.txtFont);

        HBox locationBox = new HBox(locationLbl, locationField);
        HBox.setHgrow(locationBox,Priority.ALWAYS);
        locationBox.setAlignment(Pos.CENTER_LEFT);
        locationBox.setMinHeight(rowHeight);


        HBox titleBox = Stages.createTopBar(Stages.icon, title, closeBtn, txStage);

        ImageView btnImageView = new ImageView(Stages.logo);
        btnImageView.setPreserveRatio(true);
        btnImageView.setFitHeight(75);
        

        Label textField = new Label("Authorization Required");
        textField.setFont(Stages.mainFont);
        textField.setPadding(new Insets(20,0,20,15));
        

        VBox imageBox = new VBox(btnImageView, textField);
        imageBox.setAlignment(Pos.CENTER);
        imageBox.setPadding(new Insets(10,0,10,0));

        Text passwordTxt = new Text("Enter password:");
        passwordTxt.setFill(Stages.txtColor);
        passwordTxt.setFont(Stages.txtFont);

        passwordField.setFont(Stages.txtFont);
        passwordField.setId("passField");

        HBox.setHgrow(passwordField, Priority.ALWAYS);

        HBox passwordBox = new HBox(passwordTxt, passwordField);
        passwordBox.setAlignment(Pos.CENTER_LEFT);
        passwordBox.setPadding( new Insets(5, 10, 15, 20));



        ScrollPane bodyScroll = new ScrollPane(parametersBox);


        VBox bodyBox = new VBox(locationBox, bodyScroll);
        HBox.setHgrow(bodyBox,Priority.ALWAYS);
        VBox.setVgrow(bodyBox,Priority.ALWAYS);
        bodyBox.setPadding(new Insets(0,20, 0, 20));

        Button exportBtn = new Button("🖫 Export JSON…");
        exportBtn.setOnAction(onSave->{
            ExtensionFilter txtFilter = new FileChooser.ExtensionFilter("JSON (application/json)", "*.json");
            FileChooser saveChooser = new FileChooser();
            saveChooser.setTitle("🖫 Export JSON…");
            saveChooser.getExtensionFilters().addAll(txtFilter);
            saveChooser.setSelectedExtensionFilter(txtFilter);
            File saveFile = saveChooser.showSaveDialog(txStage);
            if(saveFile != null){
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                
                try {
                    Files.writeString(saveFile.toPath(), gson.toJson(dataObject));
                } catch (IOException e1) {
                    Alert alert = new Alert(AlertType.NONE, e1.toString(), ButtonType.OK);
                    alert.setTitle("Error");
                    alert.setHeaderText("Error");
                    alert.initOwner(txStage);
                    alert.show();
                }
            }
        });

        HBox exportBtnBox = new HBox(exportBtn);
        exportBtnBox.setAlignment(Pos.CENTER_RIGHT);
        exportBtnBox.setPadding(new Insets(15,15,15,0));

        VBox layoutVBox = new VBox(titleBox, imageBox,bodyBox, exportBtnBox, passwordBox);
        VBox.setVgrow(layoutVBox, Priority.ALWAYS);

        bodyScroll.prefViewportWidthProperty().bind(bodyBox.widthProperty().subtract(1));
        bodyScroll.prefViewportHeightProperty().bind(bodyBox.heightProperty().subtract(10));

        parametersBox.setPrefWidth(bodyBox.widthProperty().get() -1);
        bodyScroll.prefViewportWidthProperty().addListener((obs,oldval,newval)->{
            parametersBox.setPrefWidth(newval.doubleValue()-50);
        });



        Scene passwordScene = new Scene(layoutVBox, 830, 600);
        passwordScene.setFill(null);
        passwordScene.getStylesheets().add(Stages.DEFAULT_CSS);
  
        return passwordScene;
    }

    /*private void addAppToTray() {
        try {

            java.awt.Toolkit.getDefaultToolkit();

            BufferedImage imgBuf = SwingFXUtils.fromFXImage(new Image("/assets/icon15.png"), null);

            m_tray = java.awt.SystemTray.getSystemTray();

            m_trayIcon = new java.awt.TrayIcon((java.awt.Image) imgBuf, "Netnotes");
            m_trayIcon.setActionCommand("show");

            m_trayIcon.addActionListener(event -> Platform.runLater(() -> {
                ActionEvent aEv = event;
                if (aEv.getActionCommand().equals("show")) {
                    m_networksData.show();
                }
              
            }));

            java.awt.MenuItem openItem = new java.awt.MenuItem("Show Netnotes");
            openItem.addActionListener(event -> Platform.runLater(() -> m_networksData.show()));

            java.awt.Font defaultFont = java.awt.Font.decode(null);
            java.awt.Font boldFont = defaultFont.deriveFont(java.awt.Font.BOLD);
            openItem.setFont(boldFont);

            java.awt.MenuItem exitItem = new java.awt.MenuItem("Close");
            exitItem.addActionListener(event -> Platform.runLater(() -> {
                m_networksData.shutdown();
                m_tray.remove(m_trayIcon);
                shutdownNow();
            }));

            final java.awt.PopupMenu popup = new java.awt.PopupMenu();
            popup.add(openItem);
            popup.addSeparator();
            popup.add(exitItem);
            m_trayIcon.setPopupMenu(popup);

            m_tray.add(m_trayIcon);

        } catch (java.awt.AWTException e) {
            try {
                Files.writeString(logFile.toPath(), "\nAWT - trayItem: " + e.toString(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e1) {
              
            }
            
        }

    }*/

}
