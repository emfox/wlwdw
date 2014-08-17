<?php

namespace Emfox\GpsBundle\DataFixtures\ORM;

use Doctrine\Common\DataFixtures\FixtureInterface;
use Doctrine\Common\Persistence\ObjectManager;
use Emfox\GpsBundle\Entity\User;

class LoadUserData implements FixtureInterface
{
	/**
	 * {@inheritDoc}
	 */
	public function load(ObjectManager $manager)
	{
		
// 		$userAdmin = new User();
// 		$userAdmin->setUsername('admin');
// 		$userAdmin->setEmail('admin@user.wlwdw.com');
// 		$userAdmin->setPlainPassword('admin');
// 		$userAdmin->setEnabled(true);
// 		$userAdmin->addRole('ROLE_ADMIN');

// 		$manager->persist($userAdmin);
		
// 		$manager->flush();
	}
}