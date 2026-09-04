<?php

namespace App\Tests\Controller;

use App\Entity\Category;
use App\Entity\Trail;

/**
 * Covers the Gedmo nested-set driven category tree: access control,
 * hierarchy JSON, root/child creation, moving and deletion.
 */
class CategoryControllerTest extends AbstractAppTestCase
{
    public function testHierarchyRequiresAuthentication(): void
    {
        $client = $this->client();
        $client->request('GET', '/category/hierarchy');

        self::assertResponseRedirects('/login', 302);
    }

    public function testHierarchyAccessibleToRegularUser(): void
    {
        $client = $this->loginAs('user');
        $client->request('GET', '/category/hierarchy');

        self::assertResponseIsSuccessful();
        $body = $this->jsonBody($client);
        self::assertSame(100, $body['code']);
        self::assertTrue($body['success']);
        self::assertSame([], $body['ztree']);
    }

    public function testIndexPageListsCategories(): void
    {
        $this->createCategoryViaForm('总部','dev01');

        $client = $this->adminClient();
        $crawler = $client->request('GET', '/category/');

        self::assertResponseIsSuccessful();
        self::assertGreaterThan(0, $crawler->filter('html:contains("总部")')->count());
    }

    public function testCreateRootCategoryBuildsTreeBounds(): void
    {
        $this->createCategoryViaForm('测试根单位','dev01');

        $client = $this->adminClient();
        $client->request('GET', '/category/hierarchy');
        $ztree = $this->jsonBody($client)['ztree'];

        self::assertCount(1, $ztree);
        self::assertSame('测试根单位', $ztree[0]['title']);
        // A bare root in a nested set spans [1, 2].
        self::assertSame(1, $ztree[0]['lft']);
        self::assertSame(2, $ztree[0]['rgt']);
        self::assertSame(0, $ztree[0]['lvl']);
    }

    public function testCreateRootCategoryAlsoSeedsThirtyTrails(): void
    {
        $id = $this->createCategoryViaForm('总部','dev-trail-seed');

        $em = $this->client()->getContainer()->get('doctrine')->getManager();
        $count = $em->getRepository(Trail::class)->count(['catid' => $id]);
        self::assertSame(30, $count);
    }

    public function testCreateChildCategoryBuildsNestedHierarchy(): void
    {
        $rootId = $this->createCategoryViaForm('总部','dev01');
        $client = $this->adminClient();

        $crawler = $client->request('GET', '/category/new');
        $form = $crawler->selectButton('保存单位')->form([
            'category[title]' => '第一分部',
            'category[devid]' => 'dev01',
            'category[parent]' => (string) $rootId,
        ]);
        $client->submit($form);
        self::assertResponseRedirects('/category/', 302);

        $client->request('GET', '/category/hierarchy');
        $ztree = $this->jsonBody($client)['ztree'];

        self::assertCount(1, $ztree);
        $root = $ztree[0];
        self::assertSame('总部', $root['title']);
        self::assertSame(1, $root['lft']);
        self::assertSame(4, $root['rgt']); // root now wraps its child
        self::assertCount(1, $root['children']);
        self::assertSame('第一分部', $root['children'][0]['title']);
        self::assertSame(2, $root['children'][0]['lft']);
        self::assertSame(3, $root['children'][0]['rgt']);
        self::assertSame(1, $root['children'][0]['lvl']);
    }

    public function testMoveDownReordersSiblings(): void
    {
        // Gedmo nested sets only move a node among its own siblings, so we
        // create one root with two children and move the first child down.
        $rootId = $this->createCategoryViaForm('总部','dev00');
        $this->createCategoryViaForm('甲分部','dev01', $rootId);
        $this->createCategoryViaForm('乙分部','dev02', $rootId);

        $em = $this->client()->getContainer()->get('doctrine')->getManager();
        $jia = $em->getRepository(Category::class)->findOneBy(['title' => '甲分部']);

        $client = $this->adminClient();
        $client->request('GET', sprintf('/category/%d/move/down', $jia->getId()));
        self::assertResponseRedirects('/category/', 302);

        $client->request('GET', '/category/hierarchy');
        $ztree = $this->jsonBody($client)['ztree'];
        self::assertSame(['乙分部', '甲分部'], array_column($ztree[0]['children'], 'title'));
    }

    public function testEditPageAllowsTitleUpdate(): void
    {
        $id = $this->createCategoryViaForm('旧名称','dev01');
        $client = $this->adminClient();

        $crawler = $client->request('GET', sprintf('/category/%d/edit', $id));
        self::assertResponseIsSuccessful();
        $form = $crawler->selectButton('确认修改')->form();
        $form['category[title]'] = '新名称';
        $client->submit($form);

        self::assertResponseRedirects('/category/', 302);
        $client->request('GET', '/category/hierarchy');
        $ztree = $this->jsonBody($client)['ztree'];
        self::assertSame('新名称', $ztree[0]['title']);
    }

    public function testDeleteCategory(): void
    {
        $id = $this->createCategoryViaForm('待删除单位','dev01');
        $client = $this->adminClient();

        $crawler = $client->request('GET', sprintf('/category/%d/edit', $id));
        self::assertResponseIsSuccessful();
        $deleteForm = $crawler->selectButton('删除该单位')->form();
        $client->submit($deleteForm);
        self::assertResponseRedirects('/category/', 302);

        $client->request('GET', '/category/hierarchy');
        self::assertSame([], $this->jsonBody($client)['ztree']);
    }

    public function testMoveRequiresAdminRole(): void
    {
        $id = $this->createCategoryViaForm('单位','dev01');
        $client = $this->loginAs('user');

        $client->request('GET', sprintf('/category/%d/move/down', $id));
        self::assertResponseStatusCodeSame(403);
    }
}
