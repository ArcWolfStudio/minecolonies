package com.minecolonies.api.items;

import com.minecolonies.api.util.constant.TagConstants;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

import static com.minecolonies.api.util.constant.Constants.MOD_ID;

public class ModTags
{
    /**
     * Flag to check if tags are already loaded.
     */
    public static boolean tagsLoaded = false;

    public static final TagKey<Block> decorationItems = BlockTags.create(TagConstants.DECORATION_ITEMS);
    public static final TagKey<Item>  concretePowder  = ItemTags.create(TagConstants.CONCRETE_POWDER);
    public static final TagKey<Block> concreteBlock = BlockTags.create(TagConstants.CONCRETE_BLOCK);
    public static final TagKey<Block> pathingBlocks = BlockTags.create(TagConstants.PATHING_BLOCKS);

    public static final TagKey<Block> colonyProtectionException = BlockTags.create(TagConstants.COLONYPROTECTIONEXCEPTION);
    public static final TagKey<Block> indestructible = BlockTags.create(TagConstants.INDESTRUCTIBLE);

    public static final TagKey<Block> oreChanceBlocks = BlockTags.create(TagConstants.ORECHANCEBLOCKS);

    public static final TagKey<Item> fungi = ItemTags.create(TagConstants.FUNGI);
    public static final TagKey<Item> compostables = ItemTags.create(TagConstants.COMPOSTABLES);
    public static final TagKey<Item> compostables_poor = ItemTags.create(TagConstants.COMPOSTABLES_POOR);
    public static final TagKey<Item> compostables_rich = ItemTags.create(TagConstants.COMPOSTABLES_RICH);

    public static final TagKey<Item> meshes = ItemTags.create(TagConstants.MESHES);

    public static final TagKey<Item> floristFlowers = ItemTags.create(TagConstants.FLORIST_FLOWERS);
    public static final TagKey<Item> excludedFood = ItemTags.create(TagConstants.EXCLUDED_FOOD);

    public static final TagKey<Item> breakable_ore = ItemTags.create(TagConstants.BREAKABLE_ORE);
    public static final TagKey<Item> raw_ore = ItemTags.create(TagConstants.RAW_ORE);

    public static final TagKey<EntityType<?>> hostile = TagKey.create(Registry.ENTITY_TYPE_REGISTRY, TagConstants.HOSTILE);
    public static final TagKey<EntityType<?>> mobAttackBlacklist = TagKey.create(Registry.ENTITY_TYPE_REGISTRY, TagConstants.MOB_ATTACK_BLACKLIST);

    public static final Map<String, TagKey<Item>> crafterProduct              = new HashMap<>();
    public static final Map<String, TagKey<Item>> crafterProductExclusions    = new HashMap<>();
    public static final Map<String, TagKey<Item>> crafterIngredient           = new HashMap<>();
    public static final Map<String, TagKey<Item>> crafterIngredientExclusions = new HashMap<>();

    /**
     * Tag specifier for Products to Include
     */
    private static final String PRODUCT = "_product";

    /**
     * Tag specifier for Products to Exclude
     */
    private static final String PRODUCT_EXCLUDED = "_product_excluded";

    /**
     * Tag specifier for Ingredients to include
     */
    private static final String INGREDIENT = "_ingredient";

    /**
     * Tag specifier for Ingredients to exclude
     */
    private static final String INGREDIENT_EXCLUDED = "_ingredient_excluded";

    public static void init()
    {
        initCrafterRules(TagConstants.CRAFTING_BAKER);  // both crafting and smelting
        initCrafterRules(TagConstants.CRAFTING_BLACKSMITH);
        initCrafterRules(TagConstants.CRAFTING_COOK);   // both crafting and smelting
        initCrafterRules(TagConstants.CRAFTING_DYER);
        initCrafterRules(TagConstants.CRAFTING_DYER_SMELTING);
        initCrafterRules(TagConstants.CRAFTING_FARMER);
        initCrafterRules(TagConstants.CRAFTING_FLETCHER);
        initCrafterRules(TagConstants.CRAFTING_GLASSBLOWER);
        initCrafterRules(TagConstants.CRAFTING_GLASSBLOWER_SMELTING);
        initCrafterRules(TagConstants.CRAFTING_MECHANIC);
        initCrafterRules(TagConstants.CRAFTING_PLANTATION);
        initCrafterRules(TagConstants.CRAFTING_SAWMILL);
        initCrafterRules(TagConstants.CRAFTING_STONEMASON);
        initCrafterRules(TagConstants.CRAFTING_STONE_SMELTERY);

        initCrafterRules(TagConstants.CRAFTING_REDUCEABLE);
    }

    /**
     * Initialize the four tags for a particular crafter
     * @param crafterName the string name of the crafter to initialize
     */
    private static void initCrafterRules(@NotNull final String crafterName)
    {
        final ResourceLocation products = new ResourceLocation(MOD_ID, crafterName.concat(PRODUCT));
        final ResourceLocation ingredients = new ResourceLocation(MOD_ID, crafterName.concat(INGREDIENT));
        final ResourceLocation productsExcluded = new ResourceLocation(MOD_ID, crafterName.concat(PRODUCT_EXCLUDED));
        final ResourceLocation ingredientsExcluded = new ResourceLocation(MOD_ID, crafterName.concat(INGREDIENT_EXCLUDED));

        crafterProduct.put(crafterName, ItemTags.create(products));
        crafterProductExclusions.put(crafterName, ItemTags.create(productsExcluded));
        crafterIngredient.put(crafterName, ItemTags.create(ingredients));
        crafterIngredientExclusions.put(crafterName, ItemTags.create(ingredientsExcluded));
    }

    private ModTags()
    {
        throw new IllegalStateException("Can not instantiate an instance of: ModTags. This is a utility class");
    }
}
