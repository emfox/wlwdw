<?php

namespace App\Controller;

use DateTime;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\Routing\Annotation\Route;
use Sensio\Bundle\FrameworkExtraBundle\Configuration\Template;
use App\Entity\Category;
use App\Form\CategoryType;
use App\Entity\Trail;
use Symfony\Component\Form\Extension\Core\Type\SubmitType;

/**
 * Category controller.
 */
class CategoryController extends AbstractController
{
	/**
     * @var \Doctrine\Persistence\ManagerRegistry
     */
    private $managerRegistry;
    public function __construct(\Doctrine\Persistence\ManagerRegistry $managerRegistry)
    {
        $this->managerRegistry = $managerRegistry;
    }
    /**
     * Lists all Category entities via ajax.
     *
     * @Route("/category/hierarchy", name="category_hierarchy", methods={"GET"})
     */
    public function hierarchy($root = null): \Symfony\Component\HttpFoundation\Response
	{
		$em = $this->managerRegistry->getManager();
		$repo = $em->getRepository('App\Entity\Category');
		$repo->setChildrenIndex('children');
		$arrayTree = $repo->childrenHierarchy();
		$response = array("code" => 100, "success" => true, "ztree" => $arrayTree);
		return new Response(json_encode($response, JSON_THROW_ON_ERROR));
	}

    /**
     * Lists all Category entities.
     *
     * @Route("/category/", name="category", methods={"GET"})
     * @Template("category/index.html.twig")
     */
    public function index(): array
    {
        $em = $this->managerRegistry->getManager();
        $qb = $em->createQueryBuilder();
        
        $qb->select('c')
        ->from('App\Entity\Category','c')
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
     * @Route("/category/", name="category_create", methods={"POST"})
     * @Template("category/new.html.twig")
     */
    public function create(Request $request)
    {
        $entity = new Category();
        $form = $this->createCreateForm($entity);
        $form->handleRequest($request);

        if ($form->isValid()) {
        	$entity->setUpdatetime(new DateTime());
        	$entity->setLat('0');
        	$entity->setLng('0');
            $em = $this->managerRegistry->getManager();
            $em->persist($entity);
            $em->flush();
            for($i=1;$i<=30;$i++)
            {
            	$trail = new Trail();
            	$trail->setCatid($entity->getId());
            	$trail->setLat('0');
            	$trail->setLng('0');
            	$trail->setTime(new DateTime());
            	$em->persist($trail);
            }
            $em->flush();

            return $this->redirectToRoute('category');
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
        $form = $this->createForm(CategoryType::class, $entity, array(
            'action' => $this->generateUrl('category_create'),
            'method' => 'POST',
        ));

        $form->add('submit', SubmitType::class, array('label' => '保存单位'));

        return $form;
    }

    /**
     * Displays a form to create a new Category entity.
     *
     * @Route("/category/new", name="category_new", methods={"GET"})
     * @Template("category/new.html.twig")
     */
    public function new(): array
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
     * @Route("/category/{id}/edit", name="category_edit", methods={"GET"})
     * @Template("category/edit.html.twig")
     */
    public function edit($id): array
    {
        $em = $this->managerRegistry->getManager();

        $entity = $em->getRepository('App\Entity\Category')->find($id);

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
        $form = $this->createForm(CategoryType::class, $entity, array(
            'action' => $this->generateUrl('category_update', array('id' => $entity->getId())),
            'method' => 'PUT',
        ));

        $form->add('submit', SubmitType::class, array('label' => '确认修改'));

        return $form;
    }
    /**
     * Edits an existing Category entity.
     *
     * @Route("/category/{id}", name="category_update", methods={"PUT"})
     * @Template("category/edit.html.twig")
     */
    public function update(Request $request, $id)
    {
        $em = $this->managerRegistry->getManager();

        $entity = $em->getRepository('App\Entity\Category')->find($id);

        if (!$entity) {
            throw $this->createNotFoundException('Unable to find Category entity.');
        }

        $deleteForm = $this->createDeleteForm($id);
        $editForm = $this->createEditForm($entity);
        $editForm->handleRequest($request);

        if ($editForm->isValid()) {
            $em->flush();

            return $this->redirectToRoute('category');
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
     * @Route("/category/{id}", name="category_delete", methods={"DELETE"})
     */
    public function delete(Request $request, $id): \Symfony\Component\HttpFoundation\RedirectResponse
    {
        $form = $this->createDeleteForm($id);
        $form->handleRequest($request);

        if ($form->isValid()) {
            $em = $this->managerRegistry->getManager();
            $entity = $em->getRepository('App\Entity\Category')->find($id);

            if (!$entity) {
                throw $this->createNotFoundException('Unable to find Category entity.');
            }

            $em->remove($entity);
            $em->flush();
        }

        return $this->redirectToRoute('category');
    }

    /**
     * Moves a Category entity.
     *
     * @Route("/category/{id}/move/{direction}", name="category_move")
     */
    public function move($id, $direction): \Symfony\Component\HttpFoundation\RedirectResponse
    {
    	$em = $this->managerRegistry->getManager();
    	
    	$repo = $em->getRepository('App\Entity\Category');
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

    	return $this->redirectToRoute('category');
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
            ->setMethod(\Symfony\Component\HttpFoundation\Request::METHOD_DELETE)
            ->add('submit', SubmitType::class, array('label' => '删除该单位'))
            ->getForm()
        ;
    }
}
