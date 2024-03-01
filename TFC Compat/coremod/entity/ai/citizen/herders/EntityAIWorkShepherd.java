package com.minecolonies.coremod.entity.ai.citizen.herders;

import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.constant.ToolType;
import com.minecolonies.coremod.Network;
import com.minecolonies.coremod.colony.buildings.workerbuildings.BuildingShepherd;
import com.minecolonies.coremod.colony.jobs.JobShepherd;
import com.minecolonies.coremod.network.messages.client.LocalizedParticleEffectMessage;
import net.dries007.tfc.common.entities.livestock.WoolyAnimal;
import net.dries007.tfc.common.items.TFCItems;
import net.dries007.tfc.common.entities.livestock.TFCAnimalProperties;
import net.dries007.tfc.util.Metal;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ItemLike;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.*;
import static com.minecolonies.api.util.constant.Constants.TICKS_SECOND;
import static com.minecolonies.api.util.constant.ToolLevelConstants.TOOL_LEVEL_COPPER;
import static net.minecraft.world.entity.animal.Sheep.ITEM_BY_DYE;

/**
 * The AI behind the {@link JobShepherd} for Breeding, Killing and Shearing sheep.
 */
public class EntityAIWorkShepherd extends AbstractEntityAIHerder<JobShepherd, BuildingShepherd>
{
    /**
     * Constants used for sheep dying calculations.
     */
//    private static final int HUNDRED_PERCENT_CHANCE      = 100;

    /**
     * Creates the abstract part of the AI. Always use this constructor!
     *
     * @param job the job to fulfill
     */
    public EntityAIWorkShepherd(@NotNull final JobShepherd job)
    {
        super(job);
        super.registerTargets(
//          new AITarget(SHEPHERD_SHEAR, this::shearSheep, TICKS_SECOND)
          new AITarget(SHEPHERD_SHEAR, this::shearWooly, TICKS_SECOND)
        );
    }

    @NotNull
    @Override
    public List<ToolType> getExtraToolsNeeded()
    {
        final List<ToolType> toolsNeeded = super.getExtraToolsNeeded();
        if (building.getSetting(BuildingShepherd.SHEARING).getValue())
        {
            toolsNeeded.add(ToolType.SHEARS);
        }
        return toolsNeeded;
    }
     @Override
    public Class<BuildingShepherd> getExpectedBuildingClass()
    {
        return BuildingShepherd.class;
    }

    @Override
    public IAIState decideWhatToDo()
    {
        final IAIState result = super.decideWhatToDo();

//        final Sheep shearingSheep = findShearableSheep();
        final WoolyAnimal shearingWooly = FindShearableWooly();

//        if (building.getSetting(BuildingShepherd.SHEARING).getValue() && result.equals(START_WORKING) && shearingSheep != null)
        if (building.getSetting(BuildingShepherd.SHEARING).getValue() && result.equals(START_WORKING) && shearingWooly != null)
        {
            return SHEPHERD_SHEAR;
        }

        worker.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

        return result;
    }

    @Override
    public double getButcheringAttackDamage()
    {
        return Math.max(1.0, getSecondarySkillLevel() / 10.0);
    }

    /**
     * @return a shearable {@link Sheep} or null.
     */
    @Nullable
//    private Sheep findShearableSheep()
    private WoolyAnimal FindShearableWooly()
    {
//        return searchForAnimals(a -> a instanceof Sheep sheepie && !sheepie.isSheared() && !sheepie.isBaby())
//        return searchForAnimals(a -> a instanceof WoolyAnimal sheepie && sheepie.hasProduct()) //!hasProduct has no wool, hasProduct has wool
        return searchForAnimals(a -> a instanceof WoolyAnimal sheepie && sheepie.isReadyForAnimalProduct()) //!hasProduct has no wool, hasProduct has wool 0.1.34
//                .stream().map(a -> (Sheep) a).findAny().orElse(null);
                .stream().map(a -> (WoolyAnimal) a).findAny().orElse(null);
    }

