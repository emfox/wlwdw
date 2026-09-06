<?php

namespace App\Service;

/**
 * Publishes a message row id to a device's MQTT topic.
 *
 * Implementations must not throw: a message is already persisted by the time
 * it is published, so a failed push is only logged (the client can still pull
 * it via /message/{id}/{devid} when it comes back online).
 */
interface MessagePublisherInterface
{
    /**
     * @param string $topic   device topic (the category/devid)
     * @param array<string, mixed> $payload
     */
    public function publish(string $topic, array $payload): bool;
}
