/*
 * Adapted from the Wizardry License
 *
 * Copyright (c) 2017-2019 DaPorkchop_
 *
 * Permission is hereby granted to any persons and/or organizations using this software to copy, modify, merge, publish, and distribute it.
 * Said persons and/or organizations are not allowed to use the software or any derivatives of the work for commercial use or any other means to generate income, nor are they allowed to claim this software as their own.
 *
 * The persons and/or organizations are also disallowed from sub-licensing and/or trademarking this software without explicit permission from DaPorkchop_.
 *
 * Any persons and/or organizations using this software must disclose their source code and have it publicly available, include this license, provide sufficient credit to the original author of the project (IE: DaPorkchop_), as well as provide a link to the original project.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON INFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */

package net.daporkchop.pepsimod.mixin.client.gui;

import net.daporkchop.pepsimod.util.mixin.MergedBossInfo;
import net.minecraft.client.gui.BossInfoClient;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiBossOverlay;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.network.play.server.SPacketUpdateBossInfo;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.BossInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static net.daporkchop.pepsimod.util.PepsiConstants.RESOLUTION;
import static net.daporkchop.pepsimod.util.PepsiConstants.mc;

/**
 * Inspired by the version I made for Future.
 * <p>
 * This merges boss bars with identical names together and adds a count behind boss bars that appear multiple times.
 *
 * A major difference from the Future version (aside from the implementation, which is basically completely different) is that it renders all boss bars
 * simultaneously, adjusting the opacity according to the count.
 *
 * @author DaPorkchop_
 */
@Mixin(GuiBossOverlay.class)
abstract class MixinGuiBossOverlay extends Gui {
    @Shadow
    @Final
    private static ResourceLocation          GUI_BARS_TEXTURES;
    @Shadow
    @Final
    private        Map<UUID, BossInfoClient> mapBossInfos;

    private final Map<ITextComponent, MergedBossInfo> mergedBossInfos = new HashMap<>();

    /**
     * This method is overwritten because I change nearly the whole thing.
     *
     * @author DaPorkchop_
     * @reason efficiency
     */
    @Overwrite
    public void renderBossHealth() {
        if (!this.mergedBossInfos.isEmpty()) {
            int abortHeight = RESOLUTION.height() / 3; //the Y position at which we will stop rendering boss bars to avoid filling the screen

            int center = RESOLUTION.width() >> 1;
            int y = 12;

            for (MergedBossInfo merged : this.mergedBossInfos.values()) {
                mc.getTextureManager().bindTexture(GUI_BARS_TEXTURES);
                this.render(center - 91, y, merged);
                String s = merged.formattedName();
                mc.fontRenderer.drawStringWithShadow(s, center - mc.fontRenderer.getStringWidth(s) * 0.5f, y - 9.0f, 16777215);

                y += 10 + mc.fontRenderer.FONT_HEIGHT;
                if (y >= abortHeight) {
                    break;
                }
            }
        }
    }

    private void render(int x, int y, MergedBossInfo merged) {
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        this.drawTexturedModalRect(x, y, 0, merged.color().ordinal() * 10, 182, 5);

        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f / merged.entries().size());
        for (BossInfoClient info : merged.entries()) {
            int progress = (int) (info.getPercent() * 183.0f);
            if (progress > 0) {
                this.drawTexturedModalRect(x, y, 0, info.getColor().ordinal() * 10 + 5, progress, 5);
            }
        }

        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        if (merged.overlay() != BossInfo.Overlay.PROGRESS) {
            this.drawTexturedModalRect(x, y, 0, 80 + (merged.overlay().ordinal() - 1) * 10, 182, 5);
        }
    }

    /**
     * This method is overwritten because I change nearly the whole thing.
     *
     * @author DaPorkchop_
     * @reason efficiency
     */
    @Overwrite
    public void read(SPacketUpdateBossInfo packetIn) {
        switch (packetIn.getOperation()) {
            case ADD: {
                BossInfoClient info = new BossInfoClient(packetIn);
                this.mapBossInfos.put(packetIn.getUniqueId(), info);
                this.mergedBossInfos.computeIfAbsent(info.getName(), MergedBossInfo::new).add(info);
            }
            break;
            case REMOVE: {
                BossInfoClient info = this.mapBossInfos.remove(packetIn.getUniqueId());
                if (info != null) {
                    MergedBossInfo merged = this.mergedBossInfos.get(info.getName());
                    if (merged != null && merged.remove(info)) { //remove the old MergedBossInfo if it's empty
                        this.mergedBossInfos.remove(info.getName());
                    }
                }
            }
            break;
            case UPDATE_NAME: {
                //remove this entry from the old name and add it to the new name
                BossInfoClient info = this.mapBossInfos.get(packetIn.getUniqueId());
                MergedBossInfo merged = this.mergedBossInfos.get(info.getName());
                if (merged != null && merged.remove(info)) {
                    this.mergedBossInfos.remove(info.getName());
                }
                info.setName(packetIn.getName());
                this.mergedBossInfos.computeIfAbsent(info.getName(), MergedBossInfo::new).add(info);
            }
            break;
            default:
                //otherwise we let the packet handle it
                this.mapBossInfos.get(packetIn.getUniqueId()).updateFromPacket(packetIn);
        }
    }

    /**
     * This method is overwritten for efficiency since I add one line.
     *
     * @author DaPorkchop_
     * @reason efficiency
     */
    @Overwrite
    public void clearBossInfos() {
        this.mapBossInfos.clear();
        this.mergedBossInfos.clear();
    }
}

