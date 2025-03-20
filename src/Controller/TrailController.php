<?php

namespace App\Controller;

use DateTime;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\Routing\Attribute\Route;
use Sensio\Bundle\FrameworkExtraBundle\Configuration\Template;
use App\Entity\Trail;

/**
 * Trail controller.
 */
class TrailController extends AbstractController
{
	/**
     * @var \Doctrine\Persistence\ManagerRegistry
     */
    private $managerRegistry;
    public function __construct(\Doctrine\Persistence\ManagerRegistry $managerRegistry)
    {
        $this->managerRegistry = $managerRegistry;
    }
    /**
     * Add a new Trail point entities via ajax.
     */
    #[Route(path: '/trail/new/{devid}/{lng}/{lat}', name: 'trail_new')]
    public function new($devid,$lng,$lat): Response
	{
		$em = $this->managerRegistry->getManager();
		$category = $em->getRepository('App\Entity\Category')->findOneByDevid($devid);
		if(!$category)
		{
			$response = array("code" => 403, "success" => false, "message"=>"Device Unauthorized");
			return new Response(json_encode($response, JSON_THROW_ON_ERROR));
		}
		if($lat<0.01 or $lng<0.01){
			$response = array("code" => 403, "success" => false, "message"=>"Invalid Coordinates");
			return new Response(json_encode($response, JSON_THROW_ON_ERROR));
		}
		$trail = $em->getRepository('App\Entity\Trail')->findOneBy(
				array('catid'=>$category->getId()),
				array('time'=>'ASC')
		);
		$trail->setTime($category->getUpdatetime());
		$trail->setLat($category->getLat());
		$trail->setLng($category->getLng());
		$em->persist($trail);
		$category->setUpdatetime(new DateTime());
		$category->setLat($lat);
		$category->setLng($lng);
		$em->persist($category);
		$em->flush();

		$response = array("code" => 100, "success" => true);
		return new Response(json_encode($response, JSON_THROW_ON_ERROR));
	}
	/**
     * Lists all Trail entities of an specified category via ajax.
     */
    #[Route(path: '/trail/list/{catid}', name: 'trail_list')]
    public function list($catid): Response
	{
		$em = $this->managerRegistry->getManager();
		$entities = $em->getRepository('App\Entity\Trail')->findBy(
				array('catid'=>$catid),
				array('time'=>'DESC'),
				30
		);
		if(!$entities)
		{
			$response = array("code" => 403, "success" => false, "message"=>"Category not found");
			return new Response(json_encode($response, JSON_THROW_ON_ERROR));
		}
		foreach($entities as $entity)
		{
			$id = $entity->getId();
			$trail[$id] = array("lng" => $entity->getLng(),
					"lat" => $entity->getLat(),
					"time" => $entity->getTime()->format("Y-m-d H:i:s"));
		}
		$response = array("code" => 100, "success" => true, "trail" => $trail);
		return new Response(json_encode($response, JSON_THROW_ON_ERROR));
	}
}
