package com.ipuuuuu.doctorwang.ui.screen.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ipuuuuu.doctorwang.viewmodel.ProfileViewModel

@Composable
fun HealthProfileScreen(viewModel: ProfileViewModel = hiltViewModel()) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    val profile = uiState.profile

    if (uiState.isLoading) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    if (uiState.errorMessage != null || profile == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = uiState.errorMessage ?: "健康档案加载失败", color = MaterialTheme.colorScheme.error)
            Button(onClick = viewModel::refresh, modifier = Modifier.padding(top = 12.dp)) {
                Text("重新加载")
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(text = profile.name, style = MaterialTheme.typography.headlineSmall)
            Text(text = "年龄：${profile.age}")
            Text(text = "身高：${profile.height} cm")
            Text(text = "体重：${profile.weight} kg")
        }
        item {
            Text(text = "既往疾病", style = MaterialTheme.typography.titleMedium)
        }
        items(profile.historyDiseases) {
            Text(text = "• $it")
        }
        item {
            Text(text = "检查记录", style = MaterialTheme.typography.titleMedium)
        }
        items(profile.records) { record ->
            Text(text = "• ${record.date} ${record.type}")
        }
    }
}
