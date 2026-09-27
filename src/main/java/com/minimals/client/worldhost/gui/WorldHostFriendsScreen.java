package com.minimals.client.worldhost.gui;

import com.minimals.client.ui.UiRenderer;
import com.minimals.client.worldhost.WorldHostManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Friends screen for the World Host relay: add a friend by username, see which friends
 * currently have a joinable world open, join one, or remove a friend. Opened from the
 * pause menu's "Multiplayer..." button (see {@link com.minimals.client.mixin.PauseMenuMultiplayerMixin}
 * and its follow-up hook) and from the RSHIFT radial menu.
 */
public class WorldHostFriendsScreen extends Screen {

    private static final int PANEL_W = 320;
    private static final int PANEL_H = 260;
    private static final int PANEL_RADIUS = 10;
    private static final int PAD = 14;
    private static final int HEADER_H = 62;
    private static final int ROW_H = 26;
    private static final int ROW_GAP = 4;
    private static final int FOOTER_H = 34;
    private static final int SCROLL_STEP = 16;

    private static final int JOIN_BTN_W = 46;
    private static final int REMOVE_BTN_W = 18;

    private final Screen returnTo;
    private EditBox addBox;
    private Button addButton;
    private Button publishButton;
    private String status = "";
    private boolean statusIsError;
    private double scroll;

    /** Cached row list, rebuilt each frame from WorldHostManager's friend map. */
    private List<Map.Entry<UUID, String>> rows = new ArrayList<>();

    public WorldHostFriendsScreen(Screen returnTo) {
        super(Component.literal("Multiplayer Friends"));
        this.returnTo = returnTo;
    }

    private int panelX() {
        return (width - PANEL_W) / 2;
    }

    private int panelY() {
        return (height - PANEL_H) / 2;
    }

    private int listY() {
        return panelY() + HEADER_H;
    }

    private int listBottom() {
        return panelY() + PANEL_H - FOOTER_H;
    }

