<?php

namespace App\Entity;

use Doctrine\Common\Collections\ArrayCollection;
use Gedmo\Mapping\Annotation as Gedmo;
use Doctrine\ORM\Mapping as ORM;

#[Gedmo\Tree(type: 'nested')]
#[ORM\Entity(repositoryClass: \Gedmo\Tree\Entity\Repository\NestedTreeRepository::class)]
#[ORM\Table(name: 'categories')]
class Category
{
    #[ORM\Column(name: 'id', type: 'integer')]
    #[ORM\Id]
    #[ORM\GeneratedValue]
    private $id;

    #[ORM\Column(name: 'devid', type: 'string', length: 40)]
    private $devid;
    #[ORM\Column(name: 'title', type: 'string', length: 64)]
    private $title;

    private $indentLabel;

    #[ORM\Column(name: 'updatetime', type: 'datetime')]
    private $updatetime;
    #[ORM\Column(name: 'lat', type: 'float')]
    private $lat;
    #[ORM\Column(name: 'lng', type: 'float')]
    private $lng;
    
    #[Gedmo\TreeLeft]
    #[ORM\Column(name: 'lft', type: 'integer')]
    private $lft;

    #[Gedmo\TreeLevel]
    #[ORM\Column(name: 'lvl', type: 'integer')]
    private $lvl;

    #[Gedmo\TreeRight]
    #[ORM\Column(name: 'rgt', type: 'integer')]
    private $rgt;

    #[Gedmo\TreeRoot]
    #[ORM\Column(name: 'root', type: 'integer', nullable: true)]
    private $root;

    #[Gedmo\TreeParent]
    #[ORM\ManyToOne(targetEntity: \Category::class, inversedBy: 'children')]
    #[ORM\JoinColumn(name: 'parent_id', referencedColumnName: 'id', onDelete: 'CASCADE')]
    private $parent;

    #[ORM\OneToMany(targetEntity: \Category::class, mappedBy: 'parent')]
    #[ORM\OrderBy(['lft' => 'ASC'])]
    private $children;

    public function __toString()
    {
    	return $this->title;
    }
    
    public function getId()
    {
        return $this->id;
    }

    public function setTitle($title)
    {
        $this->title = $title;
    }

    public function getTitle()
    {
        return $this->title;
    }

    public function setParent(?Category $parent = null)
    {
        $this->parent = $parent;
    }

    public function getParent()
    {
        return $this->parent;
    }
    /**
     * Constructor
     */
    public function __construct()
    {
        $this->children = new ArrayCollection();
    }

    /**
     * Set lft
     *
     * @param integer $lft
     * @return Category
     */
    public function setLft($lft)
    {
        $this->lft = $lft;

        return $this;
    }

    /**
     * Get lft
     *
     * @return integer 
     */
    public function getLft()
    {
        return $this->lft;
    }

    /**
     * Set lvl
     *
     * @param integer $lvl
     * @return Category
     */
    public function setLvl($lvl)
    {
        $this->lvl = $lvl;

        return $this;
    }

    /**
     * Get lvl
     *
     * @return integer 
     */
    public function getLvl()
    {
        return $this->lvl;
    }

    /**
     * Set rgt
     *
     * @param integer $rgt
     * @return Category
     */
    public function setRgt($rgt)
    {
        $this->rgt = $rgt;

        return $this;
    }

    /**
     * Get rgt
     *
     * @return integer 
     */
    public function getRgt()
    {
        return $this->rgt;
    }

    /**
     * Set root
     *
     * @param integer $root
     * @return Category
     */
    public function setRoot($root)
    {
        $this->root = $root;

        return $this;
    }

    /**
     * Get root
     *
     * @return integer 
     */
    public function getRoot()
    {
        return $this->root;
    }

    /**
     * Add children
     *
     * @param \App\Entity\Category $children
     * @return Category
     */
    public function addChild(\App\Entity\Category $children)
    {
        $this->children[] = $children;

        return $this;
    }

    /**
     * Remove children
     *
     * @param \App\Entity\Category $children
     */
    public function removeChild(\App\Entity\Category $children)
    {
        $this->children->removeElement($children);
    }

    /**
     * Get children
     *
     * @return \Doctrine\Common\Collections\Collection 
     */
    public function getChildren()
    {
        return $this->children;
    }

    /**
     * Set updatetime
     *
     * @param \DateTime $updatetime
     * @return Category
     */
    public function setUpdatetime($updatetime)
    {
        $this->updatetime = $updatetime;

        return $this;
    }

    /**
     * Get updatetime
     *
     * @return \DateTime 
     */
    public function getUpdatetime()
    {
        return $this->updatetime;
    }

    /**
     * Set lat
     *
     * @param float $lat
     * @return Category
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
     * @return Category
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

    public function setIndentLabel()
    {
    	$this->indentLabel = str_repeat(
    			html_entity_decode('&nbsp;', ENT_QUOTES, 'UTF-8'),
    			($this->getLvl() + 1) * 3
    	) . $this->getTitle();

        return $this;
    }

    public function getIndentLabel()
    {
    	return str_repeat(
                        html_entity_decode('&nbsp;', ENT_QUOTES, 'UTF-8'),
                        ($this->getLvl() + 1) * 3
        ) . $this->getTitle();
    }

    /**
     * Set devid
     *
     * @param string $devid
     * @return Category
     */
    public function setDevid($devid)
    {
        $this->devid = $devid;

        return $this;
    }

    /**
     * Get devid
     *
     * @return string 
     */
    public function getDevid()
    {
        return $this->devid;
    }
}
