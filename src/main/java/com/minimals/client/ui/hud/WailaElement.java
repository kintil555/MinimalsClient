package com.minimals.client.ui.hud;

import com.minimals.client.module.ModuleManager;
import com.minimals.client.module.WailaModule;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Optional;

/**
 * WAILA info box for whatever the crosshair is on. Looks like a vanilla tooltip: name in white,
 * detail lines in grey, and the owning mod's name in blue italics at the bottom, with an item
 * icon on the left for blocks.
 *
 * Only shown while the target is inside the player's real block/entity interaction range, i.e.
 * something the player could break or interact with right now.
 */
public class WailaElement extends HudElement {

    // Vanilla tooltip sprites (same ones TooltipRenderUtil uses, so the box is pixel-identical).
    private static final Identifier BACKGROUND_SPRITE = Identifier.withDefaultNamespace("tooltip/background");
    private static final Identifier FRAME_SPRITE = Identifier.withDefaultNamespace("tooltip/frame");
    private static final int FRAME_MARGIN = 9;
    private static final int PADDING = 3;

    private static final Identifier HEART_CONTAINER = Identifier.withDefaultNamespace("hud/heart/container");
    private static final Identifier HEART_FULL = Identifier.withDefaultNamespace("hud/heart/full");
    private static final Identifier HEART_HALF = Identifier.withDefaultNamespace("hud/heart/half");
    private static final Identifier HEART_POISONED_FULL = Identifier.withDefaultNamespace("hud/heart/poisoned_full");
    private static final Identifier HEART_POISONED_HALF = Identifier.withDefaultNamespace("hud/heart/poisoned_half");
    private static final Identifier HEART_WITHERED_FULL = Identifier.withDefaultNamespace("hud/heart/withered_full");
    private static final Identifier HEART_WITHERED_HALF = Identifier.withDefaultNamespace("hud/heart/withered_half");
    private static final Identifier HEART_FROZEN_FULL = Identifier.withDefaultNamespace("hud/heart/frozen_full");
    private static final Identifier HEART_FROZEN_HALF = Identifier.withDefaultNamespace("hud/heart/frozen_half");
    private static final Identifier HEART_ABSORBING_FULL = Identifier.withDefaultNamespace("hud/heart/absorbing_full");
    private static final Identifier HEART_ABSORBING_HALF = Identifier.withDefaultNamespace("hud/heart/absorbing_half");

    private static final int HEART_SIZE = 9;
    /** Vanilla heart spacing: hearts overlap by one pixel. */
    private static final int HEART_STEP = 8;
    /** Above this many heart containers the bar becomes text ("120 / 200") instead of a wall of hearts. */
    private static final int MAX_HEART_ICONS = 20;
    private static final int HEARTS_PER_ROW = 10;
    private static final int ROW_HEIGHT = 10;

    private static final int EFFECT_ICON = 9;
    private static final int ICON_SIZE = 16;
    private static final int ICON_GAP = 5;

    private static final int COLOR_MOD = 0xFF5555FF;
    private static final int COLOR_HARMFUL = 0xFFFF5555;
    private static final int COLOR_BENEFICIAL = 0xFF55FF55;
    private static final int COLOR_NEUTRAL = 0xFFAAAAAA;

    /** One row of the box. Text rows carry a component; the health row is drawn from hearts. */
    private sealed interface Line permits TextLine, HealthLine, EffectLine {
        int width(Font font);

        int height(Font font);
    }

    private record TextLine(Component text, int color) implements Line {
        @Override
        public int width(Font font) {
            return font.width(text);
        }

        @Override
        public int height(Font font) {
            return font.lineHeight + 1;
        }
    }

    private record HealthLine(float health, float maxHealth, float absorption, HeartStyle style) implements Line {
        private boolean asText() {
            return Mth.ceil(maxHealth / 2f) > MAX_HEART_ICONS;
        }

        private int iconCount() {
            return Mth.ceil(maxHealth / 2f) + Mth.ceil(absorption / 2f);
        }

        @Override
        public int width(Font font) {
            if (asText()) {
                return font.width(textValue());
            }
            int perRow = Math.min(HEARTS_PER_ROW, iconCount());
            return (perRow - 1) * HEART_STEP + HEART_SIZE;
        }

