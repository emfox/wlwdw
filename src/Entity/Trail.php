<?php

namespace App\Entity;

use Doctrine\ORM\Mapping as ORM;

/**
 * Trail
 */
#[ORM\Entity]
#[ORM\Table]
class Trail
{
    /**
     * @var integer
     */
    #[ORM\Column(name: 'id', type: 'integer')]
    #[ORM\Id]
    #[ORM\GeneratedValue(strategy: 'AUTO')]
    private $id;

    /**
     * @var integer
     */
    #[ORM\Column(name: 'catid', type: 'integer')]
    private $catid;

    /**
     * @var \DateTime
     */
    #[ORM\Column(name: 'time', type: 'datetime')]
    private $time;

    /**
     * @var float
     */
    #[ORM\Column(name: 'lat', type: 'float')]
    private $lat;

    /**
     * @var float
     */
    #[ORM\Column(name: 'lng', type: 'float')]
    private $lng;


    /**
     * Get id
     *
     * @return integer 
     */
    public function getId()
    {
        return $this->id;
    }

    /**
     * Set catid
     *
     * @param integer $catid
     * @return Trail
     */
    public function setCatid($catid)
    {
        $this->catid = $catid;

        return $this;
    }

    /**
     * Get catid
     *
     * @return integer 
     */
    public function getCatid()
    {
        return $this->catid;
    }

    /**
     * Set time
     *
     * @param \DateTime $time
     * @return Trail
     */
    public function setTime($time)
    {
        $this->time = $time;

        return $this;
    }

    /**
     * Get time
     *
     * @return \DateTime 
     */
    public function getTime()
    {
        return $this->time;
    }

    /**
     * Set lat
     *
     * @param float $lat
     * @return Trail
     */
    public function setLat($lat)
    {
        $this->lat = $lat;

        return $this;
    }

    /**
     * Get lat
     *
     * @return float 
     */
    public function getLat()
    {
        return $this->lat;
    }

    /**
     * Set lng
     *
     * @param float $lng
     * @return Trail
     */
    public function setLng($lng)
    {
        $this->lng = $lng;

        return $this;
    }

    /**
     * Get lng
     *
     * @return float 
     */
    public function getLng()
    {
        return $this->lng;
    }
}
