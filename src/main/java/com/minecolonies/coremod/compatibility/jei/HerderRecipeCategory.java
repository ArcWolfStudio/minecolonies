package com.minecolonies.coremod.compatibility.jei;

import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.api.colony.jobs.IJob;
import com.minecolonies.api.crafting.IGenericRecipe;
import com.minecolonies.api.crafting.registry.CraftingType;
import com.minecolonies.api.util.constant.IToolType;
import com.minecolonies.api.util.constant.ToolType;
import com.minecolonies.coremod.colony.buildings.modules.AnimalHerdingModule;
import com.minecolonies.coremod.colony.crafting.LootTableAnalyzer;
import com.mojang.blaze3d.vertex.PoseStack;
import mezz.jei.api.gui.ITickTimer;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * The JEI recipe category for animal herders.
 */
@OnlyIn(Dist.CLIENT)
public class HerderRecipeCategory extends JobBasedRecipeCategory<HerderRecipeCategory.HerdingRecipe>
{
    private final AnimalHerdingModule herding;
    private final ITickTimer animalTimer;

    public HerderRecipeCategory(@NotNull final BuildingEntry building,
                                @NotNull final IJob<?> job,
                                @NotNull final AnimalHerdingModule herding,
                                @NotNull final IGuiHelper guiHelper)
    {
        super(job, createRecipeType(job), getCatalyst(building), guiHelper);

        this.herding = herding;
        this.animalTimer = guiHelper.createTickTimer(200, 359, false);
    }

    private static final int LOOT_SLOTS_X = CITIZEN_X + CITIZEN_W + 4;
    private static final int LOOT_SLOTS_W = WIDTH - LOOT_SLOTS_X;

    private static final int ANIMAL_W  = (WIDTH - CITIZEN_W) / 2;
    private static final int ANIMAL_H = CITIZEN_H - 10;
    private static final int ANIMAL_X = CITIZEN_X + CITIZEN_W + (WIDTH - CITIZEN_X - CITIZEN_W - ANIMAL_W) / 2;
    private static final int ANIMAL_Y = CITIZEN_Y - 20;

    @NotNull
    private static RecipeType<HerdingRecipe> createRecipeType(@NotNull final IJob<?> job)
    {
        final ResourceLocation uid = job.getJobRegistryEntry().getKey();
        return RecipeType.create(uid.getNamespace(), uid.getPath(), HerdingRecipe.class);
    }

    @NotNull
    @Override
    protected List<Component> generateInfoBlocks(@NotNull HerdingRecipe recipe)
    {
        return Collections.emptyList();
    }

    @Override
    public void setRecipe(@NotNull final IRecipeLayoutBuilder builder,
                          @NotNull final HerdingRecipe recipe,
                          @NotNull final IFocusGroup focuses)
    {
        addToolSlot(builder, recipe.getRequiredTool(), WIDTH - 18, CITIZEN_Y, true);
        builder.addSlot(RecipeIngredientRole.INPUT, CITIZEN_X + CITIZEN_W + 2, CITIZEN_Y)
                .setBackground(this.slot, -1, -1)
                .addItemStacks(recipe.getBreedingItems());

        if (!recipe.getDrops().isEmpty())
        {
            final int initialColumns = LOOT_SLOTS_W / this.slot.getWidth();
            final int rows = (recipe.getDrops().size() + initialColumns - 1) / initialColumns;
            final int columns = (recipe.getDrops().size() + rows - 1) / rows;
            final int startX = LOOT_SLOTS_X + (LOOT_SLOTS_W - (columns * this.slot.getWidth())) / 2;
            int x = startX;
            int y = CITIZEN_Y + CITIZEN_H - rows * this.slot.getHeight() + 1;
            int c = 0;

            for (final LootTableAnalyzer.LootDrop drop : recipe.getDrops())
            {
                builder.addSlot(RecipeIngredientRole.OUTPUT, x, y)
                        .setBackground(this.chanceSlot, -1, -1)
                        .addItemStacks(drop.getItemStacks())
                        .addTooltipCallback(new LootTableTooltipCallback(drop, recipe.getId()));
                if (++c >= columns)
                {
                    c = 0;
                    x = startX;
                    y += this.slot.getHeight();
                }
                else
                {
                    x += this.slot.getWidth();
                }
            }
        }
    }

    @Override
    public void draw(@NotNull final HerdingRecipe recipe,
                     @NotNull final IRecipeSlotsView recipeSlotsView,
                     @NotNull final PoseStack stack,
                     final double mouseX, final double mouseY)
    {
        super.draw(recipe, recipeSlotsView, stack, mouseX, mouseY);

        final LivingEntity animal = recipe.getAnimal();
        if (animal != null)
        {
            final float scale = ANIMAL_H / 2.4f;
            final int animal_cx = ANIMAL_X + (ANIMAL_W / 2);
            final int animal_cy = ANIMAL_Y + (ANIMAL_H / 2);
            final int animal_by = ANIMAL_Y + ANIMAL_H;
            final int offsetY = 16;
            final float yaw = animalTimer.getValue();
            final float headYaw = (float) Math.atan((animal_cx - mouseX) / 40.0F) * 40.0F + yaw;
            final float pitch = (float) Math.atan((animal_cy - offsetY - mouseY) / 40.0F) * 20.0F;
            RenderHelper.scissor(stack, ANIMAL_X, ANIMAL_Y, ANIMAL_W, ANIMAL_H);
            RenderHelper.renderEntity(stack, animal_cx, animal_by - offsetY, scale, headYaw, yaw, pitch, animal);
            RenderHelper.stopScissor();
        }
    }

    @NotNull
    @Override
    public List<HerdingRecipe> findRecipes(@NotNull final Map<CraftingType, List<IGenericRecipe>> vanilla, Level world)
    {
        final List<ItemStack> breedingItems = this.herding.getBreedingItems();

        final List<LootTableAnalyzer.LootDrop> drops = this.herding.getExpectedLoot();
        drops.sort(Comparator.comparing(LootTableAnalyzer.LootDrop::getProbability).reversed());

        final HerdingRecipe recipe = new HerdingRecipe(this.herding.getDefaultLootTable(), this.herding.getAnimalType(), breedingItems, drops);
        return Collections.singletonList(recipe);
    }

    public static class HerdingRecipe
    {
        @NotNull
        private final ResourceLocation id;
        @Nullable
        private final LivingEntity animal;
        @NotNull
        private final List<ItemStack> breedingItems;
        @NotNull
        private final List<LootTableAnalyzer.LootDrop> drops;

        public HerdingRecipe(@NotNull final ResourceLocation id,
                             @NotNull final EntityType<?> animalType,
                             @NotNull final List<ItemStack> breedingItems,
                             @NotNull final List<LootTableAnalyzer.LootDrop> drops)
        {
            this.id = id;
            this.animal = (LivingEntity) animalType.create(Minecraft.getInstance().level);
            this.breedingItems = breedingItems;
            this.drops = drops.size() > 18 ? LootTableAnalyzer.consolidate(drops) : drops;
        }

        @NotNull
        public ResourceLocation getId()
        {
            return this.id;
        }

        @Nullable
        public LivingEntity getAnimal()
        {
            return this.animal;
        }

        @NotNull
        public List<ItemStack> getBreedingItems()
        {
            return this.breedingItems;
        }

        @NotNull
        public IToolType getRequiredTool()
        {
            return ToolType.AXE;
        }

        @NotNull
        public List<LootTableAnalyzer.LootDrop> getDrops()
        {
            return this.drops;
        }
    }
}
