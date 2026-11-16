package dev.virtualkeys.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.virtualkeys.VirtualKeyDefinition;
import dev.virtualkeys.VirtualKeysConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * VirtualKeyScreen
 *
 * 他のGUIが開いている状態で重ねて表示できる仮想キーパネル。
 * ボタンをクリックすると、このパネルを閉じ、復元された親画面に対して
 * 仮想キーの GLFW key press / release イベントを発火する。
 */
public class VirtualKeyScreen extends Screen {

    private final Screen parentScreen;

    // ── レイアウト定数 ──────────────────────────────────────────
    private static final int PANEL_PADDING   = 6;
    private static final int HEADER_HEIGHT   = 16;

    // ── 状態 ───────────────────────────────────────────────────
    private int hoveredIndex = -1;
    private int panelX, panelY, panelW, panelH;
    private boolean settingsModeActive = false;
    private int activeTab = 0; // 0 = Layout, 1 = Keys
    private int selectedKeyIndex = -1;
    private int buttonsPerRow = 8;

    // 設定パネルドラッグ用の状態
    private boolean draggingSettings = false;
    private int settingsX = -1;
    private int settingsY = -1;
    private double dragOffsetX = 0;
    private double dragOffsetY = 0;
    private int clickedKeyIndex = -1;

    private static java.lang.reflect.Method cachedOnKeyMethod = null;
    private static boolean checkedOnKeyMethod = false;
    private static java.lang.reflect.Constructor<?> cachedKeyEventConstructor = null;
    private static boolean checkedKeyEventConstructor = false;

    private net.minecraft.client.gui.components.EditBox nameEditBox;

    public VirtualKeyScreen(Screen parentScreen) {
        super(Component.translatable("gui.virtualkeys.title"));
        this.parentScreen = parentScreen;
    }

    public Screen getParentScreen() {
        return parentScreen;
    }

    @Override
    public boolean isPauseScreen() {
        return parentScreen != null && parentScreen.isPauseScreen();
    }

    // ── 背景・フェードインの抑制 ──────────────────────────────────

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        // 背景を描画しない
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

