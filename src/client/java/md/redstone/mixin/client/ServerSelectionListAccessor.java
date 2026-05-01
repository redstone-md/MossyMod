package md.redstone.mixin.client;

import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ServerSelectionList.class)
public interface ServerSelectionListAccessor {
    @Invoker("refreshEntries")
    void mossy$refreshEntries();
}
