package willow.train.hexie.item;

import com.simibubi.create.AllEntityTypes;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import willow.train.hexie.compat.ItemData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import willow.train.hexie.menu.AnimatableBlockControllerMenu;
import willow.train.hexie.init.item.ResourceItemInit;
import willow.train.hexie.interaction.InteractionProcessor;
import willow.train.hexie.interaction.base.ICustomInteraction;
import willow.train.hexie.interaction.base.ICustomInteractionGroup;
import willow.train.hexie.util.ByteBufUtil;
import willow.train.hexie.client.util.ComponentTranslationTool;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AnimatableBlockControllerItem extends Item implements MenuProvider {

    public AnimatableBlockControllerItem(Properties pProperties) {
        super(pProperties);
    }

    @Override
    public ItemStack getDefaultInstance() {
        ItemStack stack = super.getDefaultInstance();
        CompoundTag tag = ItemData.getOrCreateTagElement(stack, "control_elements");
        InteractionProcessor.toFullTag(tag);
        ItemData.setTagElement(stack, "control_elements", tag);
        ItemData.updateTagElement(stack, "recent", data -> data.putInt("number", 0));
        ItemData.updateTagElement(stack, "recent", data -> data.putInt("max", getMaxNumbers(stack)));
        return stack;
    }

    @Override
    public void onCraftedBy(ItemStack stack, Level pLevel, Player pPlayer) {
        super.onCraftedBy(stack, pLevel, pPlayer);
        CompoundTag tag = ItemData.getOrCreateTagElement(stack, "control_elements");
        InteractionProcessor.toFullTag(tag);
        ItemData.setTagElement(stack, "control_elements", tag);
        ItemData.updateTagElement(stack, "recent", data -> data.putInt("number", 0));
        ItemData.updateTagElement(stack, "recent", data -> data.putInt("max", getMaxNumbers(stack)));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable TooltipContext level, List<Component> components, TooltipFlag pIsAdvanced) {
        super.appendHoverText(stack, level, components, pIsAdvanced);
        if(Screen.hasShiftDown()) {
            components.add(Component.translatable("tooltip.hexie.abc.binding.explain.bind_mode.title").withStyle(ChatFormatting.GOLD));
            components.add(Component.translatable("tooltip.hexie.abc.binding.explain.bind_mode.text_1").
                    //append(Component.literal(String.valueOf(HXConfig.getCachedMaxAbcControlDistance())).withStyle(ChatFormatting.GREEN)).
                    append(Component.translatable("tooltip.hexie.abc.binding.explain.bind_mode.text_2")));
            components.add(Component.translatable("tooltip.hexie.abc.binding.explain.use_mode.title").withStyle(ChatFormatting.GOLD));
            components.add(Component.translatable("tooltip.hexie.abc.binding.explain.use_mode.text"));
            components.add(Component.translatable("tooltip.hexie.abc.binding.explain.caution").withStyle(ChatFormatting.RED));
        } else {
            components.add(ComponentTranslationTool.HELP_WITH_LSHIFT_COMPONENT);
            CompoundTag tag = ItemData.getOrCreateTagElement(stack, "control_elements");
            int controllingNumber = ItemData.getOrCreateTagElement(stack, "recent").getInt("number");

            components.add(Component.translatable("tooltip.hexie.abc.binding.state")
                    .append(hasId(stack) ? Component.translatable("tooltip.hexie.abc.binding.tr").withStyle(ChatFormatting.GREEN) :
                            Component.translatable("tooltip.hexie.abc.binding.un").withStyle(ChatFormatting.RED)));
            int counter = 0;
            components.add(Component.translatable("tooltip.hexie.abc.title"));
            for(String s : tag.getAllKeys()) {
                ICustomInteractionGroup group = InteractionProcessor.INTERACTIONS.get(s);
                components.add((Component.literal("-> ").append(group.descriptionId().copy())).withStyle(ChatFormatting.GRAY));
                for(String key : group.interactions().keySet()) {
                    components.add((Component.literal("--> ").append(group.getInteraction(key).descriptionKey().copy())).withStyle(counter == controllingNumber ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY));
                    counter++;
                }
            }
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level pLevel, Player pPlayer, InteractionHand pUsedHand) {
        if(pPlayer.getItemInHand(pUsedHand).getItem() instanceof AnimatableBlockControllerItem) {
            ItemStack stack = pPlayer.getItemInHand(pUsedHand);
            if (pPlayer.isShiftKeyDown()) {
                if(!pLevel.isClientSide)
                    ((ServerPlayer) pPlayer).openMenu( this);
            } else {
                if(hasId(stack)) {
                    boolean successed = onUseItem(pLevel, pPlayer, pUsedHand, stack);
                    if(successed)
                        ComponentTranslationTool.showSuccess(pPlayer, true, Component.translatable("tooltip.hexie.abc.binding.use.success"));
                    else
                        ComponentTranslationTool.showError(pPlayer, true, Component.translatable("tooltip.hexie.abc.binding.use.fail"));
                }
            }
        }
        return super.use(pLevel, pPlayer, pUsedHand);
    }

    public static String[] getControllingGroupAndKey(ItemStack stack) {
        if(!(stack.getItem() instanceof AnimatableBlockControllerItem)) return new String[]{};
        CompoundTag itemTag = ItemData.getTag(stack);
        int recentNumber = itemTag.getCompound("recent").getInt("number");
        CompoundTag controlElements = itemTag.getCompound("control_elements");
        int counter = 0;
        for(String s : controlElements.getAllKeys()) {
            String[] values = controlElements.getString(s).split(",");
            for(String value : values) {
                if(counter == recentNumber)
                    return new String[]{s, value.replace(" ", "")};
                counter++;
            }
        }
        return new String[]{};
    }

    public static boolean hasId(ItemStack stack) {
        return ItemData.getOrCreateTagElement(stack, "coupling").contains("contents");
    }

    public static boolean isCouplingValid(Level level, BlockPos playerPos, ItemStack stack) {
        return ItemData.getOrCreateTagElement(stack, "coupling").getCompound("contents").contains("basic");
    }

    public static boolean onUseItem(Level level, Player player, InteractionHand hand, ItemStack stack) {
        BlockPos playerPos = player.getOnPos();
        if(!isCouplingValid(level, playerPos, stack)) return false;
        CompoundTag nbt = ItemData.getOrCreateTagElement(stack, "coupling").getCompound("contents");
        CompoundTag basicTag = nbt.getCompound("basic");
        boolean isTrain = basicTag.getBoolean("train");
        UUID entityId = basicTag.getUUID("id");
        String[] groupAndKey = AnimatableBlockControllerItem.getControllingGroupAndKey(stack);
        if (groupAndKey.length != 2) return false;
        ICustomInteraction interaction = InteractionProcessor.getInteraction(groupAndKey[0], groupAndKey[1]);
        if(interaction == null) return false;

        Map<UUID, Entity> entitiesById = new HashMap<>();
        for (Entity entity : getEntities(level, player.getPosition(0), 64,
                AllEntityTypes.CARRIAGE_CONTRAPTION.get(),
                AllEntityTypes.CONTROLLED_CONTRAPTION.get(),
                AllEntityTypes.GANTRY_CONTRAPTION.get(),
                AllEntityTypes.ORIENTED_CONTRAPTION.get())) {
            entitiesById.putIfAbsent(entity.getUUID(), entity);
        }
        UUID trainId = isTrain ? basicTag.getUUID("uuid") : null;
        for(String key : nbt.getAllKeys()) {
            if(key.equals("basic")) continue;
            CompoundTag subTag = nbt.getCompound(key);
            BlockPos localPos = new BlockPos(subTag.getInt("px"), subTag.getInt("py"), subTag.getInt("pz"));
            if(isTrain) {
                entityId = subTag.getUUID("entity");
                Entity entity = entitiesById.get(entityId);
                if (!(entity instanceof CarriageContraptionEntity carriage)
                        || !trainId.equals(carriage.trainId)) continue;
                onContraptionLocating(carriage, localPos, player, hand, interaction);
            } else {
                Entity entity = entitiesById.get(entityId);
                if (!(entity instanceof AbstractContraptionEntity contraptionEntity)) continue;
                onContraptionLocating(contraptionEntity, localPos, player, hand, interaction);
            }
        }
        return true;
    }

    private static boolean onContraptionLocating(AbstractContraptionEntity entity, BlockPos localPos, Player player, InteractionHand hand, ICustomInteraction interaction) {
        Contraption contraption = entity.getContraption();
        if (contraption == null) return false;
        StructureTemplate.StructureBlockInfo info = contraption.getBlocks().get(localPos);
        if (info == null) return false;
        BlockEntity be = contraption.getBlockEntityClientSide(localPos);
        if(interaction.canActive(entity.level(), player, hand, entity, localPos, info.state(), be)) {
            entity.handlePlayerInteraction(player, localPos, Direction.DOWN, hand);
            return true;
        }
        return false;
    }

    public static List<Entity> getEntities(Level level, Vec3 centrePos, int radius, EntityType<?>... types) {
        if (types.length == 0) return List.of();
        return level.getEntities((Entity) null, AABB.ofSize(centrePos, 2 * radius, 2 * radius, 2 * radius),
                entity -> {
                    for (EntityType<?> type : types)
                        if (entity.getType() == type) return true;
                    return false;
                });
    }

    public static void loadCoupling(ItemStack controller, ItemStack coupling) {
        if(coupling.is(Items.AIR)) {
            ItemData.removeTagKey(controller, "coupling");
        }
        if(!coupling.is(ResourceItemInit.AutomaticControlCoupling.get())) return;
        CompoundTag tag = ItemData.getOrCreateTagElement(coupling, "contents");
        if(tag.isEmpty()) return;
        ItemData.updateTagElement(controller, "coupling", data -> data.put("contents", tag));
    }

    public static ItemStack generateCoupling(CompoundTag tag) {
        CompoundTag t = tag.getCompound("coupling").getCompound("contents");
        if(t.isEmpty()) return Items.AIR.getDefaultInstance();
        ItemStack coupling = ResourceItemInit.AutomaticControlCoupling.get().getDefaultInstance();
        CompoundTag nbt = ItemData.getTag(coupling);
        nbt.put("contents", t);
        ItemData.setTag(coupling, nbt);
        return coupling;
    }

    public static void setNumber(ItemStack stack, int number) {
        if(!(stack.getItem() instanceof AnimatableBlockControllerItem)) return;
        ItemData.updateTagElement(stack, "recent", data -> data.putInt("number", number));
    }

    private int getMaxNumbers(ItemStack stack) {
        int result = 0;
        for(String s : ItemData.getOrCreateTagElement(stack, "control_elements").getAllKeys()) {
            String value = ItemData.getOrCreateTagElement(stack, "control_elements").getString(s);
            result += value.split(",").length;
        }
        return result;
    }

    @Override
    public Component getDisplayName() {
        return Component.empty();
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int pContainerId, Inventory pPlayerInventory, Player pPlayer) {
        return new AnimatableBlockControllerMenu(pContainerId, pPlayerInventory, ByteBufUtil.defaultBuf());
    }
}
