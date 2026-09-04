<?php

namespace App\Tests\Controller;

use App\Entity\Category;
use App\Entity\User;
use Psr\Container\ContainerInterface;
use Symfony\Bundle\FrameworkBundle\KernelBrowser;
use Symfony\Bundle\FrameworkBundle\Test\WebTestCase;
use Symfony\Component\PasswordHasher\Hasher\UserPasswordHasherInterface;

/**
 * Shared helpers for functional tests.
 *
 * Symfony 8 forbids booting the kernel before WebTestCase::createClient(),
 * so createClient() must be the FIRST kernel operation of every test. For
 * that reason every helper goes through the lazily created per-test client
 * (never through static::getContainer() directly).
 *
 * Each test gets its own client (fresh, unauthenticated). Within a test the
 * logged-in user is switched via loginUser() on the shared client.
 *
 * Data isolation is provided by dama/doctrine-test-bundle: every test method
 * runs inside a database transaction that is rolled back afterwards, so each
 * test can freely create the users/categories it needs without cleanup.
 */
abstract class AbstractAppTestCase extends WebTestCase
{
    private ?KernelBrowser $client = null;

    protected function tearDown(): void
    {
        parent::tearDown();
        $this->client = null;
    }

    /**
     * Lazily creates and returns the client for the current test.
     * Creating the client boots the kernel, so call this first.
     */
    protected function client(): KernelBrowser
    {
        return $this->client ??= static::createClient();
    }

    /**
     * Returns the container of the current test client.
     * Calling this boots the kernel through client(), so never call it before
     * createClient() has run -- every helper below already respects that.
     */
    protected function container(): ContainerInterface
    {
        return $this->client()->getContainer();
    }

    protected function createUser(string $username, array $roles = ['ROLE_USER']): User
    {
        $em = $this->container()->get('doctrine')->getManager();

        // Idempotent: several helpers may ask for the same user within one
        // test (the username column is unique).
        $existing = $em->getRepository(User::class)->findOneBy(['username' => $username]);
        if ($existing instanceof User) {
            return $existing;
        }

        $hasher = $this->container()->get(UserPasswordHasherInterface::class);

        $user = new User();
        $user->setUsername($username);
        $user->setEmail($username.'@user.wlwdw.com');
        $user->setEnabled(true);
        $user->setRoles($roles);
        $user->setPassword($hasher->hashPassword($user, 'test-pass'));

        $em->persist($user);
        $em->flush();

        return $user;
    }

    protected function createAdmin(): User
    {
        return $this->createUser('admin', ['ROLE_ADMIN']);
    }

    /**
     * Authenticates the current test client as the given user.
     */
    protected function loginAs(string $username, array $roles = ['ROLE_USER']): KernelBrowser
    {
        $user = $this->createUser($username, $roles);
        $client = $this->client();
        $client->loginUser($user);

        return $client;
    }

    protected function adminClient(): KernelBrowser
    {
        return $this->loginAs('admin', ['ROLE_ADMIN']);
    }

    /**
     * Creates a category through the real HTTP form and returns its id.
     * Creating through the controller also seeds the 30-trail ring buffer,
     * which is exactly what the trail-report endpoint relies on.
     */
    protected function createCategoryViaForm(string $title, string $devid, ?int $parentId = null): int
    {
        $crawler = $this->adminClient()->request('GET', '/category/new');
        $form = $crawler->selectButton('保存单位')->form([
            'category[title]' => $title,
            'category[devid]' => $devid,
            'category[parent]' => (string) ($parentId ?? ''),
        ]);
        $this->client()->submit($form);
        self::assertResponseRedirects('/category/', 302);

        $em = $this->container()->get('doctrine')->getManager();
        $category = $em->getRepository(Category::class)->findOneBy(['title' => $title]);
        self::assertNotNull($category, "Category '$title' should exist");

        return $category->getId();
    }

    /**
     * Decodes a JSON response body.
     */
    protected function jsonBody(KernelBrowser $client): mixed
    {
        return json_decode($client->getResponse()->getContent(), true, flags: JSON_THROW_ON_ERROR);
    }
}
