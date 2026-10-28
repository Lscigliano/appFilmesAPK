package io.zaffa.filmes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Tela de login — nossa, não a do painel.
 *
 * O login continua sendo validado pelo painel; o que é nosso é o formulário e a
 * aparência. Depois de autenticar, a sessão é passada ao WebView, então o
 * catálogo abre já logado.
 *
 * A senha **não é guardada pelo app**: o que persiste é o cookie de sessão do
 * painel. Ver `painel/ClientePainel.kt` e docs/PROTOCOLO.md §2.
 */
@Composable
fun TelaLogin(modelo: ModeloPrincipal) {
    val estado by modelo.login.collectAsStateWithLifecycle()
    var senhaVisivel by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF170C2E), Cores.Fundo, Color(0xFF07040F)),
                ),
            )
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .verticalScroll(rememberScrollState())
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Cores.Violeta.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Cores.VioletaClaro,
                    modifier = Modifier.size(42.dp),
                )
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = "Filmes Full",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                color = Cores.Texto,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Entre com os dados da sua assinatura",
                style = MaterialTheme.typography.bodyMedium,
                color = Cores.TextoFraco,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(32.dp))

            OutlinedTextField(
                value = estado.usuario,
                onValueChange = modelo::aoDigitarUsuario,
                label = { Text("Usuário") },
                singleLine = true,
                enabled = !estado.ocupado,
                shape = RoundedCornerShape(12.dp),
                colors = coresDoCampo(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = estado.senha,
                onValueChange = modelo::aoDigitarSenha,
                label = { Text("Senha") },
                singleLine = true,
                enabled = !estado.ocupado,
                shape = RoundedCornerShape(12.dp),
                colors = coresDoCampo(),
                visualTransformation = if (senhaVisivel) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = { senhaVisivel = !senhaVisivel }) {
                        Icon(
                            imageVector = if (senhaVisivel) {
                                Icons.Filled.VisibilityOff
                            } else {
                                Icons.Filled.Visibility
                            },
                            contentDescription = if (senhaVisivel) "Ocultar senha" else "Mostrar senha",
                            tint = Cores.TextoFraco,
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(6.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = !estado.ocupado) { modelo.aoAlternarLembrar() }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = estado.lembrar,
                    onCheckedChange = { modelo.aoAlternarLembrar() },
                    enabled = !estado.ocupado,
                    colors = CheckboxDefaults.colors(
                        checkedColor = Cores.Violeta,
                        uncheckedColor = Cores.TextoFraco,
                    ),
                )
                Column {
                    Text(
                        text = "Lembrar meus dados",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Cores.Texto,
                    )
                    Text(
                        text = "Entra sozinho da próxima vez",
                        style = MaterialTheme.typography.bodySmall,
                        color = Cores.TextoFraco,
                    )
                }
            }

            if (estado.erro != null) {
                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Cores.Perigo.copy(alpha = 0.14f))
                        .padding(14.dp),
                ) {
                    Text(
                        text = estado.erro.orEmpty(),
                        color = Cores.Perigo,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Spacer(Modifier.height(26.dp))

            Button(
                onClick = modelo::entrar,
                enabled = !estado.ocupado,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
            ) {
                if (estado.ocupado) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Cores.Texto,
                    )
                } else {
                    Text("Entrar", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(28.dp))
            Text(
                text = "Seus dados ficam apenas neste aparelho. " +
                    "O app não guarda a sua senha.",
                style = MaterialTheme.typography.bodySmall,
                color = Cores.TextoFraco,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun coresDoCampo() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Cores.Violeta,
    unfocusedBorderColor = Cores.SuperficieAlta,
    focusedLabelColor = Cores.VioletaClaro,
    unfocusedLabelColor = Cores.TextoFraco,
    focusedTextColor = Cores.Texto,
    unfocusedTextColor = Cores.Texto,
    cursorColor = Cores.Violeta,
)
