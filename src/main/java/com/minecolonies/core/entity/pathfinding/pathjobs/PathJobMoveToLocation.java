package com.minecolonies.core.entity.pathfinding.pathjobs;

import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.Log;
import com.minecolonies.core.MineColonies;
import com.minecolonies.core.entity.pathfinding.MNode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.minecolonies.api.util.constant.PathingConstants.DEBUG_VERBOSITY_NONE;

/**
 * Job that handles moving to a location.
 */
public class PathJobMoveToLocation extends AbstractPathJob
{
    private static final float    DESTINATION_SLACK_NONE     = 0.1F;
    // 1^2 + 1^2 + 1^2 + (epsilon of 0.1F)
    private static final float    DESTINATION_SLACK_ADJACENT = (float) Math.sqrt(2f);
    @NotNull
    private final        BlockPos destination;
    // 0 = exact match
    private              float    destinationSlack           = DESTINATION_SLACK_NONE;

    // TODO: Adjust and scale heuristics, overestimate for large distances to lower cost & increase reachability

    /**
     * Prepares the PathJob for the path finding system.
     *
     * @param world  world the entity is in.
     * @param start  starting location.
     * @param end    target location.
     * @param range  max search range.
     * @param entity the entity.
     */
    public PathJobMoveToLocation(final Level world, @NotNull final BlockPos start, @NotNull final BlockPos end, final int range, final LivingEntity entity)
    {
        super(world, start, end, range, entity);

        this.destination = new BlockPos(end);
    }

    /**
     * Perform the search.
     *
     * @return Path of a path to the given location, a best-effort, or null.
     */
    @Nullable
    @Override
    protected Path search()
    {
        if (MineColonies.getConfig().getServer().pathfindingDebugVerbosity.get() > DEBUG_VERBOSITY_NONE)
        {
            Log.getLogger().info(String.format("Pathfinding from [%d,%d,%d] to [%d,%d,%d]",
              start.getX(), start.getY(), start.getZ(), destination.getX(), destination.getY(), destination.getZ()));
        }

        //  Compute destination slack - if the destination point cannot be stood in
        if (getGroundHeight(null, destination.getX(), destination.getY(), destination.getZ()) != destination.getY())
        {
            destinationSlack = DESTINATION_SLACK_ADJACENT;
        }

        return super.search();
    }

    @Override
    protected BlockPos getPathTargetPos(final MNode finalNode)
    {
        return destination;
    }

    @Override
    protected double computeHeuristic(final int x, final int y, final int z)
    {
        return BlockPosUtil.distManhattan(destination, x, y, z);
    }

    /**
     * Checks if the target has been reached.
     *
     * @param n Node to test.
     * @return true if has been reached.
     */
    @Override
    protected boolean isAtDestination(@NotNull final MNode n)
    {
        if (destinationSlack <= DESTINATION_SLACK_NONE)
        {
            return n.x == destination.getX()
                     && n.y == destination.getY()
                     && n.z == destination.getZ();
        }

        if (n.y == destination.getY() - 1)
        {
            return BlockPosUtil.distSqr(destination, n.x, destination.getY(), n.z) < DESTINATION_SLACK_ADJACENT * DESTINATION_SLACK_ADJACENT;
        }
        return BlockPosUtil.distSqr(destination, n.x, n.y, n.z) < DESTINATION_SLACK_ADJACENT * DESTINATION_SLACK_ADJACENT;
    }

    /**
     * Calculate the distance to the target.
     *
     * @param n Node to test.
     * @return double of the distance.
     */
    @Override
    protected double getNodeResultScore(@NotNull final MNode n)
    {
        //  For Result Score lower is better
        return BlockPosUtil.distManhattan(destination, n.x, n.y, n.z);
    }
}
