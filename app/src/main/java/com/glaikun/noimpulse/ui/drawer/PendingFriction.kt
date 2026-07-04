package com.glaikun.noimpulse.ui.drawer

import com.glaikun.noimpulse.model.AppEntry
import com.glaikun.noimpulse.model.FrictionRule

/**
 * A friction change waiting on the user to confirm it. [Strengthen] makes the app
 * harder to open (light confirmation); [Weaken] makes it easier (the UUID code gate).
 * Both carry the edits to run once confirmed: [remove] comes off, then [add] goes on.
 */
internal sealed interface PendingFriction {
    val app: AppEntry
    val remove: FrictionRule?
    val add: FrictionRule?

    data class Strengthen(
        override val app: AppEntry,
        override val remove: FrictionRule?, // an existing timer being replaced by a longer one
        override val add: FrictionRule,
    ) : PendingFriction

    data class Weaken(
        override val app: AppEntry,
        override val remove: FrictionRule,
        override val add: FrictionRule? = null, // set only when shortening a timer
    ) : PendingFriction
}
