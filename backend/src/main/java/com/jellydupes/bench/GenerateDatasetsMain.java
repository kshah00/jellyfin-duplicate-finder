package com.jellydupes.bench;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes the seeded synthetic libraries to disk:
 * {@code mvn -q compile exec:java -Dexec.mainClass=com.jellydupes.bench.GenerateDatasetsMain -Dexec.args="../samples"}.
 */
public final class GenerateDatasetsMain {

    public static final long SEED = 42L;

    public static void main(String[] args) throws IOException {
        Path dir = Path.of(args.length > 0 ? args[0] : "data");
        Files.createDirectories(dir);
        LibraryGenerator generator = new LibraryGenerator();
        for (int size : new int[]{1_000, 10_000, 100_000}) {
            LibraryGenerator.Dataset d = generator.generate(size, SEED + size);
            Path file = dir.resolve("library-" + size + ".csv");
            Files.writeString(file, d.toCsv());
            System.out.printf("%s: %d records, %d planted exact variants, %d planted near variants (seed %d)%n",
                    file, d.rows().size(), d.exactVariants(), d.nearVariants(), d.seed());
        }
    }
}
