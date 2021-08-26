package com.direwolf20.mininggadgets.client.screens;

import com.direwolf20.mininggadgets.common.items.gadget.MiningProperties;
import com.direwolf20.mininggadgets.common.network.PacketHandler;
import com.direwolf20.mininggadgets.common.network.packets.PacketChangeBreakType;
import com.direwolf20.mininggadgets.common.network.packets.PacketChangeColor;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraftforge.fmlclient.gui.widget.Slider;

import java.awt.*;

public class MiningVisualsScreen extends Screen implements Slider.ISlider {
    private ItemStack gadget;
    private Button blockBreakButton;
    private int red;
    private int green;
    private int blue;
    private int red_inner;
    private int green_inner;
    private int blue_inner;
    private Slider sliderRedInner;
    private Slider sliderGreenInner;
    private Slider sliderBlueInner;
    private Slider sliderRedOuter;
    private Slider sliderGreenOuter;
    private Slider sliderBlueOuter;

    public MiningVisualsScreen(ItemStack gadget) {
        super(new TextComponent("title"));
        this.gadget = gadget;
        this.red = MiningProperties.getColor(gadget, MiningProperties.COLOR_RED);
        this.green = MiningProperties.getColor(gadget, MiningProperties.COLOR_GREEN);
        this.blue = MiningProperties.getColor(gadget, MiningProperties.COLOR_BLUE);
        this.red_inner = MiningProperties.getColor(gadget, MiningProperties.COLOR_RED_INNER);
        this.green_inner = MiningProperties.getColor(gadget, MiningProperties.COLOR_GREEN_INNER);
        this.blue_inner = MiningProperties.getColor(gadget, MiningProperties.COLOR_BLUE_INNER);
    }

    @Override
    protected void init() {
        int baseX = width / 2, baseY = height / 2;

        TranslatableComponent buttonText;
        if (MiningProperties.getBreakType(gadget) == MiningProperties.BreakTypes.SHRINK)
            buttonText = new TranslatableComponent("mininggadgets.tooltip.screen.shrink");
        else
            buttonText = new TranslatableComponent("mininggadgets.tooltip.screen.fade");

        blockBreakButton = new Button(baseX - (150), baseY - 55, 150, 20, buttonText, (button) -> {
            if (blockBreakButton.getMessage().getString().contains("Shrink"))
                button.setMessage(new TranslatableComponent("mininggadgets.tooltip.screen.fade"));
            else
                button.setMessage(new TranslatableComponent("mininggadgets.tooltip.screen.shrink"));

            PacketHandler.sendToServer(new PacketChangeBreakType());
        });

        addRenderableWidget(blockBreakButton);

        sliderRedInner = new Slider(baseX - (150), baseY - 10, 150, 20, new TranslatableComponent("mininggadgets.tooltip.screen.red").append(": "), TextComponent.EMPTY, 0, 255, this.red, false, true, s -> {
        }, this);
        sliderGreenInner = new Slider(baseX - (150), baseY + 15, 150, 20, new TranslatableComponent("mininggadgets.tooltip.screen.green").append(": "), TextComponent.EMPTY, 0, 255, this.green, false, true, s -> {
        }, this);
        sliderBlueInner = new Slider(baseX - (150), baseY + 40, 150, 20, new TranslatableComponent("mininggadgets.tooltip.screen.blue").append(": "), TextComponent.EMPTY, 0, 255, this.blue, false, true, s -> {
        }, this);

        sliderRedOuter = new Slider(baseX + (25), baseY - 10, 150, 20, new TranslatableComponent("mininggadgets.tooltip.screen.red").append(": "), TextComponent.EMPTY, 0, 255, this.red_inner, false, true, s -> {
        }, this);
        sliderGreenOuter = new Slider(baseX + (25), baseY + 15, 150, 20, new TranslatableComponent("mininggadgets.tooltip.screen.green").append(": "), TextComponent.EMPTY, 0, 255, this.green_inner, false, true, s -> {
        }, this);
        sliderBlueOuter = new Slider(baseX + (25), baseY + 40, 150, 20, new TranslatableComponent("mininggadgets.tooltip.screen.blue").append(": "), TextComponent.EMPTY, 0, 255, this.blue_inner, false, true, s -> {
        }, this);

        addRenderableWidget(sliderRedInner);
        addRenderableWidget(sliderGreenInner);
        addRenderableWidget(sliderBlueInner);
        addRenderableWidget(sliderRedOuter);
        addRenderableWidget(sliderGreenOuter);
        addRenderableWidget(sliderBlueOuter);
    }