        this.nameEditBox = new net.minecraft.client.gui.components.EditBox(
            this.font,
            0, 0, 100, 14,
            Component.literal("Key Name")
        ) {
            @Override
            public boolean keyPressed(KeyEvent event) {
                if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
                    selectedKeyIndex = -1;
                    this.setFocused(false);
                    VirtualKeyScreen.this.setFocused(null);
                    super.keyPressed(event);
                    return true;
                }
                if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
                    selectedKeyIndex = -1;
                    this.setFocused(false);
                    VirtualKeyScreen.this.setFocused(null);
                    super.keyPressed(event);
                    return true;
                }
                return super.keyPressed(event);
            }
        };
        this.nameEditBox.setMaxLength(2048);
        this.nameEditBox.setVisible(false);
        this.nameEditBox.setResponder(val -> {
            if (settingsModeActive && activeTab == 1 && selectedKeyIndex >= 0 && selectedKeyIndex < VirtualKeyDefinition.ALL.size()) {
                VirtualKeyDefinition def = VirtualKeyDefinition.ALL.get(selectedKeyIndex);
                if (!def.label.equals(val)) {
                    def.label = val;
                    VirtualKeysConfig.save();
                }
            }
        });
        this.addRenderableWidget(this.nameEditBox);

        // ボタンの列数を設定ファイルから復元
        buttonsPerRow = VirtualKeysConfig.INSTANCE.keysPerRow;
        recalcLayout();
    }

    private void recalcLayout() {
        int numKeys = VirtualKeyDefinition.ALL.size();
        if (numKeys == 0) return;

        int buttonW = VirtualKeysConfig.INSTANCE.buttonWidth;
        int buttonH = VirtualKeysConfig.INSTANCE.buttonHeight;
        int buttonGap = VirtualKeysConfig.INSTANCE.buttonGap;

        int cols = Math.min(numKeys, buttonsPerRow);
        int rows = (int) Math.ceil((double) numKeys / buttonsPerRow);

        panelW = PANEL_PADDING * 2 + cols * buttonW + (cols - 1) * buttonGap;
        panelH = PANEL_PADDING * 2 + HEADER_HEIGHT + rows * buttonH + (rows - 1) * buttonGap;

        // 水平位置の計算
        switch (VirtualKeysConfig.INSTANCE.horizontalAlign) {
            case LEFT -> panelX = 10;
            case RIGHT -> panelX = this.width - panelW - 10;
            case CENTER -> panelX = (this.width - panelW) / 2;
        }

        // 垂直位置の計算
        switch (VirtualKeysConfig.INSTANCE.verticalAlign) {
            case TOP -> panelY = 10;
            case BOTTOM -> panelY = this.height - panelH - 10;
            case CENTER -> panelY = (this.height - panelH) / 2;
        }

        // 画面外にはみ出さないようクランプ
        panelX = Math.max(0, Math.min(panelX, this.width - panelW));
        panelY = Math.max(0, Math.min(panelY, this.height - panelH));

        // EditBoxの位置更新
        updateEditBoxPosition();
    }

    private void updateEditBoxPosition() {
        if (this.nameEditBox != null) {
            int sx = getSettingsX();
            int sy = getSettingsY();
            int sw = getSettingsW();
            int y2 = sy + PANEL_PADDING + 30 + 18;
            this.nameEditBox.setX(sx + PANEL_PADDING);
            this.nameEditBox.setY(y2);
            this.nameEditBox.setWidth(sw - PANEL_PADDING * 2);
        }
    }

    private int getSettingsW() {
        return 190;
    }

    private int getSettingsH() {
        return 90;
    }

    private int getSettingsX() {
        if (settingsX == -1) {
            resetSettingsPosition();
        }
        return settingsX;
    }

    private int getSettingsY() {
        if (settingsY == -1) {
            resetSettingsPosition();
        }
        return settingsY;
    }

    private void resetSettingsPosition() {
        int sw = getSettingsW();
        // パネルの右側に配置。はみ出る場合は左側に配置
        if (panelX + panelW + 10 + sw <= this.width) {
            settingsX = panelX + panelW + 10;
        } else {
            settingsX = Math.max(10, panelX - sw - 10);
        }
        settingsY = Math.max(10, Math.min(panelY, this.height - getSettingsH() - 10));
    }

    private void clampSettingsPosition() {
        int sw = getSettingsW();
        int sh = getSettingsH();
        settingsX = Math.max(0, Math.min(settingsX, this.width - sw));
        settingsY = Math.max(0, Math.min(settingsY, this.height - sh));
    }

    /**
     * テキストボックスで編集中かどうかを返す
     */
    public boolean isEditingText() {
        return settingsModeActive && activeTab == 1 && nameEditBox != null && nameEditBox.isVisible() && nameEditBox.isFocused();
    }

    /**
     * オーバーレイ表示中にマウスカーソルがパネルまたは設定画面の上にあるかを判定する
     */
    public boolean isMouseOverPanel(double mx, double my) {
        boolean overMain = mx >= panelX && mx < panelX + panelW && my >= panelY && my < panelY + panelH;
        if (overMain) return true;

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

    // ── 描画ヘルパー ────────────────────────────────────────────

    private void drawString(GuiGraphicsExtractor g, Component text, int x, int y, int color) {
        g.textRenderer().accept(x, y, text.copy().withStyle(s -> s.withColor(color & 0xFFFFFF)));
    }

    private void drawString(GuiGraphicsExtractor g, String text, int x, int y, int color) {
        g.textRenderer().accept(x, y, Component.literal(text).withStyle(s -> s.withColor(color & 0xFFFFFF)));
    }

    private void drawCenteredString(GuiGraphicsExtractor g, String text, int x, int y, int color) {
        g.textRenderer().accept(TextAlignment.CENTER, x, y, Component.literal(text).withStyle(s -> s.withColor(color & 0xFFFFFF)));
    }

    private void drawCenteredString(GuiGraphicsExtractor g, Component text, int x, int y, int color) {
        g.textRenderer().accept(TextAlignment.CENTER, x, y, text.copy().withStyle(s -> s.withColor(color & 0xFFFFFF)));
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

        // Synchronize nameEditBox visibility and values based on editing states
        boolean showEditBox = settingsModeActive && activeTab == 1 && selectedKeyIndex >= 0 && selectedKeyIndex < VirtualKeyDefinition.ALL.size();
        if (this.nameEditBox != null) {
            if (showEditBox) {
                updateEditBoxPosition();
                if (!this.nameEditBox.isVisible()) {
                    this.nameEditBox.setVisible(true);
                    this.nameEditBox.setValue(VirtualKeyDefinition.ALL.get(selectedKeyIndex).label);
                    this.nameEditBox.setFocused(true);
                    this.setFocused(this.nameEditBox);
                    this.nameEditBox.setCursorPosition(this.nameEditBox.getValue().length());
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

        // タイトルと歯車アイコンの描画（幅に合わせて文字を省略または非表示にして崩れを防ぐ）
        if (panelW >= 80) {
            drawString(g, Component.translatable("gui.virtualkeys.title"), panelX + PANEL_PADDING, panelY + PANEL_PADDING + 2, 0xFFE0E0E0);
        } else if (panelW >= 40) {
            drawString(g, "VKeys", panelX + PANEL_PADDING, panelY + PANEL_PADDING + 2, 0xFFE0E0E0);
        }
        
        int gearW = 16;
        int gearH = 12;
        int gearX = panelX + panelW - PANEL_PADDING - gearW;
        int gearY = panelY + PANEL_PADDING;
        boolean gearHovered = mouseX >= gearX && mouseX < gearX + gearW && mouseY >= gearY && mouseY < gearY + gearH;
        drawString(g, "⚙", gearX + (gearW - this.font.width("⚙")) / 2, gearY + (gearH - 8) / 2, gearHovered ? 0xFFFFFFFF : 0xFF888888);

        hoveredIndex = -1;
        int numKeys = VirtualKeyDefinition.ALL.size();

        int buttonW = VirtualKeysConfig.INSTANCE.buttonWidth;
        int buttonH = VirtualKeysConfig.INSTANCE.buttonHeight;
        int buttonGap = VirtualKeysConfig.INSTANCE.buttonGap;

        for (int i = 0; i < numKeys; i++) {
            int col = i % buttonsPerRow;
            int row = i / buttonsPerRow;
            int bx = panelX + PANEL_PADDING + col * (buttonW + buttonGap);
            int by = panelY + PANEL_PADDING + HEADER_HEIGHT + row * (buttonH + buttonGap);

            // メインパネルのサイズがボタンの配置範囲より小さい場合は描画をはみ出させない
            if (bx + buttonW <= panelX + panelW) {
                boolean hovered = mouseX >= bx && mouseX < bx + buttonW
                               && mouseY >= by && mouseY < by + buttonH;
                if (hovered) hoveredIndex = i;

                // 通常選択中と非選択中のカラーリング
                int bgColor;
                if (settingsModeActive && activeTab == 1 && selectedKeyIndex == i) {
                    bgColor = 0xFF2563EB; // 青ハイライト（編集中のキー）
                } else {
                    bgColor = hovered ? 0x44FFFFFF : 0x22FFFFFF;
                }
                int textColor = (settingsModeActive && activeTab == 1 && selectedKeyIndex == i) ? 0xFFFFFFFF : (hovered ? 0xFFFFFFFF : 0xFFCCCCCC);

                g.fill(bx, by, bx + buttonW, by + buttonH, bgColor);
                drawCenteredString(g, VirtualKeyDefinition.ALL.get(i).label,
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
            drawString(g, "Settings", sx + PANEL_PADDING, sy + PANEL_PADDING + 2, 0xFFE0E0E0);

            // 設定ウィンドウの閉じる(×)ボタン
            int closeW = 16;
            int closeH = 12;
            int closeX = sx + sw - PANEL_PADDING - closeW;
            int closeY = sy + PANEL_PADDING;
            boolean closeHovered = mouseX >= closeX && mouseX < closeX + closeW && mouseY >= closeY && mouseY < closeY + closeH;
            drawString(g, "×", closeX + (closeW - this.font.width("×")) / 2, closeY + (closeH - 8) / 2, closeHovered ? 0xFFFFFFFF : 0xFF888888);

            // タブ切り替えボタン
            int tabY = sy + PANEL_PADDING + 12;
            drawButton(g, sx + PANEL_PADDING, tabY, 50, 14, "Layout", activeTab == 0, mouseX, mouseY);
            drawButton(g, sx + PANEL_PADDING + 55, tabY, 50, 14, "Keys", activeTab == 1, mouseX, mouseY);

            int y1 = sy + PANEL_PADDING + 30;
            int y2 = y1 + 18;
            int y3 = y2 + 18;

            if (activeTab == 0) {
                // Row 1: H Align
                drawString(g, "H:", sx + PANEL_PADDING, y1 + 4, 0xFFCCCCCC);
                drawButton(g, sx + 25, y1, 35, 14, "L", VirtualKeysConfig.INSTANCE.horizontalAlign == VirtualKeysConfig.HorizontalAlign.LEFT, mouseX, mouseY);
                drawButton(g, sx + 65, y1, 45, 14, "C", VirtualKeysConfig.INSTANCE.horizontalAlign == VirtualKeysConfig.HorizontalAlign.CENTER, mouseX, mouseY);
                drawButton(g, sx + 115, y1, 35, 14, "R", VirtualKeysConfig.INSTANCE.horizontalAlign == VirtualKeysConfig.HorizontalAlign.RIGHT, mouseX, mouseY);

                // Row 2: V Align
                drawString(g, "V:", sx + PANEL_PADDING, y2 + 4, 0xFFCCCCCC);
                drawButton(g, sx + 25, y2, 35, 14, "T", VirtualKeysConfig.INSTANCE.verticalAlign == VirtualKeysConfig.VerticalAlign.TOP, mouseX, mouseY);
                drawButton(g, sx + 65, y2, 45, 14, "C", VirtualKeysConfig.INSTANCE.verticalAlign == VirtualKeysConfig.VerticalAlign.CENTER, mouseX, mouseY);
                drawButton(g, sx + 115, y2, 35, 14, "B", VirtualKeysConfig.INSTANCE.verticalAlign == VirtualKeysConfig.VerticalAlign.BOTTOM, mouseX, mouseY);

                // Row 3: Sizes (W, H, Gap, Keys/Row)
                drawString(g, "W:", sx + PANEL_PADDING, y3 + 4, 0xFF999999);
                drawButton(g, sx + 20, y3, 10, 14, "-", false, mouseX, mouseY);
                drawCenteredString(g, String.valueOf(buttonW), sx + 40, y3 + 4, 0xFFFFFFFF);
                drawButton(g, sx + 48, y3, 10, 14, "+", false, mouseX, mouseY);

                drawString(g, "H:", sx + 65, y3 + 4, 0xFF999999);
                drawButton(g, sx + 77, y3, 10, 14, "-", false, mouseX, mouseY);
                drawCenteredString(g, String.valueOf(buttonH), sx + 97, y3 + 4, 0xFFFFFFFF);
                drawButton(g, sx + 105, y3, 10, 14, "+", false, mouseX, mouseY);

                drawString(g, "Col:", sx + 122, y3 + 4, 0xFF999999);
                drawButton(g, sx + 145, y3, 10, 14, "-", false, mouseX, mouseY);
                drawCenteredString(g, String.valueOf(buttonsPerRow), sx + 165, y3 + 4, 0xFFFFFFFF);
                drawButton(g, sx + 173, y3, 10, 14, "+", false, mouseX, mouseY);
            } else {
                // Tab 1: Keys Editor
                // Row 1: Add Key button
                drawButton(g, sx + PANEL_PADDING, y1, 60, 14, "+ Add Key", false, mouseX, mouseY);

                // Row 2 & 3: Selection information or editing controls
                if (selectedKeyIndex < 0 || selectedKeyIndex >= numKeys) {
                    drawString(g, "Click a key to edit.", sx + PANEL_PADDING, y2 + 4, 0xFF888888);
                } else {
                    // Row 2 is rendered automatically by standard nameEditBox widget, nothing custom to draw here.

                    // Row 3: Key Operations (Move Up, Move Down, Delete)
                    drawButton(g, sx + PANEL_PADDING, y3, 40, 14, "Up", false, mouseX, mouseY);
                    drawButton(g, sx + PANEL_PADDING + 45, y3, 40, 14, "Down", false, mouseX, mouseY);
                    drawButton(g, sx + PANEL_PADDING + 90, y3, 45, 14, "Delete", false, mouseX, mouseY);
                }
            }
        }

        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    private void drawButton(GuiGraphicsExtractor g, int x, int y, int w, int h, String text, boolean active, int mx, int my) {
        boolean hovered = mx >= x && mx < x + w && my >= y && my < y + h;
        int bgColor;
        if (active) {
            bgColor = hovered ? 0xFF3B82F6 : 0xFF2563EB; // 青ハイライト
        } else {
            bgColor = hovered ? 0x44FFFFFF : 0x22FFFFFF;
        }
        int textColor = active ? 0xFFFFFFFF : (hovered ? 0xFFFFFFFF : 0xFFCCCCCC);
        g.fill(x, y, x + w, y + h, bgColor);
        drawCenteredString(g, text, x + w / 2, y + (h - 8) / 2, textColor);
    }

    // ── インプット処理 (1.21.11 の新シグネチャ) ──────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean consumed) {
        if (event.button() == 0) {
            int mx = (int) event.x();
            int my = (int) event.y();
            if (handleMouseClick(mx, my, event.button(), 1)) {
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

    public boolean handleMouseClick(double mx, double my, int button, int action) {
        if (action == 1 && button == 0) {
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
                        dev.virtualkeys.client.VirtualKeysClient.closeOverlay();
                        mc.gui.setScreen(this);
                    }
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

            // 4. 仮想キーのクリック判定
            int idx = getButtonAt((int)mx, (int)my);
            if (idx >= 0) {
                if (settingsModeActive && activeTab == 1) {
                    selectedKeyIndex = idx;
                    if (this.nameEditBox != null) {
                        this.nameEditBox.setValue(VirtualKeyDefinition.ALL.get(selectedKeyIndex).label);
                        this.nameEditBox.setVisible(true);
                        this.nameEditBox.setFocused(true);
                        this.setFocused(this.nameEditBox);
                        this.nameEditBox.setCursorPosition(this.nameEditBox.getValue().length());
                    }
                } else {
                    clickedKeyIndex = idx;
                }
                return true;
            }
        }
        return false;
    }

    public void handleMouseRelease(double mx, double my, int button) {
        if (button == 0) {
            draggingSettings = false;
            if (clickedKeyIndex >= 0) {
                int idx = clickedKeyIndex;
                clickedKeyIndex = -1;
                if (getButtonAt((int)mx, (int)my) == idx) {
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

        // 1. 設定パネルの外側をクリックした場合は処理しない
        if (mx < sx || mx >= sx + sw || my < sy || my >= sy + sh) {
            return false;
        }

        // タブ切り替えクリック
        int tabY = sy + PANEL_PADDING + 12;
        if (my >= tabY && my < tabY + 14) {
            if (mx >= sx + PANEL_PADDING && mx < sx + PANEL_PADDING + 50) {
                activeTab = 0;
                return true;
            }
            if (mx >= sx + PANEL_PADDING + 55 && mx < sx + PANEL_PADDING + 55 + 50) {
                activeTab = 1;
                return true;
            }
        }

        int y1 = sy + PANEL_PADDING + 30;
        int y2 = y1 + 18;
        int y3 = y2 + 18;

        int numKeys = VirtualKeyDefinition.ALL.size();

        if (activeTab == 0) {
            // Row 1: H Align (L=25, C=65, R=115)
            if (my >= y1 && my < y1 + 14) {
                if (mx >= sx + 25 && mx < sx + 25 + 35) {
                    VirtualKeysConfig.INSTANCE.horizontalAlign = VirtualKeysConfig.HorizontalAlign.LEFT;
                    recalcLayout();
                    return true;
                }
                if (mx >= sx + 65 && mx < sx + 65 + 45) {
                    VirtualKeysConfig.INSTANCE.horizontalAlign = VirtualKeysConfig.HorizontalAlign.CENTER;
                    recalcLayout();
                    return true;
                }
                if (mx >= sx + 115 && mx < sx + 115 + 35) {
                    VirtualKeysConfig.INSTANCE.horizontalAlign = VirtualKeysConfig.HorizontalAlign.RIGHT;
                    recalcLayout();
                    return true;
                }
            }

            // Row 2: V Align (T=25, C=65, B=115)
            if (my >= y2 && my < y2 + 14) {
                if (mx >= sx + 25 && mx < sx + 25 + 35) {
                    VirtualKeysConfig.INSTANCE.verticalAlign = VirtualKeysConfig.VerticalAlign.TOP;
                    recalcLayout();
                    return true;
                }
                if (mx >= sx + 65 && mx < sx + 65 + 45) {
                    VirtualKeysConfig.INSTANCE.verticalAlign = VirtualKeysConfig.VerticalAlign.CENTER;
                    recalcLayout();
                    return true;
                }
                if (mx >= sx + 115 && mx < sx + 115 + 35) {
                    VirtualKeysConfig.INSTANCE.verticalAlign = VirtualKeysConfig.VerticalAlign.BOTTOM;
                    recalcLayout();
                    return true;
                }
            }

            // Row 3: Button Sizes (-=20, +=48 / -=77, +=105 / -=145, +=173)
            if (my >= y3 && my < y3 + 14) {
                // Width adjustment (10 - 100)
                if (mx >= sx + 20 && mx < sx + 20 + 10) {
                    VirtualKeysConfig.INSTANCE.buttonWidth = Math.max(10, VirtualKeysConfig.INSTANCE.buttonWidth - 2);
                    recalcLayout();
                    return true;
                }
                if (mx >= sx + 48 && mx < sx + 48 + 10) {
                    VirtualKeysConfig.INSTANCE.buttonWidth = Math.min(100, VirtualKeysConfig.INSTANCE.buttonWidth + 2);
                    recalcLayout();
                    return true;
                }

                // Height adjustment (8 - 60)
                if (mx >= sx + 77 && mx < sx + 77 + 10) {
                    VirtualKeysConfig.INSTANCE.buttonHeight = Math.max(8, VirtualKeysConfig.INSTANCE.buttonHeight - 2);
                    recalcLayout();
                    return true;
                }
                if (mx >= sx + 105 && mx < sx + 105 + 10) {
                    VirtualKeysConfig.INSTANCE.buttonHeight = Math.min(60, VirtualKeysConfig.INSTANCE.buttonHeight + 2);
                    recalcLayout();
                    return true;
                }

                // Keys per row (Columns) adjustment (1 - 32)
                if (mx >= sx + 145 && mx < sx + 145 + 10) {
                    buttonsPerRow = Math.max(1, buttonsPerRow - 1);
                    VirtualKeysConfig.INSTANCE.keysPerRow = buttonsPerRow;
                    recalcLayout();
                    return true;
                }
                if (mx >= sx + 173 && mx < sx + 173 + 10) {
                    buttonsPerRow = Math.min(32, buttonsPerRow + 1);
                    VirtualKeysConfig.INSTANCE.keysPerRow = buttonsPerRow;
                    recalcLayout();
                    return true;
                }
            }
        } else {
            // Tab 1: Keys Editor
            // Row 1: Add Key button (+ Add Key = sx + PANEL_PADDING, w=60, h=14)
            if (my >= y1 && my < y1 + 14 && mx >= sx + PANEL_PADDING && mx < sx + PANEL_PADDING + 60) {
                VirtualKeyDefinition newDef = new VirtualKeyDefinition(
                    "NewKey",
                    401 + VirtualKeyDefinition.ALL.size()
                );
                VirtualKeyDefinition.ALL.add(newDef);
                selectedKeyIndex = VirtualKeyDefinition.ALL.size() - 1;
                VirtualKeysConfig.save();
                recalcLayout();
                if (this.nameEditBox != null) {
                    this.nameEditBox.setValue(newDef.label);
                    this.nameEditBox.setVisible(true);
                    this.nameEditBox.setFocused(true);
                    this.setFocused(this.nameEditBox);
                    this.nameEditBox.setCursorPosition(this.nameEditBox.getValue().length());
                }
                return true;
            }

            // Row 3: Key Operations (Up=40, Down=40, Delete=45)
            if (selectedKeyIndex >= 0 && selectedKeyIndex < numKeys && my >= y3 && my < y3 + 14) {
                // Move Up
                if (mx >= sx + PANEL_PADDING && mx < sx + PANEL_PADDING + 40) {
                    if (selectedKeyIndex > 0) {
                        VirtualKeyDefinition def = VirtualKeyDefinition.ALL.remove(selectedKeyIndex);
                        selectedKeyIndex--;
                        VirtualKeyDefinition.ALL.add(selectedKeyIndex, def);
                        VirtualKeysConfig.save();
                        recalcLayout();
                    }
                    return true;
                }
                // Move Down
                if (mx >= sx + PANEL_PADDING + 45 && mx < sx + PANEL_PADDING + 45 + 40) {
                    if (selectedKeyIndex < numKeys - 1) {
                        VirtualKeyDefinition def = VirtualKeyDefinition.ALL.remove(selectedKeyIndex);
                        selectedKeyIndex++;
                        VirtualKeyDefinition.ALL.add(selectedKeyIndex, def);
                        VirtualKeysConfig.save();
                        recalcLayout();
                    }
                    return true;
                }
                // Delete Key
                if (mx >= sx + PANEL_PADDING + 90 && mx < sx + PANEL_PADDING + 90 + 45) {
                    VirtualKeyDefinition.ALL.remove(selectedKeyIndex);
                    selectedKeyIndex = -1;
                    VirtualKeysConfig.save();
                    recalcLayout();
                    return true;
                }
            }
        }

        return true; // 設定パネル内をクリックした場合はイベントを消費
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.nameEditBox != null && this.nameEditBox.isVisible() && this.nameEditBox.isFocused()) {
            if (this.nameEditBox.keyPressed(event)) {
                return true;
            }
        }

        if (event.key() == GLFW.GLFW_KEY_ESCAPE && shouldCloseOnEsc()) {
            if (settingsModeActive) {
                settingsModeActive = false;
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
    public boolean charTyped(CharacterEvent event) {
        if (this.nameEditBox != null && this.nameEditBox.isVisible() && this.nameEditBox.isFocused()) {
            if (this.nameEditBox.charTyped(event)) {
                return true;
            }
        }
        return super.charTyped(event);
    }

    @Override
    public void onClose() {
        closePanel();
    }

    private void selectAndClose(int index) {
        if (index < 0 || index >= VirtualKeyDefinition.ALL.size()) return;
        VirtualKeyDefinition keyDef = VirtualKeyDefinition.ALL.get(index);

        // 1. パネルを閉じる (オーバーレイ表示ならオーバーレイを閉じ、通常画面なら親画面に復帰)
        closePanel();

        // 2. 仮想キー入力をシミュレート
        simulateKeyPress(keyDef.glfwKey);
    }

    public void closePanel() {
        if (dev.virtualkeys.client.VirtualKeysClient.overlayActive && dev.virtualkeys.client.VirtualKeysClient.overlayScreen == this) {
            dev.virtualkeys.client.VirtualKeysClient.closeOverlay();
        } else {
            Minecraft.getInstance().gui.setScreen(parentScreen);
        }
    }

    /**
     * 仮想キーイベントを発火する。
     */
    private static void simulateKeyPress(int glfwKey) {
        Minecraft mc = Minecraft.getInstance();

        try {
            long windowHandle = mc.getWindow().handle();
            if (!checkedOnKeyMethod) {
                cachedOnKeyMethod = findOnKeyMethod(mc.keyboardHandler);
                checkedOnKeyMethod = true;
            }
            java.lang.reflect.Method m = cachedOnKeyMethod;
            if (m != null) {
                m.setAccessible(true);
                int scancode = org.lwjgl.glfw.GLFW.glfwGetKeyScancode(glfwKey);
                dev.virtualkeys.client.VirtualKeysClient.setVirtualKeyDown(glfwKey, true);
                
                Class<?>[] params = m.getParameterTypes();
                if (params.length == 5) {
                    m.invoke(mc.keyboardHandler, windowHandle, glfwKey, scancode, GLFW.GLFW_PRESS, 0);
                    m.invoke(mc.keyboardHandler, windowHandle, glfwKey, scancode, GLFW.GLFW_RELEASE, 0);
                } else if (params.length == 3) {
                    if (!checkedKeyEventConstructor) {
                        Class<?> keyEventClass = params[2];
                        try {
                            cachedKeyEventConstructor = keyEventClass.getDeclaredConstructor(int.class, int.class, int.class);
                            cachedKeyEventConstructor.setAccessible(true);
                        } catch (Exception e) {
                            System.err.println("[VirtualKeys Error] Failed to get KeyEvent constructor: " + e.getMessage());
                        }
                        checkedKeyEventConstructor = true;
                    }
                    if (cachedKeyEventConstructor != null) {
                        Object pressEvent = cachedKeyEventConstructor.newInstance(glfwKey, scancode, 0);
                        m.invoke(mc.keyboardHandler, windowHandle, GLFW.GLFW_PRESS, pressEvent);
                        
                        Object releaseEvent = cachedKeyEventConstructor.newInstance(glfwKey, scancode, 0);
                        m.invoke(mc.keyboardHandler, windowHandle, GLFW.GLFW_RELEASE, releaseEvent);
                    }
                }
                
                dev.virtualkeys.client.VirtualKeysClient.setVirtualKeyDown(glfwKey, false);
            } else {
                System.err.println("[VirtualKeys Error] Could not find onKey/keyPress method on KeyboardHandler!");
            }
        } catch (Exception e) {
            System.err.println("[VirtualKeys Error] Failed to simulate keypress: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static java.lang.reflect.Method findOnKeyMethod(Object handler) {
        for (java.lang.reflect.Method m : handler.getClass().getDeclaredMethods()) {
            if (m.isSynthetic() || m.isBridge()) {
                continue;
            }
            Class<?>[] p = m.getParameterTypes();
            if (p.length == 5
                    && p[0] == long.class
                    && p[1] == int.class
                    && p[2] == int.class
                    && p[3] == int.class
                    && p[4] == int.class) {
                return m;
            }
            if (p.length == 3
                    && p[0] == long.class
                    && p[1] == int.class
                    && !p[2].isPrimitive()) {
                try {
                    p[2].getDeclaredConstructor(int.class, int.class, int.class);
                    return m;
                } catch (NoSuchMethodException ignored) {
                }
            }
        }
        return null;
    }

    // ── ユーティリティ ───────────────────────────────────────────

    private int getButtonAt(int mx, int my) {
        int buttonW = VirtualKeysConfig.INSTANCE.buttonWidth;
        int buttonH = VirtualKeysConfig.INSTANCE.buttonHeight;
        int buttonGap = VirtualKeysConfig.INSTANCE.buttonGap;

        for (int i = 0; i < VirtualKeyDefinition.ALL.size(); i++) {
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
