<?php

namespace App\Tests\Controller;

/**
 * Root entry points and the /main/{maptype} viewer page.
 */
class DefaultControllerTest extends AbstractAppTestCase
{
    public function testRootRedirectsAnonymousUserToLogin(): void
    {
        $client = $this->client();
        $client->request('GET', '/');

        self::assertResponseRedirects('/login', 302);
    }

    public function testRootRedirectsLoggedInUserToMainGoogle(): void
    {
        $client = $this->loginAs('viewer');
        $client->request('GET', '/');

        // The RedirectController route is configured as a permanent redirect.
        self::assertResponseRedirects('/main/google', 301);
    }

    public function testMainGooglePageRendersForRegularUser(): void
    {
        $client = $this->loginAs('viewer');
        $client->request('GET', '/main/google');

        self::assertResponseIsSuccessful();
    }

    public function testMainBaiduPageRendersForRegularUser(): void
    {
        $client = $this->loginAs('viewer');
        $client->request('GET', '/main/baidu');

        self::assertResponseIsSuccessful();
    }
}
