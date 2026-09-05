<?php

namespace App\Command;

use App\Entity\User;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Component\Console\Attribute\AsCommand;
use Symfony\Component\Console\Command\Command;
use Symfony\Component\Console\Input\InputInterface;
use Symfony\Component\Console\Input\InputOption;
use Symfony\Component\Console\Output\OutputInterface;
use Symfony\Component\Console\Style\SymfonyStyle;
use Symfony\Component\PasswordHasher\Hasher\UserPasswordHasherInterface;

/**
 * Creates an admin user in any environment. doctrine:fixtures:load only
 * exists in dev/test (DoctrineFixturesBundle is not registered in prod), so a
 * fresh prod deploy had no supported way to create the first admin -- README
 * used to point at fixtures for this. The command refuses to overwrite an
 * existing user; use the user admin page to change a password instead.
 */
#[AsCommand(name: 'app:create-admin', description: 'Create an admin user (works in every environment)')]
class CreateAdminCommand extends Command
{
    public function __construct(
        private readonly EntityManagerInterface $em,
        private readonly UserPasswordHasherInterface $passwordHasher
    ) {
        parent::__construct();
    }

    protected function configure(): void
    {
        $this->addOption('username', null, InputOption::VALUE_REQUIRED, 'Username', 'admin');
        $this->addOption('email', null, InputOption::VALUE_REQUIRED, 'Email (defaults to <username>@wlwdw.rpwt.org)');
        $this->addOption('password', 'p', InputOption::VALUE_REQUIRED, 'Password (if omitted, prompts interactively or generates a random one)');
    }

    protected function execute(InputInterface $input, OutputInterface $output): int
    {
        $io = new SymfonyStyle($input, $output);
        $username = $input->getOption('username');
        $email = $input->getOption('email') ?? sprintf('%s@wlwdw.rpwt.org', $username);

        if (null !== $this->em->getRepository(User::class)->findOneBy(['username' => $username])) {
            $io->error(sprintf('User "%s" already exists. Refusing to overwrite; change its password from the user admin page instead.', $username));

            return Command::FAILURE;
        }

        $password = $input->getOption('password');
        $generated = false;
        if (null === $password || '' === $password) {
            $password = $io->askHidden('Password (leave empty to generate a random one)');
            if (null === $password || '' === $password) {
                $password = bin2hex(random_bytes(16));
                $generated = true;
            }
        }

        $user = new User();
        $user->setUsername($username);
        $user->setEmail($email);
        $user->setEnabled(true);
        $user->setRoles(['ROLE_ADMIN']);
        $user->setPassword($this->passwordHasher->hashPassword($user, $password));

        $this->em->persist($user);
        $this->em->flush();

        $io->success(sprintf('Admin user "%s" created.', $username));
        $io->note('Logged-in credentials will only work over HTTPS in production.');
        if ($generated) {
            $io->writeln(sprintf('Generated password (shown once): <options=bold>%s</>', $password));
        }

        return Command::SUCCESS;
    }
}
