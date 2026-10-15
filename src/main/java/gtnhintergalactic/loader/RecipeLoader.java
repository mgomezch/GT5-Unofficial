package gtnhintergalactic.loader;

import gregtech.api.enums.ItemList;
import gregtech.api.util.GTModHandler;
import gtnhintergalactic.recipe.GasSiphonRecipes;
import gtnhintergalactic.recipe.SpaceMiningRecipes;
import gtnhintergalactic.recipe.SpacePumpingRecipes;

/**
 * Loader for all recipes
 *
 * @author minecraft7771
 */
public class RecipeLoader implements Runnable {

    /**
     * Execute the recipe loader
     */
    @Override
    public void run() {
        SpaceMiningRecipes.addAsteroids();
        SpacePumpingRecipes.addPumpingRecipes();
        GasSiphonRecipes.addPumpingRecipes();

        if (ItemList.PlanetarySiphon.hasBeenSet() && ItemList.PlanetaryGasSiphonController.hasBeenSet()) {
            GTModHandler.addShapelessCraftingRecipe(
                ItemList.PlanetarySiphon.get(1),
                GTModHandler.RecipeBits.DO_NOT_CHECK_FOR_COLLISIONS | GTModHandler.RecipeBits.BUFFERED,
                new Object[] { ItemList.PlanetaryGasSiphonController });
        }
    }
}
