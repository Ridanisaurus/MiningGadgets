package com.direwolf20.mininggadgets.common.tiles;

import com.direwolf20.mininggadgets.client.particles.laserparticle.LaserParticleData;
import com.direwolf20.mininggadgets.common.Config;
import com.direwolf20.mininggadgets.common.events.ServerTickHandler;
import com.direwolf20.mininggadgets.common.items.ModItems;
import com.direwolf20.mininggadgets.common.items.gadget.MiningProperties;
import com.direwolf20.mininggadgets.common.items.upgrade.Upgrade;
import com.direwolf20.mininggadgets.common.items.upgrade.UpgradeTools;
import com.direwolf20.mininggadgets.common.util.SpecialBlockActions;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SUpdateTileEntityPacket;
import net.minecraft.stats.Stats;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.event.ForgeEventFactory;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import static com.direwolf20.mininggadgets.common.blocks.ModBlocks.RENDERBLOCK_TILE;

public class RenderBlockTileEntity extends TileEntity implements ITickableTileEntity {
    private BlockState renderBlock;

    private int priorDurability = 9999;
    private int clientPrevDurability;
    private int clientDurability;
    private int durability;
    private UUID playerUUID;
    private int originalDurability;
    private final Random rand = new Random();
    private int ticksSinceMine = 0;
    private List<Upgrade> gadgetUpgrades;
    private List<ItemStack> gadgetFilters;
    private boolean gadgetIsWhitelist;
    private boolean packetReceived = false;
    private int totalAge;
    private MiningProperties.BreakTypes breakType;
    private boolean blockAllowed;

    public RenderBlockTileEntity() {
        super(RENDERBLOCK_TILE.get());
    }

    public static boolean blockAllowed(List<ItemStack> drops, List<ItemStack> filters, boolean isWhiteList) {
        boolean blockAllowed = false;
        for (ItemStack dropStack : drops) {
            if (filters.size() == 0)
                return true;

            boolean contains = false;
            for (ItemStack filter : filters) {
                if (dropStack.sameItem(filter)) {
                    contains = true;
                    break;
                }
            }

            blockAllowed = (isWhiteList && contains) || (!isWhiteList && !contains);

            if (blockAllowed)
                break;
        }

        return blockAllowed;
    }

    public BlockState getRenderBlock() {
        return renderBlock;
    }

    public void setRenderBlock(BlockState state) {
        renderBlock = state;
    }

    public MiningProperties.BreakTypes getBreakType() {
        return breakType;
    }

    public void setBreakType(MiningProperties.BreakTypes breakType) {
        this.breakType = breakType;
    }

    public void justSetDurability(int durability) {
        priorDurability = this.durability;
        this.durability = durability;
        //System.out.println("Got:"+ " Prior: " + priorDurability + " Dur: " + durability);
    }

    public void setDurability(int dur, ItemStack stack) {
        ticksSinceMine = 0;
        if (durability != 0)
            priorDurability = durability;
        durability = dur;
        if (dur <= 0) {
            removeBlock();
            if (UpgradeTools.containsActiveUpgradeFromList(gadgetUpgrades, Upgrade.FREEZING)) {
                freeze(stack);
            }
        }
        if (!(level.isClientSide)) {
            setChanged();
            ServerTickHandler.addToList(worldPosition, durability, level);
            //PacketHandler.sendToAll(new PacketDurabilitySync(pos, dur), world);
            //System.out.println("Sent: "+ " Prior: " + priorDurability + " Dur: " + dur);
        }
    }

    private void freeze(ItemStack stack) {
        int freezeCost = Config.UPGRADECOST_FREEZE.get() * -1;
        int energy = stack.getCapability(CapabilityEnergy.ENERGY).map(IEnergyStorage::getEnergyStored).orElse(0);

        if (energy == 0) {
            return;
        }

        for (Direction side : Direction.values()) {
            BlockPos sidePos = worldPosition.relative(side);
            FluidState state = level.getFluidState(sidePos);
            if (state.getType().isSame(Fluids.LAVA) && state.getType().isSource(state)) {
                energy -= this.replaceBlockWithAlternative(level, sidePos, Blocks.OBSIDIAN.defaultBlockState(), stack, freezeCost, energy);
            } else if (state.getType().isSame(Fluids.WATER) && state.getType().isSource(state)) {
                energy -= this.replaceBlockWithAlternative(level, sidePos, Blocks.PACKED_ICE.defaultBlockState(), stack, freezeCost, energy);
            } else if ((state.getType().isSame(Fluids.WATER) || state.getType().isSame(Fluids.LAVA)) && !state.getType().isSource(state)) {
                energy -= this.replaceBlockWithAlternative(level, sidePos, Blocks.COBBLESTONE.defaultBlockState(), stack, freezeCost, energy);
            }
        }
    }

