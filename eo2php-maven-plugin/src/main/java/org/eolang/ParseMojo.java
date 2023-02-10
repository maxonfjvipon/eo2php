package org.eolang;

import com.jcabi.log.Logger;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.cactoos.io.InputOf;
import org.cactoos.io.OutputTo;
import org.cactoos.set.SetOf;
import org.eolang.parser.Syntax;
import org.slf4j.impl.StaticLoggerBinder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.HashSet;
import java.util.Set;

@Mojo(
        name = "parse",
        defaultPhase = LifecyclePhase.GENERATE_SOURCES,
        threadSafe = true,
        requiresDependencyResolution = ResolutionScope.COMPILE
)
final public class ParseMojo extends AbstractMojo {
    /**
     * Target directory.
     */
    @Parameter(
            required = true,
            defaultValue = "${project.build.directory}/eo"
    )
    private File targetDir;

    /**
     * Directory in which .eo files are located.
     */
    @Parameter(
            required = true,
            defaultValue = "${project.basedir}/src/main/eo"
    )
    private File sourcesDir;

    /**
     * List of inclusion GLOB filters for finding EO files.
     */
    @Parameter
    private final Set<String> includes = new SetOf<>("**/*.eo");

    /**
     * List of exclusion GLOB filters for finding EO files.
     */
    @Parameter
    private final Set<String> excludes = new HashSet<>(0);

    @Override
    public void execute() throws MojoFailureException {
        StaticLoggerBinder.getSingleton().setMavenLog(this.getLog());
        try {
            Files.walk(this.sourcesDir.toPath())
                    .filter(file -> !file.toFile().isDirectory())
                    .filter(
                            file -> this.includes.stream().anyMatch(
                                    glob -> ParseMojo.matcher(glob).matches(file)
                            )
                    )
                    .filter(
                            file -> this.excludes.stream().noneMatch(
                                    glob -> ParseMojo.matcher(glob).matches(file)
                            )
                    )
                    .forEach(this::parse);
        } catch (final IOException ex) {
            throw new MojoFailureException(
                    String.format(
                            "Can't list EO files in %s",
                            this.sourcesDir
                    ),
                    ex
            );
        }
    }

    /**
     * Create glob matcher from text.
     *
     * @param text The pattern
     * @return Matcher
     */
    private static PathMatcher matcher(final String text) {
        return FileSystems.getDefault()
                .getPathMatcher(String.format("glob:%s", text));
    }

    /**
     * Parse EO file to XML.
     *
     * @param file EO file
     */
    private void parse(final Path file) {
        // TODO: add optimizations to the pipeline
        final String name =  file.toFile().getName().replaceAll(".eo", "");
        final String xml = String.format("%s.xmir", name);
        final Path path = this.targetDir.toPath()
                .resolve("01-parse")
                .resolve(xml);
        try {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            new Syntax(
                    name,
                    new InputOf(file),
                    new OutputTo(baos)
            ).parse();
            new Save(baos.toString(), path).save();
        } catch (final IOException ex) {
            throw new IllegalStateException(
                    String.format(
                            "Can't parse %s into %s",
                            file, this.targetDir
                    ),
                    ex
            );
        }
        Logger.info(this, "%s parsed to %s", file, path);
    }
}