    @Override
    public void render(PoseStack stack, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(stack);
        super.render(stack, mouseX, mouseY, partialTicks);

        drawCenteredString(stack, font, new TranslatableComponent("mininggadgets.tooltip.screen.visual_settings"), (width / 2), (height / 2) - 95, 0xFFFFFF);
        drawString(stack, font, new TranslatableComponent("mininggadgets.tooltip.screen.block_break_style"), (width / 2) - 150, (height / 2) - 70, 0xFFFFFF);
        drawString(stack, font, new TranslatableComponent("mininggadgets.tooltip.screen.beam_preview"), (width / 2) + 25, (height / 2) - 70, 0xFFFFFF);
        drawString(stack, font, new TranslatableComponent("mininggadgets.tooltip.screen.outer_color"), (width / 2) - 150, (height / 2) - 25, 0xFFFFFF);
        drawString(stack, font, new TranslatableComponent("mininggadgets.tooltip.screen.inner_color"), (width / 2) + 25, (height / 2) - 25, 0xFFFFFF);

        stack.pushPose();
        fill(stack, (width / 2) + 25, (height / 2) - 55, ((width / 2) + 25) + 150, ((height / 2) - 55) + 20, this.rgbToInt(this.red, this.green, this.blue));
        fill(stack, (width / 2) + 25, (height / 2) - 50, ((width / 2) + 25) + 150, ((height / 2) - 50) + 10, this.rgbToInt(this.red_inner, this.green_inner, this.blue_inner));
        stack.popPose();
    }

    private int rgbToInt(int r, int g, int b) {
        int red = (r << 16) & 0x00FF0000;
        int green = (g << 8) & 0x0000FF00;
        int blue = b & 0x000000FF;

        return 0xFF000000 | red | green | blue;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        super.removed();
    }

    private void syncColors() {
        PacketHandler.sendToServer(new PacketChangeColor(this.red, this.green, this.blue, this.red_inner, this.green_inner, this.blue_inner));
    }

    @Override
    public boolean keyPressed(int p_keyPressed_1_, int p_keyPressed_2_, int p_keyPressed_3_) {
        InputConstants.Key mouseKey = InputConstants.getKey(p_keyPressed_1_, p_keyPressed_2_);
        if (p_keyPressed_1_ == 256) {
            syncColors();
            ModScreens.openGadgetSettingsScreen(this.gadget);
            return true;
        }

        if (getMinecraft().options.keyInventory.isActiveAndMatches(mouseKey)) {
            syncColors();
            removed();
            return true;
        }


        return super.keyPressed(p_keyPressed_1_, p_keyPressed_2_, p_keyPressed_3_);
    }

    @Override
    public void onChangeSliderValue(Slider slider) {
        if (slider.equals(sliderRedInner)) {
            this.red = slider.getValueInt();
        } else if (slider.equals(sliderGreenInner)) {
            this.green = slider.getValueInt();
        } else if (slider.equals(sliderBlueInner)) {
            this.blue = slider.getValueInt();
        } else if (slider.equals(sliderRedOuter)) {
            this.red_inner = slider.getValueInt();
        } else if (slider.equals(sliderGreenOuter)) {
            this.green_inner = slider.getValueInt();
        } else if (slider.equals(sliderBlueOuter)) {
            this.blue_inner = slider.getValueInt();
        }
    }

    public boolean mouseReleased(double p_mouseReleased_1_, double p_mouseReleased_3_, int p_mouseReleased_5_) {
        sliderRedInner.dragging = false;
        sliderGreenInner.dragging = false;
        sliderBlueInner.dragging = false;
        sliderRedOuter.dragging = false;
        sliderGreenOuter.dragging = false;
        sliderBlueOuter.dragging = false;
        return false;
    }
}
