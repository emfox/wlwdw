<?php

namespace App\Form;

use Symfony\Component\Form\AbstractType;
use Symfony\Component\Form\FormBuilderInterface;
use Symfony\Component\Form\Extension\Core\Type\ChoiceType;

class AnchorType extends AbstractType
{
     /**
     * @param FormBuilderInterface $builder
     * @param array $options
     */
    public function buildForm(FormBuilderInterface $builder, array $options): void
    {
        $builder
            ->add('title',null,array('label' => '名称'))
            ->add('enabled',null,array('label' => '是否启用','data' => true))
            ->add('lng',null,array('label' => '经度'))
            ->add('lat',null,array('label' => '纬度'))
            ->add('icon',ChoiceType::class,array('choices' =>  $this->getIconChoices(),'label' => '图标','expanded'=>true))
        ;
    }
    public function getIconChoices()
    {
        // Resolve from the project root instead of relying on the process CWD
        // (PHP-FPM and test runners do not guarantee it points to public/).
        $icon_path = __DIR__.'/../../public/images/anchors';
        $icon_asset_path = '/images/anchors/';
        $icons_choices = [];
        if (is_dir($icon_path) && false !== ($dh = opendir($icon_path))) {
            while (false !== ($filename = readdir($dh))) {
                if ($filename != '.' && $filename != '..') {
                    $icons_choices['<img src="'.$icon_asset_path.$filename.'" />'] = $filename;
                }
            }
            closedir($dh);
        }

        return $icons_choices;
    }
}
