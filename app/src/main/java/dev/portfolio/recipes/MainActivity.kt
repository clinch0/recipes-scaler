@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.portfolio.recipes

import android.content.Context
import android.os.Bundle
import android.print.PrintManager
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal

data class RecipeUiState(val categories:List<Category> = emptyList(), val recipes:List<RecipeCard> = emptyList(), val selected:Map<Long,Int> = emptyMap())
class RecipeViewModel(private val repo: RecipeRepository): ViewModel() {
    private val selection = MutableStateFlow<Map<Long,Int>>(emptyMap())
    val state = combine(repo.categories, repo.recipes, selection, ::RecipeUiState).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RecipeUiState())
    init { viewModelScope.launch { repo.seed() } }
    fun toggle(id:Long) { selection.update { if(id in it) it-id else it+(id to 1) } }
    fun portions(id:Long,value:Int) { selection.update { it+(id to value.coerceAtLeast(0)) } }
    fun clear() { selection.value=emptyMap() }
    fun save(name:String, category:Long, yield:String, method:String, rows:List<Pair<String,String>>, done:()->Unit) = viewModelScope.launch {
        repo.save(Recipe(name=name.trim(),categoryId=category,method=method.trim(),yieldPerPortion=yield.toDecimal()), rows.map { Ingredient(recipeId=0,name=it.first.trim(),amountPerPortion=it.second.toDecimal()) }); done()
    }
}
private fun String.toDecimal()=BigDecimal(replace(',','.'))

class MainActivity:ComponentActivity(){
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{
        val repo=(application as PortfolioApp).repository
        val vm:RecipeViewModel=viewModel(factory=object:ViewModelProvider.Factory{override fun <T:ViewModel> create(c:Class<T>):T=RecipeViewModel(repo) as T})
        MaterialTheme(colorScheme=if(androidx.compose.foundation.isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()){RecipeApp(vm)}
    }}
}

@Composable fun RecipeApp(vm:RecipeViewModel){
    val state by vm.state.collectAsStateWithLifecycle(); var page by rememberSaveable{mutableStateOf("list")}; var confirm by remember{mutableStateOf(false)}
    when(page){
        "edit"->Editor(state.categories,onBack={page="list"},onSave={a,b,c,d,e->vm.save(a,b,c,d,e){page="list"}})
        "print"->PrintPreview(state,onBack={page="list"})
        else->Scaffold(topBar={TopAppBar(title={Text("Рецептура")})}){pad->
            Column(Modifier.padding(pad).padding(16.dp)){Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button({page="edit"}){Icon(Icons.Default.Add,null);Text("Добавить")};Button({page="print"},enabled=state.selected.isNotEmpty()){Text("Печать")};OutlinedButton({confirm=true},enabled=state.selected.isNotEmpty()){Text("Очистить все")}}
            Spacer(Modifier.height(12.dp));LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){state.categories.forEach{cat->item{Text(cat.name,style=MaterialTheme.typography.titleMedium)};items(state.recipes.filter{it.recipe.categoryId==cat.id},key={it.recipe.id}){card->Card{Row(Modifier.fillMaxWidth().padding(12.dp)){Checkbox(card.recipe.id in state.selected,{vm.toggle(card.recipe.id)});Column(Modifier.weight(1f)){Text(card.recipe.name);Text("Выход: ${formatGrams(card.recipe.yieldPerPortion)} г",style=MaterialTheme.typography.bodySmall)};OutlinedTextField(value=state.selected[card.recipe.id]?.toString()?:"1",onValueChange={vm.portions(card.recipe.id,it.toIntOrNull()?:0)},enabled=card.recipe.id in state.selected,label={Text("порций")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.width(100.dp))}}}}}}
        }
    }
    if(confirm) AlertDialog(onDismissRequest={confirm=false},title={Text("Сбросить выбор?")},text={Text("Рецепты останутся в базе.")},confirmButton={TextButton({vm.clear();confirm=false}){Text("Сбросить")}},dismissButton={TextButton({confirm=false}){Text("Отмена")}})
}

