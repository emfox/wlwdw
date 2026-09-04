<?php

namespace App\Tests\Controller;

use App\Entity\Anchor;

/**
 * Anchor (map reference point) management: CRUD behind ROLE_ADMIN.
 */
class AnchorControllerTest extends AbstractAppTestCase
{
    private function createAnchor(string $title = '测试参考点'): int
    {
        $client = $this->adminClient();
        $crawler = $client->request('GET', '/anchor/new');
        self::assertResponseIsSuccessful();
        $form = $crawler->selectButton('保存参考点')->form([
            'anchor[title]' => $title,
            'anchor[enabled]' => '1',
            'anchor[lng]' => '116.391',
            'anchor[lat]' => '39.907',
            'anchor[icon]' => '01.png',
        ]);
        $client->submit($form);
        self::assertResponseRedirects('/anchor/', 302);

        $em = $this->client()->getContainer()->get('doctrine')->getManager();
        $anchor = $em->getRepository(Anchor::class)->findOneBy(['title' => $title]);
        self::assertNotNull($anchor, "Anchor '$title' should exist");

        return $anchor->getId();
    }

    public function testIndexRequiresAuthentication(): void
    {
        $client = $this->client();
        $client->request('GET', '/anchor/');

        self::assertResponseRedirects('/login', 302);
    }

    public function testIndexForbiddenForRegularUser(): void
    {
        $client = $this->loginAs('user');
        $client->request('GET', '/anchor/');

        self::assertResponseStatusCodeSame(403);
    }

    public function testNewFormPageIsUsable(): void
    {
        $client = $this->adminClient();
        $client->request('GET', '/anchor/new');

        self::assertResponseIsSuccessful();
    }

    public function testCreateAnchorShowsUpInList(): void
    {
        $this->createAnchor('地铁站锚点');

        $client = $this->adminClient();
        $crawler = $client->request('GET', '/anchor/');

        self::assertResponseIsSuccessful();
        self::assertGreaterThan(0, $crawler->filter('html:contains("地铁站锚点")')->count());
    }

    public function testEditAnchorUpdatesTitle(): void
    {
        $id = $this->createAnchor('旧锚点名');

        $client = $this->adminClient();
        $crawler = $client->request('GET', sprintf('/anchor/%d/edit', $id));
        self::assertResponseIsSuccessful();

        $form = $crawler->selectButton('确认修改')->form();
        $form['anchor[title]'] = '新锚点名';
        $client->submit($form);
        self::assertResponseRedirects('/anchor/', 302);

        $crawler = $client->followRedirect();
        self::assertGreaterThan(0, $crawler->filter('html:contains("新锚点名")')->count());
    }

    public function testDeleteAnchor(): void
    {
        $id = $this->createAnchor('待删除锚点');

        $client = $this->adminClient();
        $crawler = $client->request('GET', sprintf('/anchor/%d/edit', $id));
        $deleteForm = $crawler->selectButton('删除该参考点')->form();
        $client->submit($deleteForm);
        self::assertResponseRedirects('/anchor/', 302);

        $em = $this->client()->getContainer()->get('doctrine')->getManager();
        self::assertNull($em->getRepository(Anchor::class)->find($id));
    }
}
