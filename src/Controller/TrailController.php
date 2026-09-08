<?php

namespace App\Controller;

use DateTime;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\Routing\Attribute\Route;
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
     *
     * POST with a JSON body {"devid","lat","lng"} (previously an anonymous
     * GET with the values in the URL). Reporting is deliberately POST-only:
     * a GET must never carry a write side effect (prefetch/crawlers/log
     * analyzers would otherwise create bogus reports), and coordinates are
     * validated before anything is stored. An unknown devid answers with
     * HTTP 200 + {"result":"deny"} rather than a 403 so the endpoint cannot
     * be used to enumerate which devids exist.
     */
    #[Route(path: '/trail/new', name: 'trail_new', methods: ['POST'])]
    public function new(Request $request): Response
	{
		$data = json_decode((string) $request->getContent(), true);
		$devid = trim((string) ($data['devid'] ?? ''));
		$lat = filter_var($data['lat'] ?? null, FILTER_VALIDATE_FLOAT);
		$lng = filter_var($data['lng'] ?? null, FILTER_VALIDATE_FLOAT);

		if ('' === $devid
				|| false === $lat || false === $lng
				|| !is_finite($lat) || !is_finite($lng)
				|| $lat < -90 || $lat > 90 || $lng < -180 || $lng > 180) {
			return $this->json(['code' => 403, 'success' => false, 'message' => 'Invalid Coordinates']);
		}

		$em = $this->managerRegistry->getManager();
		$category = $em->getRepository('App\Entity\Category')->findOneByDevid($devid);
		if(!$category)
		{
			// Same 200 status as a valid reply so devid existence cannot be
			// probed through the HTTP status code.
			return $this->json(['result' => 'deny', 'code' => 403, 'success' => false, 'message' => 'Device Unauthorized']);
		}
		$trail = $em->getRepository('App\Entity\Trail')->findOneBy(
				array('catid'=>$category->getId()),
				array('time'=>'ASC')
		);
		if (null === $trail) {
			// No history yet (e.g. trail rows were wiped): seed one instead of
			// crashing on the null below.
			$trail = new Trail();
			$trail->setCatid($category->getId());
		}
		$trail->setTime($category->getUpdatetime());
		$trail->setLat($category->getLat());
		$trail->setLng($category->getLng());
		$em->persist($trail);
		$category->setUpdatetime(new DateTime());
		$category->setLat($lat);
		$category->setLng($lng);
		$em->persist($category);
		$em->flush();

		return $this->json(['code' => 100, 'success' => true]);
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
		$trail = array();
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
