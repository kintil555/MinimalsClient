package com.minimals.client.worldhost.gui;

import com.minimals.client.worldhost.WorldHostManager;
import com.minimals.client.worldhost.WorldHostManager.Mode;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.WorldOptionsScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.HttpUtil;
import net.minecraft.world.level.GameType;
import org.jspecify.annotations.Nullable;

/**
 * Vanilla-style replacement for the "Open to LAN" screen. Same controls as vanilla
 * (port, game mode and commands for other players) plus two buttons at the top to choose
 * how the world is opened:
 * <ul>
 *   <li>LAN - vanilla LAN only.</li>
 *   <li>Multiplayer - LAN plus World Host: friends from the vanilla Friends list get a
 *       Join button and connect through the relay (upgraded to e4mc).</li>
 * </ul>
 * Both modes publish the integrated server with the vanilla LAN scope (26.2 has no other
 * scope); the mode only decides whether the world is announced to friends.
 */
public class OpenWorldScreen extends Screen {
    private static final int PORT_LOWER_BOUND = 1024;
    private static final int PORT_HIGHER_BOUND = 65535;
    private static final int ROW_WIDTH = 308;
    private static final Component TITLE = Component.translatable("options.multiplayer.title");
    private static final Component ALLOW_COMMANDS_LABEL = Component.translatable("selectWorld.allowCommands");
    private static final Component GAME_MODE_LABEL = Component.translatable("selectWorld.gameMode");
    private static final Component PORT_INFO_TEXT = Component.translatable("lanServer.port");
    private static final Component PORT_UNAVAILABLE = Component.translatable("lanServer.port.unavailable", PORT_LOWER_BOUND, PORT_HIGHER_BOUND);
    private static final Component INVALID_PORT = Component.translatable("lanServer.port.invalid", PORT_LOWER_BOUND, PORT_HIGHER_BOUND);
    private static final Component OTHER_PLAYERS_HEADER = Component.translatable("menu.multiplayerOptions.otherPlayers.header")
            .withStyle(ChatFormatting.UNDERLINE, ChatFormatting.BOLD);
    private static final Component APPLY_CHANGES = Component.translatable("menu.multiplayerOptions.applyChanges");
    private static final Component OPEN_LAN = Component.literal("Open LAN");
    private static final Component OPEN_MULTIPLAYER = Component.literal("Open Multiplayer");
    private static final Component CLOSE_WORLD = Component.literal("Close World");
    private static final Component LAN_INFO = Component.literal("Only players on your local network can join.");
    private static final Component MULTIPLAYER_INFO = Component.literal("Friends can join from their Friends list.");
    private static final Component MULTIPLAYER_CONNECTING = Component.literal("Connecting to World Host...");
    private static final Identifier INWORLD_MENU_LIST_BACKGROUND = Identifier.withDefaultNamespace("textures/gui/inworld_menu_list_background.png");

    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    private final Screen lastScreen;

    private Mode mode = Mode.LAN;
    private Mode initialMode = Mode.LAN;
    private boolean initiallyOpen;
    private GameType gameMode = GameType.SURVIVAL;
    private GameType initialGameMode = GameType.SURVIVAL;
    private boolean commands;
    private boolean initialCommands;
    private int port = HttpUtil.getAvailablePort();
    private int initialPort;
    private boolean portValid = true;

    @Nullable private Button lanButton;
    @Nullable private Button multiplayerButton;
    @Nullable private Button applyButton;
    @Nullable private EditBox portEdit;
    @Nullable private StringWidget infoSlot;

    public OpenWorldScreen(final Screen lastScreen) {
        super(TITLE);
        this.lastScreen = lastScreen;
    }

