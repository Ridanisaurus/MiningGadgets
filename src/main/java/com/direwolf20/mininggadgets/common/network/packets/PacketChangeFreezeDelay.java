package com.direwolf20.mininggadgets.common.network.packets;

import com.direwolf20.mininggadgets.common.items.gadget.MiningProperties;
import com.direwolf20.mininggadgets.common.items.MiningGadget;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.fmllegacy.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketChangeFreezeDelay {
    private int freezeDelay;

    public PacketChangeFreezeDelay(int freezeDelay) {
        this.freezeDelay = freezeDelay;
    }

    public static void encode(PacketChangeFreezeDelay msg, FriendlyByteBuf buffer) {
        buffer.writeInt(msg.freezeDelay);
    }

    public static PacketChangeFreezeDelay decode(FriendlyByteBuf buffer) {
        return new PacketChangeFreezeDelay(buffer.readInt());
    }

    public static class Handler {
        public static void handle(PacketChangeFreezeDelay msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null)
                    return;

                ItemStack stack = MiningGadget.getGadget(player);

                // Active toggle feature
                MiningProperties.setFreezeDelay(stack, msg.freezeDelay);
            });

            ctx.get().setPacketHandled(true);
        }
    }
}
