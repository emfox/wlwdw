<?php

namespace App\Tests\Controller;

/**
 * End-to-end form login flow.
 *
 * NOTE: the login form currently carries no CSRF token and form_login does not
 * enable CSRF either. Submitting through the crawler keeps these tests working
 * both with and without a token field, so they survive the upcoming CSRF fix.
 */
class LoginControllerTest extends AbstractAppTestCase
{
    public function testLoginPageIsPublic(): void
    {
        $client = $this->client();
        $crawler = $client->request('GET', '/login');

        self::assertResponseIsSuccessful();
        self::assertSelectorExists('form');
        self::assertCount(1, $crawler->filter('input[name="_username"]'));
        self::assertCount(1, $crawler->filter('input[name="_password"]'));
    }

    public function testLoginWithValidCredentials(): void
    {
        $this->createAdmin();
        $client = $this->client();

        $crawler = $client->request('GET', '/login');
        $form = $crawler->selectButton('login')->form([
            '_username' => 'admin',
            '_password' => 'test-pass',
        ]);
        $client->submit($form);

        self::assertResponseRedirects('/', 302);

        // The session must actually be authenticated: /user/ requires ROLE_ADMIN.
        $client->followRedirect();
        $client->request('GET', '/user/');
        self::assertResponseIsSuccessful();
    }

    public function testLoginWithWrongPasswordShowsError(): void
    {
        $this->createAdmin();
        $client = $this->client();

        $crawler = $client->request('GET', '/login');
        $form = $crawler->selectButton('login')->form([
            '_username' => 'admin',
            '_password' => 'wrong-password',
        ]);
        $client->submit($form);

        self::assertResponseRedirects('/login', 302);
        $crawler = $client->followRedirect();
        // The login template renders the (untranslated) security message.
        self::assertStringContainsString('Invalid credentials', $crawler->text());
    }

    public function testUnknownUserFailsLogin(): void
    {
        $client = $this->client();

        $crawler = $client->request('GET', '/login');
        $form = $crawler->selectButton('login')->form([
            '_username' => 'ghost',
            '_password' => 'whatever',
        ]);
        $client->submit($form);

        self::assertResponseRedirects('/login', 302);
    }

    public function testProtectedRouteRedirectsAnonymousUserToLogin(): void
    {
        $client = $this->client();
        $client->request('GET', '/user/');

        self::assertResponseRedirects('/login', 302);
    }

    public function testLogoutInvalidatesSession(): void
    {
        $client = $this->adminClient();
        $client->request('GET', '/user/');
        self::assertResponseIsSuccessful();

        $client->request('POST', '/logout');
        self::assertResponseRedirects('/', 302);

        // After logout the protected page must redirect to login again.
        $client->followRedirect();
        $client->request('GET', '/user/');
        self::assertResponseRedirects('/login', 302);
    }
}
