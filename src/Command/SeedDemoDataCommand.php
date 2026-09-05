<?php

namespace App\Command;

use App\Entity\Category;
use App\Entity\Trail;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Component\Console\Attribute\AsCommand;
use Symfony\Component\Console\Command\Command;
use Symfony\Component\Console\Input\InputInterface;
use Symfony\Component\Console\Input\InputOption;
use Symfony\Component\Console\Output\OutputInterface;
use Symfony\Component\Console\Style\SymfonyStyle;

/**
 * Seeds a three-level demo category tree. Only the leaf categories carry a
 * real-looking trail history (10-15 points) that moves like a tracked device:
 * consecutive points are 70-79 m apart with a gently turning heading and 1-3
 * minute gaps, so the map shows a plausible short patrol instead of a pile of
 * zero-coordinate placeholder rows. Safe to re-run: existing nodes are reused,
 * zero-coordinate trails left behind by the first version of this command are
 * cleaned up, and any leaf whose trail count does not match the plan yet is
 * rebuilt to the planned number.
 */
#[AsCommand(name: 'app:seed-demo-data', description: 'Insert demo categories and trails')]
class SeedDemoDataCommand extends Command
{
    private const ROOT_DEVID = 'demo-root-001';

    /**
     * One row per category: [title, devid, parent devid, anchor lat, anchor lng, trail count].
     * trail count > 0 marks a leaf and must stay within [10, 15].
     */
    private const PLAN = [
        // Headquarters (root)
        ['示例总部', self::ROOT_DEVID, null, 31.2304, 121.4737, 0],
        // East China branch (Shanghai) -> 4 leaves
        ['示例-华东', 'demo-east-001', self::ROOT_DEVID, 31.2304, 121.4737, 0],
        ['华东-一部', 'demo-east-a-001', 'demo-east-001', 31.2204, 121.4437, 12],
        ['华东-二部', 'demo-east-b-001', 'demo-east-001', 31.2837, 121.5092, 14],
        ['华东-三部', 'demo-east-c-001', 'demo-east-001', 31.1743, 121.4800, 11],
        ['华东-四部', 'demo-east-d-001', 'demo-east-001', 31.3200, 121.5100, 15],
        // North China branch (Beijing) -> 4 leaves
        ['示例-华北', 'demo-north-001', self::ROOT_DEVID, 39.9042, 116.4074, 0],
        ['华北-一部', 'demo-north-a-001', 'demo-north-001', 39.9169, 116.3906, 11],
        ['华北-二部', 'demo-north-b-001', 'demo-north-001', 40.0444, 116.3200, 13],
        ['华北-三部', 'demo-north-c-001', 'demo-north-001', 39.9300, 116.4550, 10],
        ['华北-四部', 'demo-north-d-001', 'demo-north-001', 39.8900, 116.4700, 14],
        // South China branch (Guangzhou) -> 4 leaves
        ['示例-华南', 'demo-south-001', self::ROOT_DEVID, 23.1291, 113.2644, 0],
        ['华南-一部', 'demo-south-a-001', 'demo-south-001', 23.1200, 113.3200, 13],
        ['华南-二部', 'demo-south-b-001', 'demo-south-001', 23.0800, 113.3000, 12],
        ['华南-三部', 'demo-south-c-001', 'demo-south-001', 23.1500, 113.3500, 15],
        ['华南-四部', 'demo-south-d-001', 'demo-south-001', 23.0500, 113.4000, 11],
    ];

    public function __construct(private readonly EntityManagerInterface $em)
    {
        parent::__construct();
    }

    protected function configure(): void
    {
        $this->addOption('apply', null, InputOption::VALUE_NONE, 'Actually write to the database (default is a dry run)');
    }

