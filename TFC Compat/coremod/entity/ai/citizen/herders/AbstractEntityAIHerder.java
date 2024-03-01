package com.minecolonies.coremod.entity.ai.citizen.herders;

import com.minecolonies.api.colony.permissions.OldRank;
import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.api.util.WorldUtil;
import com.minecolonies.api.util.constant.ToolType;
import com.minecolonies.coremod.colony.buildings.AbstractBuilding;
import com.minecolonies.coremod.colony.buildings.modules.AnimalHerdingModule;
import com.minecolonies.coremod.colony.jobs.AbstractJob;
import com.minecolonies.coremod.entity.ai.basic.AbstractEntityAIInteract;
import net.dries007.tfc.common.entities.livestock.TFCAnimal;
import net.dries007.tfc.common.entities.livestock.TFCAnimalProperties;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.*;
import static com.minecolonies.api.util.constant.Constants.TICKS_SECOND;
import static com.minecolonies.api.util.constant.ToolLevelConstants.TOOL_LEVEL_BRONZE;
import static com.minecolonies.api.util.constant.ToolLevelConstants.TOOL_LEVEL_COPPER;
import static net.dries007.tfc.common.entities.livestock.TFCAnimalProperties.Age.OLD;

/**
 * Abstract class for all Citizen Herder AIs
 */
public abstract class AbstractEntityAIHerder<J extends AbstractJob<?, J>, B extends AbstractBuilding> extends AbstractEntityAIInteract<J, B>
{
    /**
     * How many animals per hut level the worker should max have.
     */
//    private static final int ANIMAL_MULTIPLIER = 2;
    private static final int ANIMAL_MULTIPLIER = 3;

    /**
     * Amount of animals needed to bread.
     */
    private static final int NUM_OF_ANIMALS_TO_BREED = 2;

    /**
     * Request this many sets of breeding items at once, to reduce courier workload.
     */
    private static final int EXTRA_BREEDING_ITEMS_REQUEST = 8;

    /**
     * Butchering attack damage.
     */
    protected static final int BUTCHERING_ATTACK_DAMAGE = 5;

    /**
     * Distance two animals need to be inside to breed.
     */
    private static final int DISTANCE_TO_BREED = 10;

    /**
     * Delays used to setDelay()
     */
    private static final int BUTCHER_DELAY    = 20;
    private static final int DECIDING_DELAY   = 40;
    private static final int BREEDING_DELAY   = 40;

    /**
     * Level limit to feed children.
     */
    public static final int LIMIT_TO_FEED_CHILDREN = 0;
//    public static final int LIMIT_TO_FEED_CHILDREN = 10;

    /**
     * Number of actions needed to dump inventory.
     */
    private static final int ACTIONS_FOR_DUMP   = 10;

    /**
     * New born age.
     */
    private static final double MAX_ENTITY_AGE = AgeableMob.BABY_START_AGE;

    /**
     * Xp per action, like breed feed butcher
     */
    protected static final double XP_PER_ACTION = 0.5;

    /**
     * The current herding module we're working on
     */
    @Nullable protected AnimalHerdingModule current_module;

    /**
     * Selected breeding partners
     */
    private final List<TFCAnimal> animalsToBreed = new ArrayList<>();

    /**
     * Prevents retrying breeding too quickly if last attempt failed
     */
    private int breedTimeOut = 0;

    /**
     * Creates the abstract part of the AI. Always use this constructor!
     *
     * @param job the job to fulfill.
     */
    public AbstractEntityAIHerder(@NotNull final J job)
    {
        super(job);
        super.registerTargets(
          new AITarget(IDLE, START_WORKING, 1),
          new AITarget(START_WORKING, this::startWorkingAtOwnBuilding, TICKS_SECOND),
          new AITarget(PREPARING, this::prepareForHerding, TICKS_SECOND),
          new AITarget(DECIDE, this::decideWhatToDo, DECIDING_DELAY),
          new AITarget(HERDER_BREED, this::breedAnimals, BREEDING_DELAY),
          new AITarget(HERDER_BUTCHER, this::butcherAnimals, BUTCHER_DELAY),
          new AITarget(HERDER_PICKUP, this::pickupItems, TICKS_SECOND),
          new AITarget(HERDER_FEED, this::feedAnimals, TICKS_SECOND)
        );
        worker.setCanPickUpLoot(true);
    }

    @Override
    protected int getActionsDoneUntilDumping()
    {
        return ACTIONS_FOR_DUMP;
    }

