//package com.minecolonies.coremod.entity.ai.basic;
//
//import com.minecolonies.api.colony.interactionhandling.ChatPriority;
//import com.minecolonies.api.colony.requestsystem.requestable.IRequestable;
//import com.minecolonies.api.colony.requestsystem.requestable.StackList;
//import com.minecolonies.api.crafting.ItemStorage;
//import com.minecolonies.api.entity.ai.statemachine.AIEventTarget;
//import com.minecolonies.api.entity.ai.statemachine.AITarget;
//import com.minecolonies.api.entity.ai.statemachine.states.AIBlockingEventType;
//import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
//import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
//import com.minecolonies.api.util.InventoryUtils;
//import com.minecolonies.api.util.ItemStackUtils;
//import com.minecolonies.api.util.Tuple;
//import com.minecolonies.api.util.WorldUtil;
//import com.minecolonies.api.util.constant.translation.RequestSystemTranslationConstants;
//import com.minecolonies.coremod.colony.buildings.AbstractBuilding;
//import com.minecolonies.coremod.colony.buildings.modules.PotUserModule;
//import com.minecolonies.coremod.colony.buildings.modules.FurnaceUserModule;
//import com.minecolonies.coremod.colony.buildings.modules.ItemListModule;
//import com.minecolonies.coremod.colony.interactionhandling.StandardInteraction;
//import com.minecolonies.coremod.colony.jobs.AbstractJob;
//import net.dries007.tfc.common.blockentities.PotBlockEntity;
//import net.dries007.tfc.common.blocks.TFCBlocks;
//import net.dries007.tfc.common.container.TFCContainerTypes;
//import net.minecraft.core.BlockPos;
//import net.minecraft.network.chat.Component;
//import net.minecraft.world.Container;
//import net.minecraft.world.item.ItemStack;
//import net.minecraft.world.level.Level;
//import net.minecraft.world.level.block.Blocks;
//import net.minecraft.world.level.block.FurnaceBlock;
//import net.minecraft.world.level.block.entity.BlockEntity;
//import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
//import net.minecraftforge.items.wrapper.InvWrapper;
//import org.jetbrains.annotations.NotNull;
//
//import java.util.ArrayList;
//import java.util.List;
//
//import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.*;
//import static com.minecolonies.api.util.ItemStackUtils.*;
//import static com.minecolonies.api.util.constant.BuildingConstants.FUEL_LIST;
//import static com.minecolonies.api.util.constant.Constants.*;
//import static com.minecolonies.api.util.constant.TranslationConstants.BAKER_HAS_NO_FURNACES_MESSAGE;
//import static com.minecolonies.api.util.constant.TranslationConstants.FURNACE_USER_NO_FUEL;
//
//import static net.dries007.tfc.common.blockentities.PotBlockEntity.SLOT_EXTRA_INPUT_START;
//
///**
// * AI class for all workers which use a furnace and require fuel and a block to smelt in it.
// *
// * @param <J> the job it is for.
// */
//public abstract class AbstractEntityAIUsesPot<J extends AbstractJob<?, J>, B extends AbstractBuilding> extends AbstractEntityAISkill<J, B>
//{
//    /**
//     * Base xp gain for the basic xp.
//     */
//    protected static final double BASE_XP_GAIN = 2;
//
//    /**
//     * Retrieve smeltable if more than a certain amount.
//     */
////    private static final int RETRIEVE_SMELTABLE_IF_MORE_THAN = 10;
//    private static final int RETRIEVE_SMELTABLE_IF_MORE_THAN = 10;
//
//    /**
//    /**
//     * Storage buffer, slots to not fill with requests.
//     */
//    private static final int STORAGE_BUFFER = 3;
//
//    /**
//     * Sets up some important skeleton stuff for every ai.
//     *
//     * @param job the job class.
//     */
//    protected AbstractEntityAIUsesPot(@NotNull final J job)
//    {
//        super(job);
//        super.registerTargets(
//          new AITarget(IDLE, START_WORKING, STANDARD_DELAY),
//          new AITarget(START_WORKING, this::startWorking, 60),
//          new AITarget(START_USING_POT, this::fillUpPot, STANDARD_DELAY),
//          new AIEventTarget(AIBlockingEventType.AI_BLOCKING, this::accelerateFurnaces, TICKS_SECOND),
//          new AITarget(RETRIEVING_END_PRODUCT_FROM_POT, this::retrieveFoodFromPot, STANDARD_DELAY),
//          new AITarget(RETRIEVING_USED_FUEL_FROM_POT, this::retrieveUsedFuel, STANDARD_DELAY));
//    }
//
//    /**
//     * Method called to extract things from the furnace after it has been reached already. Has to be overwritten by the exact class.
//     *
//     * @param pot the furnace to retrieveSmeltableFromFurnace from.
//     */
//    protected abstract void extractFromPot(final PotBlockEntity pot);
//
//    /**
//     * Extract fuel from the furnace.
//     *
//     * @param pot the furnace to retrieve from.
//     */
//    private void extractFuelFromPot(final PotBlockEntity pot)
//    {
//        InventoryUtils.transferItemStackIntoNextFreeSlotInItemHandler(
//                new InvWrapper((Container) pot), FUEL_SLOT,
//                worker.getInventoryCitizen());
//    }
//
//    /**
//     * Method called to detect if a certain stack is of the type we want to be put in the furnace.
//     *
//     * @param stack the stack to check.
//     * @return true if so.
//     */
//    protected abstract boolean isSmeltable(final ItemStack stack);
//
//    /**
//     * If the worker reached his max amount.
//     *
//     * @return true if so.
//     */
//    protected boolean reachedMaxToKeep()
//    {
//        final int count = InventoryUtils.countEmptySlotsInBuilding(building);
//        return count <= STORAGE_BUFFER;
//    }
//
//    /**
//     * Get the furnace which has finished smeltables. For this check each furnace which has been registered to the building. Check if the furnace is turned off and has something in
//     * the result slot or check if the furnace has more than x results.
//     *
//     * @return the position of the furnace.
//     */
//    protected BlockPos getPositionOfOvenToRetrieveFrom()
//    {
//        for (final BlockPos pos : building.getFirstModuleOccurance(PotUserModule.class).getPots())
//        {
//            final BlockEntity entity = world.getBlockEntity(pos);
//            if (entity instanceof PotBlockEntity)
//            {
//                /**
//                 * TFC pot is shift+right click to get soup out of
//                 */
//                final PotBlockEntity pot = (PotBlockEntity) entity;
//
////                final int countInResultSlot = ItemStackUtils.isEmpty(pot.getItem(RESULT_SLOT)) ? 0 : pot.getItem(RESULT_SLOT).getCount();
////                final int countInInputSlot = ItemStackUtils.isEmpty(pot.getItem(SMELTABLE_SLOT)) ? 0 : pot.getItem(SMELTABLE_SLOT).getCount();
//
////                if ((!pot.isLit() && countInResultSlot > 0)
////                      || countInResultSlot > RETRIEVE_SMELTABLE_IF_MORE_THAN
////                      || (countInResultSlot > 0 && countInInputSlot == 0))
////                {
////                    return pos;
////                }
//            }
//        }
//        return null;
//    }
//
//    /**
//     * Get the furnace which has used fuel. For this check each furnace which has been registered to the building. Check if the furnace has used fuel in the fuel slot.
//     *
//     * @return the position of the furnace.
//     */
//    protected BlockPos getPositionOfOvenToRetrieveFuelFrom()
//    {
//        final ItemListModule module = building.getModuleMatching(ItemListModule.class, m -> m.getId().equals(FUEL_LIST));
//        for (final BlockPos pos : building.getFirstModuleOccurance(PotUserModule.class).getPots())
//        {
//            final BlockEntity entity = world.getBlockEntity(pos);
//            if (entity instanceof PotBlockEntity)
//            {
//                final PotBlockEntity pot = (PotBlockEntity) entity;
//
//                if (!pot.getItem(FUEL_SLOT).isEmpty() && !module.isItemInList(new ItemStorage(pot.getItem(FUEL_SLOT))))
//                {
//                    return pos;
//                }
//            }
//        }
//        return null;
//    }
//
//    /**
//     * Central method of the furnace user, he decides about what to do next from here. First check if any of the workers has important tasks to handle first. If not check if there
//     * is an oven with an item which has to be retrieved. If not check if fuel and smeltable are available and request if necessary and get into inventory. Then check if able to
//     * smelt already.
//     *
//     * @return the next state to go to.
//     */
//    public IAIState startWorking()
//    {
//        if (walkToBuilding())
//        {
//            return getState();
//        }
//
//        final PotUserModule furnaceModule = building.getFirstModuleOccurance(PotUserModule.class);
//        final ItemListModule itemListModule = building.getModuleMatching(ItemListModule.class, m -> m.getId().equals(FUEL_LIST));
//        worker.getCitizenData().setVisibleStatus(VisibleCitizenStatus.WORKING);
//
//        if (itemListModule.getList().isEmpty())
//        {
//            if (worker.getCitizenData() != null)
//            {
//                worker.getCitizenData().triggerInteraction(new StandardInteraction(Component.translatable(FURNACE_USER_NO_FUEL), ChatPriority.BLOCKING));
//            }
//            return getState();
//        }
//
//        if (furnaceModule.getPots().isEmpty())
//        {
//            if (worker.getCitizenData() != null)
//            {
//                worker.getCitizenData()
//                  .triggerInteraction(new StandardInteraction(Component.translatable(BAKER_HAS_NO_FURNACES_MESSAGE), ChatPriority.BLOCKING));
//            }
//            return getState();
//        }
//
//        final IAIState nextState = checkForImportantJobs();
//        if (nextState != START_WORKING)
//        {
//            return nextState;
//        }
//
//        final BlockPos posOfUsedFuelOven = getPositionOfOvenToRetrieveFuelFrom();
//        if (posOfUsedFuelOven != null)
//        {
//            walkTo = posOfUsedFuelOven;
//            return RETRIEVING_USED_FUEL_FROM_POT;
//        }
//
//        final BlockPos posOfOven = getPositionOfOvenToRetrieveFrom();
//        if (posOfOven != null)
//        {
//            walkTo = posOfOven;
//            return RETRIEVING_END_PRODUCT_FROM_POT;
//        }
//
//        final int amountOfSmeltableInBuilding = InventoryUtils.getCountFromBuilding(building, this::isSmeltable);
//        final int amountOfSmeltableInInv = InventoryUtils.getItemCountInItemHandler((worker.getInventoryCitizen()), this::isSmeltable);
//
//        final int amountOfFuelInBuilding = InventoryUtils.getCountFromBuilding(building, itemListModule.getList());
//        final int amountOfFuelInInv =
//          InventoryUtils.getItemCountInItemHandler((worker.getInventoryCitizen()), stack -> itemListModule.isItemInList(new ItemStorage(stack)));
//
//        if (amountOfSmeltableInBuilding + amountOfSmeltableInInv <= 0 && !reachedMaxToKeep())
//        {
//            requestSmeltable();
//        }
//
//        if (amountOfFuelInBuilding + amountOfFuelInInv <= 0 && !building.hasWorkerOpenRequestsFiltered(worker.getCitizenData().getId(),
//          req -> req.getShortDisplayString().getSiblings().contains(Component.translatable(RequestSystemTranslationConstants.REQUESTS_TYPE_BURNABLE))))
//        {
//            worker.getCitizenData().createRequestAsync(new StackList(getAllowedFuel(), RequestSystemTranslationConstants.REQUESTS_TYPE_BURNABLE, STACKSIZE * furnaceModule.getPots().size(), 1));
//        }
//
//        if (amountOfSmeltableInBuilding > 0 && amountOfSmeltableInInv == 0)
//        {
//            needsCurrently = new Tuple<>(this::isSmeltable, STACKSIZE);
//            return GATHERING_REQUIRED_MATERIALS;
//        }
//        else if (amountOfFuelInBuilding > 0 && amountOfFuelInInv == 0)
//        {
//            needsCurrently = new Tuple<>(stack -> itemListModule.isItemInList(new ItemStorage(stack)), STACKSIZE);
//            return GATHERING_REQUIRED_MATERIALS;
//        }
//
//        return checkIfAbleToSmelt(amountOfFuelInBuilding + amountOfFuelInInv, amountOfSmeltableInBuilding + amountOfSmeltableInInv);
//    }
//
//    /**
//     * Get a copy of the list of allowed fuel.
//     * @return the list.
//     */
//    private List<ItemStack> getAllowedFuel()
//    {
//        final List<ItemStack> list = new ArrayList<>();
//        for (final ItemStorage storage : building.getModuleMatching(ItemListModule.class, m -> m.getId().equals(FUEL_LIST)).getList())
//        {
//            final ItemStack stack = storage.getItemStack().copy();
//            stack.setCount(stack.getMaxStackSize());
//            list.add(stack);
//        }
//        return list;
//    }
//
//    /**
//     * Actually accelerate the furnaces
//     */
//    private IAIState accelerateFurnaces()
//    {
//        final int accelerationTicks = (worker.getCitizenData().getCitizenSkillHandler().getLevel(getModuleForJob().getPrimarySkill()) / 10) * 2;
//        final Level world = building.getColony().getWorld();
//        for (final BlockPos pos : building.getFirstModuleOccurance(PotUserModule.class).getPots())
//        {
//            if (WorldUtil.isBlockLoaded(world, pos))
//            {
//                final BlockEntity entity = world.getBlockEntity(pos);
//                if (entity instanceof PotBlockEntity)
//                {
//                    final PotBlockEntity pot = (PotBlockEntity) entity;
//                    for(int i = 0; i < accelerationTicks; i++)
//                    {
//                        if (pot.isLit())
//                        {
//                            pot.serverTick(world, pos, world.getBlockState(pos), pot);
//                        }
//                    }
//                }
//            }
//        }
//        return null;
//    }
//
//    /**
//     * Request the smeltable item to the building. Specific worker has to override this.
//     */
//    public abstract void requestSmeltable();
//
//    /**
//     * Checks if the worker has enough fuel and/or smeltable to start smelting.
//     *
//     * @param amountOfFuel      the total amount of fuel.
//     * @param amountOfSmeltable the total amount of smeltables.
//     * @return START_USING_POT if enough, else check for additional worker specific jobs.
//     */
//        public static boolean hasFoodInFurnaceAndNoFuel(final PotBlockEntity entity)
//    {
//        return !ItemStackUtils.isEmpty(entity.getItem(SMELTABLE_SLOT))
//                 && ItemStackUtils.isEmpty(entity.getItem(FUEL_SLOT));
//    }
//        /**
//     * Check if the furnace has smeltable in it and fuel empty.
//     *
//     * @param entity the furnace.
//     * @return true if so.
//     */
//    public static boolean hasNeitherFuelNorFood(final PotBlockEntity entity)
//    {
//        return ItemStackUtils.isEmpty(entity.getItem(SMELTABLE_SLOT))
//                 && ItemStackUtils.isEmpty(entity.getItem(FUEL_SLOT));
//    }
//
//    /**
//     * Check if the furnace has fuel in it and smeltable empty.
//     *
//     * @param entity the furnace.
//     * @return true if so.
//     */
//    public static boolean hasFuelInFurnaceAndNoFood(final PotBlockEntity entity)
//    {
//        return ItemStackUtils.isEmpty(entity.getItem(SMELTABLE_SLOT))
//                 && !ItemStackUtils.isEmpty(entity.getItem(FUEL_SLOT));
//    }
//    private IAIState checkIfAbleToSmelt(final int amountOfFuel, final int amountOfSmeltable)
//    {
//        final PotUserModule module = building.getFirstModuleOccurance(PotUserModule.class);
//        for (final BlockPos pos : module.getPots())
//        {
//            final BlockEntity entity = world.getBlockEntity(pos);
//
//            if (entity instanceof PotBlockEntity)
//            {
//                final PotBlockEntity pot = (PotBlockEntity) entity;
//                if ((amountOfFuel > 0 && hasFoodInFurnaceAndNoFuel(pot))
//                      || (amountOfSmeltable > 0 && hasFuelInFurnaceAndNoFood(pot))
//                      || (amountOfFuel > 0 && amountOfSmeltable > 0 && hasNeitherFuelNorFood(pot)))
//                {
//                    walkTo = pos;
//                    return START_USING_POT;
//                }
//            }
//            else
//            {
//                if (!(world.getBlockState(pos).getBlock() instanceof FurnaceBlock))
//                {
//                    module.removeFromPot(pos);
//                }
//            }
//        }
//
//        return checkForAdditionalJobs();
//    }
//
//    /**
//     * Check for additional jobs to execute after the traditional furnace user jobs have been handled.
//     *
//     * @return the next IAIState to go to.
//     */
//    protected IAIState checkForAdditionalJobs()
//    {
//        return START_WORKING;
//    }
//
//    /**
//     * Check for important jobs to execute before the traditional furnace user jobs are handled.
//     *
//     * @return the next IAIState to go to.
//     */
//    protected IAIState checkForImportantJobs()
//    {
//        return START_WORKING;
//    }
//
//    /**
//     * Specify that we dump inventory after every action.
//     *
//     * @return 1 to indicate that we dump inventory after every action
//     * @see AbstractEntityAIBasic#getActionsDoneUntilDumping()
//     */
//    @Override
//    protected int getActionsDoneUntilDumping()
//    {
//        return 1;
//    }
//
//    /**
//     * Retrieve ready bars from the furnaces. If no position has been set return. Else navigate to the position of the furnace. On arrival execute the extract method of the
//     * specialized worker.
//     *
//     * @return the next state to go to.
//     */
//    private IAIState retrieveFoodFromPot()
//    {
//        if (walkTo == null)
//        {
//            return START_WORKING;
//        }
//
//        if (walkToBlock(walkTo))
//        {
//            return getState();
//        }
//
//        final BlockEntity entity = world.getBlockEntity(walkTo);
//        if (!(entity instanceof PotBlockEntity)
//              || (ItemStackUtils.isEmpty(((PotBlockEntity) entity).getItem(RESULT_SLOT))))
//        {
//            walkTo = null;
//            return START_WORKING;
//        }
//
//        walkTo = null;
//
//        extractFromPot((PotBlockEntity) entity);
//        incrementActionsDoneAndDecSaturation();
//        return START_WORKING;
//    }
//
//    /**
//     * Retrieve used fuel from the furnaces. If no position has been set return. Else navigate to the position of the furnace. On arrival execute the extract method of the
//     * specialized worker.
//     *
//     * @return the next state to go to.
//     */
//    private IAIState retrieveUsedFuel()
//    {
//        if (walkTo == null)
//        {
//            return START_WORKING;
//        }
//
//        if (walkToBlock(walkTo))
//        {
//            return getState();
//        }
//
//        final BlockEntity entity = world.getBlockEntity(walkTo);
//        if (!(entity instanceof PotBlockEntity)
//                || (ItemStackUtils.isEmpty(((PotBlockEntity) entity).getItem(FUEL_SLOT))))
//        {
//            walkTo = null;
//            return START_WORKING;
//        }
//
//        walkTo = null;
//
//        extractFuelFromPot((PotBlockEntity) entity);
//        return START_WORKING;
//    }
//
//    /**
//     * Smelt the smeltable after the required items are in the inv.
//     *
//     * @return the next state to go to.
//     */
//    private IAIState fillUpPot()
//    {
//        if (building.getFirstModuleOccurance(PotUserModule.class).getPots().isEmpty())
//        {
//            if (worker.getCitizenData() != null)
//            {
//                worker.getCitizenData()
//                  .triggerInteraction(new StandardInteraction(Component.translatable(BAKER_HAS_NO_FURNACES_MESSAGE), ChatPriority.BLOCKING));
//            }
//            return START_WORKING;
//        }
//
//        if (walkTo == null || world.getBlockState(walkTo).getBlock() != TFCBlocks.POT.get())
//        {
//            walkTo = null;
//            return START_WORKING;
//        }
//
//        if (walkToBlock(walkTo))
//        {
//            return getState();
//        }
//
//        final BlockEntity entity = world.getBlockEntity(walkTo);
//        if (entity instanceof PotBlockEntity)
//        {
//            final PotBlockEntity pot = (PotBlockEntity) entity;
//
//            if (InventoryUtils.hasItemInItemHandler((worker.getInventoryCitizen()), this::isSmeltable)
//                  && (hasFuelInFurnaceAndNoFood(pot) || hasNeitherFuelNorFood(pot)))
//            {
//                InventoryUtils.transferXOfFirstSlotInItemHandlerWithIntoInItemHandler(
//                  (worker.getInventoryCitizen()), this::isSmeltable, STACKSIZE,
//                  new InvWrapper((Container)pot), SMELTABLE_SLOT);
//            }
//
//            final ItemListModule module = building.getModuleMatching(ItemListModule.class, m -> m.getId().equals(FUEL_LIST));
//            if (InventoryUtils.hasItemInItemHandler((worker.getInventoryCitizen()), stack -> module.isItemInList(new ItemStorage(stack)))
//                  && (hasFoodInFurnaceAndNoFuel(pot) || hasNeitherFuelNorFood(pot)))
//            {
//                InventoryUtils.transferXOfFirstSlotInItemHandlerWithIntoInItemHandler(
//                  (worker.getInventoryCitizen()), stack -> module.isItemInList(new ItemStorage(stack)), STACKSIZE,
//                  new InvWrapper((Container)pot), FUEL_SLOT);
////                  new InvWrapper((Container)pot), SLOT_EXTRA_INPUT_START);
//            }
//        }
//        walkTo = null;
//        return START_WORKING;
//    }
//
//    /**
//     * Smeltabel the worker requires. Each worker has to override this.
//     *
//     * @return the type of it.
//     */
//    protected abstract IRequestable getPotAbleClass();
//}
