package dev.portfolio.recipes

import android.app.Application
import androidx.room.Room

class PortfolioApp : Application() {
    val database by lazy { Room.databaseBuilder(this, RecipeDatabase::class.java, "recipes.db").build() }
    val repository by lazy { RecipeRepository(database.recipeDao()) }
}
