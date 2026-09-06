<?php

namespace App\Tests\Service;

use App\Service\MessagePublisherInterface;

/**
 * Test double for MessagePublisherInterface: records every publish call so
 * functional tests can assert the controller publishes the right topic/payload
 * without a real MQTT broker.
 */
class SpyMessagePublisher implements MessagePublisherInterface
{
    /**
     * @var list<array{topic: string, payload: array<string, mixed>}>
     */
    public array $calls = [];

    public function publish(string $topic, array $payload): bool
    {
        $this->calls[] = ['topic' => $topic, 'payload' => $payload];

        return true;
    }
}
