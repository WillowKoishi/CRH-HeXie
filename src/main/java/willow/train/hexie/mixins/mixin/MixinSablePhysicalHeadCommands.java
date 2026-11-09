package willow.train.hexie.mixins.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import willow.train.hexie.compat.simurail.PhysicalHeadCommandAssembly;

/** Wrap only ordinary assembly commands, leaving Sable's explicit shatter operations alone. */
@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.command.SableAssembleCommands", remap = false)
public abstract class MixinSablePhysicalHeadCommands {
    @WrapOperation(method = {"executeAssembleAreaCommand", "executeAssembleCubeCommand",
            "executeAssembleConnectedCommand", "executeAssembleSphereCommand"},
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/api/SubLevelAssemblyHelper;assembleBlocks(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Ljava/lang/Iterable;Ldev/ryanhcode/sable/companion/math/BoundingBox3ic;)Ldev/ryanhcode/sable/sublevel/ServerSubLevel;"),
            require = 4)
    private static ServerSubLevel hexie$assembleWholeHead(ServerLevel level, BlockPos anchor, Iterable<BlockPos> blocks,
            BoundingBox3ic bounds, Operation<ServerSubLevel> original) throws CommandSyntaxException {
        if (!ModList.get().isLoaded("simurail")) return original.call(level, anchor, blocks, bounds);
        var selection = PhysicalHeadCommandAssembly.includeWholeHeads(level, blocks, bounds);
        return original.call(level, anchor, selection.blocks(), selection.bounds());
    }
}
