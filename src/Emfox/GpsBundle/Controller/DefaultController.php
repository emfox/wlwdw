<?php

namespace Emfox\GpsBundle\Controller;

use Symfony\Bundle\FrameworkBundle\Controller\Controller;

class DefaultController extends Controller
{
    public function mainAction($maptype)
    {
        return $this->render('EmfoxGpsBundle:Default:index.html.twig',array('maptype' => $maptype));
    }
}
