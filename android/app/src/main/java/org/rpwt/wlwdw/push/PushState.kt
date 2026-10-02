package org.rpwt.wlwdw.push

/**
 * What the MQTT link is doing.
 *
 * Reported separately from the upload link because the two fail for different
 * reasons and are fixed differently: a dead push link means messages do not
 * arrive even while every report is going through.
 */
enum class PushState {

    /** The service is not running. */
    Idle,

    /** An attempt is in flight, or the loop is waiting to retry one. */
    Connecting,

    /** Connected and subscribed; the broker can reach this device. */
    Connected,

    /**
     * Nothing can be tried: there is no device id to authenticate as.
     *
     * Not "the broker refused us" -- that is [Connecting], because refusing is
     * what a wrong secret or an unregistered id does and retrying is what the
     * pre-rewrite service did about it.
     */
    Failed,
}
