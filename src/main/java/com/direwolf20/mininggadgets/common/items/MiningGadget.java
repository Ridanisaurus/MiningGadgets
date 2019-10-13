package com.direwolf20.mininggadgets.common.items;

import com.direwolf20.mininggadgets.Setup;
import com.direwolf20.mininggadgets.common.blocks.ModBlocks;
import com.direwolf20.mininggadgets.common.blocks.RenderBlock;
import com.direwolf20.mininggadgets.common.tiles.RenderBlockTileEntity;
import com.direwolf20.mininggadgets.common.util.MiscTools;
import com.direwolf20.mininggadgets.common.util.VectorHelper;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.material.Material;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.UseAction;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.RayTraceContext;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.List;

public class MiningGadget extends Item {
    public MiningGadget() {
        super(new Item.Properties().maxStackSize(1).group(Setup.getItemGroup()));
        setRegistryName("mininggadget");
    }

    private static void setToolRange(ItemStack tool, int range) {
        CompoundNBT tagCompound = MiscTools.getOrNewTag(tool);
        tagCompound.putInt("range", range);
    }

    public static int getToolRange(ItemStack tool) {
        CompoundNBT tagCompound = MiscTools.getOrNewTag(tool);
        int range = tagCompound.getInt("range");
        if (range == 0) {
            setToolRange(tool, 1);
            return 1;
        }
        return tagCompound.getInt("range");
    }

    public static void changeRange(ItemStack tool) {
        if (getToolRange(tool) == 1)
            setToolRange(tool, 3);
        else
            setToolRange(tool, 1);
    }

    public static void setLastBreak(ItemStack tool, long lastBreak) {
        CompoundNBT tagCompound = MiscTools.getOrNewTag(tool);
        tagCompound.putLong("lastBreak", lastBreak);
    }

    public static long getLastBreak(ItemStack tool) {
        CompoundNBT tagCompound = MiscTools.getOrNewTag(tool);
        return tagCompound.getLong("lastBreak");
    }

    public static boolean canMine(ItemStack tool, World world) {
        long lastBreak = getLastBreak(tool);
        if ((world.getGameTime() - lastBreak) < 4) return false;
        return true;
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.NONE;
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return false;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, PlayerEntity player, Hand hand) {
        ItemStack itemstack = player.getHeldItem(hand);
        if (!world.isRemote) {
            if (player.isSneaking()) {
                changeRange(itemstack);
                player.sendStatusMessage(new StringTextComponent(TextFormatting.AQUA + I18n.format("mininggadgets.mininggadget.range_change", getToolRange(itemstack))), true);
                return new ActionResult<>(ActionResultType.SUCCESS, itemstack);
            } else {
                player.setActiveHand(hand);
                return new ActionResult<>(ActionResultType.PASS, itemstack);
            }
        }
        return new ActionResult<>(ActionResultType.PASS, itemstack);
    }

    @Override
    public void onUsingTick(ItemStack stack, LivingEntity player, int count) {
        World world = player.world;
        BlockRayTraceResult lookingAt = VectorHelper.getLookingAt((PlayerEntity) player, RayTraceContext.FluidMode.NONE);
        if (lookingAt == null || (world.getBlockState(VectorHelper.getLookingAt((PlayerEntity) player, stack).getPos()) == Blocks.AIR.getDefaultState()))
            return;
        List<BlockPos> coords = getMinableBlocks(stack, lookingAt, (PlayerEntity) player);
        float hardness = getHardness(coords, (PlayerEntity) player);
        hardness = hardness * getToolRange(stack) * 1;
        for (BlockPos coord : coords) {
            BlockState state = world.getBlockState(coord);
            if (!(state.getBlock() instanceof RenderBlock)) {
                if (!world.isRemote) {
                    if (!canMine(stack, world)) {
                        player.resetActiveHand();
                        return;
                    }
                    world.setBlockState(coord, ModBlocks.RENDERBLOCK.getDefaultState());
                    RenderBlockTileEntity te = (RenderBlockTileEntity) world.getTileEntity(coord);
                    te.setRenderBlock(state);
                    te.setDurability((int) hardness - 1);
                    te.setOriginalDurability((int) hardness);
                    te.setPlayer((PlayerEntity) player);
                }
            } else {
                RenderBlockTileEntity te = (RenderBlockTileEntity) world.getTileEntity(coord);
                if (player.getHeldItemMainhand().getItem() instanceof MiningGadget && player.getHeldItemOffhand().getItem() instanceof MiningGadget) {
                    if (te.getDurability() <= 2)
                        setLastBreak(stack, world.getGameTime());
                    te.setDurability(te.getDurability() - 2);
                } else {
                    if (te.getDurability() <= 1)
                        setLastBreak(stack, world.getGameTime());
                    te.setDurability(te.getDurability() - 1);
                }
            }
        }

    }

