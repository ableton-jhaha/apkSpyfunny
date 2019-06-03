package jadx.core.export;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jadx.core.dex.attributes.AFlag;
import jadx.core.dex.nodes.ClassNode;
import jadx.core.dex.nodes.DexNode;
import jadx.core.dex.nodes.RootNode;
import jadx.core.utils.exceptions.JadxRuntimeException;
import jadx.core.utils.files.FileUtils;

public class ExportGradleProject {

	private static final Logger LOG = LoggerFactory.getLogger(ExportGradleProject.class);

	private static final Set<String> IGNORE_CLS_NAMES = new HashSet<>(Arrays.asList("R", "BuildConfig"));

	private final RootNode root;
	private final File outDir;
	private File srcOutDir;
	private File resOutDir;

	public ExportGradleProject(RootNode root, File outDir) {
		this.root = root;
		this.outDir = outDir;
		this.srcOutDir = new File(outDir, "src/main/java");
		this.resOutDir = new File(outDir, "src/main");
	}

	public void init(boolean x) {
		try {
			FileUtils.makeDirs(srcOutDir);
			FileUtils.makeDirs(resOutDir);
			skipGeneratedClasses();
		} catch (Exception e) {
			throw new JadxRuntimeException("Gradle export failed", e);
		}
	}

	public void saveBuildGradle(List<String> dependencies) throws IOException {
		TemplateFile tmpl = TemplateFile.fromResources("/export/build.gradle.tmpl");
		String appPackage = root.getAppPackage();
		if (appPackage == null) {
			appPackage = "UNKNOWN";
		}
		tmpl.add("applicationId", appPackage);
		// TODO: load from AndroidManifest.xml
		tmpl.add("minSdkVersion", 9);
		tmpl.add("targetSdkVersion", 21);
		tmpl.add("extractedDependencies", String.join(System.lineSeparator(), dependencies.stream()
				.map(dependency -> "implementation '" + dependency + "'").collect(Collectors.toList())));
		tmpl.save(new File(outDir, "build.gradle"));
	}

	private void skipGeneratedClasses() {
		for (DexNode dexNode : root.getDexNodes()) {
			List<ClassNode> classes = dexNode.getClasses();
			for (ClassNode cls : classes) {
				String shortName = cls.getClassInfo().getShortName();
				if (IGNORE_CLS_NAMES.contains(shortName)) {
					cls.add(AFlag.DONT_GENERATE);
					LOG.debug("Skip class: {}", cls);
				}
			}
		}
	}

	public File getSrcOutDir() {
		return srcOutDir;
	}

	public File getResOutDir() {
		return resOutDir;
	}
}
