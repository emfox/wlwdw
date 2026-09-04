<?php

namespace App\Tests\Controller;

use App\Entity\Category;
use App\Entity\Trail;

/**
 * Device trail reporting: a PUBLIC API endpoint used by the Android app.
 *
 * A category must exist whose `devid` matches the request, and a valid
 * report updates the category position while rotating the oldest of the
 * 30 seeded trail records (ring buffer).
 */
class TrailControllerTest extends AbstractAppTestCase
{
    public function testNewRejectsUnknownDevid(): void
    {
        $client = $this->client();
        $client->request('GET', '/trail/new/ghost-device/116.4/39.9');

        self::assertResponseIsSuccessful();
        $body = $this->jsonBody($client);
        self::assertSame(403, $body['code']);
        self::assertFalse($body['success']);
    }

    public function testNewRejectsInvalidCoordinates(): void
    {
        $this->createCategoryViaForm('坐标校验单位', 'coord-dev');

        $client = $this->client();
        $client->request('GET', '/trail/new/coord-dev/0/0');

        self::assertResponseIsSuccessful();
        $body = $this->jsonBody($client);
        self::assertSame(403, $body['code']);
        self::assertStringContainsString('Invalid Coordinates', $body['message']);
    }

    public function testNewWithValidReportUpdatesCategoryPosition(): void
    {
        $this->createCategoryViaForm('上报单位', 'report-dev');

        $client = $this->client();
        $client->request('GET', '/trail/new/report-dev/116.391/39.907');

        self::assertResponseIsSuccessful();
        $body = $this->jsonBody($client);
        self::assertSame(100, $body['code']);
        self::assertTrue($body['success']);

        // The category must now carry the freshly reported coordinates.
        $em = $this->client()->getContainer()->get('doctrine')->getManager();
        $category = $em->getRepository(Category::class)->findOneBy(['devid' => 'report-dev']);
        self::assertNotNull($category);
        $em->refresh($category);
        self::assertSame(39.907, $category->getLat());
        self::assertSame(116.391, $category->getLng());

        // The trail ring buffer must not grow beyond its 30 slots.
        $trailCount = $em->getRepository(Trail::class)->count(['catid' => $category->getId()]);
        self::assertSame(30, $trailCount);
    }

    public function testListRequiresAuthentication(): void
    {
        $client = $this->client();
        $client->request('GET', '/trail/list/1');

        self::assertResponseRedirects('/login', 302);
    }

    public function testListReturnsTrailJsonForRegularUser(): void
    {
        $categoryId = $this->createCategoryViaForm('轨迹查询单位', 'list-dev');

        $client = $this->loginAs('viewer');
        $client->request('GET', sprintf('/trail/list/%d', $categoryId));

        self::assertResponseIsSuccessful();
        $body = $this->jsonBody($client);
        self::assertSame(100, $body['code']);
        self::assertCount(30, $body['trail']);
    }
}
