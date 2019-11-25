<?php

namespace Emfox\GpsBundle\Controller;

use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Bundle\FrameworkBundle\Controller\Controller;
use Sensio\Bundle\FrameworkExtraBundle\Configuration\Method;
use Sensio\Bundle\FrameworkExtraBundle\Configuration\Route;
use Sensio\Bundle\FrameworkExtraBundle\Configuration\Template;
use Emfox\GpsBundle\Entity\Anchor;
use Emfox\GpsBundle\Form\AnchorType;
use Symfony\Component\Form\Extension\Core\Type\SubmitType;

/**
 * Anchor controller.
 *
 * @Route("/anchor")
 */
class AnchorController extends Controller
{
	/**
	 * Lists all Anchor entities via ajax.
	 *
	 * @Route("/list", name="anchor_list")
	 */
	public function listAction()
	{
		$em = $this->getDoctrine()->getManager();
		$entities = $em->getRepository('EmfoxGpsBundle:Anchor')->findBy(
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
     * @Route("/", name="anchor")
     * @Method("GET")
     * @Template("EmfoxGpsBundle:Anchor:index.html.twig")
     */
    public function indexAction()
    {
        $em = $this->getDoctrine()->getManager();

        $entities = $em->getRepository('EmfoxGpsBundle:Anchor')->findAll();

        return array(
            'entities' => $entities,
        );
    }
    /**
     * Creates a new Anchor entity.
     *
     * @Route("/", name="anchor_create")
     * @Method("POST")
     * @Template("EmfoxGpsBundle:Anchor:new.html.twig")
     */
    public function createAction(Request $request)
    {
        $entity = new Anchor();
        $form = $this->createCreateForm($entity);
        $form->handleRequest($request);

        if ($form->isValid()) {
            $em = $this->getDoctrine()->getManager();
            $em->persist($entity);
            $em->flush();

            return $this->redirect($this->generateUrl('anchor'));
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
     * @Route("/new", name="anchor_new")
     * @Method("GET")
     * @Template("EmfoxGpsBundle:Anchor:new.html.twig")
     */
    public function newAction()
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
     * @Route("/{id}/edit", name="anchor_edit")
     * @Method("GET")
     * @Template("EmfoxGpsBundle:Anchor:edit.html.twig")
     */
    public function editAction($id)
    {
        $em = $this->getDoctrine()->getManager();

        $entity = $em->getRepository('EmfoxGpsBundle:Anchor')->find($id);

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
     * @Route("/{id}", name="anchor_update")
     * @Method("PUT")
     * @Template("EmfoxGpsBundle:Anchor:edit.html.twig")
     */
    public function updateAction(Request $request, $id)
    {
        $em = $this->getDoctrine()->getManager();

        $entity = $em->getRepository('EmfoxGpsBundle:Anchor')->find($id);

        if (!$entity) {
            throw $this->createNotFoundException('Unable to find Anchor entity.');
        }

        $deleteForm = $this->createDeleteForm($id);
        $editForm = $this->createEditForm($entity);
        $editForm->handleRequest($request);

        if ($editForm->isValid()) {
            $em->flush();

            return $this->redirect($this->generateUrl('anchor'));
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
     * @Route("/{id}", name="anchor_delete")
     * @Method("DELETE")
     */
    public function deleteAction(Request $request, $id)
    {
        $form = $this->createDeleteForm($id);
        $form->handleRequest($request);

        if ($form->isValid()) {
            $em = $this->getDoctrine()->getManager();
            $entity = $em->getRepository('EmfoxGpsBundle:Anchor')->find($id);

            if (!$entity) {
                throw $this->createNotFoundException('Unable to find Anchor entity.');
            }

            $em->remove($entity);
            $em->flush();
        }

        return $this->redirect($this->generateUrl('anchor'));
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
            ->setMethod('DELETE')
            ->add('submit', SubmitType::class, array('label' => '删除该参考点'))
            ->getForm()
        ;
    }
}
