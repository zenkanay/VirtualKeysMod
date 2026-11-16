package dev.virtualkeys.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.virtualkeys.VirtualKeyDefinition;
import dev.virtualkeys.VirtualKeysConfig;
import dev.virtualkeys.VirtualKeysConfig.TabConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * VirtualKeyScreen
 *
 * 他のGUIが開いている状態で重ねて表示できるタブ対応仮想キーパネル。
 * ボタンをクリックすると、このパネルを閉じ、復元された親画面に対して
 * 仮想キーの key press / release イベントを発火する。
 */
public class VirtualKeyScreen extends Screen {

    private final Screen parentScreen;

    // ── レイアウト定数 ──────────────────────────────────────────
    private static final int PANEL_PADDING   = 6;
    private static final int HEADER_HEIGHT   = 16;
    private static final int TAB_HEIGHT      = 12;
    private static final int TAB_GAP         = 2;
    private static final int SCROLL_BTN_W    = 10;

    // ── 状態 ───────────────────────────────────────────────────
    private int hoveredIndex = -1;
    private int panelX, panelY, panelW, panelH;
    private boolean settingsModeActive = false;
    private int settingsTab = 0; // 0 = Layout, 1 = Keys, 2 = Tabs
    private int selectedKeyIndex = -1;
    private int buttonsPerRow = 3;
    private int tabScrollIndex = 0;

    // 設定パネルドラッグ用の状態
    private boolean draggingSettings = false;
    private int settingsX = -1;
    private int settingsY = -1;
    private double dragOffsetX = 0;
    private double dragOffsetY = 0;
    private int clickedKeyIndex = -1;

    private EditBox nameEditBox;
    private boolean suppressResponder = false;

    private record HeaderTabsLayout(
        int startX,
        int endX,
        int y,
        int innerStartX,
        int innerEndX,
        boolean showArrows,
        List<int[]> visibleTabRects // [tabIndex, x, width]
    ) {}

    public VirtualKeyScreen(Screen parentScreen) {
        super(Component.translatable("gui.virtualkeys.title"));
        this.parentScreen = parentScreen;
    }

    @Override
    public boolean isPauseScreen() {
        return parentScreen != null && parentScreen.isPauseScreen();
    }

