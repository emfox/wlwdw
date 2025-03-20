<?php

namespace App\Controller;

use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;

class DefaultController extends AbstractController
{
    public function main($maptype)
    {
        return $this->render('default/index.html.twig',array('maptype' => $maptype));
    }
}
