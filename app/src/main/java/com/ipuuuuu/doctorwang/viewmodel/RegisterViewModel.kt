package com.ipuuuuu.doctorwang.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ipuuuuu.doctorwang.data.repository.DoctorWangRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RegisterUiState(
    val account: String = "",
    val password: String = "",
    val name: String = "",
    val age: String = "",
    val height: String = "",
    val weight: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val registerSuccess: Boolean = false,
)

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val repository: DoctorWangRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    fun updateAccount(value: String) { _uiState.value = _uiState.value.copy(account = value, errorMessage = null) }
    fun updatePassword(value: String) { _uiState.value = _uiState.value.copy(password = value, errorMessage = null) }
    fun updateName(value: String) { _uiState.value = _uiState.value.copy(name = value, errorMessage = null) }
    fun updateAge(value: String) { _uiState.value = _uiState.value.copy(age = value, errorMessage = null) }
    fun updateHeight(value: String) { _uiState.value = _uiState.value.copy(height = value, errorMessage = null) }
    fun updateWeight(value: String) { _uiState.value = _uiState.value.copy(weight = value, errorMessage = null) }

    fun register() {
        val current = _uiState.value
        if (current.account.isBlank() || current.password.isBlank() || current.name.isBlank()) {
            _uiState.value = current.copy(errorMessage = "请填写账号、密码和姓名")
            return
        }
        if (current.password.length < 6) {
            _uiState.value = current.copy(errorMessage = "密码长度不能少于6位")
            return
        }
        val age = current.age.toIntOrNull()
        val height = current.height.toIntOrNull()
        val weight = current.weight.toFloatOrNull()
        // 上界与后端一致（BackendService：age 1..150、height 30..250、weight 1..500），
        // isFinite 拒绝 NaN/Infinity（"NaN".toFloatOrNull() 不会返回 null）。
        if (age == null || age !in 1..150) {
            _uiState.value = current.copy(errorMessage = "年龄需为 1–150 的整数")
            return
        }
        if (height == null || height !in 30..250) {
            _uiState.value = current.copy(errorMessage = "身高需为 30–250 cm 的整数")
            return
        }
        if (weight == null || !weight.isFinite() || weight !in 1f..500f) {
            _uiState.value = current.copy(errorMessage = "体重需为 1–500 kg 的正数")
            return
        }

        viewModelScope.launch {
            _uiState.value = current.copy(isLoading = true, errorMessage = null)
            repository.register(current.account, current.password, current.name, age, height, weight)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isLoading = false, registerSuccess = true)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = error.message ?: "注册失败")
                }
        }
    }
}
