package com.ilankog.coinwallet

object Fixtures {
    fun text(name: String): String =
        requireNotNull(Fixtures::class.java.classLoader?.getResource(name)) { "missing fixture $name" }.readText()
}
