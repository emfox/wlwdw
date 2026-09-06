<?php

namespace App\Tests\Controller;

use App\Entity\Message;
use App\Tests\Service\SpyMessagePublisher;

/**
 * /message/send persists a message row per recipient and publishes a
 * realtime notification through MessagePublisherInterface; /message/show
 * returns the row to whoever proves the recipient devid.
 */
class MessageControllerTest extends AbstractAppTestCase
{
    protected function createMessage(string $devid, string $content): int
    {
        $em = $this->container()->get('doctrine')->getManager();

        $message = new Message();
        $message->setRecipient($devid);
        $message->setTime(new \DateTime('2026-01-01 12:00:00'));
        $message->setContent($content);
        $em->persist($message);
        $em->flush();

        return $message->getId();
    }

    public function testSendPersistsAndPublishesOneRowPerRecipient(): void
    {
        $client = $this->loginAs('msg-sender');
        $client->request('POST', '/message/send', [
            'topics' => 'dev-a, dev-b ,dev-a',
            'msg' => 'hello from test',
        ]);

        self::assertResponseIsSuccessful();
        self::assertSame('hello from test', $client->getResponse()->getContent());

        $em = $this->container()->get('doctrine')->getManager();
        $rows = $em->getRepository(Message::class)->findBy([], ['id' => 'ASC']);
        self::assertCount(3, $rows); // dev-a appears twice -> two rows (historic behaviour)

        /** @var SpyMessagePublisher $spy */
        $spy = $this->container()->get(SpyMessagePublisher::class);
        self::assertCount(3, $spy->calls);
        self::assertSame('dev-a', $spy->calls[0]['topic']);
        self::assertSame('dev-b', $spy->calls[1]['topic']);
        self::assertSame($rows[0]->getId(), $spy->calls[0]['payload']['id']);
    }

    public function testSendRejectsAnonymousUser(): void
    {
        $this->client()->request('POST', '/message/send', ['topics' => 'dev-a', 'msg' => 'x']);
        self::assertResponseStatusCodeSame(302); // redirected to login
    }

    public function testShowReturnsContentToRightDevid(): void
    {
        $id = $this->createMessage('dev-secret-1', 'content-abc');

        $this->client()->request('GET', '/message/'.$id.'/dev-secret-1');
        self::assertResponseIsSuccessful();
        $body = $this->jsonBody($this->client());
        self::assertTrue($body['success']);
        self::assertSame('content-abc', $body['message']['content']);
    }

    public function testShowDeniesWrongDevid(): void
    {
        $id = $this->createMessage('dev-secret-1', 'content-abc');

        // Historic contract: HTTP stays 200, the body carries code=403 (the
        // Android client decides on body.code).
        $this->client()->request('GET', '/message/'.$id.'/dev-other');
        self::assertResponseIsSuccessful();
        $body = $this->jsonBody($this->client());
        self::assertSame(403, $body['code']);
        self::assertFalse($body['success']);
    }

    public function testShowReturns404ForUnknownMessage(): void
    {
        $this->client()->request('GET', '/message/99999999/dev-whatever');
        self::assertResponseStatusCodeSame(404);
    }
}