    @NotNull
    @Override
    protected List<ItemStack> itemsNiceToHave()
    {
        final List<ItemStack> list = super.itemsNiceToHave();
        if (building.getSetting(AbstractBuilding.BREEDING).getValue() ||
                building.getSetting(AbstractBuilding.FEEDING).getValue())
        {
            for (final AnimalHerdingModule module : building.getModules(AnimalHerdingModule.class))
            {
                list.addAll(getRequestBreedingItems(module));
            }
        }
        return list;
    }

    /**
     * Get the extra tools needed for this job.
     *
     * @return a list of tools or empty.
     */
    @NotNull
    public List<ToolType> getExtraToolsNeeded()
    {
        final List<ToolType> toolsNeeded = new ArrayList<>();
        toolsNeeded.add(ToolType.AXE);
        return toolsNeeded;
    }

    /**
     * Get the extra items needed for this job.
     *
     * @return a list of items needed or empty.
     */
    @NotNull
    public List<ItemStack> getExtraItemsNeeded()
    {
        return new ArrayList<>();
    }


    /**
     * Decides what job the herder should switch to, breeding or Butchering.
     *
     * @return The next {@link IAIState} the herder should switch to, after executing this method.
     */
    public IAIState decideWhatToDo()
    {
        worker.getCitizenData().setVisibleStatus(VisibleCitizenStatus.WORKING);

        for (final AnimalHerdingModule module : building.getModules(AnimalHerdingModule.class))
        {
            final List<? extends TFCAnimal> animals = (List<? extends TFCAnimal>) searchForAnimals(module::isCompatible);
            if (animals.isEmpty())
            {
                continue;
            }

            current_module = module;

            int numOfBreedableAnimals = 0;
            int numOfFeedableAnimals = 0;
            for (final TFCAnimal entity : animals)
            {
                if (isBreedAble(entity))
                {
                    numOfBreedableAnimals++;
                }
                else if (isFeedAble(entity))
                {
                    numOfFeedableAnimals++;
                }
            }

            final boolean hasBreedingItem =
                    InventoryUtils.getItemCountInItemHandler((worker.getInventoryCitizen()),
                            (ItemStack stack) -> ItemStackUtils.compareItemStackListIgnoreStackSize(module.getBreedingItems(), stack)) > 1;

            if (!searchForItemsInArea().isEmpty())
            {
                return HERDER_PICKUP;
            }
            else if (maxAnimals(animals)) //need new logic
            {
                return HERDER_BUTCHER;
            }
//            else if (canFeedChildren() && numOfFeedableAnimals > 0 && hasBreedingItem)
            else if (numOfFeedableAnimals > 0 && hasBreedingItem)
            {
                return HERDER_FEED;
            }
//            else if ( canBreedChildren() && numOfBreedableAnimals >= NUM_OF_ANIMALS_TO_BREED && hasBreedingItem && breedTimeOut == 0)
            else if ( numOfBreedableAnimals >= NUM_OF_ANIMALS_TO_BREED && hasBreedingItem && breedTimeOut == 0) //0.1.22
//            else if ( numOfBreedableAnimals >= NUM_OF_ANIMALS_TO_BREED && hasBreedingItem) //0.1.23
            {
                return HERDER_BREED;
            }
        }

        if (breedTimeOut > 0)
        {
            --breedTimeOut;
        }
        return START_WORKING;
    }

    /**
     * Checks if we can breed this entity
     *
     * calls TFC Method
     *
     * @param entity to check
     * @return true if breed able
     */
//    protected boolean isBreedAble(final Animal entity)
//    {
//        return entity.getAge() == 0 && (entity.isInLove() || entity.canFallInLove());
//    }
    protected boolean isBreedAble(final TFCAnimal entity)
    {
//        return entity.canMate(entity); //0.1.1 test
        return !entity.isFertilized() && !entity.isBaby(); //0.1.24
    }

    /**
     * Checks if we can feed this entity
     *
     * @param entity to check
     * @return true if feed able
     */
    protected boolean isFeedAble(final TFCAnimal entity)
    {
//        return entity.isBaby() && MAX_ENTITY_AGE / entity.getAge() <= 1 + getSecondarySkillLevel()/100.0;
//        return entity.isHungry() && entity.getFamiliarity() < .99f; //0.1.1
        //        return entity.isHungry() && (entity.isBaby() && entity.getFamiliarity() < .99f) || (!entity.isBaby() && entity.getFamiliarity() < .35f); //0.1.3 does not work

        if(entity.isHungry() && entity.isBaby() && entity.getFamiliarity() < .99f) {
            return true;
        }
        if(entity.isHungry() && !entity.isBaby() && entity.getFamiliarity() < .35f) {
            return true;
        }
        return false; //0.1.4
    }

