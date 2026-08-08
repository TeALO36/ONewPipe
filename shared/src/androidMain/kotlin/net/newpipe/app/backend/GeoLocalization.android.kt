package net.newpipe.app.backend

actual fun systemCountryCode(): String = java.util.Locale.getDefault().country

actual fun systemLanguageCode(): String = java.util.Locale.getDefault().language
