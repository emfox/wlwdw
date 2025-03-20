<?php

namespace App\Controller;

use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Sensio\Bundle\FrameworkExtraBundle\Configuration\Template;
use Symfony\Component\Routing\Attribute\Route;
use App\Entity\User;
use App\Form\UserType;
use Symfony\Component\Form\Extension\Core\Type\SubmitType;
use Symfony\Component\PasswordHasher\Hasher\UserPasswordHasherInterface;

/**
 * User controller.
 */
class UserController extends AbstractController
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
     * Lists all User entities.
     */
    #[Route(path: '/user/', name: 'user', methods: ['GET'])]
    public function index(): Response
    {
        $em = $this->managerRegistry->getManager();

        $entities = $em->getRepository('App\Entity\User')->findAll();

        return $this->render('user/index.html.twig', array(
            'entities' => $entities,
        ));
    }
    /**
     * Creates a new User entity.
     */
    #[Route(path: '/user/', name: 'user_create', methods: ['POST'])]
    public function create(Request $request, UserPasswordHasherInterface $hasher): Response
    {
        $entity = new User();
        $form = $this->createCreateForm($entity);
        $form->handleRequest($request);

        if ($form->isValid()) {
        	$password = $form->get('password')->getData();
        	if(strlen($password)==0)
        		$password='000000';
                $hashed = $hasher->hashPassword($entity, $password);
                $entity->setPassword($hashed);
        	$entity->setEmail($entity->getUsername() . "@user.wlwdw.com");
        	$entity->setEnabled(TRUE);
            $em = $this->managerRegistry->getManager();
            $em->persist($entity);
            $em->flush();

            return $this->redirectToRoute('user');
        }

        return $this->render('user/new.html.twig', array(
            'entity' => $entity,
            'form'   => $form->createView(),
        ));
    }

    /**
     * Creates a form to create a User entity.
     *
     * @param User $entity The entity
     *
     * @return \Symfony\Component\Form\Form The form
     */
    private function createCreateForm(User $entity)
    {
        $form = $this->createForm(UserType::class, $entity, array(
            'action' => $this->generateUrl('user_create'),
            'method' => 'POST',
        ));

        $form->add('submit', SubmitType::class, array('label' => '保存用户'));

        return $form;
    }

    /**
     * Displays a form to create a new User entity.
     */
    #[Route(path: '/user/new', name: 'user_new', methods: ['GET'])]
    public function new(): Response
    {
        $entity = new User();
        $form   = $this->createCreateForm($entity);

        return $this->render('user/new.html.twig', array(
            'entity' => $entity,
            'form'   => $form->createView(),
        ));
    }

    /**
     * Displays a form to edit an existing User entity.
     */
    #[Route(path: '/user/{id}/edit', name: 'user_edit', methods: ['GET'])]
    public function edit($id): Response
    {
        $em = $this->managerRegistry->getManager();

        $entity = $em->getRepository('App\Entity\User')->find($id);

        if (!$entity) {
            throw $this->createNotFoundException('Unable to find User entity.');
        }

        $editForm = $this->createEditForm($entity);
        $deleteForm = $this->createDeleteForm($id);

        return $this->render('user/edit.html.twig', array(
            'entity'      => $entity,
            'edit_form'   => $editForm->createView(),
            'delete_form' => $deleteForm->createView(),
        ));
    }

    /**
    * Creates a form to edit a User entity.
    *
    * @param User $entity The entity
    *
    * @return \Symfony\Component\Form\Form The form
    */
    private function createEditForm(User $entity)
    {
        $form = $this->createForm(UserType::class, $entity, array(
            'action' => $this->generateUrl('user_update', array('id' => $entity->getId())),
            'method' => 'PUT',
        ));

        $form->add('submit', SubmitType::class, array('label' => '确认修改'));

        return $form;
    }
    /**
     * Edits an existing User entity.
     */
    #[Route(path: '/user/{id}', name: 'user_update', methods: ['PUT'])]
    public function update(Request $request, $id, UserPasswordHasherInterface $hasher): Response
    {
        $em = $this->managerRegistry->getManager();

        $entity = $em->getRepository('App\Entity\User')->find($id);

        if (!$entity) {
            throw $this->createNotFoundException('Unable to find User entity.');
        }
        $deleteForm = $this->createDeleteForm($id);
        $editForm = $this->createEditForm($entity);
        $editForm->handleRequest($request);

        if ($editForm->isValid()) {
            $password = $editForm->get('password')->getData();
            if (strlen($password)>0) {
                    $entity->setPassword($hasher->hashPassword($entity, $password));
            }
            $entity->setEmail($entity->getUsername() . "@user.wlwdw.com");
            $em->persist($entity);
            $em->flush();

            return $this->redirectToRoute('user');
        }

        return $this->render('user/edit.html.twig', array(
            'entity'      => $entity,
            'edit_form'   => $editForm->createView(),
            'delete_form' => $deleteForm->createView(),
        ));
    }
    /**
     * Deletes a User entity.
     */
    #[Route(path: '/user/{id}', name: 'user_delete', methods: ['DELETE'])]
    public function delete(Request $request, $id): \Symfony\Component\HttpFoundation\RedirectResponse
    {
        $form = $this->createDeleteForm($id);
        $form->handleRequest($request);

        if ($form->isValid()) {
            $em = $this->managerRegistry->getManager();
            $entity = $em->getRepository('App\Entity\User')->find($id);

            if (!$entity) {
                throw $this->createNotFoundException('Unable to find User entity.');
            }

            $em->remove($entity);
            $em->flush();
        }

        return $this->redirectToRoute('user');
    }

    /**
     * Creates a form to delete a User entity by id.
     *
     * @param mixed $id The entity id
     *
     * @return \Symfony\Component\Form\Form The form
     */
    private function createDeleteForm($id)
    {
        return $this->createFormBuilder()
            ->setAction($this->generateUrl('user_delete', array('id' => $id)))
            ->setMethod(\Symfony\Component\HttpFoundation\Request::METHOD_DELETE)
            ->add('submit', SubmitType::class, array('label' => '删除该用户'))
            ->getForm()
        ;
    }
}
