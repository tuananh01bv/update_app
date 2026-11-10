package com.panda.update.model

enum class UpdateType(val value: String) {
    FORCE_UPDATE("force_update"),
    OPTIONAL_UPDATE("optional_update"),
    OFF("off_pop_up_update");

    companion object {
        fun fromValue(value: String?): UpdateType {
            return entries.find { it.value.equals(value, ignoreCase = true) } ?: OFF
        }
    }
}