    protected boolean isButcherable(final TFCAnimal entity){
        if(entity.isMale() && !entity.isBaby()){
            return true;
        }
        return false;
    }

    /**
     * Whether or not this one can feed adults to breed children.
     * @return true if so.
     */
//    protected boolean canBreedChildren()
//    {
//        return building.getSetting(AbstractBuilding.BREEDING).getValue();
//    }

    /**
     * Whether or not this one can feed children to speed up growth.
     * @return true if so.
     */
//    protected boolean canFeedChildren()
//    {
//        return building.getSetting(AbstractBuilding.FEEDING).getValue() &&
//                getSecondarySkillLevel() >= LIMIT_TO_FEED_CHILDREN;
//    }

    /**
     * Redirects the herder to their building.
     *
     * @return The next {@link IAIState}.
     */
    private IAIState startWorkingAtOwnBuilding()
    {
        if (walkToBuilding())
        {
            return getState();
        }
        return PREPARING;
    }

    /**
     * Prepares the herder for herding
     *
     * @return The next {@link IAIState}.
     */
    private IAIState prepareForHerding()
    {
        if (current_module == null)
        {
            return DECIDE;
        }

        for (final ToolType tool : getExtraToolsNeeded())
        {
            if (checkForToolOrWeapon(tool))
            {
                return getState();
            }
        }

        if (building.getSetting(AbstractBuilding.BREEDING).getValue() ||
                building.getSetting(AbstractBuilding.FEEDING).getValue())
        {
            for (final ItemStack breedingItem : current_module.getBreedingItems())
            {
                checkIfRequestForItemExistOrCreateAsync(breedingItem, breedingItem.getCount() * EXTRA_BREEDING_ITEMS_REQUEST, breedingItem.getCount());
            }
        }

        for (final ItemStack item : getExtraItemsNeeded())
        {
            checkIfRequestForItemExistOrCreateAsync(item);
        }

        return DECIDE;
    }

    /**
     * Butcher some animals (Preferably Adults and not recently fed) that the herder looks after.
     *
     * @return The next {@link IAIState}.
     */
    protected IAIState butcherAnimals()
    {
        if (current_module == null)
        {
            return DECIDE;
        }

        final List<? extends TFCAnimal> animals = searchForAnimals(current_module::isCompatible);
        //TODO: also needs work


        int numOfMales = 0;
        for(final TFCAnimal entity : animals){
            if (isButcherable(entity)){
                numOfMales++;
            }
        }
        final TFCAnimal maleOne = animals.stream().filter(animal1 -> animal1.isMale()).findAny().orElse(null);
        final TFCAnimal maletwo = animals.stream().filter(animal2 ->animal2.isMale() && !animal2.equals(maleOne)).findAny().orElse(null);
        if(maleOne == null){
            return DECIDE;
        }
        if(maletwo == null){
            return DECIDE;
        }
        if (!equipTool(InteractionHand.MAIN_HAND, ToolType.AXE))
        {
            return START_WORKING;
        }

        if(numOfMales >= 2 && !maleOne.equals(maletwo)){
            int a = maleOne.getGeneticSize();
            int b = maletwo.getGeneticSize();
            if(a > b){
                butcherAnimal(maletwo);
            }
            else if(a < b){
                butcherAnimal(maleOne);
            }
            return DECIDE;
        }
        if (!maxAnimals(animals)) //needs logic to count male adults and their size, butchering smallest.
        {
            return DECIDE;
        }



        final TFCAnimal animal = animals
                                      .stream()
                                      .filter(animalToButcher -> !animalToButcher.isBaby()
//                                              && !animalToButcher.isInLove() //0.1.15 test removed
                                              && !animalToButcher.isFertilized())
                                      .findFirst()
                                      .orElse(null);

        if (animal == null)
        {
            return DECIDE;
        }
        if (animal.getAgeType() == TFCAnimalProperties.Age.OLD){
            butcherAnimal(animal);
        }

        butcherAnimal(animal);

        if (!animal.isAlive())
        {
            worker.getCitizenExperienceHandler().addExperience(XP_PER_ACTION);
            incrementActionsDoneAndDecSaturation();
        }

        return HERDER_BUTCHER;
    }

