package com.minecolonies.coremod.colony.buildings.workerbuildings;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.modules.IBuildingEventsModule;
import com.minecolonies.api.colony.buildings.modules.IHasRequiredItemsModule;
import com.minecolonies.api.colony.buildings.modules.IPersistentModule;
import com.minecolonies.api.colony.buildings.modules.settings.ISettingKey;
import com.minecolonies.api.colony.jobs.ModJobs;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.crafting.GenericRecipe;
import com.minecolonies.api.crafting.IGenericRecipe;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.util.constant.ToolType;
import com.minecolonies.coremod.colony.buildings.AbstractBuilding;
import com.minecolonies.coremod.colony.buildings.modules.AnimalHerdingModule;
import com.minecolonies.coremod.colony.buildings.modules.settings.IntSetting;
import com.minecolonies.coremod.colony.buildings.modules.settings.SettingKey;
import net.dries007.tfc.common.TFCTags;
import net.dries007.tfc.common.items.Food;
import net.dries007.tfc.common.items.TFCItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Cow;
import net.dries007.tfc.common.entities.livestock.DairyAnimal;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;

import static com.minecolonies.api.util.constant.Constants.MOD_ID;

/**
 * Creates a new building for the Cowboy.
 */
public class BuildingCowboy extends AbstractBuilding
{
    /**
     * Description of the job executed in the hut.
     */
    private static final String COWBOY = "cowboy";

    /**
     * The hut name, used for the lang string in the GUI
     */
    private static final String HUT_NAME = "cowboyhut";

    /**
     * Max building level of the hut.
     */
    private static final int MAX_BUILDING_LEVEL = 5;

    /**
     * Milking amount setting.
     */
    public static final ISettingKey<IntSetting> MILKING_AMOUNT  = new SettingKey<>(IntSetting.class, new ResourceLocation(MOD_ID, "milking_amount"));

    /**
     * Stewing amount setting.
     */
    public static final ISettingKey<IntSetting> STEWING_AMOUNT = new SettingKey<>(IntSetting.class, new ResourceLocation(MOD_ID, "stewing_amount"));

    /**
     * Milking days setting.
     */
    public static final ISettingKey<IntSetting> MILKING_DAYS  = new SettingKey<>(IntSetting.class, new ResourceLocation(MOD_ID, "milking_days"));

    /**
     * Instantiates the building.
     *
     * @param c the colony.
     * @param l the location.
     */
    public BuildingCowboy(final IColony c, final BlockPos l)
    {
        super(c, l);
    }

    @NotNull
    @Override
    public String getSchematicName()
    {
        return COWBOY;
    }

    @Override
    public int getMaxBuildingLevel()
    {
        return MAX_BUILDING_LEVEL;
    }

    @Override
    public boolean canEat(final ItemStack stack)
    {
        if (stack.getItem() == TFCItems.FOOD.get(Food.BARLEY_GRAIN).get()
                || stack.getItem() == TFCItems.FOOD.get(Food.WHEAT_GRAIN).get()
                || stack.getItem() == TFCItems.FOOD.get(Food.OAT_GRAIN).get()
                || stack.getItem() == TFCItems.FOOD.get(Food.RYE_GRAIN).get()
                || stack.getItem() == TFCItems.FOOD.get(Food.MAIZE_GRAIN).get()
                || stack.getItem() == TFCItems.FOOD.get(Food.RICE_GRAIN).get())
        {
            return false;
        }
        return super.canEat(stack);
    }

    /**
     * Cow (and Mooshroom) herding module
     */
    public static class HerdingModule extends AnimalHerdingModule implements IBuildingEventsModule, IHasRequiredItemsModule, IPersistentModule
    {
        private int currentMilk;
        private int currentStew;
        private int currentMilkDays;

        public HerdingModule()
        {
            super(ModJobs.cowboy.get(), a -> a instanceof DairyAnimal, new ItemStack(TFCItems.FOOD.get(Food.BARLEY_GRAIN).get() , 2));

        }