    private int replaceBlockWithAlternative(World world, BlockPos pos, BlockState state, ItemStack stack, int costOfOperation, int remainingEnergy) {
        if (remainingEnergy < costOfOperation) {
            return 0;
        }

        stack.getCapability(CapabilityEnergy.ENERGY).ifPresent(e -> e.receiveEnergy(costOfOperation, false));
        world.setBlockAndUpdate(pos, state);
        return costOfOperation;
    }

    public void spawnParticle() {
        if (UpgradeTools.containsActiveUpgradeFromList(gadgetUpgrades, Upgrade.MAGNET) && originalDurability > 0) {
            int PartCount = 20 / originalDurability;
            if (PartCount <= 1) PartCount = 1;
            for (int i = 0; i <= PartCount; i++) {
                double randomPartSize = 0.125 + rand.nextDouble() * 0.5;
                double randomX = rand.nextDouble();
                double randomY = rand.nextDouble();
                double randomZ = rand.nextDouble();

                LaserParticleData data = LaserParticleData.laserparticle(renderBlock, (float) randomPartSize, 1f, 1f, 1f, 200);
                getLevel().addParticle(data, this.getBlockPos().getX() + randomX, this.getBlockPos().getY() + randomY, this.getBlockPos().getZ() + randomZ, 0, 0.0f, 0);
            }
        }
    }

    public int getDurability() {
        return durability;
    }

    public int getOriginalDurability() {
        return originalDurability;
    }

    public void setOriginalDurability(int originalDurability) {
        this.originalDurability = originalDurability;
    }

    public PlayerEntity getPlayer() {
        if (getLevel() == null)
            return null;

        return this.getLevel().getPlayerByUUID(playerUUID);
    }

    public void setPlayer(PlayerEntity player) {
        this.playerUUID = player.getUUID();
    }

    public UUID getPlayerUUID() {
        return this.playerUUID;
    }

    public int getTicksSinceMine() {
        return ticksSinceMine;
    }

    public void setTicksSinceMine(int ticksSinceMine) {
        this.ticksSinceMine = ticksSinceMine;
    }

    public int getPriorDurability() {
        return priorDurability;
    }

    public void setPriorDurability(int priorDurability) {
        this.priorDurability = priorDurability;
    }

    public int getClientDurability() {
        return clientDurability;
    }

    public void setClientDurability(int clientDurability) {
        if (this.durability == 0)
            this.clientPrevDurability = clientDurability;
        else
            this.clientPrevDurability = this.durability;
        this.clientDurability = clientDurability;
        packetReceived = true;
    }

    public List<Upgrade> getGadgetUpgrades() {
        return gadgetUpgrades;
    }

    public void setGadgetUpgrades(List<Upgrade> gadgetUpgrades) {
        this.gadgetUpgrades = gadgetUpgrades;
    }

    public List<ItemStack> getGadgetFilters() {
        return gadgetFilters;
    }

    public void setGadgetFilters(List<ItemStack> gadgetFilters) {
        this.gadgetFilters = gadgetFilters;
    }

    public boolean isGadgetIsWhitelist() {
        return gadgetIsWhitelist;
    }

    public void setGadgetIsWhitelist(boolean gadgetIsWhitelist) {
        this.gadgetIsWhitelist = gadgetIsWhitelist;
    }

    @Override
    public SUpdateTileEntityPacket getUpdatePacket() {
        // Vanilla uses the type parameter to indicate which type of tile entity (command block, skull, or beacon?) is receiving the packet, but it seems like Forge has overridden this behavior
        return new SUpdateTileEntityPacket(worldPosition, 0, getUpdateTag());
    }

    @Override
    public void handleUpdateTag(BlockState state, CompoundNBT tag) {
        load(state, tag);
    }

    @Override
    public CompoundNBT getUpdateTag() {
        return save(new CompoundNBT());
    }

    @Override
    public void onDataPacket(NetworkManager net, SUpdateTileEntityPacket pkt) {
        load(this.getBlockState(), pkt.getTag());
    }

    public void markDirtyClient() {
        setChanged();
        if (getLevel() != null) {
            BlockState state = getLevel().getBlockState(getBlockPos());
            getLevel().sendBlockUpdated(getBlockPos(), state, state, 3);
        }
    }