    /**
     * Breed some animals together.
     *
     * @return The next {@link IAIState}.
     */
    protected IAIState breedAnimals()
    {
        if (current_module == null)
        {
            worker.getCitizenItemHandler().removeHeldItem();
            return DECIDE;
        }

        if (breedTwoAnimals())
        {
            return getState();
        }

        final Predicate<TFCAnimal> predicate = ((Predicate<TFCAnimal>) current_module::isCompatible).and(this::isBreedAble);
        final List<? extends TFCAnimal> breedables = new ArrayList<>(searchForAnimals(predicate));
        Collections.shuffle(breedables);

//        final TFCAnimal animalOne = breedables.stream().findAny().orElse(null); //0.1.22
//
//        if (animalOne == null)
//        {
//            worker.getCitizenItemHandler().removeHeldItem();
//            breedTimeOut = 15;
//            return DECIDE;
//        }
//
//        final int oldAnimalOneLove = animalOne.getInLoveTime();
//        animalOne.setInLoveTime(5);
//
//        final TFCAnimal animalTwo = breedables.stream().filter(animal ->
//          {
//              if (animalOne.equals(animal) || animal.distanceTo(animalOne) > DISTANCE_TO_BREED)
//              {
//                  return false;
//              }
//
//              final int oldLove = animal.getInLoveTime();
//              animal.setInLoveTime(5);
//              final boolean canMate = animalOne.canMate(animal);
//              animal.setInLoveTime(oldLove);
//
//              return canMate;
//          }
//        ).findAny().orElse(null);
//
//        animalOne.setInLoveTime(oldAnimalOneLove);
//
//        if (animalTwo == null)
//        {
//            worker.getCitizenItemHandler().removeHeldItem();
//            breedTimeOut = 5;
//            return DECIDE;
//        }

//        //0.1.23
//        final TFCAnimal animalOne = breedables.stream().filter(animal ->!animal.isMale() && !animal.isFertilized()).findAny().orElse(null);
//        if (animalOne == null)
//        {
//            worker.getCitizenItemHandler().removeHeldItem();
//            breedTimeOut = 15;
//            return DECIDE;
//        }
//
//        final int oldAnimalOneLove = animalOne.getInLoveTime();
//        animalOne.setInLoveTime(5);
//
//        final TFCAnimal animalTwo = breedables.stream().filter(animal ->
//          {
//              if (!animal.isMale() || animal.distanceTo(animalOne) > DISTANCE_TO_BREED)
//              {
//                  return false;
//              }
//
//              final int oldLove = animal.getInLoveTime();
//              animal.setInLoveTime(5);
//              final boolean canMate = animalOne.canMate(animal);
//              animal.setInLoveTime(oldLove);
//
//              return canMate;
//          }
//        ).findAny().orElse(null);

//        //0.1.24
//        // TODO: still not working right
//        final TFCAnimal animalOne = breedables.stream().filter(animal ->!animal.isMale() && !animal.isFertilized()).findAny().orElse(null);
//        if (animalOne == null)
//        {
//            worker.getCitizenItemHandler().removeHeldItem();
//            breedTimeOut = 15;
//            return DECIDE;
//        }
//
//        final int oldAnimalOneLove = animalOne.getInLoveTime();
//        animalOne.setInLoveTime(5);
//
//        final TFCAnimal animalTwo = breedables.stream().filter(animal ->
//          {
//              if (!animal.isMale())
//              {
//                  return false;
//              }
//
//              final int oldLove = animal.getInLoveTime();
//              animal.setInLoveTime(5);
//              final boolean canMate = animalOne.canMate(animal);
//              animal.setInLoveTime(oldLove);
//
//              return canMate;
//          }
//        ).findAny().orElse(null);
//
//        animalOne.setInLoveTime(oldAnimalOneLove);
//
//        if (animalTwo == null)
//        {
//            worker.getCitizenItemHandler().removeHeldItem();
//            breedTimeOut = 5;
//            return DECIDE;
//        }
//
//        if (!equipItem(InteractionHand.MAIN_HAND, current_module.getBreedingItems()))
//        {
//            worker.getCitizenItemHandler().removeHeldItem();
//            return START_WORKING;
//        }

//        //0.1.25
//        final TFCAnimal animalOne = breedables.stream().filter(animal ->!animal.isMale() && !animal.isFertilized()).findAny().orElse(null);
//        if (animalOne == null)
//        {
//            worker.getCitizenItemHandler().removeHeldItem();
//            breedTimeOut = 15;
//            return DECIDE;
//        }
//
//        final TFCAnimal animalTwo = breedables.stream().filter(animal ->
//                {
//                    if (!animal.isMale())
//                    {
//                        return false;
//                    }
//
//                    final int oldLove = animal.getInLoveTime();
//                    final boolean canMate = animalOne.canMate(animal);
//                    animal.setInLoveTime(oldLove);
//
//                    return canMate;
//                }
//        ).findAny().orElse(null);
//
//        if (animalTwo == null)
//        {
//            worker.getCitizenItemHandler().removeHeldItem();
//            breedTimeOut = 5;
//            return DECIDE;
//        }
//
//        if (!equipItem(InteractionHand.MAIN_HAND, current_module.getBreedingItems()))
//        {
//            worker.getCitizenItemHandler().removeHeldItem();
//            return START_WORKING;
//        }

//        //0.1.26
//        final TFCAnimal animalOne = breedables.stream().filter(animal ->!animal.isMale() && !animal.isFertilized()).findAny().orElse(null);
//        if (animalOne == null)
//        {
//            worker.getCitizenItemHandler().removeHeldItem();
//            breedTimeOut = 15;
//            return DECIDE;
//        }
//
//        final TFCAnimal animalTwo = breedables.stream().filter(animal -> animal.isMale()).findAny().orElse(null);
//
//        if (animalTwo == null)
//        {
//            worker.getCitizenItemHandler().removeHeldItem();
//            breedTimeOut = 5;
//            return DECIDE;
//        }
//
//        if (!equipItem(InteractionHand.MAIN_HAND, current_module.getBreedingItems()))
//        {
//            worker.getCitizenItemHandler().removeHeldItem();
//            return START_WORKING;
//        }
//        if (!walkingToAnimal(animalOne))
//        {
//
//            worker.swing(InteractionHand.MAIN_HAND);
//            worker.getMainHandItem().shrink(1);
//            worker.getCitizenExperienceHandler().addExperience(XP_PER_ACTION);
//            animalOne.playSound(SoundEvents.GENERIC_EAT, 1.0F, 1.0F);
//            worker.getCitizenItemHandler().removeHeldItem();
//            animalOne.eatFood(worker.getMainHandItem(), InteractionHand.MAIN_HAND, getFakePlayer());
////            return DECIDE;
//        }

//        //0.1.27
//        final TFCAnimal animalOne = breedables.stream().filter(animal ->!animal.isMale() && animal.isReadyToMate()).findAny().orElse(null);
//        if (animalOne == null)
//        {
//            worker.getCitizenItemHandler().removeHeldItem();
//            breedTimeOut = 15;
//            return DECIDE;
//        }
//
//        final TFCAnimal animalTwo = breedables.stream().filter(animal -> animal.isMale()).findAny().orElse(null);
//
//        if (animalTwo == null)
//        {
//            worker.getCitizenItemHandler().removeHeldItem();
//            breedTimeOut = 5;
//            return DECIDE;
//        }
//
//        if (!equipItem(InteractionHand.MAIN_HAND, current_module.getBreedingItems()))
//        {
//            worker.getCitizenItemHandler().removeHeldItem();
//            return START_WORKING;
//        }
//        if (!walkingToAnimal(animalOne))
//        {
//
//            worker.swing(InteractionHand.MAIN_HAND);
//            worker.getMainHandItem().shrink(1);
//            worker.getCitizenExperienceHandler().addExperience(XP_PER_ACTION);
//            animalOne.playSound(SoundEvents.GENERIC_EAT, 1.0F, 1.0F);
//            worker.getCitizenItemHandler().removeHeldItem();
//            animalOne.eatFood(worker.getMainHandItem(), InteractionHand.MAIN_HAND, getFakePlayer());
//            breedTimeOut = 60;
////            return DECIDE;
//        }
//        animalsToBreed.add(animalOne);
//        animalsToBreed.add(animalTwo);
//
//        if (breedTwoAnimals())
//        {
//            return getState();
//        }

//        //0.1.28
//        final TFCAnimal animalOne = breedables.stream().filter(animal ->!animal.isMale() && animal.isReadyToMate()).findAny().orElse(null);
//        if (animalOne == null)
//        {
//            worker.getCitizenItemHandler().removeHeldItem();
//            breedTimeOut = 15;
//            return DECIDE;
//        }
//
//        final TFCAnimal animalTwo = breedables.stream().filter(animal -> animal.isMale()).findAny().orElse(null);
//
//        if (animalTwo == null)
//        {
//            worker.getCitizenItemHandler().removeHeldItem();
//            breedTimeOut = 5;
//            return DECIDE;
//        }
//
//        if (!equipItem(InteractionHand.MAIN_HAND, current_module.getBreedingItems()))
//        {
//            worker.getCitizenItemHandler().removeHeldItem();
//            return START_WORKING;
//        }
//        if (!walkingToAnimal(animalOne))
//        {
//            if(animalOne.isReadyToMate()){
//
//            worker.swing(InteractionHand.MAIN_HAND);
//            worker.getMainHandItem().shrink(1);
//            worker.getCitizenExperienceHandler().addExperience(XP_PER_ACTION);
//            animalOne.playSound(SoundEvents.GENERIC_EAT, 1.0F, 1.0F);
//            worker.getCitizenItemHandler().removeHeldItem();
//            animalOne.eatFood(worker.getMainHandItem(), InteractionHand.MAIN_HAND, getFakePlayer());
//            breedTimeOut = 60;
////            return DECIDE;
//            }
//        }

//        //0.1.29
//        final TFCAnimal animalOne = breedables.stream().filter(animal ->!animal.isMale() && animal.isReadyToMate()).findAny().orElse(null);
//        if (animalOne == null)
//        {
//            worker.getCitizenItemHandler().removeHeldItem();
//            breedTimeOut = 15;
//            return DECIDE;
//        }
//
//        final TFCAnimal animalTwo = breedables.stream().filter(animal -> animal.isMale()).findAny().orElse(null);
//
//        if (animalTwo == null)
//        {
//            worker.getCitizenItemHandler().removeHeldItem();
//            breedTimeOut = 5;
//            return DECIDE;
//        }
//
//        if (!equipItem(InteractionHand.MAIN_HAND, current_module.getBreedingItems()))
//        {
//            worker.getCitizenItemHandler().removeHeldItem();
//            return START_WORKING;
//        }
//        if (!walkingToAnimal(animalOne))
//        {
//            if(animalOne.isReadyToMate()){
//
//            worker.swing(InteractionHand.MAIN_HAND);
//            worker.getMainHandItem().shrink(1);
//            worker.getCitizenExperienceHandler().addExperience(XP_PER_ACTION);
//            animalOne.playSound(SoundEvents.GENERIC_EAT, 1.0F, 1.0F);
//            worker.getCitizenItemHandler().removeHeldItem();
//            animalOne.eatFood(worker.getMainHandItem(), InteractionHand.MAIN_HAND, getFakePlayer());
//            breedTimeOut = 60;
//            return DECIDE;
//            }
//        }
//        //0.1.31
//        final TFCAnimal animalOne = breedables.stream().filter(animal ->!animal.isMale() && !animal.isFertilized() && animal.isHungry()).findAny().orElse(null);
//        if (animalOne == null)
//        {
//            worker.getCitizenItemHandler().removeHeldItem();
//            breedTimeOut = 15;
//            return DECIDE;
//        }
//
//        final TFCAnimal animalTwo = breedables.stream().filter(animal -> animal.isMale() && animal.isHungry()).findAny().orElse(null);
//
//        if (animalTwo == null)
//        {
//            worker.getCitizenItemHandler().removeHeldItem();
//            breedTimeOut = 10;
//            return DECIDE;
//        }
//
//        if (!equipItem(InteractionHand.MAIN_HAND, current_module.getBreedingItems()))
//        {
//            worker.getCitizenItemHandler().removeHeldItem();
//            return START_WORKING;
//        }
//        if (!walkingToAnimal(animalOne))
//        {
//            worker.swing(InteractionHand.MAIN_HAND);
//            worker.getMainHandItem().shrink(1);
//            animalOne.eatFood(worker.getMainHandItem(), InteractionHand.MAIN_HAND, getFakePlayer());
//            animalOne.playSound(SoundEvents.GENERIC_EAT, 1.0F, 1.0F);
//            animalTwo.eatFood(worker.getMainHandItem(), InteractionHand.MAIN_HAND, getFakePlayer());
//            animalTwo.playSound(SoundEvents.GENERIC_EAT, 1.0F, 1.0F);
//            worker.swing(InteractionHand.MAIN_HAND);
//            worker.getMainHandItem().shrink(1);
//            worker.getCitizenExperienceHandler().addExperience(XP_PER_ACTION);
//            worker.getCitizenItemHandler().removeHeldItem();
//            breedTimeOut = 60;
//            return DECIDE;
//
//        }
        //0.1.33
        final TFCAnimal animalOne = breedables.stream().filter(animal ->!animal.isMale() && !animal.isFertilized() && animal.isHungry()).findAny().orElse(null);
        if (animalOne == null)
        {
            worker.getCitizenItemHandler().removeHeldItem();
            breedTimeOut = 15;
            return DECIDE;
        }

        final TFCAnimal animalTwo = breedables.stream().filter(animal -> animal.isMale() && animal.isHungry()).findAny().orElse(null);

        if (animalTwo == null)
        {
            worker.getCitizenItemHandler().removeHeldItem();
            breedTimeOut = 10;
            return DECIDE;
        }

        if (!equipItem(InteractionHand.MAIN_HAND, current_module.getBreedingItems()))
        {
            worker.getCitizenItemHandler().removeHeldItem();
            return START_WORKING;
        }

        animalsToBreed.add(animalOne);
        animalsToBreed.add(animalTwo);

        if (breedTwoAnimals())
        {
            return getState();
        }

        worker.getCitizenItemHandler().removeHeldItem();
        return IDLE;
    }

