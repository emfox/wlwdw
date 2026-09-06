<?php

namespace App\Tests\Controller;

use Symfony\Component\HttpFoundation\Response;

/**
 * EMQX calls /mqtt/auth on every client CONNECT and interprets the JSON
 * response body: {"result":"allow","acl":[...]} authenticates with per-client
 * ACLs, {"result":"deny"} rejects. Credentials come from .env.test.
 */
class MqttAuthControllerTest extends AbstractAppTestCase
{
    public function testDeviceWithSharedSecretIsAllowedWithPerClientAcl(): void
    {
        $this->client()->request('POST', '/mqtt/auth', [], [], ['CONTENT_TYPE' => 'application/json'],
            json_encode(['username' => 'dev-uuid-1', 'password' => 'mqtt-device-test-secret'], JSON_THROW_ON_ERROR));

        self::assertResponseStatusCodeSame(Response::HTTP_OK);
        $body = $this->jsonBody($this->client());
        self::assertSame('allow', $body['result']);
        self::assertSame([
            ['permission' => 'allow', 'action' => 'subscribe', 'topic' => 'all'],
            ['permission' => 'allow', 'action' => 'subscribe', 'topic' => 'dev-uuid-1'],
            ['permission' => 'allow', 'action' => 'publish', 'topic' => 'dev-uuid-1'],
        ], $body['acl']);
    }

    public function testServerAccountIsAllowedWithFullAccess(): void
    {
        $this->client()->request('POST', '/mqtt/auth', [], [], ['CONTENT_TYPE' => 'application/json'],
            json_encode(['username' => 'mqtt-server', 'password' => 'mqtt-server-test-pass'], JSON_THROW_ON_ERROR));

        self::assertResponseStatusCodeSame(Response::HTTP_OK);
        $body = $this->jsonBody($this->client());
        self::assertSame('allow', $body['result']);
        self::assertSame([
            ['permission' => 'allow', 'action' => 'all', 'topic' => '#'],
        ], $body['acl']);
    }

    public function testWrongPasswordIsDenied(): void
    {
        $this->client()->request('POST', '/mqtt/auth', [], [], ['CONTENT_TYPE' => 'application/json'],
            json_encode(['username' => 'dev-uuid-1', 'password' => 'wrong'], JSON_THROW_ON_ERROR));

        self::assertResponseStatusCodeSame(Response::HTTP_OK);
        self::assertSame('deny', $this->jsonBody($this->client())['result']);
    }

    public function testEmptyUsernameIsDenied(): void
    {
        $this->client()->request('POST', '/mqtt/auth', [], [], ['CONTENT_TYPE' => 'application/json'],
            json_encode(['username' => '', 'password' => 'mqtt-device-test-secret'], JSON_THROW_ON_ERROR));

        self::assertResponseStatusCodeSame(Response::HTTP_OK);
        self::assertSame('deny', $this->jsonBody($this->client())['result']);
    }

    public function testServerSecretDoesNotAuthenticateDeviceAccount(): void
    {
        // device accounts may not use the server password (vice versa tested above)
        $this->client()->request('POST', '/mqtt/auth', [], [], ['CONTENT_TYPE' => 'application/json'],
            json_encode(['username' => 'dev-uuid-1', 'password' => 'mqtt-server-test-pass'], JSON_THROW_ON_ERROR));

        self::assertResponseStatusCodeSame(Response::HTTP_OK);
        self::assertSame('deny', $this->jsonBody($this->client())['result']);
    }

}
