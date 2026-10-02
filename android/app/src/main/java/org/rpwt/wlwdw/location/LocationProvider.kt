package org.rpwt.wlwdw.location

/**
 * Where the device is, as the tracking loop uses it.
 *
 * An interface rather than [LocationSource] itself because the loop has to be
 * testable on the JVM and [LocationSource] cannot exist there: it is built
 * around a `Context`. These three calls are everything the loop needs from the
 * platform's location service, so they are the boundary.
 */
interface LocationProvider {

    /** Granted while the app is in use. */
    fun hasForegroundPermission(): Boolean

    /** Granted to run with the screen off. */
    fun hasBackgroundPermission(): Boolean

    /**
     * The best fix available now, or null if none can be had.
     *
     * Blocking is the caller's problem: the loop is already a coroutine, and
     * waiting up to twenty seconds for a GPS fix is exactly the kind of thing
     * that must not happen on the main thread.
     */
    suspend fun current(): Fix?
}