    protected function execute(InputInterface $input, OutputInterface $output): int
    {
        $io = new SymfonyStyle($input, $output);
        $repo = $this->em->getRepository(Category::class);

        $io->title('Demo seed plan');
        $this->printTree($io);
        $io->note('Leaf categories get 10-15 trail points. Consecutive points are 70-79 m apart (gently turning heading, 1-3 min gaps), like a short real patrol.');

        // Load whatever already exists so a re-run is an incremental no-op.
        $devids = array_map(static fn (array $row): string => $row[1], self::PLAN);
        $existing = $repo->findBy(['devid' => $devids]);
        $byDevid = [];
        foreach ($existing as $category) {
            $byDevid[$category->getDevid()] = $category;
        }

        $toCreate = array_filter(self::PLAN, static fn (array $row): bool => !isset($byDevid[$row[1]]));
        $toBuild = array_filter(self::PLAN, static fn (array $row): bool => $row[5] > 0);

        if (!$toCreate && !$this->demoDataNeedsWork($toBuild, $byDevid)) {
            $io->success('Demo data is already up to date – nothing to do.');

            return Command::SUCCESS;
        }

        if (!$input->getOption('apply')) {
            $io->warning(sprintf(
                'Dry run – %d category/categories to create, %d leaf trail set(s) to (re)build%s. Re-run with --apply to write.',
                count($toCreate),
                count($toBuild),
                $this->hasZeroCoordinateDemoTrails() ? ' and zero-coordinate leftovers to clean up' : ''
            ));

            return Command::SUCCESS;
        }

        // 1. Ensure the category tree exists (parents referenced in-memory so
        // the unit of work orders the inserts correctly for Gedmo).
        $created = 0;
        foreach (self::PLAN as $row) {
            if (isset($byDevid[$row[1]])) {
                continue;
            }
            $category = new Category();
            $category->setTitle($row[0]);
            $category->setDevid($row[1]);
            $category->setUpdatetime(new \DateTime());
            $category->setLat($row[3]);
            $category->setLng($row[4]);
            if (null !== $row[2]) {
                $parent = $byDevid[$row[2]] ?? null;
                if (!$parent) {
                    $io->error(sprintf('Parent "%s" not found, aborting.', $row[2]));

                    return Command::FAILURE;
                }
                $category->setParent($parent);
            }
            $this->em->persist($category);
            $byDevid[$row[1]] = $category;
            ++$created;
        }
        if ($created > 0) {
            $this->em->flush();
        }

        // 2. Drop the zero-coordinate placeholder trails the first version of
        // this command wrote (they render at 0,0 on the map). Real reports
        // with proper coordinates are never touched.
        $this->deleteZeroCoordinateDemoTrails();

        // 3. (Re)build leaf trails to the planned count.
        $written = 0;
        foreach ($toBuild as $row) {
            $leaf = $byDevid[$row[1]];
            $current = $this->em->getRepository(Trail::class)->count(['catid' => $leaf->getId()]);
            if ($current === $row[5]) {
                continue;
            }
            // The leaf already carries trails from an earlier run that no longer
            // match the plan (e.g. old 30-row seeds): replace them wholesale.
            $this->em->createQueryBuilder()
                ->delete(Trail::class, 't')
                ->where('t.catid = :catid')
                ->setParameter('catid', $leaf->getId())
                ->getQuery()
                ->execute();
            $written += $this->generateLeafTrails($leaf, $row);
        }
        $this->em->flush();

        $io->success(sprintf(
            'Done: %d categories created, %d trail rows written for %d leaf/leaves.',
            $created,
            $written,
            count($toBuild)
        ));

        return Command::SUCCESS;
    }

    private function printTree(SymfonyStyle $io): void
    {
        // PLAN is in pre-order, so a node's depth is the parent's depth + 1.
        $depth = [];
        $first = self::PLAN[0];
        $io->writeln(sprintf('%s [%s]', $first[0], $first[1]));
        foreach (array_slice(self::PLAN, 1) as $row) {
            $depth[$row[1]] = $lvl = null === $row[2] ? 1 : ($depth[$row[2]] ?? 0) + 1;
            $suffix = $row[5] > 0 ? sprintf(' — %d pts @ %.4f,%.4f', $row[5], $row[3], $row[4]) : '';
            $io->writeln(str_repeat('    ', $lvl).sprintf('%s [%s]%s', $row[0], $row[1], $suffix));
        }
    }

