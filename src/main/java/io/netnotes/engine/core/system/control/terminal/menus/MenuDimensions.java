package io.netnotes.engine.core.system.control.terminal.menus;

public class MenuDimensions {
    private final int boxWidth;
    private final int boxCol;
    private final int itemContentWidth;
    
    
	public MenuDimensions(int boxWidth, int boxCol, int itemContentWidth) {
        this.boxWidth = boxWidth;
        this.boxCol = boxCol;
        this.itemContentWidth = itemContentWidth;
    }

    public int getBoxWidth() {
		return boxWidth;
	}

	public int getBoxCol() {
		return boxCol;
	}

	public int getItemContentWidth() {
		return itemContentWidth;
	}

    
}