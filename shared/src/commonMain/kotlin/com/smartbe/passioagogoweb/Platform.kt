package com.smartbe.passioagogoweb

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform