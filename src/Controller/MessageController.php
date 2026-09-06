<?php

namespace App\Controller;

use DateTime;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\Routing\Attribute\Route;
use App\Service\MessagePublisherInterface;
use App\Entity\Message;

/**
 * Message controller.
 */
class MessageController extends AbstractController {
	/**
     * @var \Doctrine\Persistence\ManagerRegistry
     */
    private $managerRegistry;
    private MessagePublisherInterface $publisher;

    public function __construct(
        \Doctrine\Persistence\ManagerRegistry $managerRegistry,
        MessagePublisherInterface $publisher
    ) {
        $this->managerRegistry = $managerRegistry;
        $this->publisher = $publisher;
    }

    /**
     * Send Message direct to clients.
     *
     * Persists one Message per recipient (devid) and pushes a realtime
     * notification {"id": <message row id>} to the device's MQTT topic.
     * The client pulls the body via message_show; a failed push is only
     * logged (the message stays available for later pulls).
     */
    #[Route(path: '/message/send', name: 'message_send')]
    public function Send(Request $request): Response {
		$msg = (string) $request->request->get('msg');
		$topics = explode(',', (string) $request->request->get('topics'));
		$em = $this->managerRegistry->getManager();

		foreach ($topics as $topic) {
			$topic = trim($topic);
			if ($topic === '') {
				continue;
			}

			$message = new Message();
			$message->setRecipient($topic);
			$message->setTime(new DateTime());
			$message->setContent($msg);

			$em->persist($message);
			$em->flush();

			$this->publisher->publish($topic, ['id' => $message->getId()]);
		}

		return new Response($msg);
	}

	/**
     * show specific Message.
     */
    #[Route(path: '/message/{id}/{devid}', name: 'message_show')]
    public function show($id,$devid): Response{
		$em = $this->managerRegistry->getManager();
		$message = $em->getRepository('App\Entity\Message')->find($id);

		if(!$message){
			$response = array("code" => 404, "success" => false, "message"=>"Message not found");
			return new Response(json_encode($response, JSON_THROW_ON_ERROR), Response::HTTP_NOT_FOUND);
		}
		//direct authenticate user via devid
		if($message->getRecipient() != $devid){
			$response = array("code" => 403, "success" => false, "message"=>"Device Unauthorized");
			return new Response(json_encode($response, JSON_THROW_ON_ERROR));
		}
		$response = array("code" => 100, "success" => true, "message" => array("time" => $message->getTime()->format("Y-m-d H:i:s"),
																				"content" => $message->getContent()
		));
		return new Response(json_encode($response, JSON_THROW_ON_ERROR));
	}
}