    private static float getHardness(List<BlockPos> coords, PlayerEntity player) {
        float hardness = 0;
        World world = player.getEntityWorld();
        for (BlockPos coord : coords) {
            BlockState state = world.getBlockState(coord);
            float temphardness = state.getBlockHardness(world, coord);
            if (state.getMaterial() == Material.EARTH) temphardness = temphardness * 4;
            hardness += temphardness;
        }
        return ((hardness / coords.size()) * 3);
    }

    public static List<BlockPos> getMinableBlocks(ItemStack stack, BlockRayTraceResult lookingAt, PlayerEntity player) {
        List<BlockPos> coordinates = new ArrayList<>();
        World world = player.world;

        if (getToolRange(stack) == 1) {
            addCoord(coordinates, lookingAt.getPos(), world);
            return coordinates;
        }

        Direction side = lookingAt.getFace();
        boolean vertical = side.getAxis().isVertical();
        Direction up = vertical ? player.getHorizontalFacing() : Direction.UP;
        Direction down = up.getOpposite();
        Direction right = vertical ? up.rotateY() : side.rotateYCCW();
        Direction left = right.getOpposite();

        addCoord(coordinates, lookingAt.getPos().offset(up).offset(left), world);
        addCoord(coordinates, lookingAt.getPos().offset(up), world);
        addCoord(coordinates, lookingAt.getPos().offset(up).offset(right), world);
        addCoord(coordinates, lookingAt.getPos().offset(left), world);
        addCoord(coordinates, lookingAt.getPos(), world);
        addCoord(coordinates, lookingAt.getPos().offset(right), world);
        addCoord(coordinates, lookingAt.getPos().offset(down).offset(left), world);
        addCoord(coordinates, lookingAt.getPos().offset(down), world);
        addCoord(coordinates, lookingAt.getPos().offset(down).offset(right), world);

        return coordinates;
    }

    private static void addCoord(List<BlockPos> coordinates, BlockPos coord, World world) {
        BlockState state = world.getBlockState(coord);

        // Reject fluids and air
        if (!state.getFluidState().isEmpty() || world.isAirBlock(coord))
            return;

        // Rejects any blocks with a hardness less than 0
        if (state.getBlockHardness(world, coord) < 0)
            return;

        coordinates.add(coord);
    }

    /**
     * UPGRADE CODE :D
     */
    public static void applyUpgrade(ItemStack tool, UpgradeCard upgradeCard) {
        if( hasUpgrade(tool, upgradeCard.getUpgrade()) )
            return;

        setUpgrade(tool, upgradeCard);
    }

    private static void setUpgrade(ItemStack tool, UpgradeCard upgrade) {
        CompoundNBT tagCompound = MiscTools.getOrNewTag(tool);

        ListNBT list = tagCompound.getList("upgrades", Constants.NBT.TAG_COMPOUND);
        CompoundNBT compound = new CompoundNBT();
        compound.putString("upgrade", upgrade.getUpgrade().getName());
        compound.putInt("tier", upgrade.getTier());

        list.add(compound);
        tagCompound.put("upgrades", list);
    }

    // Return all upgrades in the item.
    public static List<TieredUpgrade> getUpgrades(ItemStack tool) {
        CompoundNBT tagCompound = MiscTools.getOrNewTag(tool);
        ListNBT upgrades = tagCompound.getList("upgrades", Constants.NBT.TAG_COMPOUND);

        List<TieredUpgrade> functionalUpgrades = new ArrayList<>();
        if( upgrades.isEmpty() )
            return functionalUpgrades;

        for (int i = 0; i < upgrades.size(); i++) {
            CompoundNBT tag = upgrades.getCompound(i);

            // If the name doesn't exist then move on
            try {
                Upgrade type = Upgrade.valueOf(tag.getString("upgrade").toUpperCase());
                functionalUpgrades.add(new TieredUpgrade(tag.getInt("tier"), type));
            } catch (IllegalArgumentException ignored) {}
        }

        return functionalUpgrades;
    }

    // Get a single upgrade and it's tier
    public static TieredUpgrade getUpgrade(ItemStack tool, Upgrade type) {
        List<TieredUpgrade> upgrades = getUpgrades(tool);
        if( upgrades.isEmpty() )
            return null;

        for (TieredUpgrade upgrade: upgrades) {
            if(upgrade.getUpgrade().getName().equals(type.getName()))
                return upgrade;
        }

        return null;
    }

    public static boolean hasUpgrade(ItemStack tool, Upgrade type) {
        return getUpgrade(tool, type) != null;
    }

    public static final class TieredUpgrade {
        private int tier;
        private Upgrade upgrade;

        public TieredUpgrade(int tier, Upgrade upgrade) {
            this.tier = tier;
            this.upgrade = upgrade;
        }

        public int getTier() {
            return tier;
        }

        public Upgrade getUpgrade() {
            return upgrade;
        }
    }
}
