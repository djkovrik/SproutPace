package com.sedsoftware.sproutpace.component

/** Absolute preference setters may revisit an earlier value after another selection. */
val ScreenCommand.isImmediatePreference: Boolean
    get() = operation == "set-theme" || operation == "set-crash-reporting" || operation == "update-notifications"
