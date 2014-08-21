<?php

namespace Emfox\GpsBundle\Controller;

use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Bundle\FrameworkBundle\Controller\Controller;
use Sensio\Bundle\FrameworkExtraBundle\Configuration\Method;
use Sensio\Bundle\FrameworkExtraBundle\Configuration\Route;
use Sensio\Bundle\FrameworkExtraBundle\Configuration\Template;
use Emfox\GpsBundle\Model\Yunba;

/**
 * Message controller.
 *
 * @Route("/message")
 */
class MessageController extends Controller {
	/**
	 * Send Message direct to clients.
	 *
	 * @Route("/send", name="message_send")
	 */
	public function TestAction(Request $request) {
		$msg = $request->request->get('msg');
		$topics = explode(',', $request->request->get('topics'));

		$yunba = new Yunba ( array (
				"appkey" => "53e491034e9f46851d5a573a" 
		) );
		// 初始化
		$yunba->init ( function ($success) {
			echo "[YunBa]init " . ($success ? "success" : "fail") . "\n";
		} );
		
		// 连接
		$yunba->connect ( function ($success) {
			if ($success) {
				echo "[YunBa]connect success\n";
			} else {
				echo "[YunBa]connect fail\n";
			}
		} );
		
		foreach($topics as $topic){
			$yunba->publish(array(
					"topic" => $topic,
					"qos" => 2,
					"msg" => $msg
			), function ($success) {
				echo "[YunBa]publish1 " . ($success ? "success" : "fail") . "\n";
			});
		}

		$yunba->disconnect();
		
		return new Response($msg);
	}
}