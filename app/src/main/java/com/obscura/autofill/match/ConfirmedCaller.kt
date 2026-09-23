package com.obscura.autofill.match

/**
 * The app an autofill screen works for, confirmed rather than taken on the word of an intent.
 *
 * Our screens are started from a PendingIntent that names the app being filled. That intent is
 * immutable and built by our own service, but "nothing else leads here" is a property of today's
 * code, not something the screen can see. So a screen confirms the caller itself, and the only
 * way to get a [ConfirmedCaller] is [confirm]: code that matches entries or saves links takes
 * this type, and a new path onto a screen cannot skip the check without the types showing it.
 */
class ConfirmedCaller private constructor(val identity: CallerIdentity) {

    companion object {

        /**
         * Null unless both hold:
         * - [claimedPackage], the package our intent names, is the app that actually started the
         *   screen: [startedBy] is what the system reports as the calling activity's package. The
         *   app starts our screens for a result, so the system knows it (measured, see
         *   AutofillPackageVisibilityTest.thePickerKnowsWhichAppStartedIt).
         * - its signing certificate can be read through [readIdentity]. Without it nothing can
         *   be matched to a link, and no link can be saved.
         */
        fun confirm(
            claimedPackage: String,
            startedBy: String?,
            readIdentity: (String) -> CallerIdentity?
        ): ConfirmedCaller? {
            if (claimedPackage.isEmpty() || startedBy != claimedPackage) return null
            val identity = readIdentity(claimedPackage) ?: return null
            if (identity.packageName != claimedPackage || identity.certificateHashes.isEmpty()) return null
            return ConfirmedCaller(identity)
        }
    }
}