    /**
     * Breed some animals together.
     *
     * @return The next {@link IAIState}.
     */
    protected IAIState feedAnimals()
    {
        if (current_module == null)
        {
            return DECIDE;
        }

        final Predicate<TFCAnimal> predicate = ((Predicate<TFCAnimal>) current_module::isCompatible).and(this::isFeedAble);
        final TFCAnimal animalOne = searchForAnimals(predicate).stream().findAny().orElse(null);

        if (animalOne == null)
        {
            return DECIDE;
        }

        if (!equipItem(InteractionHand.MAIN_HAND, current_module.getBreedingItems()))
        {
            return START_WORKING;
        }

        if (!walkingToAnimal(animalOne))
        {

            worker.swing(InteractionHand.MAIN_HAND);
            worker.getMainHandItem().shrink(1);
            worker.getCitizenExperienceHandler().addExperience(XP_PER_ACTION);
            animalOne.playSound(SoundEvents.GENERIC_EAT, 1.0F, 1.0F);
            worker.getCitizenItemHandler().removeHeldItem();
            animalOne.eatFood(worker.getMainHandItem(), InteractionHand.MAIN_HAND, getFakePlayer());
            return DECIDE;
        }

        worker.decreaseSaturationForContinuousAction();
        return getState();
    }


    /**
     * Allows the worker to pickup any stray items around Hut. Specifically useful when he possibly leaves Butchered drops OR with chickens (that drop feathers and etc)!
     *
     * @return The next {@link IAIState}.
     */
    private IAIState pickupItems()
    {
        final List<? extends ItemEntity> items = searchForItemsInArea();

        if (!items.isEmpty() && walkToBlock(items.get(0).blockPosition()))
        {
            return getState();
        }

        incrementActionsDoneAndDecSaturation();

        return DECIDE;
    }

