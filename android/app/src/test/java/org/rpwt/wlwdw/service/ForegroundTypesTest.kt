package org.rpwt.wlwdw.service

import android.content.pm.ServiceInfo
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Which foreground service types the one service claims.
 *
 * Worth a test because the wrong answer is not a wrong word on a screen: the
 * system checks the type against the permissions the app holds and throws
 * `SecurityException` when they do not match, which takes the service -- and
 * with it the push link -- down. That is a failure that only shows up on a
 * device, so the decision is pinned here where it can be checked in a second.
 *
 * The two rules being encoded: `dataSync` can never be dropped (the push link
 * always runs, and the runtime type has to be a subset of the manifest's
 * `dataSync|location`), and `location` can never be claimed without the
 * permission it requires.
 */
class ForegroundTypesTest {

    private val push = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC

    private val location = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION

    @Test
    fun `push alone is dataSync`() {
        // The permission is held and tracking is off: still push only. The type
        // says what the service is doing, not what it could do.
        assertEquals(push, foregroundTypes(trackingRequested = false, hasLocationPermission = true))
    }

    @Test
    fun `tracking without a location permission falls back to dataSync`() {
        // The case that would throw: a user can switch tracking on before
        // granting location, and the loop reports the missing permission in its
        // own words rather than the service failing to start.
        assertEquals(push, foregroundTypes(trackingRequested = true, hasLocationPermission = false))
    }

    @Test
    fun `tracking with a location permission adds location`() {
        assertEquals(
            push or location,
            foregroundTypes(trackingRequested = true, hasLocationPermission = true),
        )
    }
}
