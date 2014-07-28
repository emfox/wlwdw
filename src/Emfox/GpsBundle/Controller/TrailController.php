<?php

namespace Emfox\GpsBundle\Controller;

use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Bundle\FrameworkBundle\Controller\Controller;
use Sensio\Bundle\FrameworkExtraBundle\Configuration\Method;
use Sensio\Bundle\FrameworkExtraBundle\Configuration\Route;
use Sensio\Bundle\FrameworkExtraBundle\Configuration\Template;
use Emfox\GpsBundle\Entity\Trail;

/**
 * Trail controller.
 *
 * @Route("/trail")
 */
class TrailController extends Controller
{
	/**
	 * Add a new Trail point entities via ajax.
	 *
	 * @Route("/new/{devid}/{lng}/{lat}", name="trail_new")
	 */
	public function newAction($devid,$lng,$lat)
	{
		$em = $this->getDoctrine()->getManager();
		$category = $em->getRepository('EmfoxGpsBundle:Category')->findOneByDevid($devid);
		if(!$category)
		{
			$response = array("code" => 403, "success" => false, "message"=>"Device Unauthorized");
			return new Response(json_encode($response));
		}
		$trail = $em->getRepository('EmfoxGpsBundle:Trail')->findOneBy(
				array('catid'=>$category->getId()),
				array('time'=>'ASC')
		);
		$trail->setTime($category->getUpdatetime());
		$trail->setLat($category->getLat());
		$trail->setLng($category->getLng());
		$em->persist($trail);
		$category->setUpdatetime(new \DateTime());
		$category->setLat($lat);
		$category->setLng($lng);
		$em->persist($category);
		$em->flush();

		$response = array("code" => 100, "success" => true);
		return new Response(json_encode($response));
	}
	/**
	 * Lists all Trail entities of an specified category via ajax.
	 *
	 * @Route("/list/{catid}", name="trail_list")
	 */
	public function listAction($catid)
	{
		$em = $this->getDoctrine()->getManager();
		$entities = $em->getRepository('EmfoxGpsBundle:Trail')->findBy(
				array('catid'=>$catid),
				array('time'=>'DESC'),
				30
		);
		if(!$entities)
		{
			$response = array("code" => 403, "success" => false, "message"=>"Category not found");
			return new Response(json_encode($response));
		}
		foreach($entities as $entity)
		{
			$id = $entity->getId();
			$trail[$id] = array("lng" => $entity->getLng(),
					"lat" => $entity->getLat(),
					"time" => $entity->getTime()->format("Y-m-d H:i:s"));
		}
		$response = array("code" => 100, "success" => true, "trail" => $trail);
		return new Response(json_encode($response));
	}
	
}