        @Override
        public Map<Predicate<ItemStack>, Tuple<Integer, Boolean>> getRequiredItemsAndAmount()
        {
            final int days = Math.max(1, getBuilding().getSetting(MILKING_DAYS).getValue());
            final int bucketsToKeep = (int) Math.ceil(2D * getBuilding().getSetting(MILKING_AMOUNT).getValue() / days);
//            final int bowlsToKeep = (int) Math.ceil(2D * getBuilding().getSetting(STEWING_AMOUNT).getValue() / days);

            final Map<Predicate<ItemStack>, Tuple<Integer, Boolean>> requiredItems = new HashMap<>();
            if (bucketsToKeep > 0)
            {
                requiredItems.put(s -> s.is(TFCItems.WOODEN_BUCKET.get()), new Tuple<>(bucketsToKeep, false));

            }


//            if (bowlsToKeep > 0)
//            {
//                requiredItems.put(s -> s.is(Items.BOWL), new Tuple<>(bowlsToKeep, false));
//            }
            return requiredItems;
        }

        @Override
        public Map<ItemStorage, Integer> reservedStacksExcluding(@Nullable IRequest<? extends IDeliverable> excluded)
        {
            return Collections.emptyMap();
        }

        @NotNull
        @Override
        public List<IGenericRecipe> getRecipesForDisplayPurposesOnly(@NotNull Animal animal)
        {
            final List<IGenericRecipe> recipes = new ArrayList<>(super.getRecipesForDisplayPurposesOnly(animal));

            if (animal instanceof MushroomCow)
            {
                recipes.add(new GenericRecipe(null,
                        new ItemStack(Items.MUSHROOM_STEW),                                                 // output
                        Collections.singletonList(new ItemStack(Items.SUSPICIOUS_STEW)),                    // alt output
                        Collections.emptyList(),                                                            // extra output
                        Collections.singletonList(Collections.singletonList(new ItemStack(Items.BOWL))),    // input
                        1, Blocks.AIR, null, ToolType.NONE, animal, Collections.emptyList(), 0));
            }
            else if (animal instanceof DairyAnimal)
            {
                recipes.add(new GenericRecipe(null,
                        new ItemStack(Items.MILK_BUCKET),                                                   // output
                        Collections.emptyList(),                                                            // alt output
                        Collections.emptyList(),                                                            // extra output
                        Collections.singletonList(Collections.singletonList(new ItemStack(TFCItems.WOODEN_BUCKET.get()))),  // input
                        1, Blocks.AIR, null, ToolType.NONE, animal, Collections.emptyList(), 0));
            }

            return recipes;
        }

        @Override
        public void serializeNBT(@NotNull CompoundTag compound)
        {
            compound.putInt("milkValue", currentMilk);
            compound.putInt("stewValue", currentStew);
            compound.putInt("milkDays", currentMilkDays);
        }

        @Override
        public void deserializeNBT(CompoundTag compound)
        {
            this.currentMilk = compound.getInt("milkValue");
            this.currentStew = compound.getInt("stewValue");
            this.currentMilkDays = compound.getInt("milkDays");
        }

        @Override
        public void onWakeUp()
        {
            ++this.currentMilkDays;

            if (this.currentMilkDays >= getBuilding().getSetting(MILKING_DAYS).getValue())
            {
                this.currentMilk = 0;
                this.currentStew = 0;
                this.currentMilkDays = 0;
            }
        }

        /**
         * @return true if the cowboy should be allowed to try to milk (not yet reached limit)
         */
        public boolean canTryToMilk()
        {
            return this.currentMilk < getBuilding().getSetting(MILKING_AMOUNT).getValue();
        }

        /**
         * @return true if the cowboy should be allowed to try to collect stew (not yet reached limit)
         */
        public boolean canTryToStew()
        {
            return this.currentStew < getBuilding().getSetting(STEWING_AMOUNT).getValue();
        }

        /**
         * Called to record successful milking.
         */
        public void onMilked()
        {
            ++this.currentMilk;
        }

        /**
         * Called to record successful stewing.
         */
        public void onStewed()
        {
            ++this.currentStew;
        }
    }
}
