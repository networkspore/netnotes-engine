package io.netnotes.engine;

import javafx.beans.value.ChangeListener;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.text.Text;
import javafx.geometry.Pos;

public class PageBox extends HBox{

    private Button m_updateBtn;
    private Text m_maxText = null;
    private Text m_pageOffsetText = null;
    private Text m_pageLimitText = null;
    private TextField m_maxField = null;
    private TextField m_pageOffsetTextField = null;
    private TextField m_pageLimitField = null;
    private Button m_pageNextBtn = null;
    private Button m_pagePrevBtn = null;
    private ChangeListener<String> m_pageOffsetFieldListener;
    private ChangeListener<String> m_pageLimitFieldListener;
    private int m_maxItems = -1;
    private int m_limit = 50;
    private int m_offset = 0;
    private HBox m_maxBox;

    public PageBox(Button updateBtn){
        super();
        setAlignment(Pos.CENTER);
        m_updateBtn = updateBtn;
    }

    public int getMaxItems(){
        return m_maxItems == -1 ? Integer.MAX_VALUE : m_maxItems;
    }

    public void setMaxItems(int maxItems){
        m_maxItems = maxItems;
        if(maxItems > -1){
            if(m_maxBox != null && !m_maxBox.getChildren().contains(m_maxField)){
                m_maxBox.getChildren().addAll(m_maxText, m_maxField);
            }
        }else{
            if(m_maxBox != null && m_maxBox.getChildren().contains(m_maxField)){
                m_maxBox.getChildren().removeAll(m_maxText, m_maxField);
            }
        }
        if(m_maxField != null){
            m_maxField.setText(maxItems + "");
        }
    }

    public int getLimit(){
        return m_limit < 1 ? 1 : m_limit;
    }

    public void setLimit(int limit){
        limit = !isUnknown() ? (limit > m_maxItems ? m_maxItems : limit) : limit;
        m_limit = limit < 1 ? 1 : limit;
    }

    public int getOffset(){
        return m_offset;
    }

    public void setOffest(int offset){
        offset = isUnknown() ? offset : ( offset > getMaxOffset() ? getMaxOffset() : offset);
        m_offset = offset < 0 ? 0 : offset; 
    }

    public int getMaxPages(){
        return  (int)Math.floor(getMaxItems() / getLimit());
    }

    public int getMaxOffset(){
        return isUnknown() ? Integer.MAX_VALUE - getLimit() : getMaxPages() * getLimit();
    }

    public boolean isUnknown(){
        return m_maxItems == -1 || m_limit == -1;
    }

    public int nextPage(){
        int limit = getLimit();
        int offset = getOffset();
        
        setOffest(offset + limit);
        offset = getOffset();
        if(m_pageOffsetTextField != null){
            m_pageOffsetTextField.setText( offset + "");
            m_updateBtn.fire();
        }
        return offset;
    }

    public int prevPage(){
        int limit = getLimit();
        int offset = getOffset();
        setOffest(offset - limit);
        offset = getOffset();
        if(m_pageOffsetTextField != null){
            m_pageOffsetTextField.setText( offset + "");
            m_updateBtn.fire();
        }
        return getOffset();
    }


    public void clear(){
        getChildren().clear();
        if(m_pageOffsetTextField != null){
            m_pageOffsetTextField.setOnAction(null);
        }
        if(m_pageOffsetFieldListener != null){
            m_pageOffsetTextField.textProperty().addListener(m_pageOffsetFieldListener);
            m_pageOffsetFieldListener = null;
        }
        if(m_pageLimitFieldListener != null){
            m_pageLimitField.textProperty().removeListener(m_pageLimitFieldListener);
            m_pageLimitFieldListener = null;
        }
        if(m_pageNextBtn != null){
            m_pageNextBtn.setOnAction(null);
        }
        if(m_pagePrevBtn != null){
            m_pagePrevBtn.setOnAction(null);
        }
        if(m_maxBox != null){
            m_maxBox.getChildren().clear();
        }
        m_maxBox = null;
        m_maxField = null;
        m_maxText = null;
        m_pageNextBtn = null;
        m_pagePrevBtn = null;
        m_pageOffsetTextField = null;
        m_pageLimitField = null; 
    }

