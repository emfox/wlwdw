<?php

namespace Emfox\GpsBundle\Controller;

use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Bundle\FrameworkBundle\Controller\Controller;
use Sensio\Bundle\FrameworkExtraBundle\Configuration\Method;
use Sensio\Bundle\FrameworkExtraBundle\Configuration\Route;
use Sensio\Bundle\FrameworkExtraBundle\Configuration\Template;
use Emfox\GpsBundle\Entity\Category;
use Emfox\GpsBundle\Form\CategoryType;
use Emfox\GpsBundle\Entity\Trail;

/**
 * Category controller.
 *
 * @Route("/category")
 */
class CategoryController extends Controller
{
	/**
	 * Lists all Category entities via ajax.
	 *
	 * @Route("/hierarchy", name="category_hierarchy")
	 * @Method("GET")
	 */
	public function hierarchyAction($root = null)
	{
		$em = $this->getDoctrine()->getManager();
		$repo = $em->getRepository('EmfoxGpsBundle:Category');
		$repo->setChildrenIndex('children');
		$arrayTree = $repo->childrenHierarchy();
		$response = array("code" => 100, "success" => true, "ztree" => $arrayTree);
		return new Response(json_encode($response));
	}

    /**
     * Lists all Category entities.
     *
     * @Route("/", name="category")
     * @Method("GET")
     * @Template()
     */
    public function indexAction()
    {
        $em = $this->getDoctrine()->getManager();
        $qb = $em->createQueryBuilder();
        
        $qb->select('c')
        ->from('EmfoxGpsBundle:Category','c')
        ->orderBy('c.root', 'ASC')
        ->addOrderBy('c.lft', 'ASC');
        
        $query = $qb->getQuery();
        $entities = $query->getResult();
        
        foreach($entities as $entity){
        	$entity->indentLabel = $entity->getIndentLabel();
        }

        return array(
            'entities' => $entities,
        );
    }
    /**
     * Creates a new Category entity.
     *
     * @Route("/", name="category_create")
     * @Method("POST")
     * @Template("EmfoxGpsBundle:Category:new.html.twig")
     */
    public function createAction(Request $request)
    {
        $entity = new Category();
        $form = $this->createCreateForm($entity);
        $form->handleRequest($request);

        if ($form->isValid()) {
        	$entity->setUpdatetime(new \DateTime());
        	$entity->setLat('0');
        	$entity->setLng('0');
            $em = $this->getDoctrine()->getManager();
            $em->persist($entity);
            $em->flush();
            for($i=1;$i<=30;$i++)
            {
            	$trail = new Trail();
            	$trail->setCatid($entity->getId());
            	$trail->setLat('0');
            	$trail->setLng('0');
            	$trail->setTime(new \DateTime());
            	$em->persist($trail);
            }
            $em->flush();

            return $this->redirect($this->generateUrl('category'));
        }

        return array(
            'entity' => $entity,
            'form'   => $form->createView(),
        );
    }

    /**
     * Creates a form to create a Category entity.
     *
     * @param Category $entity The entity
     *
     * @return \Symfony\Component\Form\Form The form
     */
    private function createCreateForm(Category $entity)
    {
        $form = $this->createForm(new CategoryType(), $entity, array(
            'action' => $this->generateUrl('category_create'),
            'method' => 'POST',
        ));

        $form->add('submit', 'submit', array('label' => '保存单位'));

        return $form;
    }

    /**
     * Displays a form to create a new Category entity.
     *
     * @Route("/new", name="category_new")
     * @Method("GET")
     * @Template()
     */
    public function newAction()
    {
        $entity = new Category();
        $form   = $this->createCreateForm($entity);

        return array(
            'entity' => $entity,
            'form'   => $form->createView(),
        );
    }

