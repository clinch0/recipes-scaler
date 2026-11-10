package dev.portfolio.recipes

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

class DecimalConverter {
    @TypeConverter fun from(value: BigDecimal?): String? = value?.toPlainString()
    @TypeConverter fun to(value: String?): BigDecimal? = value?.let(::BigDecimal)
}

@Entity(tableName = "categories") data class Category(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String)
@Entity(tableName = "recipes", foreignKeys = [ForeignKey(entity = Category::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.CASCADE)], indices = [Index("categoryId")])
data class Recipe(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val categoryId: Long, val method: String, val yieldPerPortion: BigDecimal)
@Entity(tableName = "ingredients", foreignKeys = [ForeignKey(entity = Recipe::class, parentColumns = ["id"], childColumns = ["recipeId"], onDelete = ForeignKey.CASCADE)], indices = [Index("recipeId")])
data class Ingredient(@PrimaryKey(autoGenerate = true) val id: Long = 0, val recipeId: Long, val name: String, val amountPerPortion: BigDecimal)
data class RecipeCard(@Embedded val recipe: Recipe, @Relation(parentColumn = "id", entityColumn = "recipeId") val ingredients: List<Ingredient>)

@Dao interface RecipeDao {
    @Query("SELECT * FROM categories ORDER BY id") fun categories(): Flow<List<Category>>
    @Transaction @Query("SELECT * FROM recipes ORDER BY name") fun recipes(): Flow<List<RecipeCard>>
    @Insert suspend fun insertCategory(value: Category): Long
    @Insert suspend fun insertRecipe(value: Recipe): Long
    @Insert suspend fun insertIngredients(value: List<Ingredient>)
    @Query("DELETE FROM ingredients WHERE recipeId=:id") suspend fun deleteIngredients(id: Long)
    @Query("DELETE FROM recipes WHERE id=:id") suspend fun deleteRecipe(id: Long)
    @Query("SELECT COUNT(*) FROM categories") suspend fun categoryCount(): Int
    @Query("SELECT COUNT(*) FROM recipes") suspend fun recipeCount(): Int
}

@Database(entities = [Category::class, Recipe::class, Ingredient::class], version = 1, exportSchema = false)
@TypeConverters(DecimalConverter::class)
abstract class RecipeDatabase : RoomDatabase() { abstract fun recipeDao(): RecipeDao }

class RecipeRepository(private val dao: RecipeDao) {
    val categories = dao.categories(); val recipes = dao.recipes()
    suspend fun seed() {
        if (dao.categoryCount() > 0) return
        val names = listOf("Супы", "Гарниры", "Напитки", "Салаты", "Горячее")
        val ids = names.map { dao.insertCategory(Category(name = it)) }
        val samples = listOf(
            Sample("Крем-суп из кабачка", 0, "Овощи припустить, пробить блендером и довести до кипения.", "320", listOf("Кабачок" to "180", "Картофель" to "70", "Сливки" to "50", "Вода" to "120")),
            Sample("Гречка с зеленью", 1, "Крупу промыть, сварить до мягкости, добавить масло и зелень.", "220", listOf("Гречка" to "80", "Вода" to "160", "Масло" to "8")),
            Sample("Морс ягодный", 2, "Ягоды размять, залить горячей водой, настоять и процедить.", "250", listOf("Ягоды" to "45", "Вода" to "220", "Сахар" to "12")),
            Sample("Салат хрустящий", 3, "Овощи нарезать, смешать и заправить перед подачей.", "170", listOf("Огурец" to "80", "Редис" to "55", "Йогурт" to "25", "Укроп" to "3")),
            Sample("Курица с паприкой", 4, "Филе приправить и запечь до готовности.", "190", listOf("Куриное филе" to "180", "Масло" to "6", "Паприка" to "1.5")))
        samples.forEach { s -> save(Recipe(name=s.name, categoryId=ids[s.category], method=s.method, yieldPerPortion=BigDecimal(s.yield)), s.items.map { Ingredient(name=it.first, amountPerPortion=BigDecimal(it.second), recipeId=0) }) }
    }
    suspend fun save(recipe: Recipe, ingredients: List<Ingredient>) {
        val id = dao.insertRecipe(recipe)
        dao.insertIngredients(ingredients.map { it.copy(recipeId = id) })
    }
    suspend fun delete(id: Long) = dao.deleteRecipe(id)
    private data class Sample(val name:String,val category:Int,val method:String,val yield:String,val items:List<Pair<String,String>>)
}

fun scaled(amount: BigDecimal, portions: Int): BigDecimal = amount.multiply(portions.toBigDecimal())
fun formatGrams(value: BigDecimal): String = value.stripTrailingZeros().toPlainString().replace('.', ',')