        @Override
        public int height(Font font) {
            if (asText()) {
                return font.lineHeight + 1;
            }
            int rows = Mth.positiveCeilDiv(iconCount(), HEARTS_PER_ROW);
            return (rows - 1) * ROW_HEIGHT + HEART_SIZE + 1;
        }

        private String textValue() {
            String base = formatHealth(health) + " / " + formatHealth(maxHealth);
            return absorption > 0 ? base + " +" + formatHealth(absorption) : base;
        }
    }

    private record EffectLine(MobEffectInstance instance, Component label) implements Line {
        @Override
        public int width(Font font) {
            return EFFECT_ICON + 3 + font.width(label);
        }

        @Override
        public int height(Font font) {
            return Math.max(EFFECT_ICON, font.lineHeight) + 1;
        }
    }

    private enum HeartStyle {
        NORMAL(HEART_FULL, HEART_HALF),
        POISONED(HEART_POISONED_FULL, HEART_POISONED_HALF),
        WITHERED(HEART_WITHERED_FULL, HEART_WITHERED_HALF),
        FROZEN(HEART_FROZEN_FULL, HEART_FROZEN_HALF);

        private final Identifier full;
        private final Identifier half;

        HeartStyle(Identifier full, Identifier half) {
            this.full = full;
            this.half = half;
        }
    }

    /** What the crosshair is on, snapshotted once per frame so layout and drawing agree. */
    private record Snapshot(ItemStack icon, List<Line> lines, int contentWidth, int contentHeight) {
    }

    private Snapshot last;

    public WailaElement() {
        super("waila", "WAILA", 0.5f, 0.02f);
    }

    private static int titleColor() {
        return module().titleColor.get();
    }

    private static int infoColor() {
        return module().infoColor.get();
    }

    private static WailaModule module() {
        return ModuleManager.waila();
    }

    @Override
    public boolean isActive() {
        if (!module().isEnabled()) {
            return false;
        }
        // In the HUD editor always draw the preview box so it can be positioned without
        // having to aim at something.
        if (Minecraft.getInstance().gui.screen() instanceof HudEditorScreen) {
            buildSnapshot();
            return true;
        }
        return buildSnapshot() != null;
    }

    /** What is (or would be) drawn right now: the live target, or the sample in the editor. */
    private Snapshot displaySnapshot() {
        boolean inEditor = Minecraft.getInstance().gui.screen() instanceof HudEditorScreen;
        return (inEditor || last == null) ? sampleSnapshot() : last;
    }

    @Override
    public int getWidth() {
        return boxWidth(displaySnapshot());
    }

    @Override
    public int getHeight() {
        return boxHeight(displaySnapshot());
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, int x, int y) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        // isActive() already rebuilt the snapshot this frame in the normal HUD, so reuse it
        // instead of walking the target twice. In the editor (where a dragged element skips
        // isActive) always show the sample, never a stale cached target.
        Snapshot snap = mc.gui.screen() instanceof HudEditorScreen ? null : last;
        if (snap == null) {
            drawPlaceholder(graphics, font, x, y);
            return;
        }

        int boxW = boxWidth(snap);
        int boxH = boxHeight(snap);
        drawFrame(graphics, x, y, boxW, boxH);

        int contentX = x + PADDING;
        int contentY = y + PADDING;
        boolean hasIcon = !snap.icon().isEmpty();
        if (hasIcon) {
            graphics.fakeItem(snap.icon(), contentX, contentY);
            contentX += ICON_SIZE + ICON_GAP;
        }

