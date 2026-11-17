package com.tk.executor

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

private val Bg = Color(0xFF08040F)
private val Panel = Color(0xFF120A1D)
private val Panel2 = Color(0xFF21102F)
private val Purple = Color(0xFF9B35FF)
private val Purple2 = Color(0xFFB94DFF)
private val Muted = Color(0xFF9B8EAA)
private val White = Color(0xFFF8F3FF)

data class Script(val id: Long, val name: String, val code: String)

class ScriptStore(private val activity: MainActivity) {
    private val prefs = activity.getSharedPreferences("vortex_scripts", 0)

    fun all(): List<Script> {
        val arr = JSONArray(prefs.getString("items", "[]"))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Script(o.optLong("id"), o.optString("name"), o.optString("code"))
        }
    }

    fun save(script: Script) {
        val list = all().filterNot { it.id == script.id } + script
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("name", it.name).put("code", it.code))
        }
        prefs.edit().putString("items", arr.toString()).apply()
    }

    fun delete(id: Long) {
        val arr = JSONArray()
        all().filterNot { it.id == id }.forEach {
            arr.put(JSONObject().put("id", it.id).put("name", it.name).put("code", it.code))
        }
        prefs.edit().putString("items", arr.toString()).apply()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = ScriptStore(this)
        setContent { VortexApp(store) }
    }

    fun openRoblox() {
        val launch = packageManager.getLaunchIntentForPackage("com.roblox.client")
        if (launch != null) {
            startActivity(launch)
        } else {
            Toast.makeText(this, "Roblox não está instalado.", Toast.LENGTH_LONG).show()
        }
    }

    fun startOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        } else {
            startService(Intent(this, OverlayService::class.java))
            Toast.makeText(this, "Ícone VORTEX ativado.", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun VortexApp(store: ScriptStore) {
    var page by remember { mutableStateOf("Editor") }
    var scripts by remember { mutableStateOf(store.all()) }
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var scriptName by remember { mutableStateOf("Meu script") }
    var code by remember { mutableStateOf("-- VORTEX | ambiente de testes\n\nprint(\"Olá, mundo!\")") }
    var console by remember { mutableStateOf("Console pronto. Runtime Luau isolado não está incluído nesta versão.") }

    MaterialTheme(colorScheme = darkColorScheme(
        background = Bg, surface = Panel, primary = Purple, onBackground = White,
        onSurface = White, secondary = Muted
    )) {
        Column(Modifier.fillMaxSize().background(Bg)) {
            Header()

            when (page) {
                "Início" -> HomeScreen(
                    onRoblox = { storeActivity()?.openRoblox() },
                    onOverlay = { storeActivity()?.startOverlay() },
                    onEditor = { page = "Editor" }
                )
                "Editor" -> EditorScreen(
                    name = scriptName,
                    onName = { scriptName = it },
                    code = code,
                    onCode = { code = it },
                    console = console,
                    onSave = {
                        val id = selectedId ?: System.currentTimeMillis()
                        selectedId = id
                        store.save(Script(id, scriptName.ifBlank { "Sem título" }, code))
                        scripts = store.all()
                        console = "Script salvo localmente: ${scriptName.ifBlank { "Sem título" }}"
                    },
                    onRun = {
                        console = "Recebido pelo ambiente de teste. Execução dentro do cliente Roblox não é realizada."
                    },
                    onClear = { code = ""; console = "Editor limpo." }
                )
                "Biblioteca" -> LibraryScreen(
                    scripts = scripts,
                    onOpen = { s ->
                        selectedId = s.id
                        scriptName = s.name
                        code = s.code
                        page = "Editor"
                    },
                    onDelete = {
                        store.delete(it)
                        scripts = store.all()
                    }
                )
                else -> SettingsScreen(
                    onRoblox = { storeActivity()?.openRoblox() },
                    onOverlay = { storeActivity()?.startOverlay() }
                )
            }

            Spacer(Modifier.weight(1f))

            NavigationBar(containerColor = Panel, contentColor = White) {
                listOf("Início", "Editor", "Biblioteca", "Ajustes").forEach { item ->
                    NavigationBarItem(
                        selected = page == item,
                        onClick = {
                            page = item
                            if (item == "Biblioteca") scripts = store.all()
                        },
                        icon = {
                            Icon(
                                when (item) {
                                    "Início" -> Icons.Default.Home
                                    "Editor" -> Icons.Default.Code
                                    "Biblioteca" -> Icons.Default.Folder
                                    else -> Icons.Default.Settings
                                },
                                contentDescription = item
                            )
                        },
                        label = { Text(item) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Purple2,
                            selectedTextColor = Purple2,
                            indicatorColor = Panel2,
                            unselectedIconColor = Muted,
                            unselectedTextColor = Muted
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun Header() {
    Row(
        Modifier.fillMaxWidth().background(Panel).padding(horizontal = 17.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).background(Purple, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("V", color = White, fontWeight = FontWeight.Black, fontSize = 19.sp)
        }
        Spacer(Modifier.width(11.dp))
        Column {
            Text("VORTEX", color = White, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
            Text("MOBILE SCRIPT WORKSPACE", color = Muted, fontSize = 9.sp, letterSpacing = 1.sp)
        }
        Spacer(Modifier.weight(1f))
        Surface(color = Color(0xFF243528), shape = RoundedCornerShape(50)) {
            Text("v1.0", Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = Color(0xFF8BE0A0), fontSize = 9.sp)
        }
    }
}

@Composable
private fun HomeScreen(onRoblox: () -> Unit, onOverlay: () -> Unit, onEditor: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(15.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        Box(
            Modifier.fillMaxWidth().background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(Color(0xFF32104E), Color(0xFF10071A))
                ),
                RoundedCornerShape(17.dp)
            ).padding(17.dp)
        ) {
            Column {
                Text("Vortex Mobile", color = White, fontSize = 23.sp, fontWeight = FontWeight.Black)
                Text("Painel auxiliar para Android", color = Muted, fontSize = 11.sp)
                Row(
                    Modifier.padding(top = 12.dp).background(Color(0x331E1230), RoundedCornerShape(30.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(7.dp).background(Color(0xFF4EF27D), RoundedCornerShape(50)))
                    Spacer(Modifier.width(6.dp))
                    Text("Pronto para usar", color = Color(0xFFB8D9BE), fontSize = 10.sp)
                }
            }
        }

        Button(
            onClick = onRoblox,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Purple)
        ) {
            Icon(Icons.Default.PlayArrow, null)
            Spacer(Modifier.width(7.dp))
            Text("ABRIR ROBLOX", fontWeight = FontWeight.Bold)
        }

        OutlinedButton(
            onClick = onOverlay,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Purple2)
        ) {
            Icon(Icons.Default.Launch, null)
            Spacer(Modifier.width(7.dp))
            Text("ATIVAR ÍCONE VORTEX")
        }

        Text("FERRAMENTAS", color = White, fontWeight = FontWeight.Bold, fontSize = 13.sp)

        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            ToolCard("Editor", "Lua / Luau", Icons.Default.Code, onEditor, Modifier.weight(1f))
            ToolCard("Biblioteca", "Scripts locais", Icons.Default.Folder, onEditor, Modifier.weight(1f))
        }

        Text(
            "O overlay é um painel auxiliar do Android e não injeta ou modifica o cliente Roblox.",
            color = Muted, fontSize = 10.sp
        )
    }
}

@Composable
private fun ToolCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier
) {
    Column(
        modifier.clickable { onClick() }.background(Panel, RoundedCornerShape(14.dp)).padding(14.dp)
    ) {
        Icon(icon, null, tint = Purple2, modifier = Modifier.size(25.dp))
        Spacer(Modifier.height(10.dp))
        Text(title, color = White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
        Text(subtitle, color = Muted, fontSize = 9.sp)
    }
}

@Composable
private fun EditorScreen(
    name: String, onName: (String) -> Unit,
    code: String, onCode: (String) -> Unit,
    console: String, onSave: () -> Unit, onRun: () -> Unit, onClear: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("EDITOR", color = White, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                Text("Escreva e organize seus scripts", color = Muted, fontSize = 11.sp)
            }
            Surface(color = Panel2, shape = RoundedCornerShape(8.dp)) {
                Text("LUA / LUAU", Modifier.padding(8.dp), color = Purple2, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }

        OutlinedTextField(
            value = name, onValueChange = onName, singleLine = true,
            label = { Text("Nome do script") }, modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Purple,
                unfocusedBorderColor = Panel2,
                focusedTextColor = White,
                unfocusedTextColor = White,
                focusedLabelColor = Purple,
                unfocusedLabelColor = Muted
            )
        )

        Column(
            Modifier.weight(1f).fillMaxWidth()
                .background(Panel, RoundedCornerShape(14.dp)).padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("●", color = Color(0xFFFF6B75), fontSize = 10.sp)
                Text("  ●", color = Color(0xFFFFC45C), fontSize = 10.sp)
                Text("  ●", color = Color(0xFF6DDB91), fontSize = 10.sp)
                Spacer(Modifier.weight(1f))
                Text("main.lua", color = Muted, fontSize = 10.sp)
            }
            Spacer(Modifier.height(10.dp))
            BasicTextField(
                value = code,
                onValueChange = onCode,
                modifier = Modifier.fillMaxSize(),
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = Color(0xFFE3D7F4),
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 20.sp
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Purple),
                decorationBox = { inner ->
                    Box {
                        if (code.isEmpty()) Text(
                            "Digite seu código Lua/Luau...",
                            color = Muted,
                            fontFamily = FontFamily.Monospace
                        )
                        inner()
                    }
                }
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Button(
                onClick = onRun,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Purple)
            ) {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(4.dp))
                Text("Testar")
            }

            OutlinedButton(onClick = onSave, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Save, null)
                Spacer(Modifier.width(4.dp))
                Text("Salvar")
            }

            OutlinedButton(onClick = onClear) {
                Icon(Icons.Default.Delete, null)
            }
        }

        Column(
            Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(12.dp)).padding(12.dp)
        ) {
            Text("CONSOLE", color = Purple2, fontWeight = FontWeight.Bold, fontSize = 10.sp)
            Spacer(Modifier.height(4.dp))
            Text(console, color = Muted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun LibraryScreen(scripts: List<Script>, onOpen: (Script) -> Unit, onDelete: (Long) -> Unit) {
    Column(Modifier.fillMaxSize().padding(14.dp)) {
        Text("BIBLIOTECA", color = White, fontWeight = FontWeight.Bold, fontSize = 19.sp)
        Text("${scripts.size} script(s) salvos neste dispositivo", color = Muted, fontSize = 11.sp)
        Spacer(Modifier.height(12.dp))

        if (scripts.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.FolderOpen, null, tint = Muted, modifier = Modifier.size(40.dp))
                    Text("Biblioteca vazia", color = Muted)
                    Text("Salve um script pelo editor.", color = Muted, fontSize = 10.sp)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(scripts, key = { it.id }) { script ->
                    Row(
                        Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(12.dp))
                            .clickable { onOpen(script) }.padding(13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Description, null, tint = Purple2)
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text(script.name, color = White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Text("${script.code.lines().size} linhas", color = Muted, fontSize = 9.sp)
                        }
                        IconButton(onClick = { onDelete(script.id) }) {
                            Icon(Icons.Default.Delete, null, tint = Muted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(onRoblox: () -> Unit, onOverlay: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("CONFIGURAÇÕES", color = White, fontWeight = FontWeight.Bold, fontSize = 19.sp)
        SettingButton("Abrir Roblox", Icons.Default.PlayArrow, onRoblox)
        SettingButton("Ativar ícone flutuante", Icons.Default.Launch, onOverlay)
        Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(12.dp)).padding(14.dp)) {
            Text("Sobre o VORTEX", color = White, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(5.dp))
            Text("VORTEX Mobile v1.0 • editor e biblioteca local.", color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SettingButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(50.dp)
    ) {
        Icon(icon, null, tint = Purple2)
        Spacer(Modifier.width(7.dp))
        Text(text)
    }
}

@Composable
private fun storeActivity(): MainActivity? {
    val context = androidx.compose.ui.platform.LocalContext.current
    return context as? MainActivity
}