    @Override
    protected void init() {
        int px = panelX();
        int py = panelY();

        addBox = new EditBox(font, px + PAD, py + PANEL_H - FOOTER_H + 7, PANEL_W - PAD * 2 - 70, 20, Component.literal("Username"));
        addBox.setMaxLength(16);
        addBox.setHint(Component.literal("Friend's username"));
        addRenderableWidget(addBox);

        addButton = Button.builder(Component.literal("Add"), btn -> addFriend())
                .bounds(px + PANEL_W - PAD - 60, py + PANEL_H - FOOTER_H + 7, 60, 20).build();
        addRenderableWidget(addButton);

        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server != null) {
            publishButton = Button.builder(publishLabel(server), btn -> togglePublish())
                    .bounds(px + PANEL_W - PAD - 100, py + 34, 100, 20).build();
            addRenderableWidget(publishButton);
        }
    }

    private Component publishLabel(IntegratedServer server) {
        return Component.literal(server.isPublished() ? "Close World" : "Open World");
    }

    private void togglePublish() {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) return;
        if (server.isPublished()) {
            server.unpublishServer();
            status = "World closed.";
            statusIsError = false;
        } else {
            int port = server.getPort();
            if (port <= 0 || port > 65535) port = 25565;
            boolean ok = server.publishServer(MinecraftServer.MultiplayerScope.LAN, port);
            status = ok ? "World opened to friends." : "Could not open world.";
            statusIsError = !ok;
        }
        publishButton.setMessage(publishLabel(server));
    }

    private void addFriend() {
        String name = addBox.getValue().strip();
        if (name.isEmpty()) return;
        addButton.active = false;
        status = "Looking up " + name + "...";
        statusIsError = false;
        WorldHostManager.addFriendByName(name,
                () -> {
                    status = "Added " + name + ".";
                    statusIsError = false;
                    addBox.setValue("");
                    addButton.active = true;
                },
                error -> {
                    status = error;
                    statusIsError = true;
                    addButton.active = true;
                }
        );
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x88000000);

        int px = panelX();
        int py = panelY();
        UiRenderer.roundedRect(graphics, px, py, px + PANEL_W, py + PANEL_H, PANEL_RADIUS, UiRenderer.PANEL_BG);

        UiRenderer.text(graphics, "Multiplayer Friends", px + PAD, py + 14, UiRenderer.TEXT_PRIMARY);
        String connState = WorldHostManager.isConnected() ? "Connected" : "Connecting...";
        UiRenderer.text(graphics, connState, px + PANEL_W - PAD - UiRenderer.textWidth(connState), py + 14,
                WorldHostManager.isConnected() ? 0xFF6EE7B7 : UiRenderer.TEXT_SECONDARY);

        rows = new ArrayList<>(WorldHostManager.friends().entrySet());

        graphics.enableScissor(px, listY(), px + PANEL_W, listBottom());
        int rowY = listY() - (int) scroll;
        for (Map.Entry<UUID, String> entry : rows) {
            renderRow(graphics, mouseX, mouseY, px, rowY, entry.getKey(), entry.getValue());
            rowY += ROW_H + ROW_GAP;
        }
        if (rows.isEmpty()) {
            UiRenderer.text(graphics, "No friends added yet.", px + PAD, listY() + 6, UiRenderer.TEXT_SECONDARY);
        }
        graphics.disableScissor();

        if (!status.isEmpty()) {
            UiRenderer.text(graphics, status, px + PAD, py + PANEL_H - FOOTER_H - 12,
                    statusIsError ? 0xFFFF6B6B : UiRenderer.TEXT_SECONDARY);
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void renderRow(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int px, int rowY, UUID uuid, String name) {
        if (rowY + ROW_H < listY() || rowY > listBottom()) return;

        boolean hovered = mouseX >= px + PAD && mouseX <= px + PANEL_W - PAD && mouseY >= rowY && mouseY <= rowY + ROW_H;
        UiRenderer.roundedRect(graphics, px + PAD, rowY, px + PANEL_W - PAD, rowY + ROW_H, 6,
                hovered ? UiRenderer.ROW_BG_HOVER : UiRenderer.ROW_BG);

        boolean online = WorldHostManager.isFriendOnline(uuid);
        int dotColor = online ? 0xFF6EE7B7 : 0xFF5A5A66;
        UiRenderer.roundedRect(graphics, px + PAD + 6, rowY + ROW_H / 2 - 3, px + PAD + 12, rowY + ROW_H / 2 + 3, 3, dotColor);
        UiRenderer.text(graphics, name, px + PAD + 20, rowY + ROW_H / 2 - 4, UiRenderer.TEXT_PRIMARY);

        int removeX = px + PANEL_W - PAD - REMOVE_BTN_W;
        UiRenderer.centeredText(graphics, "x", removeX + REMOVE_BTN_W / 2, rowY + ROW_H / 2 - 4, UiRenderer.TEXT_SECONDARY);

        if (online) {
            int joinX = removeX - JOIN_BTN_W - 6;
            UiRenderer.roundedRect(graphics, joinX, rowY + 3, joinX + JOIN_BTN_W, rowY + ROW_H - 3, 5, UiRenderer.ACCENT);
            UiRenderer.centeredText(graphics, "Join", joinX + JOIN_BTN_W / 2, rowY + ROW_H / 2 - 4, 0xFFFFFFFF);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        int px = panelX();
        int rowY = listY() - (int) scroll;
        for (Map.Entry<UUID, String> entry : rows) {
            if (rowY + ROW_H >= listY() && rowY <= listBottom()) {
                int removeX = px + PANEL_W - PAD - REMOVE_BTN_W;
                if (inBounds(event, removeX, rowY, REMOVE_BTN_W, ROW_H)) {
                    WorldHostManager.removeFriend(entry.getKey());
                    return true;
                }
                if (WorldHostManager.isFriendOnline(entry.getKey())) {
                    int joinX = removeX - JOIN_BTN_W - 6;
                    if (inBounds(event, joinX, rowY, JOIN_BTN_W, ROW_H)) {
                        WorldHostManager.connectToFriend(entry.getKey());
                        return true;
                    }
                }
            }
            rowY += ROW_H + ROW_GAP;
        }
        return super.mouseClicked(event, doubled);
    }

    private boolean inBounds(MouseButtonEvent event, int x, int y, int w, int h) {
        return event.x() >= x && event.x() <= x + w && event.y() >= y && event.y() <= y + h;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int contentH = rows.size() * (ROW_H + ROW_GAP);
        int visibleH = listBottom() - listY();
        double maxScroll = Math.max(0, contentH - visibleH);
        scroll = Math.max(0, Math.min(maxScroll, scroll - scrollY * SCROLL_STEP));
        return true;
    }

    @Override
    public void onClose() {
        // If we left the world (player disconnected/failed to join) while this screen was
        // open, returnTo may be a stale PauseScreen whose init() reads minecraft.player -
        // which is now null, causing a NullPointerException crash. Fall back to the title
        // screen in that case instead of trusting the cached returnTo.
        if (minecraft.player == null) {
            minecraft.gui.setScreen(new net.minecraft.client.gui.screens.TitleScreen());
        } else {
            minecraft.gui.setScreen(returnTo);
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
}