    /**
     * @param array<int, array<int, mixed>> $leafRows
     * @param array<string, Category>       $byDevid
     */
    private function demoDataNeedsWork(array $leafRows, array $byDevid): bool
    {
        $trailRepo = $this->em->getRepository(Trail::class);
        foreach ($leafRows as $row) {
            $leaf = $byDevid[$row[1]] ?? null;
            if (!$leaf) {
                continue; // creation is already counted in $toCreate
            }
            if ($trailRepo->count(['catid' => $leaf->getId()]) !== $row[5]) {
                return true;
            }
        }

        return $this->hasZeroCoordinateDemoTrails();
    }

    private function hasZeroCoordinateDemoTrails(): bool
    {
        return (int) $this->em->getRepository(Trail::class)->createQueryBuilder('t')
            ->select('COUNT(t.id)')
            ->join(Category::class, 'c', 'WITH', 'c.id = t.catid')
            ->where('c.devid LIKE :prefix')
            ->andWhere('t.lat = 0')
            ->andWhere('t.lng = 0')
            ->setParameter('prefix', 'demo-%')
            ->getQuery()
            ->getSingleScalarResult() > 0;
    }

    private function deleteZeroCoordinateDemoTrails(): void
    {
        $demoIds = $this->em->getRepository(Category::class)
            ->createQueryBuilder('c')
            ->select('c.id')
            ->where('c.devid LIKE :prefix')
            ->setParameter('prefix', 'demo-%')
            ->getQuery()
            ->getScalarResult();

        if (!$demoIds) {
            return;
        }
        $this->em->createQueryBuilder()
            ->delete(Trail::class, 't')
            ->where('t.catid IN (:ids)')
            ->andWhere('t.lat = 0')
            ->andWhere('t.lng = 0')
            ->setParameter('ids', array_column($demoIds, 'id'))
            ->getQuery()
            ->execute();
    }

    /**
     * Writes a deterministic random-walk trail (seeded by the devid) around the
     * category anchor. Every step is 70-79 m with a slowly turning heading, so
     * consecutive points land in the requested 70-80 m band; timestamps advance
     * 1-3 min per point. The leaf category ends up at the last (newest) point.
     *
     * @return int number of trail rows written
     */
    private function generateLeafTrails(Category $leaf, array $row): int
    {
        mt_srand(crc32($leaf->getDevid()));

        $lat = $row[3];
        $lng = $row[4];
        // A small jitter keeps leaves near, but not exactly on, their anchor.
        $lat += mt_rand(-150, 150) / 111320.0;
        $lng += mt_rand(-150, 150) / (111320.0 * cos($lat * M_PI / 180));

        $bearing = mt_rand(0, 359) * M_PI / 180;
        $points = [];
        for ($i = 0; $i < $row[5]; $i++) {
            $step = 70 + mt_rand(0, 9); // metres: 70-79, so gaps stay in the 70-80 m band
            $bearing += mt_rand(-35, 35) * M_PI / 180;
            $lat += $step * cos($bearing) / 111320.0;
            $lng += $step * sin($bearing) / (111320.0 * cos($lat * M_PI / 180));
            $points[] = [$lat, $lng];
        }

        $now = new \DateTime();
        $time = clone $now;
        $time->modify(sprintf('-%d minutes', ($row[5] - 1) * 3 + 2)); // oldest point
        foreach ($points as $i => [$pointLat, $pointLng]) {
            $trail = new Trail();
            $trail->setCatid($leaf->getId());
            $trail->setLat(round($pointLat, 6));
            $trail->setLng(round($pointLng, 6));
            $trail->setTime($time);
            $this->em->persist($trail);
            if ($i < $row[5] - 1) {
                $time = (clone $time)->modify(sprintf('+%d minutes', 1 + mt_rand(0, 2)));
            }
        }

        // The device's last known position matches the newest trail point.
        [$lastLat, $lastLng] = $points[$row[5] - 1];
        $leaf->setLat(round($lastLat, 6));
        $leaf->setLng(round($lastLng, 6));
        $leaf->setUpdatetime($time);

        return $row[5];
    }
}
