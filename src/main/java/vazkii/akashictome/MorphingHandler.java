package vazkii.akashictome;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import vazkii.akashictome.network.NetworkHandler;
import vazkii.akashictome.network.message.MessageUnmorphTome;
import vazkii.akashictome.utils.ItemNBTHelper;

public final class MorphingHandler {

    public static final MorphingHandler INSTANCE = new MorphingHandler();

    public static final String MINECRAFT = "minecraft";

    public static final String TAG_MORPHING = "akashictome:is_morphing";
    public static final String TAG_TOME_DATA = "akashictome:data";
    public static final String TAG_TOME_DISPLAY_NAME = "akashictome:displayName";
    public static final String TAG_ITEM_DEFINED_MOD = "akashictome:definedMod";

    @SubscribeEvent
    public void onPlayerLeftClick(PlayerInteractEvent event) {
        if (event.action == PlayerInteractEvent.Action.LEFT_CLICK_BLOCK && event.entityPlayer.isSneaking()) {
            ItemStack stack = event.entityPlayer.getHeldItem();
            if (stack != null && isAkashicTome(stack) && stack.getItem() != ModItems.tome) {
                NetworkHandler.INSTANCE.sendToServer(new MessageUnmorphTome());
            }
        }
    }

    @SubscribeEvent
    public void onItemDropped(ItemTossEvent event) {
        if (!event.player.isSneaking()) return;

        EntityItem e = event.entityItem;
        ItemStack stack = e.getEntityItem();
        if (stack != null && isAkashicTome(stack) && stack.getItem() != ModItems.tome) {
            NBTTagCompound morphData = (NBTTagCompound) stack.getTagCompound().getCompoundTag(TAG_TOME_DATA).copy();
            String currentMod = ItemNBTHelper.getString(stack, TAG_ITEM_DEFINED_MOD, getModFromStack(stack));

            ItemStack morph = makeMorphedStack(stack, MINECRAFT, morphData);
            NBTTagCompound newMorphData = morph.getTagCompound().getCompoundTag(TAG_TOME_DATA);
            newMorphData.removeTag(currentMod);

            if (!e.worldObj.isRemote) {
                EntityItem newItem = new EntityItem(e.worldObj, e.posX, e.posY, e.posZ, morph);
                e.worldObj.spawnEntityInWorld(newItem);
            }

            ItemStack copy = stack.copy();
            NBTTagCompound copyCmp = copy.getTagCompound();
            if (copyCmp == null) {
                copyCmp = new NBTTagCompound();
                copy.setTagCompound(copyCmp);
            }

            copyCmp.removeTag("display");
            String displayName = copyCmp.getString(TAG_TOME_DISPLAY_NAME);
            if (!displayName.isEmpty() && !displayName.equals(copy.getDisplayName()))
                copy.setStackDisplayName(displayName);

            copyCmp.removeTag(TAG_MORPHING);
            copyCmp.removeTag(TAG_TOME_DISPLAY_NAME);
            copyCmp.removeTag(TAG_TOME_DATA);

            e.setEntityItemStack(copy);
        }
    }

    public static String getModFromBlock(Block state) {
        return getModOrAlias(state.getUnlocalizedName());
    }

    public static String getModFromStack(ItemStack stack) {
        return getModOrAlias(stack == null ? MINECRAFT : stack.getItem().getUnlocalizedName(stack));
    }

    public static String getModOrAlias(String mod) {
        return ConfigHandler.aliases.containsKey(mod) ? ConfigHandler.aliases.get(mod) : mod;
    }

    public static boolean doesStackHaveModAttached(ItemStack stack, String mod) {
        if (!stack.hasTagCompound()) return false;

        NBTTagCompound morphData = stack.getTagCompound().getCompoundTag(TAG_TOME_DATA);
        return morphData.hasKey(mod);
    }

    public static ItemStack getShiftStackForMod(ItemStack stack, String mod) {
        if (!stack.hasTagCompound()) return stack;

        String currentMod = getModFromStack(stack);
        if (mod.equals(currentMod)) return stack;

        NBTTagCompound morphData = stack.getTagCompound().getCompoundTag(TAG_TOME_DATA);
        return makeMorphedStack(stack, mod, morphData);
    }

    public static ItemStack makeMorphedStack(ItemStack currentStack, String targetMod, NBTTagCompound morphData) {
        String currentMod = ItemNBTHelper.getString(currentStack, TAG_ITEM_DEFINED_MOD, getModFromStack(currentStack));

        NBTTagCompound currentCmp = new NBTTagCompound();
        currentStack.writeToNBT(currentCmp);
        currentCmp = (NBTTagCompound) currentCmp.copy();
        if (currentCmp.hasKey("tag")) currentCmp.getCompoundTag("tag").removeTag(TAG_TOME_DATA);

        if (!currentMod.equalsIgnoreCase(MINECRAFT) && !currentMod.equalsIgnoreCase(AkashicTome.MOD_ID))
            morphData.setTag(currentMod, currentCmp);

        ItemStack stack;
        if (targetMod.equals(MINECRAFT)) stack = new ItemStack(ModItems.tome);
        else {
            NBTTagCompound targetCmp = morphData.getCompoundTag(targetMod);
            morphData.removeTag(targetMod);

            stack = ItemStack.loadItemStackFromNBT(targetCmp);
            if (stack == null) stack = new ItemStack(ModItems.tome);
        }

        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());

        NBTTagCompound stackCmp = stack.getTagCompound();
        stackCmp.setTag(TAG_TOME_DATA, morphData);
        stackCmp.setBoolean(TAG_MORPHING, true);

        if (stack.getItem() != ModItems.tome) {
            String displayName = stack.getDisplayName();
            if (stackCmp.hasKey(TAG_TOME_DISPLAY_NAME)) displayName = stackCmp.getString(TAG_TOME_DISPLAY_NAME);
            else stackCmp.setString(TAG_TOME_DISPLAY_NAME, displayName);

            stack.setStackDisplayName(
                    EnumChatFormatting.RESET + I18n.format(
                            "akashictome.sudo_name",
                            EnumChatFormatting.GREEN + displayName + EnumChatFormatting.RESET));
        }

        stack.stackSize = 1;
        return stack;
    }

    private static final Map<String, String> modNames = new HashMap<String, String>();

    static {
        for (Map.Entry<String, ModContainer> modEntry : Loader.instance().getIndexedModList().entrySet())
            modNames.put(modEntry.getKey().toLowerCase(Locale.ENGLISH), modEntry.getValue().getName());
    }

    public static String getModNameForId(String modId) {
        modId = modId.toLowerCase(Locale.ENGLISH);
        return modNames.containsKey(modId) ? modNames.get(modId) : modId;
    }

    public static boolean isAkashicTome(ItemStack stack) {
        if (stack == null) return false;

        if (stack.getItem() == ModItems.tome) return true;

        return stack.hasTagCompound() && stack.getTagCompound().getBoolean(TAG_MORPHING);
    }

}
