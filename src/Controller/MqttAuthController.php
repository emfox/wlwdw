<?php

namespace App\Controller;

use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;

/**
 * MQTT broker authentication + per-client ACL callback.
 *
 * The EMQX broker calls this endpoint (HTTP POST, JSON) for every client
 * CONNECT. EMQX 5 interprets the response body, not the status code:
 *
 *   {"result": "allow", "acl": [...]}   -> authenticated, ACL applied first
 *   {"result": "deny"}                  -> CONNECT rejected
 *
 * The optional per-client "acl" rules (checked before any authorizer) make a
 * separate broker-side ACL file unnecessary:
 *   - devices (username = devid): may subscribe to "all" and to their own
 *     devid topic and publish only to their own topic.
 *   - app server ("mqtt-server"): publish/subscribe anything.
 * Anything not matched by the client ACL falls through to the broker's
 * authorizer chain, which is configured with no_match = deny.
 *
 * The endpoint is intentionally public (it performs its own credential
 * check, no session/CSRF applies).
 */
class MqttAuthController extends AbstractController
{
    public function __construct(
        private readonly string $deviceSharedSecret,
        private readonly string $serverPassword,
    ) {
    }

    #[Route('/mqtt/auth', name: 'mqtt_auth', methods: ['POST'])]
    public function authenticate(Request $request): Response
    {
        $data = json_decode((string) $request->getContent(), true);
        $username = (string) ($data['username'] ?? '');
        $password = (string) ($data['password'] ?? '');

        $isServer = 'mqtt-server' === $username;
        $expected = $isServer ? $this->serverPassword : $this->deviceSharedSecret;

        if ('' === $username || '' === $expected || !hash_equals($expected, $password)) {
            return $this->deny();
        }

        $acl = $isServer
            ? [['permission' => 'allow', 'action' => 'all', 'topic' => '#']]
            : [
                ['permission' => 'allow', 'action' => 'subscribe', 'topic' => 'all'],
                ['permission' => 'allow', 'action' => 'subscribe', 'topic' => $username],
                ['permission' => 'allow', 'action' => 'publish', 'topic' => $username],
            ];

        return new JsonResponse(['result' => 'allow', 'acl' => $acl]);
    }

    private function deny(): Response
    {
        return new JsonResponse(['result' => 'deny'], Response::HTTP_OK);
    }
}