    // ── 背景・フェードインの抑制 ──────────────────────────────────

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        // 背景のぼかしやグラデーションを描画しない
    }

    @Override
    protected void extractBlurredBackground(GuiGraphicsExtractor g) {
        // ブラー背景を描画しない
    }

    @Override
    protected void extractMenuBackground(GuiGraphicsExtractor g) {
        // メニュー背景を描画しない
    }

    @Override
    protected void extractMenuBackground(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        // メニュー背景を描画しない
    }

    @Override
    public void extractTransparentBackground(GuiGraphicsExtractor g) {
        // 透明暗転背景を描画しない
    }

    @Override
    protected void fadeWidgets(float delta) {
        // フェードインアニメーションを無効化
    }

    @Override
    protected void init() {
        VirtualKeysConfig.load(); // パネルを開いたタイミングでconfigを動的再ロード
        super.init();

        this.nameEditBox = new EditBox(
            this.font,
            0, 0, 100, 14,
            Component.literal("Name")
        ) {
            @Override
            public boolean keyPressed(KeyEvent event) {
                if (event.key() == InputConstants.KEY_ESCAPE
                        || event.key() == InputConstants.KEY_RETURN
                        || event.key() == InputConstants.KEY_NUMPADENTER) {
                    finishEditingText();
                    return true;
                }
                return super.keyPressed(event);
            }
        };
        this.nameEditBox.setMaxLength(2048);
        this.nameEditBox.setVisible(false);
        this.nameEditBox.setResponder(val -> {
            if (!suppressResponder && isEditingText()) {
                handleEditBoxTextChanged(val);
            }
        });
        this.addRenderableWidget(this.nameEditBox);

        recalcLayout();
        ensureActiveTabVisible();
    }

    private TabConfig activeTab() {
        return VirtualKeysConfig.INSTANCE.getActiveTab();
    }

    private void recalcLayout() {
        TabConfig tab = activeTab();
        int numKeys = tab.keys.size();
        buttonsPerRow = Math.max(1, tab.keysPerRow);

        int rows = numKeys == 0 ? 0 : (int) Math.ceil((double) numKeys / buttonsPerRow);

        int buttonW = tab.buttonWidth;
        int buttonH = tab.buttonHeight;
        int buttonGap = tab.buttonGap;

        panelW = buttonsPerRow * (buttonW + buttonGap) - buttonGap + PANEL_PADDING * 2;
        int keysHeight = rows == 0 ? 0 : rows * (buttonH + buttonGap) - buttonGap;
        panelH = PANEL_PADDING * 2 + HEADER_HEIGHT + keysHeight;

        // 水平方向のアライメント
        switch (tab.horizontalAlign) {
            case LEFT:
                panelX = 8;
                break;
            case CENTER:
                panelX = (this.width - panelW) / 2;
                break;
            case RIGHT:
            default:
                panelX = this.width - panelW - 8;
                break;
        }

        // 垂直方向のアライメント
        switch (tab.verticalAlign) {
            case TOP:
                panelY = 8;
                break;
            case CENTER:
                panelY = (this.height - panelH) / 2;
                break;
            case BOTTOM:
            default:
                panelY = this.height - panelH - 8;
                break;
        }

        // 画面外にはみ出さないようクランプ
        panelX = Math.max(0, Math.min(panelX, Math.max(0, this.width - panelW)));
        panelY = Math.max(0, Math.min(panelY, Math.max(0, this.height - panelH)));

        // 設定パネルの位置調整
        if (settingsX == -1 || settingsY == -1) {
            resetSettingsPosition();
        } else {
            clampSettingsPosition();
        }

        updateEditBoxPosition();
    }

    private void updateEditBoxPosition() {
        if (this.nameEditBox != null) {
            int sx = getSettingsX();
            int sy = getSettingsY();
            int sw = getSettingsW();
            int y2 = sy + PANEL_PADDING + 30 + 18; // Row 2
            this.nameEditBox.setX(sx + PANEL_PADDING);
            this.nameEditBox.setY(y2);
            this.nameEditBox.setWidth(sw - PANEL_PADDING * 2);
            this.nameEditBox.setHeight(14);
        }
    }

    private void setEditBoxValueSafely(String value) {
        if (this.nameEditBox == null) return;
        suppressResponder = true;
        try {
            this.nameEditBox.setValue(value != null ? value : "");
        } finally {
            suppressResponder = false;
        }
    }

    private void focusNameEditBox(String value) {
        if (this.nameEditBox != null) {
            setEditBoxValueSafely(value);
            this.nameEditBox.setVisible(true);
            this.nameEditBox.setFocused(true);
            this.setFocused(this.nameEditBox);
            this.nameEditBox.setCursorPosition(this.nameEditBox.getValue().length());
        }
    }

    private void finishEditingText() {
        sanitizeCurrentTabNameIfBlank();
        if (settingsTab == 1) {
            selectedKeyIndex = -1;
        }
        if (this.nameEditBox != null) {
            this.nameEditBox.setFocused(false);
        }
        if (this.getFocused() == this.nameEditBox) {
            this.setFocused(null);
        }
        VirtualKeyDefinition.updateFromTabs(VirtualKeysConfig.INSTANCE.tabs);
        VirtualKeysConfig.save();
    }

    private void sanitizeCurrentTabNameIfBlank() {
        TabConfig tab = activeTab();
        if (tab.name == null || tab.name.trim().isEmpty()) {
            tab.name = "Tab " + (VirtualKeysConfig.INSTANCE.activeTab + 1);
            if (settingsTab == 2 && this.nameEditBox != null && this.nameEditBox.isVisible()) {
                setEditBoxValueSafely(tab.name);
            }
        }
    }

    public void resetSettingsPosition() {
        TabConfig tab = activeTab();
        int defaultX;
        if (tab.horizontalAlign == VirtualKeysConfig.HorizontalAlign.LEFT) {
            defaultX = panelX + panelW + 4;
        } else if (tab.horizontalAlign == VirtualKeysConfig.HorizontalAlign.RIGHT) {
            defaultX = panelX - getSettingsW() - 4;
        } else {
            if (panelX + panelW + 4 + getSettingsW() <= this.width) {
                defaultX = panelX + panelW + 4;
            } else {
                defaultX = panelX - getSettingsW() - 4;
            }
        }
        settingsX = defaultX;
        settingsY = panelY;
        clampSettingsPosition();
    }

    private void clampSettingsPosition() {
        if (settingsX < 0) settingsX = 0;
        if (settingsX + getSettingsW() > this.width) settingsX = Math.max(0, this.width - getSettingsW());
        if (settingsY < 0) settingsY = 0;
        if (settingsY + getSettingsH() > this.height) settingsY = Math.max(0, this.height - getSettingsH());
    }

    // ── 設定パネルの座標計算 ──
    public int getSettingsX() {
        return settingsX;
    }

    public int getSettingsY() {
        return settingsY;
    }

    public int getSettingsW() {
        return 220;
    }

    public int getSettingsH() {
        return 114;
    }

    // ── ヘッダータブレイアウト計算 ──────────────────────────────

    private int getTabDisplayWidth(TabConfig tab, int maxAllowedW) {
        String label = (tab.name == null || tab.name.isEmpty()) ? " " : tab.name;
        int w = Math.max(20, this.font.width(label) + 8);
        return Math.min(w, Math.max(20, maxAllowedW));
    }

    private HeaderTabsLayout computeHeaderTabsLayout() {
        List<TabConfig> tabs = VirtualKeysConfig.INSTANCE.tabs;
        int gearW = 16;
        int gearX = panelX + panelW - PANEL_PADDING - gearW;
        int startX = panelX + PANEL_PADDING;
        int endX = gearX - 4;
        int y = panelY + PANEL_PADDING;
        int availW = Math.max(0, endX - startX);

        int totalW = 0;
        for (int i = 0; i < tabs.size(); i++) {
            totalW += getTabDisplayWidth(tabs.get(i), availW);
            if (i < tabs.size() - 1) totalW += TAB_GAP;
        }

        boolean needsScroll = totalW > availW && tabs.size() > 1;
        boolean showArrows = needsScroll && availW >= 36;
        int innerStartX = showArrows ? startX + SCROLL_BTN_W + 2 : startX;
        int innerEndX = showArrows ? endX - SCROLL_BTN_W - 2 : endX;
        int innerW = Math.max(0, innerEndX - innerStartX);

        if (!needsScroll) {
            tabScrollIndex = 0;
        } else if (tabScrollIndex >= tabs.size()) {
            tabScrollIndex = Math.max(0, tabs.size() - 1);
        } else if (tabScrollIndex < 0) {
            tabScrollIndex = 0;
        }

        List<int[]> rects = new ArrayList<>();
        int curX = innerStartX;
        for (int i = tabScrollIndex; i < tabs.size(); i++) {
            int tw = getTabDisplayWidth(tabs.get(i), innerW);
            if (curX >= innerEndX) break;
            rects.add(new int[]{i, curX, tw});
            curX += tw + TAB_GAP;
        }

        return new HeaderTabsLayout(startX, endX, y, innerStartX, innerEndX, showArrows, rects);
    }

    private void ensureActiveTabVisible() {
        List<TabConfig> tabs = VirtualKeysConfig.INSTANCE.tabs;
        int activeIdx = VirtualKeysConfig.INSTANCE.activeTab;
        if (activeIdx < tabScrollIndex) {
            tabScrollIndex = activeIdx;
            return;
        }
        HeaderTabsLayout layout = computeHeaderTabsLayout();
        if (!layout.showArrows()) return;

        int innerW = Math.max(20, layout.innerEndX() - layout.innerStartX());
        while (tabScrollIndex < activeIdx) {
            int widthSum = 0;
            for (int i = tabScrollIndex; i <= activeIdx; i++) {
                widthSum += getTabDisplayWidth(tabs.get(i), innerW);
                if (i < activeIdx) widthSum += TAB_GAP;
            }
            if (widthSum <= innerW) break;
            tabScrollIndex++;
        }
    }

    private void switchToTab(int tabIdx) {
        List<TabConfig> tabs = VirtualKeysConfig.INSTANCE.tabs;
        if (tabIdx < 0 || tabIdx >= tabs.size()) return;
        sanitizeCurrentTabNameIfBlank();
        VirtualKeysConfig.INSTANCE.activeTab = tabIdx;
        selectedKeyIndex = -1;
        clickedKeyIndex = -1;
        ensureActiveTabVisible();
        if (settingsModeActive && settingsTab == 2 && this.nameEditBox != null) {
            setEditBoxValueSafely(tabs.get(tabIdx).name);
            if (this.nameEditBox.isFocused()) {
                this.nameEditBox.setCursorPosition(this.nameEditBox.getValue().length());
            }
        }
        VirtualKeysConfig.save();
        recalcLayout();
    }

    // ── マウス判定 ──
    public boolean isMouseOverPanel(double mx, double my) {
        if (draggingSettings || clickedKeyIndex >= 0) return true;

        boolean overKeys = mx >= panelX && mx < panelX + panelW && my >= panelY && my < panelY + panelH;
        if (overKeys) return true;

        if (settingsModeActive) {
            int sx = getSettingsX();
            int sy = getSettingsY();
            int sw = getSettingsW();
            int sh = getSettingsH();
            boolean overSettings = mx >= sx && mx < sx + sw && my >= sy && my < sy + sh;
            if (overSettings) return true;
        }

        return false;
    }

    // ── 描画 ────────────────────────────────────────────────────

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (draggingSettings) {
            settingsX = (int) (mouseX - dragOffsetX);
            settingsY = (int) (mouseY - dragOffsetY);
            clampSettingsPosition();
            updateEditBoxPosition();
        }

        TabConfig tab = activeTab();
        int activeTabIdx = VirtualKeysConfig.INSTANCE.activeTab;

        // Synchronize nameEditBox visibility and values based on editing states
        boolean showKeyEdit = settingsModeActive && settingsTab == 1
                && selectedKeyIndex >= 0 && selectedKeyIndex < tab.keys.size();
        boolean showTabEdit = settingsModeActive && settingsTab == 2;
        boolean showEditBox = showKeyEdit || showTabEdit;

        if (this.nameEditBox != null) {
            if (showEditBox) {
                updateEditBoxPosition();
                if (!this.nameEditBox.isVisible()) {
                    String initial = showKeyEdit ? tab.keys.get(selectedKeyIndex) : tab.name;
                    focusNameEditBox(initial);
                }
            } else {
                if (this.nameEditBox.isVisible()) {
                    this.nameEditBox.setVisible(false);
                    this.nameEditBox.setFocused(false);
                    if (this.getFocused() == this.nameEditBox) {
                        this.setFocused(null);
                    }
                }
            }
        }

        // Overlay表示時（Minecraft.gui.screen() != this）は親画面の再描画をしない（親画面側で既に描画されているため）
        if (parentScreen != null && Minecraft.getInstance().gui.screen() == this) {
            parentScreen.extractRenderState(g, -9999, -9999, delta);
        }

        // シンプルな半透明の暗い背景 (メインのキーボードパネル)
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xD0121212);

        // ヘッダータブバーの描画
        HeaderTabsLayout tabsLayout = computeHeaderTabsLayout();
        if (tabsLayout.showArrows()) {
            boolean leftEnabled = tabScrollIndex > 0;
            boolean leftHovered = leftEnabled && mouseX >= tabsLayout.startX() && mouseX < tabsLayout.startX() + SCROLL_BTN_W
                    && mouseY >= tabsLayout.y() && mouseY < tabsLayout.y() + TAB_HEIGHT;
            g.fill(tabsLayout.startX(), tabsLayout.y(), tabsLayout.startX() + SCROLL_BTN_W, tabsLayout.y() + TAB_HEIGHT,
                    leftHovered ? 0x38FFFFFF : 0x18FFFFFF);
            g.centeredText(this.font, "<", tabsLayout.startX() + SCROLL_BTN_W / 2, tabsLayout.y() + (TAB_HEIGHT - 8) / 2,
                    leftEnabled ? (leftHovered ? 0xFFFFFFFF : 0xFFCCCCCC) : 0xFF555555);

            int rightX = tabsLayout.endX() - SCROLL_BTN_W;
            boolean rightEnabled = tabScrollIndex < VirtualKeysConfig.INSTANCE.tabs.size() - 1;
            boolean rightHovered = rightEnabled && mouseX >= rightX && mouseX < rightX + SCROLL_BTN_W
                    && mouseY >= tabsLayout.y() && mouseY < tabsLayout.y() + TAB_HEIGHT;
            g.fill(rightX, tabsLayout.y(), rightX + SCROLL_BTN_W, tabsLayout.y() + TAB_HEIGHT,
                    rightHovered ? 0x38FFFFFF : 0x18FFFFFF);
            g.centeredText(this.font, ">", rightX + SCROLL_BTN_W / 2, tabsLayout.y() + (TAB_HEIGHT - 8) / 2,
                    rightEnabled ? (rightHovered ? 0xFFFFFFFF : 0xFFCCCCCC) : 0xFF555555);
        }

        if (tabsLayout.innerEndX() > tabsLayout.innerStartX()) {
            g.enableScissor(tabsLayout.innerStartX(), tabsLayout.y(), tabsLayout.innerEndX(), tabsLayout.y() + TAB_HEIGHT);
            List<TabConfig> allTabs = VirtualKeysConfig.INSTANCE.tabs;
            for (int[] rect : tabsLayout.visibleTabRects()) {
                int idx = rect[0];
                int tx = rect[1];
                int tw = rect[2];
                boolean isActive = idx == activeTabIdx;
                boolean hovered = mouseX >= Math.max(tabsLayout.innerStartX(), tx)
                        && mouseX < Math.min(tabsLayout.innerEndX(), tx + tw)
                        && mouseY >= tabsLayout.y() && mouseY < tabsLayout.y() + TAB_HEIGHT;

                int bg = isActive
                        ? (hovered ? 0xFF3B82F6 : 0xFF2563EB)
                        : (hovered ? 0x38FFFFFF : 0x18FFFFFF);
                int fg = isActive ? 0xFFFFFFFF : (hovered ? 0xFFFFFFFF : 0xFFAAAAAA);

                g.fill(tx, tabsLayout.y(), tx + tw, tabsLayout.y() + TAB_HEIGHT, bg);
                String tLabel = allTabs.get(idx).name != null ? allTabs.get(idx).name : "";
                g.centeredText(this.font, tLabel, tx + tw / 2, tabsLayout.y() + (TAB_HEIGHT - 8) / 2, fg);
            }
            g.disableScissor();
        }

        // 歯車ボタン
        int gearW = 16;
        int gearH = 12;
        int gearX = panelX + panelW - PANEL_PADDING - gearW;
        int gearY = panelY + PANEL_PADDING;
        boolean gearHovered = mouseX >= gearX && mouseX < gearX + gearW && mouseY >= gearY && mouseY < gearY + gearH;
        g.text(this.font, "⚙", gearX + (gearW - this.font.width("⚙")) / 2, gearY + (gearH - 8) / 2, gearHovered ? 0xFFFFFFFF : 0xFF888888, false);

        // アクティブタブのキーボタン描画
        hoveredIndex = -1;
        int numKeys = tab.keys.size();

        int buttonW = tab.buttonWidth;
        int buttonH = tab.buttonHeight;
        int buttonGap = tab.buttonGap;

        for (int i = 0; i < numKeys; i++) {
            int col = i % buttonsPerRow;
            int row = i / buttonsPerRow;
            int bx = panelX + PANEL_PADDING + col * (buttonW + buttonGap);
            int by = panelY + PANEL_PADDING + HEADER_HEIGHT + row * (buttonH + buttonGap);

            if (bx + buttonW <= panelX + panelW) {
                boolean hovered = mouseX >= bx && mouseX < bx + buttonW
                               && mouseY >= by && mouseY < by + buttonH;
                if (hovered) hoveredIndex = i;

                int bgColor;
                if (settingsModeActive && settingsTab == 1 && selectedKeyIndex == i) {
                    bgColor = 0xFF2563EB; // 青ハイライト（編集中のキー）
                } else {
                    bgColor = hovered ? 0x44FFFFFF : 0x22FFFFFF;
                }
                int textColor = (settingsModeActive && settingsTab == 1 && selectedKeyIndex == i) ? 0xFFFFFFFF : (hovered ? 0xFFFFFFFF : 0xFFCCCCCC);

                g.fill(bx, by, bx + buttonW, by + buttonH, bgColor);
                g.centeredText(this.font, tab.keys.get(i),
                    bx + buttonW / 2, by + (buttonH - 8) / 2, textColor);
            }
        }

        // 設定モード時の描画（別窓として描画する）
        if (settingsModeActive) {
            int sx = getSettingsX();
            int sy = getSettingsY();
            int sw = getSettingsW();
            int sh = getSettingsH();

            // 設定パネルの背景
            g.fill(sx, sy, sx + sw, sy + sh, 0xD0121212);

            // 設定タイトル
            g.text(this.font, "Settings", sx + PANEL_PADDING, sy + PANEL_PADDING + 2, 0xFFE0E0E0, false);

            // 設定ウィンドウの閉じる(×)ボタン
            int closeW = 16;
            int closeH = 12;
            int closeX = sx + sw - PANEL_PADDING - closeW;
            int closeY = sy + PANEL_PADDING;
            boolean closeHovered = mouseX >= closeX && mouseX < closeX + closeW && mouseY >= closeY && mouseY < closeY + closeH;
            g.text(this.font, "×", closeX + (closeW - this.font.width("×")) / 2, closeY + (closeH - 8) / 2, closeHovered ? 0xFFFFFFFF : 0xFF888888, false);

            // 設定タブ切り替えボタン (Layout / Keys / Tabs)
            int tabY = sy + PANEL_PADDING + 12;
            drawButton(g, sx + PANEL_PADDING, tabY, 50, 14, "Layout", settingsTab == 0, mouseX, mouseY);
            drawButton(g, sx + PANEL_PADDING + 54, tabY, 50, 14, "Keys", settingsTab == 1, mouseX, mouseY);
            drawButton(g, sx + PANEL_PADDING + 108, tabY, 50, 14, "Tabs", settingsTab == 2, mouseX, mouseY);

            int y1 = sy + PANEL_PADDING + 30;
            int y2 = y1 + 18;
            int y3 = y2 + 18;
            int y4 = y3 + 18;

            if (settingsTab == 0) {
                // Row 1: H Align
                g.text(this.font, "H:", sx + PANEL_PADDING, y1 + 4, 0xFFCCCCCC, false);
                drawButton(g, sx + 25, y1, 35, 14, "L", tab.horizontalAlign == VirtualKeysConfig.HorizontalAlign.LEFT, mouseX, mouseY);
                drawButton(g, sx + 65, y1, 45, 14, "C", tab.horizontalAlign == VirtualKeysConfig.HorizontalAlign.CENTER, mouseX, mouseY);
                drawButton(g, sx + 115, y1, 35, 14, "R", tab.horizontalAlign == VirtualKeysConfig.HorizontalAlign.RIGHT, mouseX, mouseY);

                // Row 2: V Align
                g.text(this.font, "V:", sx + PANEL_PADDING, y2 + 4, 0xFFCCCCCC, false);
                drawButton(g, sx + 25, y2, 35, 14, "T", tab.verticalAlign == VirtualKeysConfig.VerticalAlign.TOP, mouseX, mouseY);
                drawButton(g, sx + 65, y2, 45, 14, "C", tab.verticalAlign == VirtualKeysConfig.VerticalAlign.CENTER, mouseX, mouseY);
                drawButton(g, sx + 115, y2, 35, 14, "B", tab.verticalAlign == VirtualKeysConfig.VerticalAlign.BOTTOM, mouseX, mouseY);

                // Row 3: Sizes (W, H, Col)
                g.text(this.font, "W:", sx + PANEL_PADDING, y3 + 4, 0xFF999999, false);
                drawButton(g, sx + 20, y3, 10, 14, "-", false, mouseX, mouseY);
                g.centeredText(this.font, String.valueOf(buttonW), sx + 40, y3 + 4, 0xFFFFFFFF);
                drawButton(g, sx + 48, y3, 10, 14, "+", false, mouseX, mouseY);

                g.text(this.font, "H:", sx + 65, y3 + 4, 0xFF999999, false);
                drawButton(g, sx + 77, y3, 10, 14, "-", false, mouseX, mouseY);
                g.centeredText(this.font, String.valueOf(buttonH), sx + 97, y3 + 4, 0xFFFFFFFF);
                drawButton(g, sx + 105, y3, 10, 14, "+", false, mouseX, mouseY);

                g.text(this.font, "Col:", sx + 122, y3 + 4, 0xFF999999, false);
                drawButton(g, sx + 145, y3, 10, 14, "-", false, mouseX, mouseY);
                g.centeredText(this.font, String.valueOf(buttonsPerRow), sx + 165, y3 + 4, 0xFFFFFFFF);
                drawButton(g, sx + 173, y3, 10, 14, "+", false, mouseX, mouseY);

                // Row 4: Gap
                g.text(this.font, "Gap:", sx + PANEL_PADDING, y4 + 4, 0xFF999999, false);
                drawButton(g, sx + 30, y4, 10, 14, "-", false, mouseX, mouseY);
                g.centeredText(this.font, String.valueOf(buttonGap), sx + 50, y4 + 4, 0xFFFFFFFF);
                drawButton(g, sx + 58, y4, 10, 14, "+", false, mouseX, mouseY);
            } else if (settingsTab == 1) {
                // Tab 1: Keys Editor
                drawButton(g, sx + PANEL_PADDING, y1, 60, 14, "+ Add Key", false, mouseX, mouseY);

                if (selectedKeyIndex < 0 || selectedKeyIndex >= numKeys) {
                    g.text(this.font, "Click a key to edit.", sx + PANEL_PADDING, y2 + 4, 0xFF888888, false);
                } else {
                    drawButton(g, sx + PANEL_PADDING, y3, 40, 14, "Up", false, mouseX, mouseY);
                    drawButton(g, sx + PANEL_PADDING + 45, y3, 40, 14, "Down", false, mouseX, mouseY);
                    drawButton(g, sx + PANEL_PADDING + 90, y3, 45, 14, "Delete", false, mouseX, mouseY);
                }

                String tabInfo = this.font.plainSubstrByWidth("Tab: " + (tab.name != null ? tab.name : ""), sw - PANEL_PADDING * 2);
                g.text(this.font, tabInfo, sx + PANEL_PADDING, y4 + 4, 0xFF888888, false);
            } else {
                // Tab 2: Tabs Editor
                drawButton(g, sx + PANEL_PADDING, y1, 60, 14, "+ Add Tab", false, mouseX, mouseY);

                // Row 2 is rendered by nameEditBox (tab name)
                boolean canMoveLeft = activeTabIdx > 0;
                boolean canMoveRight = activeTabIdx < VirtualKeysConfig.INSTANCE.tabs.size() - 1;
                boolean canDeleteTab = VirtualKeysConfig.INSTANCE.tabs.size() > 1;
                drawButton(g, sx + PANEL_PADDING, y3, 45, 14, "< Left", false, canMoveLeft ? mouseX : -9999, canMoveLeft ? mouseY : -9999, canMoveLeft);
                drawButton(g, sx + PANEL_PADDING + 50, y3, 45, 14, "Right >", false, canMoveRight ? mouseX : -9999, canMoveRight ? mouseY : -9999, canMoveRight);
                drawButton(g, sx + PANEL_PADDING + 100, y3, 45, 14, "Delete", false, canDeleteTab ? mouseX : -9999, canDeleteTab ? mouseY : -9999, canDeleteTab);

                g.text(this.font, "Tab " + (activeTabIdx + 1) + " / " + VirtualKeysConfig.INSTANCE.tabs.size(), sx + PANEL_PADDING, y4 + 4, 0xFF888888, false);
            }
        }

        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    private void drawButton(GuiGraphicsExtractor g, int x, int y, int w, int h, String text, boolean active, int mx, int my) {
        drawButton(g, x, y, w, h, text, active, mx, my, true);
    }

    private void drawButton(GuiGraphicsExtractor g, int x, int y, int w, int h, String text, boolean active, int mx, int my, boolean enabled) {
        boolean hovered = enabled && mx >= x && mx < x + w && my >= y && my < y + h;
        int bgColor;
        if (!enabled) {
            bgColor = 0x12FFFFFF;
        } else if (active) {
            bgColor = hovered ? 0xFF3B82F6 : 0xFF2563EB; // 青ハイライト
        } else {
            bgColor = hovered ? 0x44FFFFFF : 0x22FFFFFF;
        }
        int textColor = !enabled ? 0xFF666666 : (active ? 0xFFFFFFFF : (hovered ? 0xFFFFFFFF : 0xFFCCCCCC));
        g.fill(x, y, x + w, y + h, bgColor);
        g.centeredText(this.font, text, x + w / 2, y + (h - 8) / 2, textColor);
    }

    // ── インプット処理 (26.3 SDL3 シグネチャ) ──────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean consumed) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            int mx = (int) event.x();
            int my = (int) event.y();
            if (handleMouseClick(mx, my, event.button(), InputConstants.PRESS)) {
                return true;
            }
        }
        return super.mouseClicked(event, consumed);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        handleMouseRelease(event.x(), event.y(), event.button());
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        HeaderTabsLayout layout = computeHeaderTabsLayout();
        if (mx >= layout.startX() && mx < layout.endX() && my >= layout.y() && my < layout.y() + TAB_HEIGHT) {
            List<TabConfig> tabs = VirtualKeysConfig.INSTANCE.tabs;
            if (tabs.size() > 1) {
                int delta = (scrollY > 0 || scrollX < 0) ? -1 : 1;
                int nextIdx = (VirtualKeysConfig.INSTANCE.activeTab + delta + tabs.size()) % tabs.size();
                switchToTab(nextIdx);
                return true;
            }
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    public boolean handleMouseClick(double mx, double my, int button, int action) {
        if (action == InputConstants.PRESS && button == InputConstants.MOUSE_BUTTON_LEFT) {
            // 1. 歯車ボタンのクリック判定
            int gearW = 16;
            int gearH = 12;
            int gearX = panelX + panelW - PANEL_PADDING - gearW;
            int gearY = panelY + PANEL_PADDING;
            if (mx >= gearX && mx < gearX + gearW && my >= gearY && my < gearY + gearH) {
                settingsModeActive = !settingsModeActive;
                if (settingsModeActive) {
                    resetSettingsPosition();
                    // If running as an overlay, promote this screen to an active full screen to grab keyboard focus
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.gui.screen() != this) {
                        VirtualKeysClient.closeOverlay();
                        mc.gui.setScreen(this);
                    }
                } else {
                    sanitizeCurrentTabNameIfBlank();
                    selectedKeyIndex = -1;
                    VirtualKeysConfig.save();
                }
                recalcLayout();
                return true;
            }

            // 2. 設定ウィンドウの閉じる(×)ボタン & ドラッグ判定 (タイトルバー部分)
            if (settingsModeActive) {
                int sx = getSettingsX();
                int sy = getSettingsY();
                int sw = getSettingsW();
                int closeW = 16;
                int closeH = 12;
                int closeX = sx + sw - PANEL_PADDING - closeW;
                int closeY = sy + PANEL_PADDING;
                if (mx >= closeX && mx < closeX + closeW && my >= closeY && my < closeY + closeH) {
                    settingsModeActive = false;
                    sanitizeCurrentTabNameIfBlank();
                    selectedKeyIndex = -1;
                    draggingSettings = false;
                    VirtualKeysConfig.save();
                    recalcLayout();
                    return true;
                }
                if (mx >= sx && mx < sx + sw && my >= sy && my < sy + 16) {
                    draggingSettings = true;
                    dragOffsetX = mx - sx;
                    dragOffsetY = my - sy;
                    return true;
                }
            }

            // 3. 設定項目のクリック判定
            if (checkSettingsClick(mx, my)) {
                return true;
            }

            // 設定パネル内の他領域（nameEditBox 等）は super.mouseClicked に委譲し、背後のキーに貫通させない
            if (settingsModeActive) {
                int sx = getSettingsX();
                int sy = getSettingsY();
                if (mx >= sx && mx < sx + getSettingsW() && my >= sy && my < sy + getSettingsH()) {
                    return false;
                }
            }

            // 4. メインパネルのヘッダータブクリック判定
            HeaderTabsLayout tabsLayout = computeHeaderTabsLayout();
            if (my >= tabsLayout.y() && my < tabsLayout.y() + TAB_HEIGHT
                    && mx >= tabsLayout.startX() && mx < tabsLayout.endX()) {
                if (tabsLayout.showArrows()) {
                    if (mx < tabsLayout.startX() + SCROLL_BTN_W) {
                        if (tabScrollIndex > 0) {
                            tabScrollIndex--;
                        }
                        return true;
                    }
                    if (mx >= tabsLayout.endX() - SCROLL_BTN_W) {
                        if (tabScrollIndex < VirtualKeysConfig.INSTANCE.tabs.size() - 1) {
                            tabScrollIndex++;
                        }
                        return true;
                    }
                }
                for (int[] rect : tabsLayout.visibleTabRects()) {
                    int idx = rect[0];
                    int tx = rect[1];
                    int tw = rect[2];
                    int hitStart = Math.max(tabsLayout.innerStartX(), tx);
                    int hitEnd = Math.min(tabsLayout.innerEndX(), tx + tw);
                    if (mx >= hitStart && mx < hitEnd) {
                        switchToTab(idx);
                        return true;
                    }
                }
                return true;
            }

            // 5. 仮想キーのクリック判定
            int idx = getButtonAt((int) mx, (int) my);
            if (idx >= 0) {
                if (settingsModeActive && settingsTab == 1) {
                    selectedKeyIndex = idx;
                    focusNameEditBox(activeTab().keys.get(idx));
                } else {
                    clickedKeyIndex = idx;
                }
                return true;
            }
        }
        return false;
    }

    public void handleMouseRelease(double mx, double my, int button) {
        if (button == InputConstants.MOUSE_BUTTON_LEFT) {
            draggingSettings = false;
            if (clickedKeyIndex >= 0) {
                int idx = clickedKeyIndex;
                clickedKeyIndex = -1;
                if (getButtonAt((int) mx, (int) my) == idx) {
                    selectAndClose(idx);
                }
            }
        }
    }

    private boolean checkSettingsClick(double mx, double my) {
        if (!settingsModeActive) return false;

        int sx = getSettingsX();
        int sy = getSettingsY();
        int sw = getSettingsW();
        int sh = getSettingsH();

        // 設定パネルの外側をクリックした場合は処理しない
        if (mx < sx || mx >= sx + sw || my < sy || my >= sy + sh) {
            return false;
        }

        // 設定タブ切り替えクリック (Layout / Keys / Tabs)
        int tabY = sy + PANEL_PADDING + 12;
        if (my >= tabY && my < tabY + 14) {
            if (mx >= sx + PANEL_PADDING && mx < sx + PANEL_PADDING + 50) {
                sanitizeCurrentTabNameIfBlank();
                settingsTab = 0;
                return true;
            }
            if (mx >= sx + PANEL_PADDING + 54 && mx < sx + PANEL_PADDING + 54 + 50) {
                sanitizeCurrentTabNameIfBlank();
                settingsTab = 1;
                if (selectedKeyIndex >= 0 && selectedKeyIndex < activeTab().keys.size()) {
                    focusNameEditBox(activeTab().keys.get(selectedKeyIndex));
                }
                return true;
            }
            if (mx >= sx + PANEL_PADDING + 108 && mx < sx + PANEL_PADDING + 108 + 50) {
                settingsTab = 2;
                focusNameEditBox(activeTab().name);
                return true;
            }
        }

        int y1 = sy + PANEL_PADDING + 30;
        int y2 = y1 + 18;
        int y3 = y2 + 18;
        int y4 = y3 + 18;

        TabConfig tab = activeTab();
        int numKeys = tab.keys.size();

        if (settingsTab == 0) {
            // Row 1: H Align (L=25, C=65, R=115)
            if (my >= y1 && my < y1 + 14) {
                if (mx >= sx + 25 && mx < sx + 25 + 35) {
                    tab.horizontalAlign = VirtualKeysConfig.HorizontalAlign.LEFT;
                    VirtualKeysConfig.save();
                    recalcLayout();
                    return true;
                }
                if (mx >= sx + 65 && mx < sx + 65 + 45) {
                    tab.horizontalAlign = VirtualKeysConfig.HorizontalAlign.CENTER;
                    VirtualKeysConfig.save();
                    recalcLayout();
                    return true;
                }
                if (mx >= sx + 115 && mx < sx + 115 + 35) {
                    tab.horizontalAlign = VirtualKeysConfig.HorizontalAlign.RIGHT;
                    VirtualKeysConfig.save();
                    recalcLayout();
                    return true;
                }
            }

            // Row 2: V Align (T=25, C=65, B=115)
            if (my >= y2 && my < y2 + 14) {
                if (mx >= sx + 25 && mx < sx + 25 + 35) {
                    tab.verticalAlign = VirtualKeysConfig.VerticalAlign.TOP;
                    VirtualKeysConfig.save();
                    recalcLayout();
                    return true;
                }
                if (mx >= sx + 65 && mx < sx + 65 + 45) {
                    tab.verticalAlign = VirtualKeysConfig.VerticalAlign.CENTER;
                    VirtualKeysConfig.save();
                    recalcLayout();
                    return true;
                }
                if (mx >= sx + 115 && mx < sx + 115 + 35) {
                    tab.verticalAlign = VirtualKeysConfig.VerticalAlign.BOTTOM;
                    VirtualKeysConfig.save();
                    recalcLayout();
                    return true;
                }
            }

            // Row 3: Sizes (W: -=20, +=48; H: -=77, +=105; Col: -=145, +=173)
            if (my >= y3 && my < y3 + 14) {
                // Width
                if (mx >= sx + 20 && mx < sx + 20 + 10) {
                    if (tab.buttonWidth > 2) {
                        tab.buttonWidth -= 2;
                        VirtualKeysConfig.save();
                        recalcLayout();
                    }
                    return true;
                }
                if (mx >= sx + 48 && mx < sx + 48 + 10) {
                    tab.buttonWidth += 2;
                    VirtualKeysConfig.save();
                    recalcLayout();
                    return true;
                }
                // Height
                if (mx >= sx + 77 && mx < sx + 77 + 10) {
                    if (tab.buttonHeight > 2) {
                        tab.buttonHeight -= 2;
                        VirtualKeysConfig.save();
                        recalcLayout();
                    }
                    return true;
                }
                if (mx >= sx + 105 && mx < sx + 105 + 10) {
                    tab.buttonHeight += 2;
                    VirtualKeysConfig.save();
                    recalcLayout();
                    return true;
                }
                // Col
                if (mx >= sx + 145 && mx < sx + 145 + 10) {
                    if (tab.keysPerRow > 1) {
                        tab.keysPerRow -= 1;
                        VirtualKeysConfig.save();
                        recalcLayout();
                    }
                    return true;
                }
                if (mx >= sx + 173 && mx < sx + 173 + 10) {
                    tab.keysPerRow += 1;
                    VirtualKeysConfig.save();
                    recalcLayout();
                    return true;
                }
            }

            // Row 4: Gap (-=30, +=58)
            if (my >= y4 && my < y4 + 14) {
                if (mx >= sx + 30 && mx < sx + 30 + 10) {
                    if (tab.buttonGap > 0) {
                        tab.buttonGap -= 1;
                        VirtualKeysConfig.save();
                        recalcLayout();
                    }
                    return true;
                }
                if (mx >= sx + 58 && mx < sx + 58 + 10) {
                    tab.buttonGap += 1;
                    VirtualKeysConfig.save();
                    recalcLayout();
                    return true;
                }
            }
        } else if (settingsTab == 1) {
            // Tab 1: Keys Editor
            // Row 1: Add Key
            if (my >= y1 && my < y1 + 14) {
                if (mx >= sx + PANEL_PADDING && mx < sx + PANEL_PADDING + 60) {
                    String newLabel = VirtualKeysConfig.INSTANCE.allocateNextDefaultKeyLabel();
                    int newCode = VirtualKeysConfig.INSTANCE.allocateNextKeyCode();
                    tab.keys.add(newLabel);
                    tab.keyCodes.add(newCode);
                    VirtualKeyDefinition.updateFromTabs(VirtualKeysConfig.INSTANCE.tabs);
                    selectedKeyIndex = tab.keys.size() - 1;
                    VirtualKeysConfig.save();
                    recalcLayout();
                    focusNameEditBox(newLabel);
                    return true;
                }
            }

            // Row 2 clicking is handled by nameEditBox widget itself.

            if (selectedKeyIndex >= 0 && selectedKeyIndex < numKeys && my >= y3 && my < y3 + 14) {
                // Move Up
                if (mx >= sx + PANEL_PADDING && mx < sx + PANEL_PADDING + 40) {
                    if (selectedKeyIndex > 0) {
                        swapKeys(tab, selectedKeyIndex, selectedKeyIndex - 1);
                        selectedKeyIndex--;
                        VirtualKeyDefinition.updateFromTabs(VirtualKeysConfig.INSTANCE.tabs);
                        VirtualKeysConfig.save();
                        recalcLayout();
                    }
                    return true;
                }
                // Move Down
                if (mx >= sx + PANEL_PADDING + 45 && mx < sx + PANEL_PADDING + 45 + 40) {
                    if (selectedKeyIndex < numKeys - 1) {
                        swapKeys(tab, selectedKeyIndex, selectedKeyIndex + 1);
                        selectedKeyIndex++;
                        VirtualKeyDefinition.updateFromTabs(VirtualKeysConfig.INSTANCE.tabs);
                        VirtualKeysConfig.save();
                        recalcLayout();
                    }
                    return true;
                }
                // Delete
                if (mx >= sx + PANEL_PADDING + 90 && mx < sx + PANEL_PADDING + 90 + 45) {
                    tab.keys.remove(selectedKeyIndex);
                    if (selectedKeyIndex < tab.keyCodes.size()) {
                        tab.keyCodes.remove(selectedKeyIndex);
                    }
                    VirtualKeyDefinition.updateFromTabs(VirtualKeysConfig.INSTANCE.tabs);
                    selectedKeyIndex = -1;
                    VirtualKeysConfig.save();
                    recalcLayout();
                    return true;
                }
            }
        } else {
            // Tab 2: Tabs Editor
            List<TabConfig> tabs = VirtualKeysConfig.INSTANCE.tabs;
            int activeIdx = VirtualKeysConfig.INSTANCE.activeTab;

            // Row 1: + Add Tab
            if (my >= y1 && my < y1 + 14) {
                if (mx >= sx + PANEL_PADDING && mx < sx + PANEL_PADDING + 60) {
                    sanitizeCurrentTabNameIfBlank();
                    TabConfig newTab = new TabConfig();
                    newTab.name = allocateNextTabName(tabs);
                    newTab.buttonWidth = tab.buttonWidth;
                    newTab.buttonHeight = tab.buttonHeight;
                    newTab.buttonGap = tab.buttonGap;
                    newTab.horizontalAlign = tab.horizontalAlign;
                    newTab.verticalAlign = tab.verticalAlign;
                    newTab.keysPerRow = tab.keysPerRow;
                    newTab.keys.add(VirtualKeysConfig.INSTANCE.allocateNextDefaultKeyLabel());
                    newTab.keyCodes.add(VirtualKeysConfig.INSTANCE.allocateNextKeyCode());

                    tabs.add(newTab);
                    VirtualKeysConfig.INSTANCE.activeTab = tabs.size() - 1;
                    selectedKeyIndex = -1;
                    VirtualKeyDefinition.updateFromTabs(tabs);
                    VirtualKeysConfig.save();
                    ensureActiveTabVisible();
                    recalcLayout();
                    focusNameEditBox(newTab.name);
                    return true;
                }
            }

            // Row 2 clicking is handled by nameEditBox widget itself.

            // Row 3: < Left, Right >, Delete
            if (my >= y3 && my < y3 + 14) {
                // Move Left
                if (mx >= sx + PANEL_PADDING && mx < sx + PANEL_PADDING + 45) {
                    if (activeIdx > 0) {
                        TabConfig moved = tabs.remove(activeIdx);
                        tabs.add(activeIdx - 1, moved);
                        VirtualKeysConfig.INSTANCE.activeTab = activeIdx - 1;
                        ensureActiveTabVisible();
                        VirtualKeysConfig.save();
                        recalcLayout();
                    }
                    return true;
                }
                // Move Right
                if (mx >= sx + PANEL_PADDING + 50 && mx < sx + PANEL_PADDING + 50 + 45) {
                    if (activeIdx < tabs.size() - 1) {
                        TabConfig moved = tabs.remove(activeIdx);
                        tabs.add(activeIdx + 1, moved);
                        VirtualKeysConfig.INSTANCE.activeTab = activeIdx + 1;
                        ensureActiveTabVisible();
                        VirtualKeysConfig.save();
                        recalcLayout();
                    }
                    return true;
                }
                // Delete Tab
                if (mx >= sx + PANEL_PADDING + 100 && mx < sx + PANEL_PADDING + 100 + 45) {
                    if (tabs.size() > 1) {
                        tabs.remove(activeIdx);
                        VirtualKeysConfig.INSTANCE.activeTab = Math.min(activeIdx, tabs.size() - 1);
                        selectedKeyIndex = -1;
                        VirtualKeyDefinition.updateFromTabs(tabs);
                        VirtualKeysConfig.save();
                        ensureActiveTabVisible();
                        recalcLayout();
                        focusNameEditBox(activeTab().name);
                    }
                    return true;
                }
            }
        }

        return false;
    }

    private static void swapKeys(TabConfig tab, int i, int j) {
        String tmpLabel = tab.keys.get(i);
        tab.keys.set(i, tab.keys.get(j));
        tab.keys.set(j, tmpLabel);
        if (i < tab.keyCodes.size() && j < tab.keyCodes.size()) {
            Integer tmpCode = tab.keyCodes.get(i);
            tab.keyCodes.set(i, tab.keyCodes.get(j));
            tab.keyCodes.set(j, tmpCode);
        }
    }

    private static String allocateNextTabName(List<TabConfig> tabs) {
        int n = tabs.size() + 1;
        while (true) {
            String candidate = "Tab " + n;
            boolean exists = false;
            for (TabConfig t : tabs) {
                if (candidate.equals(t.name)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) return candidate;
            n++;
        }
    }

    public boolean isEditingText() {
        if (!settingsModeActive || nameEditBox == null || !nameEditBox.isVisible() || !nameEditBox.isFocused()) {
            return false;
        }
        return (settingsTab == 1 && selectedKeyIndex >= 0) || settingsTab == 2;
    }

    public Screen getParentScreen() {
        return this.parentScreen;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (isEditingText()) {
            super.charTyped(event);
            return true;
        }
        return super.charTyped(event);
    }

    private void handleEditBoxTextChanged(String val) {
        TabConfig tab = activeTab();
        if (settingsTab == 1) {
            if (selectedKeyIndex >= 0 && selectedKeyIndex < tab.keys.size()) {
                if (!tab.keys.get(selectedKeyIndex).equals(val)) {
                    tab.keys.set(selectedKeyIndex, val);
                    VirtualKeyDefinition.updateFromTabs(VirtualKeysConfig.INSTANCE.tabs);
                    VirtualKeysConfig.save();
                }
            }
        } else if (settingsTab == 2) {
            if (!val.equals(tab.name)) {
                tab.name = val;
                ensureActiveTabVisible();
                VirtualKeysConfig.save();
            }
        }
    }

    @Override
    public void removed() {
        sanitizeCurrentTabNameIfBlank();
        VirtualKeysConfig.save();
        super.removed();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (isEditingText()) {
            if (event.key() == InputConstants.KEY_RETURN
                    || event.key() == InputConstants.KEY_NUMPADENTER
                    || event.key() == InputConstants.KEY_ESCAPE) {
                finishEditingText();
                return true;
            }
            super.keyPressed(event);
            return true;
        }
        if (event.key() == InputConstants.KEY_ESCAPE) {
            if (settingsModeActive) {
                settingsModeActive = false;
                sanitizeCurrentTabNameIfBlank();
                selectedKeyIndex = -1;
                VirtualKeysConfig.save();
                recalcLayout();
                return true;
            }
            closePanel();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        closePanel();
    }

    // ── 仮想キー発火ロジック ─────────────────────────────────────

    private void selectAndClose(int idx) {
        TabConfig tab = activeTab();
        if (idx < 0 || idx >= tab.keys.size() || idx >= tab.keyCodes.size()) return;
        int keyCode = tab.keyCodes.get(idx);

        // 1. パネルを閉じる
        closePanel();

        // 2. 仮想キー入力をシミュレート
        simulateKeyPress(keyCode);
    }

    public void closePanel() {
        sanitizeCurrentTabNameIfBlank();
        if (VirtualKeysClient.overlayActive && VirtualKeysClient.overlayScreen == this) {
            VirtualKeysClient.closeOverlay();
        } else {
            Minecraft.getInstance().gui.setScreen(parentScreen);
        }
    }

    /**
     * 仮想キーイベントを発火する。
     */
    private static void simulateKeyPress(int virtualKeyCode) {
        Minecraft mc = Minecraft.getInstance();

        try {
            long windowHandle = mc.getWindow().handle();
            MalilibCompat.clearMouseButtonsFromPressedKeys();
            VirtualKeysClient.setVirtualKeyDown(virtualKeyCode, true);
            try {
                mc.keyboardHandler.keyPress(windowHandle, InputConstants.PRESS, new KeyEvent(virtualKeyCode, 0, 0));
                mc.keyboardHandler.keyPress(windowHandle, InputConstants.RELEASE, new KeyEvent(virtualKeyCode, 0, 0));
            } finally {
                VirtualKeysClient.setVirtualKeyDown(virtualKeyCode, false);
            }
        } catch (Exception e) {
            System.err.println("[VirtualKeys Error] Failed to simulate keypress: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ── ユーティリティ ───────────────────────────────────────────

    private int getButtonAt(int mx, int my) {
        TabConfig tab = activeTab();
        int buttonW = tab.buttonWidth;
        int buttonH = tab.buttonHeight;
        int buttonGap = tab.buttonGap;

        for (int i = 0; i < tab.keys.size(); i++) {
            int col = i % buttonsPerRow;
            int row = i / buttonsPerRow;
            int bx = panelX + PANEL_PADDING + col * (buttonW + buttonGap);
            int by = panelY + PANEL_PADDING + HEADER_HEIGHT + row * (buttonH + buttonGap);
            if (mx >= bx && mx < bx + buttonW && my >= by && my < by + buttonH) {
                return i;
            }
        }
        return -1;
    }
}
