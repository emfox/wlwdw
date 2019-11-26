<?php

namespace App\Controller;

use Symfony\Bundle\FrameworkBundle\Controller\Controller;

class DefaultController extends Controller
{
    public function mainAction($maptype)
    {
        return $this->render('default/index.html.twig',array('maptype' => $maptype));
    }
}