    /**
     * Find animals in area.
     *
     * @param predicate true if the animal is interesting.
     * @return a {@link Stream} of animals in the area.
     */
    public List<? extends TFCAnimal> searchForAnimals(final Predicate<TFCAnimal> predicate)
    {
        return WorldUtil.getEntitiesWithinBuilding(world, TFCAnimal.class, building, predicate);
    }

    public int getMaxAnimalMultiplier()
    {
        return ANIMAL_MULTIPLIER;
    }

    /**
     * Find items in hut area.
     *
     * @return the {@link List} of {@link ItemEntity} in the area.
     */
    public List<? extends ItemEntity> searchForItemsInArea()
    {
        return WorldUtil.getEntitiesWithinBuilding(world, ItemEntity.class, building, null);
    }

    /**
     * Lets the herder walk to the animal.
     *
     * @param animal the animal to walk to.
     * @return true if the herder is walking to the animal.
     */
    public boolean walkingToAnimal(final TFCAnimal animal)
    {
        if (animal != null)
        {
            return walkToBlock(animal.blockPosition());
        }
        else
        {
            return false;
        }
    }

    /**
     * Breed two animals together!
     * @return true if still working on it
     */ //0.1.26 remove test
    private boolean breedTwoAnimals()
    {
        for (final Iterator<TFCAnimal> it = animalsToBreed.iterator(); it.hasNext(); )
        {
            final TFCAnimal animal = it.next();
            if (animal.isInLove() || animal.isDeadOrDying())
            {
                it.remove();
            }
            else if (walkingToAnimal(animal))
            {
                break;
            }
            else
            {
                animal.setInLove(null);
                worker.swing(InteractionHand.MAIN_HAND);
                worker.getMainHandItem().shrink(1);
                worker.getCitizenExperienceHandler().addExperience(XP_PER_ACTION);
                worker.decreaseSaturationForAction();
                animal.eatFood(worker.getMainHandItem(), InteractionHand.MAIN_HAND, getFakePlayer());//0.1.33 added test
                it.remove();
            }
        }
        return !animalsToBreed.isEmpty();
    }

