package com.ipuuuuu.doctorwang.mock.config

class ApiException(val code: String, override val message: String) : RuntimeException(message)
