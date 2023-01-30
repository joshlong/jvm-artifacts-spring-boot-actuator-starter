package com.joshlong.dependencies;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.io.ClassPathResource;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.stream.Collectors;

/**
 * This looks for a file produced by configuring Apache Maven using the plugin
 * configuration documented in <pre>README.md</pre>.
 *
 * @author Josh Long
 */
@Slf4j
class MavenDependencyPluginDependencyReader implements DependencyReader, BeanFactoryInitializationAotProcessor {

	private final ClassPathResource classpath = new ClassPathResource("/META-INF/classpath");

	private final Set<Dependency> dependencies = new ConcurrentSkipListSet<>(
			Comparator.comparing(o -> (o.artifactId() + o.groupId() + o.version())));

	@Override
	public BeanFactoryInitializationAotContribution processAheadOfTime(ConfigurableListableBeanFactory beanFactory) {
		return (context, beanFactoryInitializationCode) -> context.getRuntimeHints().resources()
				.registerResource(this.classpath);
	}

	MavenDependencyPluginDependencyReader() throws Exception {
		if (!classpath.exists()) {
			return;
		}
		try (var in = classpath.getInputStream()) {
			var bytes = in.readAllBytes();
			this.dependencies.addAll(dependencies(new String(bytes)));
		}
	}

	private Set<Dependency> dependencies(String manifestString) {
		// there are two parts to the Maven manifest: the classpath and the dependencies
		// tree
		// the classpath is a list of jar files that are on the classpath.
		// the dependency tree is all the dependencies that are used by the application.
		var lines = manifestString.lines().toList();
		var cp = lines.get(0).split("::");
		var manifestDependencies = lines.subList(1, lines.size()).stream().map(line -> {
			var nc = -1;
			while (true) {
				nc += 1;
				if (!Character.isAlphabetic(line.charAt(nc))) {
					continue;
				}
				var dep = line.substring(nc);
				var triad = dep.split(":");
				var groupId = triad[0];
				var artifactId = triad[1];
				var version = triad[3];
				return new Dependency(groupId, artifactId, version);
			}
		})//
				.toList();
		return new HashSet<>(manifestDependencies)//
				.stream()//
				.filter(dependency -> Arrays//
						.stream(cp)//
						.anyMatch(fileName -> {
							if (fileName.contains(dependency.version())) {
								if (fileName.contains(dependency.artifactId())) {
									var gid = dependency.groupId();
									var gidAlternate = dependency.groupId().replaceAll("\\.", "/");
									return fileName.contains(gid) || fileName.contains(gidAlternate);
								}
							}
							return false;
						}))
				.collect(Collectors.toSet());

	}

	@Override
	public Set<Dependency> dependencies() {
		return this.dependencies;
	}

}