    /**
     * Shears a sheep, with a chance of dying it!
     * TFC ShearWooly
     *
     * @return The next {@link IAIState}
     */
//    private IAIState shearSheep()
    private IAIState shearWooly()
    {

//        final Sheep sheep = findShearableSheep();
        final WoolyAnimal sheep = FindShearableWooly();

        if (sheep == null)
        {
            return DECIDE;
        }

        if (!equipTool(InteractionHand.MAIN_HAND, ToolType.SHEARS))
        {
            return PREPARING;
        }

        if (worker.getMainHandItem() != null) {
            if (walkingToAnimal(sheep)) {
                return getState();
            }

            final List<ItemStack> items = new ArrayList<>();
            //0.1.19
//            BlockPos pos = BlockPos.containing(sheep.position());
//            if(sheep.isShearable(sheep.getWoolItem(),this.world,pos)){
//                worker.swing(InteractionHand.MAIN_HAND);
//
//                sheep.onSheared(getFakePlayer(),new ItemStack(TFCItems.WOOL.get()),this.world,pos,1);//0.1.8 0.1.15 re-added
////                sheep.onSheared(getFakePlayer(),sheep.getWoolItem(),this.world,pos,1);//0.1.11
//                if(sheep.getFamiliarity() < .99) {
//                    items.add(new ItemStack(TFCItems.WOOL.get()));
//                }
//                worker.getCitizenItemHandler().damageItemInHand(InteractionHand.MAIN_HAND, 1);
//                worker.getCitizenExperienceHandler().addExperience(XP_PER_ACTION);
//                incrementActionsDoneAndDecSaturation();
//        }
            //0.1.7 test retesting 0.1.20
//            if (!this.world.isClientSide)
//            {
//                BlockPos pos = BlockPos.containing(sheep.position());
//                sheep.isReadyForAnimalProduct();
//                sheep.getWoolItem();
//                sheep.onSheared(getFakePlayer(),new ItemStack(TFCItems.WOOL.get()),this.world,pos,1);//0.1.7
//            }
//
//            sheep.playSound(SoundEvents.SHEEP_SHEAR, 1.0F, 1.0F);
//            worker.getCitizenItemHandler().damageItemInHand(InteractionHand.MAIN_HAND, 1);
//            worker.getCitizenExperienceHandler().addExperience(XP_PER_ACTION);
//            incrementActionsDoneAndDecSaturation();

//            //0.1.21
//            if (!this.world.isClientSide)
//            {
//                BlockPos pos = BlockPos.containing(sheep.position());
//                sheep.isReadyForAnimalProduct();
//                sheep.onSheared(getFakePlayer(),new ItemStack(TFCItems.WOOL.get()),this.world,pos,1);//0.1.7
//            }
//            worker.swing(InteractionHand.MAIN_HAND);
//            sheep.getWoolItem();
//            sheep.playSound(SoundEvents.SHEEP_SHEAR, 1.0F, 1.0F);
//            worker.getCitizenItemHandler().damageItemInHand(InteractionHand.MAIN_HAND, 1);
//            worker.getCitizenExperienceHandler().addExperience(XP_PER_ACTION);
//            incrementActionsDoneAndDecSaturation();
//
//            for (final ItemStack item : items)
//            {
//                InventoryUtils.transferItemStackIntoNextBestSlotInItemHandler(item, (worker.getInventoryCitizen()));
//            }
//        }
            //0.1.22 Works as of 0.1.22
            if (!this.world.isClientSide)
            {
                BlockPos pos = BlockPos.containing(sheep.position());
                sheep.isReadyForAnimalProduct();
                sheep.onSheared(getFakePlayer(),new ItemStack(TFCItems.WOOL.get()),this.world,pos,1);//0.1.7
            }
            worker.swing(InteractionHand.MAIN_HAND);
            worker.getCitizenItemHandler().damageItemInHand(InteractionHand.MAIN_HAND, 1);
            worker.getCitizenExperienceHandler().addExperience(XP_PER_ACTION);
            incrementActionsDoneAndDecSaturation();
            int amount = sheep.getFamiliarity() > 0.99f ? 2 : 1;
            for(int j = 0; j < amount; ++j){
                items.add(new ItemStack(TFCItems.WOOL.get()));
            }

            for (final ItemStack item : items)
            {
                InventoryUtils.transferItemStackIntoNextBestSlotInItemHandler(item, (worker.getInventoryCitizen()));
            }
        }

        return DECIDE;
    }

    /**
     * Possibly dyes a sheep based on their Worker Hut Level
     * do not need to dye in TFC
     * @param sheep the {@link Sheep} to possibly dye.
     */
//    private void dyeSheepChance(final Sheep sheep)
//    {
//        if (building != null && building.getSetting(BuildingShepherd.DYEING).getValue())
//        {
//            final int chanceToDye = building.getBuildingLevel();
//            final int rand = worker.getRandom().nextInt(HUNDRED_PERCENT_CHANCE);
//
//            if (rand <= chanceToDye)
//            {
//                final DyeColor[] colors = DyeColor.values();
//                final int dyeIndex = worker.getRandom().nextInt(colors.length);
//                sheep.setColor(colors[dyeIndex]);
//            }
//        }
//    }
}
