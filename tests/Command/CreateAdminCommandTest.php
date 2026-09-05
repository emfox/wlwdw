<?php

namespace App\Tests\Command;

use App\Command\CreateAdminCommand;
use App\Entity\User;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Console\Application;
use Symfony\Bundle\FrameworkBundle\Test\KernelTestCase;
use Symfony\Component\Console\Tester\CommandTester;
use Symfony\Component\PasswordHasher\Hasher\UserPasswordHasherInterface;

/**
 * Functional coverage for app:create-admin -- the admin bootstrap command that
 * README now recommends for fresh deploys in every environment (prod
 * included, where the fixtures loader does not exist).
 */
class CreateAdminCommandTest extends KernelTestCase
{
    private function commandTester(): CommandTester
    {
        $application = new Application(self::bootKernel());
        $application->setAutoExit(false);

        return new CommandTester($application->find('app:create-admin'));
    }

    private function userRepository(): \Doctrine\ORM\EntityRepository
    {
        return static::getContainer()->get(EntityManagerInterface::class)
            ->getRepository(User::class);
    }

    public function testCreatesAdminWithExplicitPassword(): void
    {
        $tester = $this->commandTester();
        $status = $tester->execute([
            '--username' => 'cmd-admin',
            '--email' => 'cmd-admin@example.com',
            '--password' => 'Very-Strong-Pass-1',
        ]);

        self::assertSame(CreateAdminCommand::SUCCESS, $status);

        $user = $this->userRepository()->findOneBy(['username' => 'cmd-admin']);
        self::assertNotNull($user);
        self::assertTrue($user->getEnabled());
        self::assertContains('ROLE_ADMIN', $user->getRoles());
        self::assertSame('cmd-admin@example.com', $user->getEmail());

        $hasher = static::getContainer()->get(UserPasswordHasherInterface::class);
        self::assertTrue($hasher->isPasswordValid($user, 'Very-Strong-Pass-1'));
    }

    public function testEmailDefaultsFromUsername(): void
    {
        $tester = $this->commandTester();
        $tester->execute(['--username' => 'ops', '--password' => 'pw-123456']);

        $user = $this->userRepository()->findOneBy(['username' => 'ops']);
        self::assertNotNull($user);
        self::assertSame('ops@wlwdw.rpwt.org', $user->getEmail());
    }

    public function testGeneratesRandomPasswordInNonInteractiveMode(): void
    {
        $tester = $this->commandTester();
        $tester->execute(['--username' => 'rand-admin']);

        self::assertSame(CreateAdminCommand::SUCCESS, $tester->getStatusCode());
        self::assertStringContainsString('Generated password', $tester->getDisplay());

        // The printed password must actually unlock the account.
        preg_match('/Generated password \(shown once\): (\S+)/', $tester->getDisplay(), $m);
        self::assertNotEmpty($m[1] ?? '', 'command should have printed a generated password');

        $user = $this->userRepository()->findOneBy(['username' => 'rand-admin']);
        self::assertNotNull($user);
        $hasher = static::getContainer()->get(UserPasswordHasherInterface::class);
        self::assertTrue($hasher->isPasswordValid($user, $m[1]));
    }

    public function testRefusesToOverwriteExistingUser(): void
    {
        $tester = $this->commandTester();
        $tester->execute(['--username' => 'taken', '--password' => 'first-pass']);

        $status = $tester->execute(['--username' => 'taken', '--password' => 'second-pass']);
        self::assertSame(CreateAdminCommand::FAILURE, $status);
        self::assertStringContainsString('already exists', $tester->getDisplay());

        // The original password stays valid and the second one does not.
        $user = $this->userRepository()->findOneBy(['username' => 'taken']);
        self::assertNotNull($user);
        $hasher = static::getContainer()->get(UserPasswordHasherInterface::class);
        self::assertTrue($hasher->isPasswordValid($user, 'first-pass'));
        self::assertFalse($hasher->isPasswordValid($user, 'second-pass'));
    }

    public function testCommandIsAvailableInProdEnvironment(): void
    {
        // Boot the kernel in prod to mirror the fresh-deploy scenario the
        // command was added for (fixtures loader is dev/test only).
        $kernel = self::bootKernel(['environment' => 'prod', 'debug' => false]);
        $application = new Application($kernel);
        $application->setAutoExit(false);

        $tester = new CommandTester($application->find('app:create-admin'));
        $status = $tester->execute(['--username' => 'prod-admin', '--password' => 'prod-pass']);

        self::assertSame(CreateAdminCommand::SUCCESS, $status);

        // In prod there is no test.service_container; talk to the real one.
        // (Password hash correctness is already covered by the dev-env tests.)
        $em = $kernel->getContainer()->get('doctrine')->getManager();
        $user = $em->getRepository(User::class)->findOneBy(['username' => 'prod-admin']);
        self::assertNotNull($user);
        self::assertTrue($user->getEnabled());

        // dama's per-test rollback only exists in the test env; clean up here so
        // this case never leaks a committed user into other tests.
        $em->remove($user);
        $em->flush();
    }
}
