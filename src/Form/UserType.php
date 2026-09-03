<?php

namespace App\Form;

use Symfony\Component\Form\AbstractType;
use Symfony\Component\Form\FormBuilderInterface;
use Symfony\Component\Form\Extension\Core\Type\TextType;

class UserType extends AbstractType
{
	/**
	 * @param FormBuilderInterface $builder
	 * @param array $options
	 */
	public function buildForm(FormBuilderInterface $builder, array $options): void
	{
		$builder
            ->add('username',null,array('label' => '用户名'))
            ->add('password',TextType::class,array('mapped' => false,'required' => false,'label' => '密码  '))
		;
    }

}
