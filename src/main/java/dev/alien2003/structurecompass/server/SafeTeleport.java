package dev.alien2003.structurecompass.server;

import dev.alien2003.structurecompass.StructureCompass;
import dev.alien2003.structurecompass.network.TeleportRequestPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Teleport button of the structure screen. Runs on the server thread for a single, explicit player request (like the
 * vanilla command, it may load the target chunk). Uses vanilla {@code /tp} so the usual feedback and rules apply.
 */
public final class SafeTeleport {
    private SafeTeleport() {
    }

    public static void handle(ServerPlayer player, TeleportRequestPayload request) {
        if (!player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            return; // the button is only shown with cheats; ignore forged requests
        }
        ServerLevel target = player.level().getServer().getLevel(request.dimension());
        if (target == null) {
            return;
        }
        WorldBorder border = target.getWorldBorder();
        int x = (int) Math.max(border.getMinX() + 1, Math.min(border.getMaxX() - 2, request.x()));
        int z = (int) Math.max(border.getMinZ() + 1, Math.min(border.getMaxZ() - 2, request.z()));
        int y = target.dimensionType().hasCeiling() ? findGapBelowCeiling(target, x, z)
                : target.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        if (y == Integer.MIN_VALUE) {
            player.sendSystemMessage(Component.translatableWithFallback("structurecompass.message.no_safe_spot",
                    "Structure Compass: no safe spot found at this position"));
            return;
        }
        String command = "execute in " + request.dimension().identifier() + " run tp @s " + x + ".5 " + y + " " + z + ".5";
        StructureCompass.LOGGER.debug("Structure Compass teleport: /{}", command);
        player.level().getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
    }

    /** Highest spot below the ceiling with a solid floor and two free blocks, scanning down from below the roof. */
    private static int findGapBelowCeiling(ServerLevel level, int x, int z) {
        int top = Math.min(level.getMinY() + level.dimensionType().logicalHeight(), level.getMaxY()) - 3;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = top; y > level.getMinY() + 1; y--) {
            BlockState feet = level.getBlockState(pos.set(x, y, z));
            BlockState head = level.getBlockState(pos.set(x, y + 1, z));
            BlockState floor = level.getBlockState(pos.set(x, y - 1, z));
            if (feet.isAir() && head.isAir() && floor.getFluidState().isEmpty()
                    && floor.isFaceSturdy(level, pos, Direction.UP)) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }
}