        int cursorY = contentY;
        for (Line line : snap.lines()) {
            drawLine(graphics, font, line, contentX, cursorY);
            cursorY += line.height(font);
        }
    }

    // ---------------------------------------------------------------- layout

    private static int boxWidth(Snapshot snap) {
        return snap.contentWidth() + PADDING * 2;
    }

    private static int boxHeight(Snapshot snap) {
        return snap.contentHeight() + PADDING * 2;
    }

    /** Vanilla frame: background + frame sprites, padded out by the 9px sprite margin. */
    private static void drawFrame(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
        int x0 = x - FRAME_MARGIN;
        int y0 = y - FRAME_MARGIN;
        int paddedW = w + FRAME_MARGIN * 2;
        int paddedH = h + FRAME_MARGIN * 2;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BACKGROUND_SPRITE, x0, y0, paddedW, paddedH);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, FRAME_SPRITE, x0, y0, paddedW, paddedH);
    }

    /** Sample shown in the HUD editor (mirrors the reference screenshot: name, hearts, career, mod). */
    private static final List<Line> SAMPLE_LINES = List.of(
            new TextLine(Component.literal("Villager"), titleColor()),
            new HealthLine(20f, 20f, 0f, HeartStyle.NORMAL),
            new TextLine(Component.literal("Career: Shepherd"), infoColor()),
            new TextLine(Component.literal("Minecraft").withStyle(ChatFormatting.BLUE, ChatFormatting.ITALIC), COLOR_MOD));

    /** Measured sample, so the editor's drag box matches exactly what drawPlaceholder draws. */
    private static Snapshot sampleSnapshot() {
        return measure(Minecraft.getInstance().font, ItemStack.EMPTY, SAMPLE_LINES);
    }

    private static void drawPlaceholder(GuiGraphicsExtractor graphics, Font font, int x, int y) {
        Snapshot sample = sampleSnapshot();
        drawFrame(graphics, x, y, sample.contentWidth() + PADDING * 2, sample.contentHeight() + PADDING * 2);
        int cursorY = y + PADDING;
        for (Line line : SAMPLE_LINES) {
            drawLine(graphics, font, line, x + PADDING, cursorY);
            cursorY += line.height(font);
        }
    }

    // ---------------------------------------------------------------- drawing

    private static void drawLine(GuiGraphicsExtractor graphics, Font font, Line line, int x, int y) {
        switch (line) {
            case TextLine text -> graphics.text(font, text.text(), x, y, text.color());
            case HealthLine health -> drawHealth(graphics, font, health, x, y);
            case EffectLine effect -> drawEffect(graphics, font, effect, x, y);
        }
    }

    private static void drawHealth(GuiGraphicsExtractor graphics, Font font, HealthLine line, int x, int y) {
        if (line.asText()) {
            graphics.text(font, line.textValue(), x, y, infoColor());
            return;
        }

        int healthContainers = Mth.ceil(line.maxHealth() / 2f);
        int total = line.iconCount();
        int currentHalves = Mth.ceil(line.health());
        int absorptionHalves = Mth.ceil(line.absorption());
        int maxHalves = healthContainers * 2;

        for (int i = 0; i < total; i++) {
            int row = i / HEARTS_PER_ROW;
            int column = i % HEARTS_PER_ROW;
            int hx = x + column * HEART_STEP;
            int hy = y + row * ROW_HEIGHT;
            int halves = i * 2;

            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HEART_CONTAINER, hx, hy, HEART_SIZE, HEART_SIZE);

            if (i >= healthContainers) {
                int extra = halves - maxHalves;
                if (extra < absorptionHalves) {
                    boolean half = extra + 1 == absorptionHalves;
                    graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                            half ? HEART_ABSORBING_HALF : HEART_ABSORBING_FULL, hx, hy, HEART_SIZE, HEART_SIZE);
                }
            } else if (halves < currentHalves) {
                boolean half = halves + 1 == currentHalves;
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                        half ? line.style().half : line.style().full, hx, hy, HEART_SIZE, HEART_SIZE);
            }
        }
    }

    private static void drawEffect(GuiGraphicsExtractor graphics, Font font, EffectLine line, int x, int y) {
        Identifier sprite = Hud.getMobEffectSprite(line.instance().getEffect());
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, EFFECT_ICON, EFFECT_ICON);
        int color = effectColor(line.instance());
        graphics.text(font, line.label(), x + EFFECT_ICON + 3, y + (EFFECT_ICON - font.lineHeight) / 2 + 1, color);
    }

    private static int effectColor(MobEffectInstance instance) {
        return switch (instance.getEffect().value().getCategory()) {
            case BENEFICIAL -> COLOR_BENEFICIAL;
            case HARMFUL -> COLOR_HARMFUL;
            case NEUTRAL -> COLOR_NEUTRAL;
        };
    }

    // ---------------------------------------------------------------- target -> snapshot

    /**
     * Reads the current crosshair target and turns it into rows. Returns null (and clears the
     * cache) when nothing is targeted or it is out of interaction range.
     */
    private Snapshot buildSnapshot() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        HitResult hit = mc.hitResult;
        // While the HUD editor is open the box must not flicker between whatever is behind it;
        // show the fixed placeholder so it is easy to grab and position.
        if (mc.gui.screen() instanceof HudEditorScreen) {
            last = null;
            return null;
        }
        if (player == null || level == null || hit == null) {
            last = null;
            return null;
        }

        Snapshot snap = switch (hit.getType()) {
            case BLOCK -> snapshotBlock(mc, player, level, (BlockHitResult) hit);
            case ENTITY -> snapshotEntity(mc, player, ((EntityHitResult) hit).getEntity());
            case MISS -> null;
        };
        last = snap;
        return snap;
    }

    private static Snapshot snapshotBlock(Minecraft mc, LocalPlayer player, ClientLevel level, BlockHitResult hit) {
        BlockPos pos = hit.getBlockPos();
        if (!player.isWithinBlockInteractionRange(pos, 0.0)) {
            return null;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return null;
        }

        WailaModule module = module();
        Font font = mc.font;
        List<Line> lines = new ArrayList<>();
        lines.add(new TextLine(state.getBlock().getName().copy(), titleColor()));

        if (module.showBlockDetails.get()) {
            float hardness = state.getDestroySpeed(level, pos);
            if (hardness < 0f) {
                lines.add(new TextLine(Component.literal("Unbreakable"), infoColor()));
            } else {
                lines.add(new TextLine(Component.literal("Hardness: " + formatHealth(hardness)), infoColor()));
            }
            if (state.requiresCorrectToolForDrops()) {
                lines.add(new TextLine(Component.literal("Requires correct tool"), infoColor()));
            }
        }

        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        addModLine(lines, module, id);

        ItemStack icon = ItemStack.EMPTY;
        if (module.showIcon.get()) {
            ItemStack clone = state.getCloneItemStack(level, pos, true);
            // isEmpty() already covers Items.AIR, so a block with no item form just gets no icon.
            icon = clone.isEmpty() ? new ItemStack(state.getBlock().asItem()) : clone;
        }
        return measure(font, icon, lines);
    }

    private static Snapshot snapshotEntity(Minecraft mc, LocalPlayer player, Entity entity) {
        if (entity == null || entity.isInvisibleTo(player)
                || !player.isWithinEntityInteractionRange(entity, 0.0)) {
            return null;
        }

        WailaModule module = module();
        Font font = mc.font;
        List<Line> lines = new ArrayList<>();
        lines.add(new TextLine(entityTitle(entity).copy(), titleColor()));

        if (entity instanceof Villager villager) {
            Component career = professionName(villager);
            if (career != null) {
                lines.add(new TextLine(Component.literal("Career: ").append(career), infoColor()));
            }
        }

        if (entity instanceof LivingEntity living) {
            if (module.showHealth.get()) {
                lines.add(new HealthLine(living.getHealth(), living.getMaxHealth(),
                        living.getAbsorptionAmount(), heartStyle(living)));
            }
            if (module.showEffects.get()) {
                addEffects(lines, living, module.maxEffects.get());
            }
        }

        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        addModLine(lines, module, id);

        return measure(font, ItemStack.EMPTY, lines);
    }

    /**
     * Title of the box. Vanilla's Villager#getTypeName returns the profession ("Shepherd"), so
     * getName()/getDisplayName() would show the job as the title and leave the "Career" row
     * redundant. A villager without a custom name is titled by its entity type ("Villager"),
     * like the reference screenshot; everything else keeps its normal display name.
     */
    private static Component entityTitle(Entity entity) {
        if (entity instanceof Villager && !entity.hasCustomName()) {
            return entity.getType().getDescription();
        }
        return entity.getDisplayName();
    }

    /**
     * Career of a villager, or null when it has none. The profession is identified by its
     * registry id ("minecraft:none") rather than Holder#is(ResourceKey), which compares by
     * object identity.
     */
    private static Component professionName(Villager villager) {
        Holder<VillagerProfession> holder = villager.getVillagerData().profession();
        Optional<ResourceKey<VillagerProfession>> key = holder.unwrapKey();
        if (key.isEmpty() || key.get().identifier().getPath().equals("none")) {
            return null;
        }
        return holder.value().name();
    }

    private static HeartStyle heartStyle(LivingEntity living) {
        if (living.hasEffect(MobEffects.POISON)) {
            return HeartStyle.POISONED;
        }
        if (living.hasEffect(MobEffects.WITHER)) {
            return HeartStyle.WITHERED;
        }
        if (living.isFullyFrozen()) {
            return HeartStyle.FROZEN;
        }
        return HeartStyle.NORMAL;
    }

    private static void addEffects(List<Line> lines, LivingEntity living, int max) {
        List<MobEffectInstance> effects = readEffects(living);
        if (effects.isEmpty()) {
            return;
        }
        effects.sort((a, b) -> Integer.compare(b.getAmplifier(), a.getAmplifier()));

        int shown = 0;
        for (MobEffectInstance instance : effects) {
            if (shown >= max) {
                break;
            }
            lines.add(new EffectLine(instance, effectLabel(instance)));
            shown++;
        }
        int hidden = effects.size() - shown;
        if (hidden > 0) {
            lines.add(new TextLine(Component.literal("+" + hidden + " more"), infoColor()));
        }
    }

    /**
     * Active effects of an entity, as a fresh list.
     *
     * A client only ever learns another mob's effects if it is riding that mob: the server sends
     * ClientboundUpdateMobEffectPacket to passengers only (LivingEntity#sendEffectToPassengers),
     * so getActiveEffects() on a mob you merely look at is always empty. In singleplayer the
     * integrated server holds the real entity, so its effects (with level and duration) are
     * read from there. On a remote server nothing better exists client-side, so whatever the
     * client entity has (your own effects, or a mount's) is used.
     */
    private static List<MobEffectInstance> readEffects(LivingEntity living) {
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (server != null && mc.level != null) {
            ServerLevel serverLevel = server.getLevel(mc.level.dimension());
            if (serverLevel != null) {
                Entity real = serverLevel.getEntity(living.getId());
                if (real instanceof LivingEntity serverLiving) {
                    try {
                        return new ArrayList<>(serverLiving.getActiveEffects());
                    } catch (ConcurrentModificationException e) {
                        // The server thread changed the effect map mid-copy; use the client copy this frame.
                    }
                }
            }
        }
        return new ArrayList<>(living.getActiveEffects());
    }

    /** "Speed II (0:30)", same shape as the vanilla inventory effect list. */
    private static Component effectLabel(MobEffectInstance instance) {
        MobEffect effect = instance.getEffect().value();
        Component name = effect.getDisplayName();
        int amplifier = instance.getAmplifier();
        if (amplifier > 0) {
            // Vanilla ships potion.potency.0..5 (I..VI); beyond that fall back to a plain number.
            Component level = amplifier <= 5
                    ? Component.translatable("potion.potency." + amplifier)
                    : Component.literal(Integer.toString(amplifier + 1));
            name = Component.empty().append(name).append(" ").append(level);
        }
        Component duration = MobEffectUtil.formatDuration(instance, 1.0f, 20.0f);
        return Component.empty().append(name).append(" (").append(duration).append(")");
    }

    private static void addModLine(List<Line> lines, WailaModule module, Identifier id) {
        if (!module.showModName.get() || id == null) {
            return;
        }
        Component name = Component.literal(modDisplayName(id.getNamespace()))
                .withStyle(ChatFormatting.BLUE, ChatFormatting.ITALIC);
        lines.add(new TextLine(name, COLOR_MOD));
    }

    private static String modDisplayName(String namespace) {
        if (namespace.equals("minecraft")) {
            return "Minecraft";
        }
        Optional<String> name = FabricLoader.getInstance().getModContainer(namespace)
                .map(container -> container.getMetadata().getName());
        return name.filter(n -> !n.isBlank()).orElse(namespace);
    }

    private static Snapshot measure(Font font, ItemStack icon, List<Line> lines) {
        int textWidth = 0;
        int textHeight = 0;
        for (Line line : lines) {
            textWidth = Math.max(textWidth, line.width(font));
            textHeight += line.height(font);
        }
        boolean hasIcon = !icon.isEmpty();
        int width = textWidth + (hasIcon ? ICON_SIZE + ICON_GAP : 0);
        int height = hasIcon ? Math.max(textHeight, ICON_SIZE) : textHeight;
        return new Snapshot(icon, lines, width, height);
    }

    /** 20 -> "20", 3.5 -> "3.5" (health is stored in half-hearts as a float). */
    private static String formatHealth(float value) {
        if (value == Math.rint(value)) {
            return Integer.toString((int) value);
        }
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
