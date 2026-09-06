<?php

namespace App\Service;

use PhpMqtt\Client\ConnectionSettings;
use PhpMqtt\Client\MqttClient;
use Psr\Log\LoggerInterface;

/**
 * Publishes messages to the self-hosted EMQX broker via MQTT (TCP 1883 on
 * the same host). Configured through MQTT_BROKER_* / MQTT_SERVER_* env vars.
 */
class MqttPublisher implements MessagePublisherInterface
{
    public function __construct(
        private readonly string $host,
        private readonly int $port,
        private readonly string $username,
        private readonly string $password,
        private readonly LoggerInterface $logger,
    ) {
    }

    /**
     * {@inheritDoc}
     */
    public function publish(string $topic, array $payload): bool
    {
        $settings = (new ConnectionSettings())
            ->setUsername($this->username)
            ->setPassword($this->password)
            ->setConnectTimeout(5)
            ->setSocketTimeout(5)
            ->setKeepAliveInterval(60);

        $client = new MqttClient($this->host, $this->port, null, MqttClient::MQTT_3_1_1);
        try {
            $client->connect($settings, true); // clean session: one-shot publisher
            $client->publish($topic, json_encode($payload, JSON_UNESCAPED_UNICODE), 1);
            $client->disconnect();

            return true;
        } catch (\Throwable $e) {
            $this->logger->error('MQTT publish failed', [
                'topic' => $topic,
                'error' => $e->getMessage(),
            ]);

            return false;
        }
    }
}