    /**
     * Returns true if animals list is above max. Returns false if animals list is within max.
     *
     * @param allAnimals the list of animals.
     * @return if amount of animals is over max.
     */
    public boolean maxAnimals(final List<? extends TFCAnimal> allAnimals)
    {
        final List<? extends TFCAnimal> animals = allAnimals.stream()
                .filter(animalToButcher -> !animalToButcher.isBaby()).toList();
        if (animals.isEmpty())
        {
            return false;
        }

        final int numOfAnimals = animals.size();
        final int maxAnimals = building.getBuildingLevel() * getMaxAnimalMultiplier();

        return numOfAnimals > maxAnimals;
    }

    /**
     * Sets the tool as held item.
     *
     * @param toolType the {@link ToolType} we want to equip
     * @param hand     the hand to equip it in.
     * @return true if the tool was equipped.
     */
    public boolean equipTool(final InteractionHand hand, final ToolType toolType)
    {
        if (getToolSlot(toolType) != -1)
        {
            worker.getCitizenItemHandler().setHeldItem(hand, getToolSlot(toolType));
            return true;
        }
        return false;
    }

    /**
     * Gets the slot in which the Tool is in.
     *
     * @param toolType this herders tool type.
     * @return slot number.
     */
    private int getToolSlot(final ToolType toolType)
    {
        final int slot = InventoryUtils.getFirstSlotOfItemHandlerContainingTool(getInventory(), toolType,
          TOOL_LEVEL_COPPER, building.getMaxToolLevel());

        if (slot == -1)
        {
            checkForToolOrWeapon(toolType);
        }
        return slot;
    }