    public void show(){
        clear();
        double charWidth = Utils.computeTextWidth(Stages.txtFont, "0");

        m_pageOffsetText = new Text("Offset ");
        m_pageOffsetText.setFont(Stages.txtFont);
        m_pageOffsetText.setFill(Stages.txtColor);
        
        m_pageOffsetTextField = new TextField(m_offset + "");
        m_pageOffsetTextField.setId("bodyBox");
        m_pageOffsetTextField.setPrefWidth((charWidth * 3) + 20 );
        m_pageOffsetFieldListener = (obs,oldval,newval)->{
            if(m_pageOffsetTextField != null){
                if(newval.length() > 0){
                    String number = Utils.isTextZero(newval) ? "0" :  Utils.formatStringToNumber(newval, 0);
                    int value = Math.abs(Integer.parseInt(number));
                    setOffest(value);
                    number = (getOffset() + "");
                    m_pageOffsetTextField.setText(number);
                    int len = number.length();
                    double size = ((len < 3 ? 3 : len)*charWidth);
                    m_pageOffsetTextField.setPrefWidth( (size > 100 ? 100 : size) + 20);
                }
            }
        };
        m_pageOffsetTextField.setOnAction(e->{
            m_updateBtn.fire();
        });
        m_pageOffsetTextField.textProperty().addListener(m_pageOffsetFieldListener);
        
        m_pageLimitText = new Text(" Limit ");
        m_pageLimitText.setFont(Stages.txtFont);
        m_pageLimitText.setFill(Stages.txtColor);

        m_pageLimitField = new TextField(m_limit + "");
        m_pageLimitField.setPrefWidth(60);
        m_pageLimitField.setId("bodyBox");
        m_pageLimitFieldListener = (obs,oldval,newval)->{
            if(m_pageLimitField != null){
                if(newval.length() > 0){
                    String number = Utils.isTextZero(newval) ? "1" :  Utils.formatStringToNumber(newval, 0);
                    int value = Math.abs(Integer.parseInt(number));
                    setLimit(value);
                    number = (getLimit() + "");
                    m_pageLimitField.setText(number);
                    int len = number.length();
                    double size = ((len < 3 ? 3 : len)*charWidth);
                    m_pageLimitField.setPrefWidth( (size > 100 ? 100 : size) + 20);
                }
            }
        };
        m_pageLimitField.setOnAction(e->{
            m_updateBtn.fire();
        });
        m_pageLimitField.textProperty().addListener(m_pageLimitFieldListener);

        m_maxText = new Text(" Total ");
        m_maxText.setFont(Stages.txtFont);
        m_maxText.setFill(Stages.txtColor);

        m_maxField = new TextField(m_maxItems + "");
        m_maxField.setEditable(false);
        m_maxField.setId("bodyBox");

        m_maxBox = new HBox();
        m_maxBox.setAlignment(Pos.CENTER_LEFT);
        
        if(m_maxItems > -1){
            m_maxBox.getChildren().addAll(m_maxText, m_maxField);
        }

        updateTotalWidthSize(charWidth);

        m_pageNextBtn = new Button("⮞");
        m_pageNextBtn.setId("toolBtn");
        m_pageNextBtn.setOnAction(e->{
            if(m_pageLimitField != null && m_pageOffsetTextField != null){
                nextPage();
            }
        });
    
        m_pagePrevBtn = new Button("⮜");
        m_pagePrevBtn.setId("toolBtn");
        m_pagePrevBtn.setOnAction(e->{
            if(m_pageLimitField != null && m_pageOffsetTextField != null){
                prevPage();
            }
        });
        getChildren().addAll(m_pageOffsetText, m_pageOffsetTextField, m_pageLimitText, m_pageLimitField, m_maxBox, m_pagePrevBtn, m_pageNextBtn);
    }

    private void updateTotalWidthSize(double charWidth){
        int len = m_maxField.getText().length();

        double maxFieldWidth = (charWidth * (len < 3 ? 3 : len));
        maxFieldWidth = maxFieldWidth > 100 ? 100 : maxFieldWidth;
        m_maxField.setPrefWidth(maxFieldWidth + 20);
    }

}
