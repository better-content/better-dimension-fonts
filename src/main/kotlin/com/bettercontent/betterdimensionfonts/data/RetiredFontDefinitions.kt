package com.bettercontent.betterdimensionfonts.data

/** Old generated configs survive removal of bundled defaults; never reactivate retired destinations. */
internal object RetiredFontDefinitions {
    fun isRetired(id: String?, targetDimension: String?, instanceTemplateId: String?): Boolean =
        id?.trim() == "ratlantis" ||
            listOf(targetDimension, instanceTemplateId).any {
                it?.trim() in setOf("ratlantis", "rats:ratlantis")
            }
}
