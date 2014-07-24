<?php

namespace Emfox\GpsBundle\Form;

use Symfony\Component\Form\AbstractType;
use Symfony\Component\Form\FormBuilderInterface;
use Symfony\Component\OptionsResolver\OptionsResolverInterface;

class CategoryType extends AbstractType
{
        /**
     * @param FormBuilderInterface $builder
     * @param array $options
     */
    public function buildForm(FormBuilderInterface $builder, array $options)
    {
        $builder
            ->add('title',null,array('label' => '单位名'))
            ->add('parent', null, array('property' => 'optionLabel', 'label'=>'上级单位', 'query_builder' => function($er) {
    																					return $er->createQueryBuilder('c')
    																					->orderBy('c.root', 'ASC')
    																					->addOrderBy('c.lft', 'ASC');},
                                          )
            	 )
        ;
    }
    
    /**
     * @param OptionsResolverInterface $resolver
     */
    public function setDefaultOptions(OptionsResolverInterface $resolver)
    {
        $resolver->setDefaults(array(
            'data_class' => 'Emfox\GpsBundle\Entity\Category'
        ));
    }

    /**
     * @return string
     */
    public function getName()
    {
        return 'emfox_gpsbundle_category';
    }
}