    /**
     * Displays a form to edit an existing Category entity.
     *
     * @Route("/{id}/edit", name="category_edit")
     * @Method("GET")
     * @Template()
     */
    public function editAction($id)
    {
        $em = $this->getDoctrine()->getManager();

        $entity = $em->getRepository('EmfoxGpsBundle:Category')->find($id);

        if (!$entity) {
            throw $this->createNotFoundException('Unable to find Category entity.');
        }

        $editForm = $this->createEditForm($entity);
        $deleteForm = $this->createDeleteForm($id);

        return array(
            'entity'      => $entity,
            'edit_form'   => $editForm->createView(),
            'delete_form' => $deleteForm->createView(),
        );
    }

    /**
    * Creates a form to edit a Category entity.
    *
    * @param Category $entity The entity
    *
    * @return \Symfony\Component\Form\Form The form
    */
    private function createEditForm(Category $entity)
    {
        $form = $this->createForm(new CategoryType(), $entity, array(
            'action' => $this->generateUrl('category_update', array('id' => $entity->getId())),
            'method' => 'PUT',
        ));

        $form->add('submit', 'submit', array('label' => '确认修改'));

        return $form;
    }
    /**
     * Edits an existing Category entity.
     *
     * @Route("/{id}", name="category_update")
     * @Method("PUT")
     * @Template("EmfoxGpsBundle:Category:edit.html.twig")
     */
    public function updateAction(Request $request, $id)
    {
        $em = $this->getDoctrine()->getManager();

        $entity = $em->getRepository('EmfoxGpsBundle:Category')->find($id);

        if (!$entity) {
            throw $this->createNotFoundException('Unable to find Category entity.');
        }

        $deleteForm = $this->createDeleteForm($id);
        $editForm = $this->createEditForm($entity);
        $editForm->handleRequest($request);

        if ($editForm->isValid()) {
            $em->flush();

            return $this->redirect($this->generateUrl('category'));
        }

        return array(
            'entity'      => $entity,
            'edit_form'   => $editForm->createView(),
            'delete_form' => $deleteForm->createView(),
        );
    }
    /**
     * Deletes a Category entity.
     *
     * @Route("/{id}", name="category_delete")
     * @Method("DELETE")
     */
    public function deleteAction(Request $request, $id)
    {
        $form = $this->createDeleteForm($id);
        $form->handleRequest($request);

        if ($form->isValid()) {
            $em = $this->getDoctrine()->getManager();
            $entity = $em->getRepository('EmfoxGpsBundle:Category')->find($id);

            if (!$entity) {
                throw $this->createNotFoundException('Unable to find Category entity.');
            }

            $em->remove($entity);
            $em->flush();
        }

        return $this->redirect($this->generateUrl('category'));
    }

    /**
     * Moves a Category entity.
     *
     * @Route("/{id}/move/{direction}", name="category_move")
     */
    public function moveAction(Request $request, $id, $direction)
    {
    	$em = $this->getDoctrine()->getManager();
    	
    	$repo = $em->getRepository('EmfoxGpsBundle:Category');
    	$entity = $repo->find($id);
    	
    	if (!$entity) {
    		throw $this->createNotFoundException('Unable to find Category entity.');
    	}

    	switch ($direction){
    		case "up":
    			$repo->moveUp($entity,1);
    			break;
    		case "down":
    			$repo->moveDown($entity,1);
    			break;
    		case "top":
    			$repo->moveUp($entity,TRUE);
    			break;
    		case "bottom":
    			$repo->moveDown($entity,TRUE);
    			break;
    		default:
    			throw $this->NotAllowedException('No method found');
    	}
    	
    	$em->flush();

    	return $this->redirect($this->generateUrl('category'));
    }
    /**
     * Creates a form to delete a Category entity by id.
     *
     * @param mixed $id The entity id
     *
     * @return \Symfony\Component\Form\Form The form
     */
    private function createDeleteForm($id)
    {
        return $this->createFormBuilder()
            ->setAction($this->generateUrl('category_delete', array('id' => $id)))
            ->setMethod('DELETE')
            ->add('submit', 'submit', array('label' => '删除'))
            ->getForm()
        ;
    }
}
