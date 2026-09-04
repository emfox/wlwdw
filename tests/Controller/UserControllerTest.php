<?php

namespace App\Tests\Controller;

use App\Entity\User;
use Symfony\Component\PasswordHasher\Hasher\UserPasswordHasherInterface;

/**
 * User administration behind ROLE_ADMIN.
 *
 * While here we pin down two historical behaviours that deserve a closer
 * look: the empty-password fallback "000000" (Q1) and the plaintext
 * password field (Q2).
 */
class UserControllerTest extends AbstractAppTestCase
{
    private function createUserViaForm(string $username, string $password): void
    {
        $client = $this->adminClient();
        $crawler = $client->request('GET', '/user/new');
        self::assertResponseIsSuccessful();
        $form = $crawler->selectButton('保存用户')->form([
            'user[username]' => $username,
            'user[password]' => $password,
        ]);
        $client->submit($form);
        self::assertResponseRedirects('/user/', 302);
    }

    public function testIndexRequiresAuthentication(): void
    {
        $client = $this->client();
        $client->request('GET', '/user/');

        self::assertResponseRedirects('/login', 302);
    }

    public function testIndexForbiddenForRegularUser(): void
    {
        $client = $this->loginAs('user');
        $client->request('GET', '/user/');

        self::assertResponseStatusCodeSame(403);
    }

    public function testIndexAccessibleForAdmin(): void
    {
        $client = $this->adminClient();
        $client->request('GET', '/user/');

        self::assertResponseIsSuccessful();
    }

    public function testCreateUserWithExplicitPassword(): void
    {
        $this->createUserViaForm('alice', 'a-strong-pass');

        $em = $this->client()->getContainer()->get('doctrine')->getManager();
        $user = $em->getRepository(User::class)->findOneBy(['username' => 'alice']);
        self::assertNotNull($user);
        self::assertTrue($user->getEnabled());
        // The email is auto-generated from the username.
        self::assertSame('alice@user.wlwdw.com', $user->getEmail());

        // Drop the admin session, then prove the created user can log in.
        $client = $this->client();
        $client->request('POST', '/logout');
        $crawler = $client->request('GET', '/login');
        $loginForm = $crawler->selectButton('login')->form([
            '_username' => 'alice',
            '_password' => 'a-strong-pass',
        ]);
        $client->submit($loginForm);
        self::assertResponseRedirects('/', 302);
    }

    public function testCreateUserFallsBackToDefaultPassword(): void
    {
        // No password submitted: UserController::create() silently uses "000000".
        $this->createUserViaForm('bob', '');

        $client = $this->client();
        $client->request('POST', '/logout');
        $crawler = $client->request('GET', '/login');
        $loginForm = $crawler->selectButton('login')->form([
            '_username' => 'bob',
            '_password' => '000000',
        ]);
        $client->submit($loginForm);
        self::assertResponseRedirects('/', 302);
    }

    public function testEditUserChangesPassword(): void
    {
        $client = $this->adminClient();
        $container = $client->getContainer();
        $em = $container->get('doctrine')->getManager();
        $hasher = $container->get(UserPasswordHasherInterface::class);

        $user = new User();
        $user->setUsername('carol');
        $user->setEmail('carol@user.wlwdw.com');
        $user->setEnabled(true);
        $user->setPassword($hasher->hashPassword($user, 'old-pass'));
        $em->persist($user);
        $em->flush();

        $crawler = $client->request('GET', sprintf('/user/%d/edit', $user->getId()));
        self::assertResponseIsSuccessful();

        $form = $crawler->selectButton('确认修改')->form();
        $form['user[password]'] = 'new-pass';
        $client->submit($form);
        self::assertResponseRedirects('/user/', 302);

        // The new hash must accept "new-pass" and reject the old one.
        $em->clear();
        $fresh = $em->getRepository(User::class)->find($user->getId());
        self::assertTrue($hasher->isPasswordValid($fresh, 'new-pass'));
        self::assertFalse($hasher->isPasswordValid($fresh, 'old-pass'));
    }

    public function testDeleteUser(): void
    {
        $client = $this->adminClient();
        $user = $this->createUser('dave');
        $id = $user->getId();

        $crawler = $client->request('GET', sprintf('/user/%d/edit', $id));
        $deleteForm = $crawler->selectButton('删除该用户')->form();
        $client->submit($deleteForm);
        self::assertResponseRedirects('/user/', 302);

        $em = $client->getContainer()->get('doctrine')->getManager();
        self::assertNull($em->getRepository(User::class)->find($id));
    }
}