    @Override
    protected void init() {
        IntegratedServer server = this.minecraft.getSingleplayerServer();
        if (server == null) {
            this.onClose();
            return;
        }

        this.layout.addTitleHeader(this.title, this.font);
        LinearLayout content = this.layout.addToContents(LinearLayout.vertical().spacing(8));
        content.defaultCellSetting().alignHorizontallyCenter();

        this.initiallyOpen = server.getMultiplayerScope() == MinecraftServer.MultiplayerScope.LAN;
        this.initialMode = WorldHostManager.isMultiplayerActive() ? Mode.MULTIPLAYER : Mode.LAN;
        this.mode = this.initialMode;
        if (this.initiallyOpen) {
            this.port = server.getPort();
            this.initialPort = this.port;
        }

        // --- mode buttons: click one to pick how the world is opened
        LinearLayout modeRow = content.addChild(LinearLayout.horizontal().spacing(8));
        this.lanButton = modeRow.addChild(Button.builder(Component.literal("LAN"), b -> selectMode(Mode.LAN))
                .width(150).tooltip(Tooltip.create(MinecraftServer.MultiplayerScope.LAN.getTooltip())).build());
        this.multiplayerButton = modeRow.addChild(Button.builder(Component.literal("Multiplayer"), b -> selectMode(Mode.MULTIPLAYER))
                .width(150).tooltip(Tooltip.create(Component.literal("Friends on your Friends list get a Join button and connect through the built-in e4mc relay.")))
                .build());
        this.infoSlot = content.addChild(new StringWidget(ROW_WIDTH, this.font.lineHeight, Component.empty(), this.font));

        // --- port (same as vanilla)
        this.portEdit = new EditBox(this.font, PORT_INFO_TEXT);
        this.portEdit.setResponder(value -> {
            this.setPortError(this.tryParsePort(value));
            this.portEdit.setHint(Component.literal(String.valueOf(this.port)));
            this.updateApplyState();
        });
        if (this.initiallyOpen) {
            this.portEdit.setValue(String.valueOf(this.port));
        }
        this.portEdit.setHint(Component.literal(String.valueOf(this.port)));
        LinearLayout portRow = LinearLayout.vertical().spacing(4);
        portRow.addChild(new StringWidget(PORT_INFO_TEXT, this.font));
        portRow.addChild(this.portEdit);
        content.addChild(portRow);

        // --- other players (same as vanilla)
        content.addChild(new StringWidget(OTHER_PLAYERS_HEADER, this.font));
        LinearLayout otherPlayers = content.addChild(LinearLayout.horizontal().spacing(8));
        otherPlayers.defaultCellSetting().alignHorizontallyCenter();
        this.gameMode = server.getGameTypeForOtherPlayers();
        this.initialGameMode = this.gameMode;
        CycleButton<GameType> gameModeButton = otherPlayers.addChild(
                CycleButton.builder(GameType::getShortDisplayName, this.gameMode)
                        .withValues(GameType.SURVIVAL, GameType.SPECTATOR, GameType.CREATIVE, GameType.ADVENTURE)
                        .create(GAME_MODE_LABEL, (button, value) -> {
                            this.gameMode = value;
                            this.updateApplyState();
                        }));
        this.commands = server.commandsAllowedForOtherPlayers();
        this.initialCommands = this.commands;
        CycleButton<Boolean> commandsButton = otherPlayers.addChild(
                CycleButton.onOffBuilder(this.commands).create(ALLOW_COMMANDS_LABEL, (button, value) -> {
                    this.commands = value;
                    this.updateApplyState();
                }));
        if (server.isHardcore()) {
            gameModeButton.active = false;
            gameModeButton.setTooltip(WorldOptionsScreen.GAME_MODE_DISABLED_HARDCORE_TOOLTIP);
            commandsButton.active = false;
            commandsButton.setTooltip(WorldOptionsScreen.ALLOW_COMMANDS_DISABLED_TOOLTIP);
        }

        if (this.initiallyOpen) {
            content.addChild(Button.builder(CLOSE_WORLD, b -> closeWorld(server)).width(150).build());
        }

        // --- footer: open / apply + cancel
        LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
        this.applyButton = footer.addChild(Button.builder(APPLY_CHANGES, b -> apply(server)).build());
        footer.addChild(Button.builder(CommonComponents.GUI_CANCEL, b -> this.onClose()).build());

        this.layout.visitWidgets(this::addRenderableWidget);
        this.refreshModeWidgets();
        this.repositionElements();
    }

    // --- state ----------------------------------------------------------------

    private void selectMode(final Mode newMode) {
        this.mode = newMode;
        this.refreshModeWidgets();
    }

    /** Marks the selected mode button, updates the info line and the apply button. */
    private void refreshModeWidgets() {
        if (this.lanButton != null) {
            this.lanButton.active = this.mode != Mode.LAN;
            this.lanButton.setMessage(Component.literal(this.mode == Mode.LAN ? "> LAN <" : "LAN"));
        }
        if (this.multiplayerButton != null) {
            this.multiplayerButton.active = this.mode != Mode.MULTIPLAYER;
            this.multiplayerButton.setMessage(Component.literal(this.mode == Mode.MULTIPLAYER ? "> Multiplayer <" : "Multiplayer"));
        }
        this.updateApplyState();
    }

    private Component infoText() {
        if (this.mode == Mode.LAN) return LAN_INFO;
        return WorldHostManager.isConnected() ? MULTIPLAYER_INFO : MULTIPLAYER_CONNECTING;
    }

    private void updateApplyState() {
        if (this.applyButton == null) return;
        if (this.initiallyOpen) {
            this.applyButton.setMessage(APPLY_CHANGES);
        } else {
            this.applyButton.setMessage(this.mode == Mode.MULTIPLAYER ? OPEN_MULTIPLAYER : OPEN_LAN);
        }
        boolean relayOk = this.mode != Mode.MULTIPLAYER || WorldHostManager.isConnected();
        this.applyButton.active = this.portValid && relayOk && (!this.initiallyOpen || this.hasSettingsChanges());
    }

    private boolean portChanged() {
        return this.initiallyOpen && this.port != this.initialPort;
    }

    private boolean hasSettingsChanges() {
        return this.mode != this.initialMode
                || this.gameMode != this.initialGameMode
                || this.commands != this.initialCommands
                || this.portChanged();
    }

