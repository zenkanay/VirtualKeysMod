package dev.virtualkeys.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.virtualkeys.VirtualKeyDefinition;
import dev.virtualkeys.VirtualKeysConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
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

    private net.minecraft.client.gui.components.EditBox nameEditBox;

    public VirtualKeyScreen(Screen parentScreen) {
        super(Component.translatable("gui.virtualkeys.title"));
        this.parentScreen = parentScreen;
    }

    @Override
    public boolean isPauseScreen() {
        return parentScreen != null && parentScreen.isPauseScreen();
    }

    @Override
    public void renderBackground(GuiGraphics g) {
        // 背景のぼかしやグラデーションを描画しない
    }

    @Override
    protected void init() {
        VirtualKeysConfig.load(); // パネルを開いたタイミングでconfigを動的再ロード
        super.init();

        this.nameEditBox = new net.minecraft.client.gui.components.EditBox(
            this.font,
            0, 0, 100, 12,
            Component.literal("Key Name")
        ) {
            @Override
            public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
                if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                    selectedKeyIndex = -1;
                    this.setFocused(false);
                    VirtualKeyScreen.this.setFocused(null);
                    super.keyPressed(keyCode, scanCode, modifiers);
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                    selectedKeyIndex = -1;
                    this.setFocused(false);
                    VirtualKeyScreen.this.setFocused(null);
                    super.keyPressed(keyCode, scanCode, modifiers);
                    return true;
                }
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        };
        this.nameEditBox.setMaxLength(2048);
        this.nameEditBox.setVisible(false);
        this.nameEditBox.setResponder(val -> {
            if (isEditingText()) {
                updateSelectedKeyLabel(val);
            }
        });
        this.addRenderableWidget(this.nameEditBox);

        recalcLayout();
    }

    private void recalcLayout() {
        int numKeys = VirtualKeyDefinition.ALL.size();
        buttonsPerRow = VirtualKeysConfig.INSTANCE.keysPerRow;
        if (buttonsPerRow < 1) buttonsPerRow = 3;

        int rows = (int) Math.ceil((double) numKeys / buttonsPerRow);

        int buttonW = VirtualKeysConfig.INSTANCE.buttonWidth;
        int buttonH = VirtualKeysConfig.INSTANCE.buttonHeight;
        int buttonGap = VirtualKeysConfig.INSTANCE.buttonGap;

        panelW = buttonsPerRow * (buttonW + buttonGap) - buttonGap + PANEL_PADDING * 2;
        panelH = PANEL_PADDING * 2 + HEADER_HEIGHT + rows * (buttonH + buttonGap) - buttonGap;

        // 水平方向のアライメント
        switch (VirtualKeysConfig.INSTANCE.horizontalAlign) {
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
        switch (VirtualKeysConfig.INSTANCE.verticalAlign) {
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

        // 設定パネルの位置調整
        if (settingsX == -1 || settingsY == -1) {
            resetSettingsPosition();
        } else {
            clampSettingsPosition();
        }

        if (this.nameEditBox != null) {
            int sx = getSettingsX();
            int sy = getSettingsY();
            int sw = getSettingsW();
            int y2 = sy + PANEL_PADDING + 30 + 18; // Row 2
            this.nameEditBox.setX(sx + PANEL_PADDING);
            this.nameEditBox.setY(y2);
            this.nameEditBox.setWidth(sw - PANEL_PADDING * 2);
            this.nameEditBox.setHeight(12);
        }
    }

    public void resetSettingsPosition() {
        int defaultX;
        int defaultY = panelY;
        
        // Try placing to the right
        if (panelX + panelW + 4 + getSettingsW() <= this.width) {
            defaultX = panelX + panelW + 4;
        } 
        // Try placing to the left
        else if (panelX - getSettingsW() - 4 >= 0) {
            defaultX = panelX - getSettingsW() - 4;
        } 
        // Place below the panel if width is tight
        else if (panelY + panelH + 4 + getSettingsH() <= this.height) {
            defaultX = panelX;
            defaultY = panelY + panelH + 4;
        }
        // Place above the panel
        else if (panelY - getSettingsH() - 4 >= 0) {
            defaultX = panelX;
            defaultY = panelY - getSettingsH() - 4;
        }
        // Absolute fallback (center of screen)
        else {
            defaultX = (this.width - getSettingsW()) / 2;
            defaultY = (this.height - getSettingsH()) / 2;
        }
        
        settingsX = defaultX;
        settingsY = defaultY;
        clampSettingsPosition();
    }

    private void clampSettingsPosition() {
        if (settingsX < 0) settingsX = 0;
        if (settingsX + getSettingsW() > this.width) settingsX = this.width - getSettingsW();
        if (settingsY < 0) settingsY = 0;
        if (settingsY + getSettingsH() > this.height) settingsY = this.height - getSettingsH();
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
        return 100;
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
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (draggingSettings) {
            settingsX = (int) (mouseX - dragOffsetX);
            settingsY = (int) (mouseY - dragOffsetY);
            clampSettingsPosition();
            recalcLayout();
        }

        // Synchronize nameEditBox visibility and values based on editing states
        boolean showEditBox = settingsModeActive && activeTab == 1 && selectedKeyIndex >= 0 && selectedKeyIndex < VirtualKeyDefinition.ALL.size();
        if (this.nameEditBox != null) {
            if (showEditBox) {
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

        // Overlay表示時は親画面の再描画を行う（親画面の上にかぶせて描画するため）
        if (parentScreen != null && Minecraft.getInstance().screen == this) {
            parentScreen.render(g, -9999, -9999, delta);
        }

        // シンプルな半透明の暗い背景 (メインのキーボードパネル)
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 10.0F);
        
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xD0121212);

        // タイトルと歯車アイコンの描画（幅に合わせて文字を省略または非表示にして崩れを防ぐ）
        if (panelW >= 80) {
            g.drawString(this.font, Component.translatable("gui.virtualkeys.title"), panelX + PANEL_PADDING, panelY + PANEL_PADDING + 2, 0xFFE0E0E0, false);
        } else if (panelW >= 40) {
            g.drawString(this.font, "VKeys", panelX + PANEL_PADDING, panelY + PANEL_PADDING + 2, 0xFFE0E0E0, false);
        }
        
        int gearW = 16;
        int gearH = 12;
        int gearX = panelX + panelW - PANEL_PADDING - gearW;
        int gearY = panelY + PANEL_PADDING;
        boolean gearHovered = mouseX >= gearX && mouseX < gearX + gearW && mouseY >= gearY && mouseY < gearY + gearH;
        g.drawString(this.font, "⚙", gearX + (gearW - this.font.width("⚙")) / 2, gearY + (gearH - 8) / 2, gearHovered ? 0xFFFFFFFF : 0xFF888888, false);

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
                g.drawCenteredString(this.font, VirtualKeyDefinition.ALL.get(i).label,
                    bx + buttonW / 2, by + (buttonH - 8) / 2, textColor);
            }
        }
        g.pose().popPose();

        // 4. 設定パネルの描画
        if (settingsModeActive) {
            g.pose().pushPose();
            g.pose().translate(0.0F, 0.0F, 100.0F);
            
            int sx = getSettingsX();
            int sy = getSettingsY();
            int sw = getSettingsW();
            int sh = getSettingsH();

            // 設定パネルの背景
            g.fill(sx, sy, sx + sw, sy + sh, 0xD0121212);

            // 設定タイトル
            g.drawString(this.font, "Settings", sx + PANEL_PADDING, sy + PANEL_PADDING + 2, 0xFFE0E0E0, false);

            // タブ切り替えボタン
            int tabY = sy + PANEL_PADDING + 12;
            drawButton(g, sx + PANEL_PADDING, tabY, 50, 14, "Layout", activeTab == 0, mouseX, mouseY);
            drawButton(g, sx + PANEL_PADDING + 55, tabY, 50, 14, "Keys", activeTab == 1, mouseX, mouseY);

            int y1 = sy + PANEL_PADDING + 30;
            int y2 = y1 + 18;
            int y3 = y2 + 18;

            if (activeTab == 0) {
                // Row 1: H Align
                g.drawString(this.font, "H:", sx + PANEL_PADDING, y1 + 4, 0xFFCCCCCC, false);
                drawButton(g, sx + 25, y1, 35, 14, "L", VirtualKeysConfig.INSTANCE.horizontalAlign == VirtualKeysConfig.HorizontalAlign.LEFT, mouseX, mouseY);
                drawButton(g, sx + 65, y1, 45, 14, "C", VirtualKeysConfig.INSTANCE.horizontalAlign == VirtualKeysConfig.HorizontalAlign.CENTER, mouseX, mouseY);
                drawButton(g, sx + 115, y1, 35, 14, "R", VirtualKeysConfig.INSTANCE.horizontalAlign == VirtualKeysConfig.HorizontalAlign.RIGHT, mouseX, mouseY);

                // Row 2: V Align
                g.drawString(this.font, "V:", sx + PANEL_PADDING, y2 + 4, 0xFFCCCCCC, false);
                drawButton(g, sx + 25, y2, 35, 14, "T", VirtualKeysConfig.INSTANCE.verticalAlign == VirtualKeysConfig.VerticalAlign.TOP, mouseX, mouseY);
                drawButton(g, sx + 65, y2, 45, 14, "C", VirtualKeysConfig.INSTANCE.verticalAlign == VirtualKeysConfig.VerticalAlign.CENTER, mouseX, mouseY);
                drawButton(g, sx + 115, y2, 35, 14, "B", VirtualKeysConfig.INSTANCE.verticalAlign == VirtualKeysConfig.VerticalAlign.BOTTOM, mouseX, mouseY);

                // Row 3: Sizes (W, H, Gap, Keys/Row)
                g.drawString(this.font, "W:", sx + PANEL_PADDING, y3 + 4, 0xFF999999, false);
                drawButton(g, sx + 20, y3, 10, 14, "-", false, mouseX, mouseY);
                g.drawCenteredString(this.font, String.valueOf(buttonW), sx + 40, y3 + 4, 0xFFFFFFFF);
                drawButton(g, sx + 48, y3, 10, 14, "+", false, mouseX, mouseY);

                g.drawString(this.font, "H:", sx + 65, y3 + 4, 0xFF999999, false);
                drawButton(g, sx + 77, y3, 10, 14, "-", false, mouseX, mouseY);
                g.drawCenteredString(this.font, String.valueOf(buttonH), sx + 97, y3 + 4, 0xFFFFFFFF);
                drawButton(g, sx + 105, y3, 10, 14, "+", false, mouseX, mouseY);

                g.drawString(this.font, "Col:", sx + 122, y3 + 4, 0xFF999999, false);
                drawButton(g, sx + 145, y3, 10, 14, "-", false, mouseX, mouseY);
                g.drawCenteredString(this.font, String.valueOf(buttonsPerRow), sx + 165, y3 + 4, 0xFFFFFFFF);
                drawButton(g, sx + 173, y3, 10, 14, "+", false, mouseX, mouseY);
            } else {
                // Tab 1: Keys Editor
                // Row 1: Add Key button
                drawButton(g, sx + PANEL_PADDING, y1, 60, 14, "+ Add Key", false, mouseX, mouseY);

                // Row 2 & 3: Selection information or editing controls
                if (selectedKeyIndex < 0 || selectedKeyIndex >= numKeys) {
                    g.drawString(this.font, "Click a key to edit.", sx + PANEL_PADDING, y2 + 4, 0xFF888888, false);
                } else {
                    // Row 3: Key Operations (Move Up, Move Down, Delete)
                    drawButton(g, sx + PANEL_PADDING, y3, 40, 14, "Up", false, mouseX, mouseY);
                    drawButton(g, sx + PANEL_PADDING + 45, y3, 40, 14, "Down", false, mouseX, mouseY);
                    drawButton(g, sx + PANEL_PADDING + 90, y3, 45, 14, "Delete", false, mouseX, mouseY);
                }
            }
            g.pose().popPose();
        }

        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 150.0F);
        super.render(g, mouseX, mouseY, delta);
        g.pose().popPose();
    }

    private void drawButton(GuiGraphics g, int x, int y, int w, int h, String text, boolean active, int mx, int my) {
        boolean hovered = mx >= x && mx < x + w && my >= y && my < y + h;
        int bgColor;
        if (active) {
            bgColor = hovered ? 0xFF3B82F6 : 0xFF2563EB; // 青ハイライト
        } else {
            bgColor = hovered ? 0x44FFFFFF : 0x22FFFFFF;
        }
        int textColor = active ? 0xFFFFFFFF : (hovered ? 0xFFFFFFFF : 0xFFCCCCCC);
        g.fill(x, y, x + w, y + h, bgColor);
        g.drawCenteredString(this.font, text, x + w / 2, y + (h - 8) / 2, textColor);
    }

    // ── インプット処理 ──────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (handleMouseClick(mouseX, mouseY, button, 1)) {
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        handleMouseRelease(mouseX, mouseY, button);
        return super.mouseReleased(mouseX, mouseY, button);
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
                    if (mc.screen != this) {
                        dev.virtualkeys.client.VirtualKeysClient.closeOverlay();
                        mc.setScreen(this);
                    }
                }
                recalcLayout();
                return true;
            }

            // 2. 設定パネルのドラッグ判定 (タイトルバー部分)
            if (settingsModeActive) {
                int sx = getSettingsX();
                int sy = getSettingsY();
                int sw = getSettingsW();
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

            // Row 3: Sizes (W: -=20, +=48; H: -=77, +=105; Col: -=145, +=173)
            if (my >= y3 && my < y3 + 14) {
                // Width
                if (mx >= sx + 20 && mx < sx + 20 + 10) {
                    if (VirtualKeysConfig.INSTANCE.buttonWidth > 1) {
                        VirtualKeysConfig.INSTANCE.buttonWidth -= 2;
                        recalcLayout();
                    }
                    return true;
                }
                if (mx >= sx + 48 && mx < sx + 48 + 10) {
                    VirtualKeysConfig.INSTANCE.buttonWidth += 2;
                    recalcLayout();
                    return true;
                }
                // Height
                if (mx >= sx + 77 && mx < sx + 77 + 10) {
                    if (VirtualKeysConfig.INSTANCE.buttonHeight > 1) {
                        VirtualKeysConfig.INSTANCE.buttonHeight -= 2;
                        recalcLayout();
                    }
                    return true;
                }
                if (mx >= sx + 105 && mx < sx + 105 + 10) {
                    VirtualKeysConfig.INSTANCE.buttonHeight += 2;
                    recalcLayout();
                    return true;
                }
                // Col
                if (mx >= sx + 145 && mx < sx + 145 + 10) {
                    if (VirtualKeysConfig.INSTANCE.keysPerRow > 1) {
                        VirtualKeysConfig.INSTANCE.keysPerRow -= 1;
                        recalcLayout();
                    }
                    return true;
                }
                if (mx >= sx + 173 && mx < sx + 173 + 10) {
                    VirtualKeysConfig.INSTANCE.keysPerRow += 1;
                    recalcLayout();
                    return true;
                }
            }
        } else {
            // Tab 1: Keys Editor
            // Row 1: Add Key
            if (my >= y1 && my < y1 + 14) {
                if (mx >= sx + PANEL_PADDING && mx < sx + PANEL_PADDING + 60) {
                    String newLabel = "VKey" + (VirtualKeyDefinition.ALL.size() + 1);
                    VirtualKeysConfig.INSTANCE.keys.add(newLabel);
                    VirtualKeyDefinition.updateFromConfig(VirtualKeysConfig.INSTANCE.keys);
                    selectedKeyIndex = VirtualKeyDefinition.ALL.size() - 1;
                    recalcLayout();
                    return true;
                }
            }

            // Row 2 clicking is handled by the nameEditBox widget itself, so we do not intercept it here.

            if (selectedKeyIndex >= 0 && selectedKeyIndex < numKeys && my >= y3 && my < y3 + 14) {
                // Move Up
                if (mx >= sx + PANEL_PADDING && mx < sx + PANEL_PADDING + 40) {
                    if (selectedKeyIndex > 0) {
                        String temp = VirtualKeysConfig.INSTANCE.keys.get(selectedKeyIndex);
                        VirtualKeysConfig.INSTANCE.keys.set(selectedKeyIndex, VirtualKeysConfig.INSTANCE.keys.get(selectedKeyIndex - 1));
                        VirtualKeysConfig.INSTANCE.keys.set(selectedKeyIndex - 1, temp);
                        VirtualKeyDefinition.updateFromConfig(VirtualKeysConfig.INSTANCE.keys);
                        selectedKeyIndex--;
                        recalcLayout();
                    }
                    return true;
                }
                // Move Down
                if (mx >= sx + PANEL_PADDING + 45 && mx < sx + PANEL_PADDING + 45 + 40) {
                    if (selectedKeyIndex < numKeys - 1) {
                        String temp = VirtualKeysConfig.INSTANCE.keys.get(selectedKeyIndex);
                        VirtualKeysConfig.INSTANCE.keys.set(selectedKeyIndex, VirtualKeysConfig.INSTANCE.keys.get(selectedKeyIndex + 1));
                        VirtualKeysConfig.INSTANCE.keys.set(selectedKeyIndex + 1, temp);
                        VirtualKeyDefinition.updateFromConfig(VirtualKeysConfig.INSTANCE.keys);
                        selectedKeyIndex++;
                        recalcLayout();
                    }
                    return true;
                }
                // Delete
                if (mx >= sx + PANEL_PADDING + 90 && mx < sx + PANEL_PADDING + 90 + 45) {
                    VirtualKeysConfig.INSTANCE.keys.remove(selectedKeyIndex);
                    VirtualKeyDefinition.updateFromConfig(VirtualKeysConfig.INSTANCE.keys);
                    selectedKeyIndex = -1;
                    recalcLayout();
                    return true;
                }
            }
        }

        return false;
    }

    public boolean isEditingText() {
        return settingsModeActive && activeTab == 1 && selectedKeyIndex >= 0;
    }

    public Screen getParentScreen() {
        return this.parentScreen;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (isEditingText()) {
            super.charTyped(codePoint, modifiers);
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    private void updateSelectedKeyLabel(String label) {
        if (selectedKeyIndex >= 0 && selectedKeyIndex < VirtualKeyDefinition.ALL.size()) {
            VirtualKeyDefinition old = VirtualKeyDefinition.ALL.get(selectedKeyIndex);
            VirtualKeyDefinition.ALL.set(selectedKeyIndex, new VirtualKeyDefinition(label, old.glfwKey));
            VirtualKeysConfig.INSTANCE.keys.set(selectedKeyIndex, label);
        }
    }

    @Override
    public void removed() {
        VirtualKeysConfig.save();
        super.removed();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (isEditingText()) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                selectedKeyIndex = -1;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                selectedKeyIndex = -1;
                return true;
            }
            super.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            closePanel();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ── 仮想キー発火ロジック ─────────────────────────────────────

    private void selectAndClose(int idx) {
        VirtualKeyDefinition keyDef = VirtualKeyDefinition.ALL.get(idx);
        
        // 1. パネルを閉じる
        closePanel();
        
        // 2. 仮想キー入力をシミュレート
        simulateKeyPress(keyDef.glfwKey);
    }

    public void closePanel() {
        if (dev.virtualkeys.client.VirtualKeysClient.overlayActive && dev.virtualkeys.client.VirtualKeysClient.overlayScreen == this) {
            dev.virtualkeys.client.VirtualKeysClient.closeOverlay();
        } else {
            Minecraft.getInstance().setScreen(parentScreen);
        }
    }

    /**
     * 仮想キーイベントを発火する。
     */
    private static void simulateKeyPress(int glfwKey) {
        Minecraft mc = Minecraft.getInstance();

        try {
            long windowHandle = mc.getWindow().getWindow();
            int scancode = org.lwjgl.glfw.GLFW.glfwGetKeyScancode(glfwKey);
            dev.virtualkeys.client.VirtualKeysClient.setVirtualKeyDown(glfwKey, true);
            
            // Invoke the keyPress method directly for Forge 1.20.1 Compatibility
            mc.keyboardHandler.keyPress(windowHandle, glfwKey, scancode, GLFW.GLFW_PRESS, 0);
            mc.keyboardHandler.keyPress(windowHandle, glfwKey, scancode, GLFW.GLFW_RELEASE, 0);
            
            dev.virtualkeys.client.VirtualKeysClient.setVirtualKeyDown(glfwKey, false);
        } catch (Exception e) {
            System.err.println("[VirtualKeys Error] Failed to simulate keypress: " + e.getMessage());
            e.printStackTrace();
        }
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

    @Override
    public void tick() {
        super.tick();
        if (this.nameEditBox != null) {
            this.nameEditBox.tick();
        }
    }
}