    @Override
    public void load(BlockState state, CompoundNBT tag) {
        super.load(state, tag);
        renderBlock = NBTUtil.readBlockState(tag.getCompound("renderBlock"));
        originalDurability = tag.getInt("originalDurability");
        priorDurability = tag.getInt("priorDurability");
        durability = tag.getInt("durability");
        ticksSinceMine = tag.getInt("ticksSinceMine");
        playerUUID = tag.getUUID("playerUUID");
        gadgetUpgrades = UpgradeTools.getUpgradesFromTag(tag);
        breakType = MiningProperties.BreakTypes.values()[tag.getByte("breakType")];
        gadgetFilters = MiningProperties.deserializeItemStackList(tag.getCompound("gadgetFilters"));
        gadgetIsWhitelist = tag.getBoolean("gadgetIsWhitelist");
        blockAllowed = tag.getBoolean("blockAllowed");
    }

    @Override
    public CompoundNBT save(CompoundNBT tag) {
        if (renderBlock != null)
            tag.put("renderBlock", NBTUtil.writeBlockState(renderBlock));
        tag.putInt("originalDurability", originalDurability);
        tag.putInt("priorDurability", priorDurability);
        tag.putInt("durability", durability);
        tag.putInt("ticksSinceMine", ticksSinceMine);
        if (playerUUID != null)
            tag.putUUID("playerUUID", playerUUID);
        tag.put("upgrades", UpgradeTools.setUpgradesNBT(gadgetUpgrades).getList("upgrades", Constants.NBT.TAG_COMPOUND));
        tag.putByte("breakType", (byte) breakType.ordinal());
        tag.put("gadgetFilters", MiningProperties.serializeItemStackList(getGadgetFilters()));
        tag.putBoolean("gadgetIsWhitelist", isGadgetIsWhitelist());
        tag.putBoolean("blockAllowed", blockAllowed);
        return super.save(tag);
    }

    private void removeBlock() {
        if (level == null || level.isClientSide || playerUUID == null)
            return;

        PlayerEntity player = level.getPlayerByUUID(playerUUID);
        if (player == null)
            return;

        int silk = 0;
        int fortune = 0;

        ItemStack tempTool = new ItemStack(ModItems.MININGGADGET.get());

        // If silk is in the upgrades, apply it without a tier.
        if (UpgradeTools.containsActiveUpgradeFromList(gadgetUpgrades, Upgrade.SILK)) {
            tempTool.enchant(Enchantments.SILK_TOUCH, 1);
            silk = 1;
        }

        // FORTUNE_1 is eval'd against the basename so this'll support all fortune upgrades
        if (UpgradeTools.containsActiveUpgradeFromList(gadgetUpgrades, Upgrade.FORTUNE_1)) {
            Optional<Upgrade> upgrade = UpgradeTools.getUpgradeFromList(gadgetUpgrades, Upgrade.FORTUNE_1);
            if (upgrade.isPresent()) {
                fortune = upgrade.get().getTier();
                tempTool.enchant(Enchantments.BLOCK_FORTUNE, fortune);
            }
        }

        List<ItemStack> drops = Block.getDrops(renderBlock, (ServerWorld) level, this.worldPosition, null, player, tempTool);

        if (blockAllowed) {
            int exp = renderBlock.getExpDrop(level, worldPosition, fortune, silk);
            boolean magnetMode = (UpgradeTools.containsActiveUpgradeFromList(gadgetUpgrades, Upgrade.MAGNET));
            for (ItemStack drop : drops) {
                if (drop != null) {
                    if (magnetMode) {
                        int wasPickedUp = ForgeEventFactory.onItemPickup(new ItemEntity(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), drop), player);
                        // 1  = someone allowed the event meaning it's handled,
                        // -1 = someone blocked the event and thus we shouldn't drop it nor insert it
                        // 0  = no body captured the event and we should handle it by hand.
                        if (wasPickedUp == 0) {
                            if (!player.addItem(drop))
                                Block.popResource(level, worldPosition, drop);
                        }
                    } else {
                        Block.popResource(level, worldPosition, drop);
                    }
                }
            }
            if (magnetMode) {
                if (exp > 0)
                    player.giveExperiencePoints(exp);
            } else {
                if (exp > 0)
                    renderBlock.getBlock().popExperience((ServerWorld) level, worldPosition, exp);
            }

            renderBlock.spawnAfterBreak((ServerWorld) level, worldPosition, tempTool); // Fixes silver fish basically...
        }

//        BlockState underState = world.getBlockState(this.pos.down());

