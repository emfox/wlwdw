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
        $client->request('POST', '/trail/new', [], [], ['CONTENT_TYPE' => 'application/json'],
            json_encode(['devid' => 'ghost-device', 'lat' => 39.9, 'lng' => 116.4]));

        self::assertResponseIsSuccessful();
        $body = $this->jsonBody($client);
        // Same HTTP 200 as success, only the body differs: an unknown devid
        // must not be distinguishable from a valid one by status code alone.
        self::assertSame('deny', $body['result'] ?? null);
        self::assertFalse($body['success']);
    }

    public function testNewRejectsMissingCoordinates(): void
    {
        $this->createCategoryViaForm('坐标校验单位', 'coord-dev');

        $client = $this->client();
        // No lat/lng at all (e.g. a malformed client payload): must be
        // rejected before anything touches the DB. Note (0,0) is NOT invalid
        // under the range check (-90..90 / -180..180) - only missing,
        // non-numeric, non-finite or out-of-range values are.
        $client->request('POST', '/trail/new', [], [], ['CONTENT_TYPE' => 'application/json'],
            json_encode(['devid' => 'coord-dev']));

        self::assertResponseIsSuccessful();
        $body = $this->jsonBody($client);
        self::assertSame(403, $body['code']);
        self::assertStringContainsString('Invalid Coordinates', $body['message']);
    }

    public function testNewRejectsOutOfRangeCoordinates(): void
    {
        $this->createCategoryViaForm('越界校验单位', 'range-dev');

        $client = $this->client();
        $client->request('POST', '/trail/new', [], [], ['CONTENT_TYPE' => 'application/json'],
            json_encode(['devid' => 'range-dev', 'lat' => 95, 'lng' => 181]));

        self::assertResponseIsSuccessful();
        $body = $this->jsonBody($client);
        self::assertSame(403, $body['code']);
    }

    public function testNewWithValidReportUpdatesCategoryPosition(): void
    {
        $this->createCategoryViaForm('上报单位', 'report-dev');

        $client = $this->client();
        $client->request('POST', '/trail/new', [], [], ['CONTENT_TYPE' => 'application/json'],
            json_encode(['devid' => 'report-dev', 'lat' => 39.907, 'lng' => 116.391]));

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
