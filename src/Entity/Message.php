<?php

namespace App\Entity;

use Doctrine\ORM\Mapping as ORM;

/**
 * Message
 */
#[ORM\Entity]
#[ORM\Table]
class Message
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
    #[ORM\Column(name: 'recipient', type: 'string', length: 30)]
    private $recipient;

    /**
     * @var \DateTime
     */
    #[ORM\Column(name: 'time', type: 'datetime')]
    private $time;

    /**
     * @var string
     */
    #[ORM\Column(name: 'content', type: 'string', length: 255)]
    private $content;


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
     * Set recipient
     *
     * @param integer $recipient
     * @return Message
     */
    public function setRecipient($recipient)
    {
        $this->recipient = $recipient;

        return $this;
    }

    /**
     * Get recipient
     *
     * @return integer 
     */
    public function getRecipient()
    {
        return $this->recipient;
    }

    /**
     * Set time
     *
     * @param \DateTime $time
     * @return Message
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
     * Set content
     *
     * @param string $content
     * @return Message
     */
    public function setContent($content)
    {
        $this->content = $content;

        return $this;
    }

    /**
     * Get content
     *
     * @return string 
     */
    public function getContent()
    {
        return $this->content;
    }
}
