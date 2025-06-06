package ru.packetdima.datascanner.scan.functions

import androidx.compose.runtime.mutableStateListOf
import info.downdetector.bigdatascanner.common.IDetectFunction
import kotlinx.serialization.Serializable
import org.jetbrains.skia.Pattern

@Serializable
data class UserSignature(
    override var name: String,
    override var writeName: String,
    val searchSignatures: MutableList<String> = mutableStateListOf()
) : IDetectFunction {

    override fun scan(text: String): Int {
        return searchSignatures.sumOf { sig -> Pattern.quote(sig).toRegex(RegexOption.IGNORE_CASE).findAll(text).count() }
    }

    override fun toString(): String {
        return this.name
    }
}