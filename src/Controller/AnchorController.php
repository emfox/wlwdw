<?php

namespace App\Controller;

use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\Routing\Annotation\Route;
use Sensio\Bundle\FrameworkExtraBundle\Configuration\Template;
use App\Entity\Anchor;
use App\Form\AnchorType;
use Symfony\Component\Form\Extension\Core\Type\SubmitType;

/**
 * Anchor controller.
 */
class AnchorController extends AbstractController
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
     * Lists all Anchor entities via ajax.
     *
     * @Route("/anchor/list", name="anchor_list")
     */
    public function list(): \Symfony\Component\HttpFoundation\Response
	{
		$em = $this->managerRegistry->getManager();
		$entities = $em->getRepository('App\Entity\Anchor')->findBy(
				array('enabled'=>true),
				array('id'=>'ASC')
		);
		$anchor = array();
		foreach($entities as $entity)
		{
			$id = $entity->getId();
			$anchor[$id] = array("title" => $entity->getTitle(),
					"lng" => $entity->getLng(),
					"lat" => $entity->getLat(),
					"icon" => $entity->getIcon());
		}
		$response = array("code" => 100, "success" => true, "anchor" => $anchor);
		return new Response(json_encode($response, JSON_THROW_ON_ERROR));
	}
    /**
     * Lists all Anchor entities.
     *
     * @Route("/anchor/", name="anchor", methods={"GET"})
     * @Template("anchor/index.html.twig")
     */
    public function index(): array
    {
        $em = $this->managerRegistry->getManager();

        $entities = $em->getRepository('App\Entity\Anchor')->findAll();

        return array(
            'entities' => $entities,
        );
    }
    /**
     * Creates a new Anchor entity.
     *
     * @Route("/anchor/", name="anchor_create", methods={"POST"})
     * @Template("anchor/new.html.twig")
     */
    public function create(Request $request)
    {
        $entity = new Anchor();
        $form = $this->createCreateForm($entity);
        $form->handleRequest($request);

        if ($form->isValid()) {
            $em = $this->managerRegistry->getManager();
            $em->persist($entity);
            $em->flush();

            return $this->redirectToRoute('anchor');
        }

        return array(
            'entity' => $entity,
            'form'   => $form->createView(),
        );
    }

    /**
     * Creates a form to create a Anchor entity.
     *
     * @param Anchor $entity The entity
     *
     * @return \Symfony\Component\Form\Form The form
     */
    private function createCreateForm(Anchor $entity)
    {
        $form = $this->createForm(AnchorType::class, $entity, array(
            'action' => $this->generateUrl('anchor_create'),
            'method' => 'POST',
        ));

        $form->add('submit', SubmitType::class, array('label' => '保存参考点'));

        return $form;
    }

    /**
     * Displays a form to create a new Anchor entity.
     *
     * @Route("/anchor/new", name="anchor_new", methods={"GET"})
     * @Template("anchor/new.html.twig")
     */
    public function new(): array
    {
        $entity = new Anchor();
        $form   = $this->createCreateForm($entity);

        return array(
            'entity' => $entity,
            'form'   => $form->createView(),
        );
    }

    /**
     * Displays a form to edit an existing Anchor entity.
     *
     * @Route("/anchor/{id}/edit", name="anchor_edit", methods={"GET"})
     * @Template("anchor/edit.html.twig")
     */
    public function edit($id): array
    {
        $em = $this->managerRegistry->getManager();

        $entity = $em->getRepository('App\Entity\Anchor')->find($id);

        if (!$entity) {
            throw $this->createNotFoundException('Unable to find Anchor entity.');
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
    * Creates a form to edit a Anchor entity.
    *
    * @param Anchor $entity The entity
    *
    * @return \Symfony\Component\Form\Form The form
    */
    private function createEditForm(Anchor $entity)
    {
        $form = $this->createForm(AnchorType::class, $entity, array(
            'action' => $this->generateUrl('anchor_update', array('id' => $entity->getId())),
            'method' => 'PUT',
        ));

        $form->add('submit', SubmitType::class, array('label' => '确认修改'));

        return $form;
    }
    /**
     * Edits an existing Anchor entity.
     *
     * @Route("/anchor/{id}", name="anchor_update", methods={"PUT"})
     * @Template("anchor/edit.html.twig")
     */
    public function update(Request $request, $id)
    {
        $em = $this->managerRegistry->getManager();

        $entity = $em->getRepository('App\Entity\Anchor')->find($id);

        if (!$entity) {
            throw $this->createNotFoundException('Unable to find Anchor entity.');
        }

        $deleteForm = $this->createDeleteForm($id);
        $editForm = $this->createEditForm($entity);
        $editForm->handleRequest($request);

        if ($editForm->isValid()) {
            $em->flush();

            return $this->redirectToRoute('anchor');
        }

        return array(
            'entity'      => $entity,
            'edit_form'   => $editForm->createView(),
            'delete_form' => $deleteForm->createView(),
        );
    }
    /**
     * Deletes a Anchor entity.
     *
     * @Route("/anchor/{id}", name="anchor_delete", methods={"DELETE"})
     */
    public function delete(Request $request, $id): \Symfony\Component\HttpFoundation\RedirectResponse
    {
        $form = $this->createDeleteForm($id);
        $form->handleRequest($request);

        if ($form->isValid()) {
            $em = $this->managerRegistry->getManager();
            $entity = $em->getRepository('App\Entity\Anchor')->find($id);

            if (!$entity) {
                throw $this->createNotFoundException('Unable to find Anchor entity.');
            }

            $em->remove($entity);
            $em->flush();
        }

        return $this->redirectToRoute('anchor');
    }

    /**
     * Creates a form to delete a Anchor entity by id.
     *
     * @param mixed $id The entity id
     *
     * @return \Symfony\Component\Form\Form The form
     */
    private function createDeleteForm($id)
    {
        return $this->createFormBuilder()
            ->setAction($this->generateUrl('anchor_delete', array('id' => $id)))
            ->setMethod(\Symfony\Component\HttpFoundation\Request::METHOD_DELETE)
            ->add('submit', SubmitType::class, array('label' => '删除该参考点'))
            ->getForm()
        ;
    }
}