    @Override
    public void tick() {
        super.tick();
        this.updateApplyState();
    }

    // --- actions --------------------------------------------------------------

    private void apply(final IntegratedServer server) {
        this.minecraft.gui.setScreen(null);
        if (this.gameMode != this.initialGameMode) {
            server.setGameTypeForOtherPlayers(this.gameMode);
        }
        if (this.commands != this.initialCommands) {
            server.setCommandsAllowedForOtherPlayers(this.commands);
        }
        if (!this.initiallyOpen || this.mode != this.initialMode || this.portChanged()) {
            this.changeWorldSharing(server);
        }
    }

    private void closeWorld(final IntegratedServer server) {
        this.minecraft.gui.setScreen(null);
        if (server.unpublishServer()) {
            this.sendPublishMessage(Component.translatable("menu.multiplayerOptions.publish.stopped"));
        }
        this.minecraft.getPlayerSocialManager().getPresenceHandler().tryUpdatePresence();
    }

    private void changeWorldSharing(final IntegratedServer server) {
        if (server.unpublishServer()) {
            this.sendPublishMessage(Component.translatable("menu.multiplayerOptions.publish.stopped"));
        }
        // WorldHostPublishMixin -> WorldHostManager.onWorldPublished() reads this.
        WorldHostManager.setPendingMode(this.mode);
        boolean published = server.publishServer(MinecraftServer.MultiplayerScope.LAN, this.port);
        if (!published) {
            WorldHostManager.setPendingMode(Mode.LAN);
            this.sendPublishMessage(Component.translatable("commands.publish.failed"));
        } else if (this.mode == Mode.MULTIPLAYER) {
            this.sendPublishMessage(Component.literal("Your world is open to friends (Multiplayer)."));
        } else {
            this.sendPublishMessage(Component.translatable("menu.multiplayerOptions.publish.started.lan",
                    ComponentUtils.copyOnClickText(String.valueOf(this.port))));
        }
        this.minecraft.getPlayerSocialManager().getPresenceHandler().tryUpdatePresence();
    }

    private void sendPublishMessage(final Component message) {
        this.minecraft.gui.hud.getChat().addClientSystemMessage(message);
        this.minecraft.getNarrator().saySystemQueued(message);
        this.minecraft.updateTitle();
    }

    // --- port validation (copied from vanilla) ----------------------------------

    private void setPortError(@Nullable final Component errorMessage) {
        if (this.portEdit == null) return;
        this.portValid = errorMessage == null;
        if (errorMessage == null) {
            this.portEdit.setTextColor(-2039584);
            this.portEdit.setTooltip(null);
        } else {
            this.portEdit.setTextColor(-2142128);
            this.portEdit.setTooltip(Tooltip.create(errorMessage));
        }
    }

    @Nullable
    private Component tryParsePort(final String value) {
        if (value.isBlank()) {
            this.port = HttpUtil.getAvailablePort();
            return null;
        }
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < PORT_LOWER_BOUND || parsed > PORT_HIGHER_BOUND) {
                return INVALID_PORT;
            } else if (parsed != this.initialPort && !HttpUtil.isPortAvailable(parsed)) {
                return PORT_UNAVAILABLE;
            } else {
                this.port = parsed;
                return null;
            }
        } catch (NumberFormatException e) {
            this.port = HttpUtil.getAvailablePort();
            return INVALID_PORT;
        }
    }

    // --- screen plumbing ----------------------------------------------------------

    @Override
    protected void repositionElements() {
        this.layout.arrangeElements();
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.lastScreen);
    }

    @Override
    public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        if (this.infoSlot != null) {
            Component info = this.infoText();
            boolean warning = this.mode == Mode.MULTIPLAYER && !WorldHostManager.isConnected();
            graphics.centeredText(this.font, info, this.infoSlot.getX() + this.infoSlot.getWidth() / 2,
                    this.infoSlot.getY(), warning ? 0xFFFFAA00 : 0xFFA0A0A0);
        }
    }

    @Override
    public void extractBackground(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        Identifier headerSeparator = this.minecraft.level == null ? Screen.HEADER_SEPARATOR : Screen.INWORLD_HEADER_SEPARATOR;
        Identifier footerSeparator = this.minecraft.level == null ? Screen.FOOTER_SEPARATOR : Screen.INWORLD_FOOTER_SEPARATOR;
        graphics.blit(RenderPipelines.GUI_TEXTURED, headerSeparator, 0, this.layout.getHeaderHeight() - 2, 0.0F, 0.0F, this.width, 2, 32, 2);
        graphics.blit(RenderPipelines.GUI_TEXTURED, footerSeparator, 0, this.height - this.layout.getFooterHeight() - 2, 0.0F, 0.0F, this.width, 2, 32, 2);
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                INWORLD_MENU_LIST_BACKGROUND,
                0,
                this.layout.getHeaderHeight(),
                this.width,
                this.height - this.layout.getFooterHeight(),
                this.width,
                this.layout.getContentHeight() - 2,
                32,
                32
        );
    }
}