    /**
     * Sets the {@link ItemStack} as held item or returns false.
     *
     * @param itemStacks the list of {@link ItemStack}s to equip one of.
     * @param hand       the hand to equip it in.
     * @return true if the item was equipped.
     */
    public boolean equipItem(final InteractionHand hand, final List<ItemStack> itemStacks)
    {
        for (final ItemStack itemStack : itemStacks)
        {
            if (checkIfRequestForItemExistOrCreateAsync(itemStack))
            {
                worker.getCitizenItemHandler().setHeldItem(hand, getItemSlot(itemStack.getItem()));
                return true;
            }
        }

        return false;
    }

    /**
     * Gets the slot in which the inserted item is in. (if any).
     *
     * @param item The {@link Item} to check for.
     * @return slot number -1 if not in INV.
     */
    public int getItemSlot(final Item item)
    {
        return InventoryUtils.findFirstSlotInItemHandlerWith(getInventory(), item);
    }

    /**
     * Butcher an animal.
     *
     * @param animal the {@link Animal} we are butchering
     */
    protected void butcherAnimal(@Nullable final TFCAnimal animal)
    {
        if (animal != null && !walkingToAnimal(animal) && !ItemStackUtils.isEmpty(worker.getMainHandItem()))
        {
            worker.swing(InteractionHand.MAIN_HAND);
            final DamageSource ds = animal.level.damageSources().playerAttack(getFakePlayer());
            animal.hurt(ds, (float) getButcheringAttackDamage());
            worker.getCitizenItemHandler().damageItemInHand(InteractionHand.MAIN_HAND, 1);
        }
    }

    /**
     * Get the attack damage to be used.
     * @return the attack damage.
     */
    public double getButcheringAttackDamage()
    {
        return BUTCHERING_ATTACK_DAMAGE;
    }

    /**
     * Gets an ItemStack of breedingItem for requesting, requests multiple items to decrease work for delivery man
     *
     * @param module the herding module.
     * @return the BreedingItem stacks.
     */
    public List<ItemStack> getRequestBreedingItems(final AnimalHerdingModule module)
    {
        final List<ItemStack> breedingItems = new ArrayList<>();

        // TODO: currently this will request some of all items, when really we should be happy with enough of *any* of
        //       these items ... but right now it doesn't matter anyway since these are currently all single item lists.
        for (final ItemStack stack : module.getBreedingItems())
        {
            final ItemStack requestable = stack.copy();
            ItemStackUtils.setSize(requestable, stack.getCount() * EXTRA_BREEDING_ITEMS_REQUEST);
            breedingItems.add(requestable);
        }

        return breedingItems;
    }
}
