<?php

namespace Emfox\GpsBundle\Controller;

use Symfony\Bundle\FrameworkBundle\Controller\Controller;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;

class DefaultController extends Controller
{
    public function mainAction($maptype)
    {
        return $this->render('EmfoxGpsBundle:Default:index.html.twig',array('maptype' => $maptype));
    }

	public function proxyAction(Request $request, $url)
	{
		$content = file_get_contents($url . '?' . $request->getQueryString());
		$response = new Response();
		foreach ($http_response_header as $value)
		{
			if (preg_match('/^Content-Type:(.*)/i', $value, $matches)) {
				$type = $matches[1];
				$response->headers->set('Content-Type', $type);
				if (preg_match('/javascript/i', $type)) {
					$content = preg_replace('@http://@', '/proxy/http://', $content);
				}
			}
		}
		$response->setContent($content);
		return $response;
	}
}
