package io.zaffa.filmes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun TelaCarregando() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Cores.Violeta)
    }
}

@Composable
fun TelaLogin(modelo: ModeloPrincipal) {
    val estado by modelo.login.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Filmes",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = Cores.VioletaClaro,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Entre com os dados da sua assinatura",
                style = MaterialTheme.typography.bodyMedium,
                color = Cores.TextoFraco,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))

            OutlinedTextField(
                value = estado.host,
                onValueChange = modelo::aoDigitarHost,
                label = { Text("Servidor") },
                placeholder = { Text("ex.: meupainel.com") },
                supportingText = {
                    Text(
                        "Já preenchido. Troque só se o seu painel for outro — " +
                            "o app descobre a porta sozinho.",
                        color = Cores.TextoFraco,
                    )
                },
                singleLine = true,
                enabled = !estado.ocupado,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = estado.usuario,
                onValueChange = modelo::aoDigitarUsuario,
                label = { Text("Usuário") },
                singleLine = true,
                enabled = !estado.ocupado,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = estado.senha,
                onValueChange = modelo::aoDigitarSenha,
                label = { Text("Senha") },
                singleLine = true,
                enabled = !estado.ocupado,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            if (estado.erro != null) {
                Spacer(Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = Cores.Perigo.copy(alpha = 0.15f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = estado.erro.orEmpty(),
                        modifier = Modifier.padding(14.dp),
                        color = Cores.Perigo,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = modelo::entrar,
                enabled = !estado.ocupado,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                if (estado.ocupado) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp),
                        strokeWidth = 2.dp,
                        color = Cores.Texto,
                    )
                } else {
                    Text("Entrar", fontWeight = FontWeight.SemiBold)
                }
            }

            if (estado.progresso != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = estado.progresso.orEmpty(),
                    color = Cores.TextoFraco,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(Modifier.height(32.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Seus dados ficam apenas neste aparelho.",
                    color = Cores.TextoFraco,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
