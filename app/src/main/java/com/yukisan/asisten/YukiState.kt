package com.yukisan.asisten

enum class Dir { UP, DOWN, LEFT, RIGHT }

object YukiState {
    // Kontrol aktif atau tidak (diatur dari bubble ON/OFF)
    @Volatile
    var enabled: Boolean = true

    // Daftar paket aplikasi tempat tombol volume dan lambaian aktif.
    // Tambahkan paket lain di sini kalau mau dipakai di app lain.
    val targetPackages: Set<String> = setOf(
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill"
    )
}
