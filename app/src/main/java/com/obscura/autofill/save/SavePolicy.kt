package com.obscura.autofill.save

/**
 * When Obscura asks to save a login at all.
 *
 * A response carries a SaveInfo only if the save can really happen: the save screen is opened
 * through SaveCallback.onSuccess(IntentSender), which exists from API 28. Below that nothing
 * could show the user what is being saved, so nothing is offered.
 */
object SavePolicy {

    const val MIN_SDK = 28

    fun isSupported(sdkInt: Int): Boolean = sdkInt >= MIN_SDK

    /**
     * Whether a fill response may carry a SaveInfo. Never inside Obscura itself, never for a
     * form without a password field.
     */
    fun offersSave(sdkInt: Int, callerPackage: String?, ownPackage: String, hasPasswordField: Boolean): Boolean =
        isSupported(sdkInt) && callerPackage != null && callerPackage != ownPackage && hasPasswordField

    /**
     * Whether a save request is taken up: the same conditions, checked again on what arrived,
     * plus a password that was actually typed.
     */
    fun acceptsSave(sdkInt: Int, callerPackage: String?, ownPackage: String, typedPassword: CharArray?): Boolean =
        offersSave(sdkInt, callerPackage, ownPackage, hasPasswordField = true) &&
            typedPassword != null && typedPassword.isNotEmpty()
}