        level.removeBlockEntity(this.worldPosition);
        level.setBlockAndUpdate(this.worldPosition, Blocks.AIR.defaultBlockState());

//        if (UpgradeTools.containsActiveUpgradeFromList(gadgetUpgrades, Upgrade.PAVER)) {
//            if (this.pos.getY() <= player.getPosY() && underState == Blocks.AIR.getDefaultState()) {
//                world.setBlockState(this.pos.down(), Blocks.COBBLESTONE.getDefaultState());
//            }
//        }

        // Add to the break stats
        player.awardStat(Stats.BLOCK_MINED.get(renderBlock.getBlock()));

        // Handle special cases
        if (SpecialBlockActions.getRegister().containsKey(renderBlock.getBlock()))
            SpecialBlockActions.getRegister().get(renderBlock.getBlock()).accept(level, worldPosition, renderBlock);
    }

    private void resetBlock() {
        if (level == null)
            return;

        if (!level.isClientSide) {
            if (renderBlock != null)
                level.setBlockAndUpdate(this.worldPosition, renderBlock);
            else
                level.setBlockAndUpdate(this.worldPosition, Blocks.AIR.defaultBlockState());
        }
    }

    @Override
    public void tick() {
        totalAge++;
        //Client and server - spawn a 'block break' particle if the player is actively mining
        if (ticksSinceMine == 0) {
            spawnParticle();
        }
        //Client only
        if (level.isClientSide) {
            //Update ticks since last mine on client side for particle renders
            if (playerUUID != null) {
                if (getPlayer() != null && !getPlayer().isUsingItem()) ticksSinceMine++;
                else ticksSinceMine = 0;
            }
            //The packet with new durability arrives between ticks. Update it on tick.
            if (packetReceived) {
                //System.out.println("PreChange: " + this.durability + ":" + this.priorDurability);
                this.priorDurability = this.durability;
                this.durability = this.clientDurability;
                //System.out.println("PostChange: " + this.durability + ":" + this.priorDurability);
                packetReceived = false;
            } else {
                if (durability != 0)
                    this.priorDurability = this.durability;

            }


        }
        //Server Only
        if (!level.isClientSide) {
            if (ticksSinceMine == 1) {
                //Immediately after player stops mining, stability the shrinking effects and notify players
                priorDurability = durability;
                ServerTickHandler.addToList(worldPosition, durability, level);
            }
            if (ticksSinceMine >= 10) {
                //After half a second, start 'regrowing' blocks that haven't been mined.
                priorDurability = durability;
                durability++;
                ServerTickHandler.addToList(worldPosition, durability, level);
            }
            if (durability >= originalDurability) {
                //Once we reach the original durability value set the block back to its original blockstate.
                resetBlock();
            }
            ticksSinceMine++;
        }
    }

    public void setBlockAllowed() {
        if (!UpgradeTools.containsActiveUpgradeFromList(gadgetUpgrades, Upgrade.VOID_JUNK)) {
            this.blockAllowed = true;
            return;
        }
        PlayerEntity player = level.getPlayerByUUID(playerUUID);
        if (player == null) return;
        int silk = 0;
        int fortune = 0;

        ItemStack tempTool = new ItemStack(ModItems.MININGGADGET.get());

        // If silk is in the upgrades, apply it without a tier.
        if (UpgradeTools.containsActiveUpgradeFromList(gadgetUpgrades, Upgrade.SILK)) {
            tempTool.enchant(Enchantments.SILK_TOUCH, 1);
            silk = 1;
        }

        // FORTUNE_1 is eval'd against the basename so this'll support all fortune upgrades
        if (UpgradeTools.containsActiveUpgradeFromList(gadgetUpgrades, Upgrade.FORTUNE_1)) {
            Optional<Upgrade> upgrade = UpgradeTools.getUpgradeFromList(gadgetUpgrades, Upgrade.FORTUNE_1);
            if (upgrade.isPresent()) {
                fortune = upgrade.get().getTier();
                tempTool.enchant(Enchantments.BLOCK_FORTUNE, fortune);
            }
        }

        List<ItemStack> drops = Block.getDrops(renderBlock, (ServerWorld) level, this.worldPosition, null, player, tempTool);

        this.blockAllowed = blockAllowed(drops, getGadgetFilters(), isGadgetIsWhitelist());
    }

    public boolean getBlockAllowed() {
        return blockAllowed;
    }
}
