<?php

namespace App\Controller;

use DateTime;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\Routing\Annotation\Route;
use Sensio\Bundle\FrameworkExtraBundle\Configuration\Template;
use App\Model\Yunba;
use App\Entity\Message;

/**
 * Message controller.
 */
class MessageController extends AbstractController {
	/**
     * @var \Doctrine\Persistence\ManagerRegistry
     */
    private $managerRegistry;
    public function __construct(\Doctrine\Persistence\ManagerRegistry $managerRegistry)
    {
        $this->managerRegistry = $managerRegistry;
    }
    /**
     * Send Message direct to clients.
     *
     * @Route("/message/send", name="message_send")
     */
    public function Send(Request $request): \Symfony\Component\HttpFoundation\Response {

		$yunba = new Yunba ( array (
				"appkey" => "53e491034e9f46851d5a573a" 
		) );
		// 初始化
		$yunba->init ( function ($success): void {
			echo "[YunBa]init " . ($success ? "success" : "fail") . "\n";
		} );
		
		// 连接
		$yunba->connect ( function ($success): void {
			if ($success) {
				echo "[YunBa]connect success\n";
			} else {
				echo "[YunBa]connect fail\n";
			}
		} );
		
		//Should really wait until fully connected with yunba server
		sleep(1);
		
		$msg = $request->request->get('msg');
		$topics = explode(',', $request->request->get('topics'));
		$em = $this->managerRegistry->getManager();

		foreach($topics as $topic){
			
			$message = new Message();
			$message->setRecipient($topic);
			$message->setTime(new DateTime());
			$message->setContent($msg);
			
			$em->persist($message);
			$em->flush();
			$yunba->publish(array(
					"topic" => $topic,
					"qos" => 2,
					"msg" => strval($message->getId())
			), function ($success): void {
				echo "[YunBa]publish1 " . ($success ? "success" : "fail") . "\n";
			});
		}
		
		$yunba->disconnect();
		
		return new Response($msg);
	}
	
	/**
     * show specific Message.
     *
     * @Route("/message/{id}/{devid}", name="message_show")
     */
    public function show($id,$devid): \Symfony\Component\HttpFoundation\Response{
		$em = $this->managerRegistry->getManager();
		$message = $em->getRepository('App\Entity\Message')->find($id);
		
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