@Composable private fun Editor(categories:List<Category>,onBack:()->Unit,onSave:(String,Long,String,String,List<Pair<String,String>>)->Unit){
    var name by rememberSaveable{mutableStateOf("")};var category by rememberSaveable{mutableLongStateOf(0)};var yield by rememberSaveable{mutableStateOf("")};var method by rememberSaveable{mutableStateOf("")};val rows=remember{mutableStateListOf("" to "")};var error by remember{mutableStateOf("")}
    LaunchedEffect(categories){if(category==0L)category=categories.firstOrNull()?.id?:0}
    Scaffold(topBar={TopAppBar(title={Text("Новый рецепт")},navigationIcon={IconButton(onBack){Icon(Icons.Default.ArrowBack,"Назад")}})}){pad->LazyColumn(Modifier.padding(pad).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){item{OutlinedTextField(name,{name=it},label={Text("Название")},modifier=Modifier.fillMaxWidth())};item{Text("Категория");categories.forEach{FilterChip(selected=category==it.id,onClick={category=it.id},label={Text(it.name)},modifier=Modifier.padding(end=6.dp))}};items(rows.size){i->Row{OutlinedTextField(rows[i].first,{rows[i]=it to rows[i].second},label={Text("Ингредиент")},modifier=Modifier.weight(1f));OutlinedTextField(rows[i].second,{rows[i]=rows[i].first to it},label={Text("г")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.width(90.dp));IconButton({if(rows.size>1)rows.removeAt(i)}){Icon(Icons.Default.Delete,"Удалить")}}};item{TextButton({rows.add("" to "")}){Text("Добавить ингредиент")}};item{OutlinedTextField(yield,{yield=it},label={Text("Выход на порцию, г")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth())};item{OutlinedTextField(method,{method=it},label={Text("Технология")},minLines=4,modifier=Modifier.fillMaxWidth())};if(error.isNotEmpty())item{Text(error,color=MaterialTheme.colorScheme.error)};item{Button({val validRows=rows.filter{it.first.isNotBlank()&&runCatching{it.second.toDecimal()>BigDecimal.ZERO}.getOrDefault(false)};if(name.isBlank()||category==0L||runCatching{yield.toDecimal()>BigDecimal.ZERO}.getOrDefault(false).not()||validRows.size!=rows.size)error="Проверьте название, выход и ингредиенты" else onSave(name,category,yield,method,validRows)},modifier=Modifier.fillMaxWidth()){Text("Сохранить")}}}}
}

@Composable private fun PrintPreview(state:RecipeUiState,onBack:()->Unit){val context=LocalContext.current;val cards=state.recipes.filter{it.recipe.id in state.selected};val html=remember(cards,state.selected){buildHtml(cards,state.selected)};Scaffold(topBar={TopAppBar(title={Text("Предпросмотр")},navigationIcon={IconButton(onBack){Icon(Icons.Default.ArrowBack,"Назад")}})}){pad->Column(Modifier.padding(pad)){AndroidView(factory={WebView(it).apply{loadDataWithBaseURL(null,html,"text/html","UTF-8",null)}},modifier=Modifier.weight(1f));Button({val web=WebView(context);web.loadDataWithBaseURL(null,html,"text/html","UTF-8",null);web.webViewClient=object:android.webkit.WebViewClient(){override fun onPageFinished(v:WebView?,url:String?){(context.getSystemService(Context.PRINT_SERVICE) as PrintManager).print("Рецептура",web.createPrintDocumentAdapter("Рецептура"),null)}}},Modifier.fillMaxWidth().padding(16.dp)){Text("Печать / Сохранить в PDF")}}}
}
private fun buildHtml(cards:List<RecipeCard>,selected:Map<Long,Int>)="""<html><head><meta charset='utf-8'><style>body{font-family:sans-serif;padding:24px}table{border-collapse:collapse;width:100%}td,th{border:1px solid #999;padding:7px}.card{page-break-inside:avoid;margin-bottom:28px}</style></head><body>"""+cards.joinToString(""){c->val portions=selected[c.recipe.id]?:1;"<div class='card'><h2>${c.recipe.name} — $portions порц.</h2><p>${c.recipe.method}</p><table><tr><th>Ингредиент</th><th>Граммы</th></tr>${c.ingredients.joinToString(""){"<tr><td>${it.name}</td><td>${formatGrams(scaled(it.amountPerPortion,portions))}</td></tr>"}}<tr><td><b>Выход</b></td><td>${formatGrams(c.recipe.yieldPerPortion)}</td></tr></table></div>"}+"</body></html>"
