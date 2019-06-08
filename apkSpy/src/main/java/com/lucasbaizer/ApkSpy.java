package com.lucasbaizer;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;

public class ApkSpy {
	private static String getClasspath(String... libs) {
		return String.join(File.pathSeparator, Arrays.stream(libs)
				.map(lib -> String.join(File.separator, "..", "libs", lib)).collect(Collectors.toList()));
	}

	public static boolean lint(String apk, String className, ClassBreakdown content, OutputStream out)
			throws IOException, InterruptedException {
		System.out.println("Linting: " + apk);
		File modifyingApk = new File(apk);
		Path root = Paths.get("project-tmp");
		Map<String, ClassBreakdown> classes = Collections.singletonMap(className, content);

		Util.attemptDelete(root.toFile());

		String pkg = className.substring(0, className.lastIndexOf('.'));
		Path folder = root.resolve(Paths.get("src", pkg.replace('.', File.separatorChar)));
		if (!Files.isDirectory(folder)) {
			Files.createDirectories(folder);
		}
		Files.write(root.resolve(Paths.get("src", className.replace('.', File.separatorChar) + ".java")),
				content.toString().getBytes(StandardCharsets.UTF_8));

		Path stubPath = Paths.get(System.getProperty("java.io.tmpdir"), "apkSpy",
				modifyingApk.getName().replace('.', '_') + "stub.jar");
		if (!Files.exists(stubPath)) {
			JarGenerator.generateStubJar(modifyingApk, stubPath.toFile(), out, classes);
		}
		Files.createDirectories(root.resolve("libs"));
		Files.copy(stubPath, Paths.get("project-tmp", "libs", "stub.jar"));

		Files.copy(Paths.get("tools", "android.jar"), Paths.get("project-tmp", "libs", "android.jar"));
		Files.copy(Paths.get("tools", "httpcore-4.4.1.jar"), Paths.get("project-tmp", "libs", "httpcore-4.4.1.jar"));
		Files.copy(Paths.get("tools", "httpclient-4.5.jar"), Paths.get("project-tmp", "libs", "httpclient-4.5.jar"));

		Files.createDirectories(root.resolve("bin"));

		Path javac = null;
		if (System.getenv("JAVA_HOME") != null) {
			javac = Paths.get(System.getenv("JAVA_HOME"), "bin", "javac");
			if (!Files.isExecutable(javac)) {
				javac = javac.getParent().resolve("javac.exe");
				if (!Files.isExecutable(javac)) {
					javac = null;
				}
			}
		}

		out.write("Started compile...\n".getBytes(StandardCharsets.UTF_8));
		int code = Util.system(root.resolve("src").toFile(), new OutputStream() {
			@Override
			public void write(int b) throws IOException {
				out.write(b);
			}
		}, javac == null ? "javac" : javac.toAbsolutePath().toString(), "-cp",
				getClasspath("android.jar", "stub.jar", "httpcore-4.4.1.jar", "httpclient-4.5.jar"), "-d",
				".." + File.separator + "bin", className.replace('.', File.separatorChar) + ".java");

		Util.attemptDelete(root.toFile());

		return code == 0;
	}

	public static boolean merge(String apk, String outputLocation, String sdkPath, String applicationId,
			Map<String, ClassBreakdown> classes, List<String> deletions, OutputStream out)
			throws IOException, InterruptedException {
		sdkPath = sdkPath.replace("\\", "\\\\");

		System.out.println("Merging: " + apk);
		File modifyingApk = new File(apk);

		Util.attemptDelete(new File("project-tmp"));
		Util.attemptDelete(new File("smali"));

		FileUtils.copyDirectory(new File("default"), new File("project-tmp"));

		Files.write(Paths.get("project-tmp", "local.properties"),
				("sdk.dir=" + sdkPath).getBytes(StandardCharsets.UTF_8));

		Path gradleBuildPath = Paths.get("project-tmp", "app", "build.gradle");
		String buildGradle = new String(Files.readAllBytes(gradleBuildPath), StandardCharsets.UTF_8);
		buildGradle = buildGradle.replace("$APPLICATION_ID", applicationId);

		Files.write(gradleBuildPath, buildGradle.getBytes(StandardCharsets.UTF_8));

		Path manifestPath = Paths.get("project-tmp", "app", "src", "main", "AndroidManifest.xml");
		String manifest = new String(Files.readAllBytes(manifestPath), StandardCharsets.UTF_8);
		manifest = manifest.replace("$APPLICATION_ID", applicationId);

		Files.write(manifestPath, manifest.getBytes(StandardCharsets.UTF_8));

		for (Map.Entry<String, ClassBreakdown> entry : classes.entrySet()) {
			String className = entry.getKey();
			ClassBreakdown content = entry.getValue();

			File toCompile = new File(className.substring(className.lastIndexOf('.') + 1) + ".java");
			File completePath = Paths.get("project-tmp", "app", "src", "main", "java",
					className.substring(0, className.lastIndexOf('.')).replace(".", File.separator)).toFile();
			completePath.mkdirs();

			File newFile = new File(completePath, "ApkSpy_" + toCompile.getName());
			Files.write(newFile.toPath(), content.toString().getBytes(StandardCharsets.UTF_8));

			String simpleName = className.substring(className.lastIndexOf('.') + 1);
			String newFileContent = new String(Files.readAllBytes(newFile.toPath()), StandardCharsets.UTF_8);
			newFileContent = newFileContent.replaceAll("(class|interface|enum|@interface) +" + simpleName + "(.*)\\{",
					"$1 ApkSpy_" + simpleName + "$2{");
			newFileContent = newFileContent.replaceAll(simpleName + " *\\((.*)\\) *\\{",
					"ApkSpy_" + simpleName + "($1) {");
			Files.write(newFile.toPath(), newFileContent.getBytes(StandardCharsets.UTF_8));
		}

		Path stubPath = Paths.get(System.getProperty("java.io.tmpdir"), "apkSpy",
				modifyingApk.getName().replace('.', '_') + "stub.jar");
		if (!Files.exists(stubPath)) {
			JarGenerator.generateStubJar(modifyingApk, stubPath.toFile(), out, classes);
		}
		Files.createDirectories(Paths.get("project-tmp", "app", "libs"));

		if (!Files.exists(Paths.get("project-tmp", "app", "libs", "stub.jar"))) {
			// we check if it doesn't already exist, in case gradle has a lock on it and it
			// couldn't be deleted before
			Files.copy(stubPath, Paths.get("project-tmp", "app", "libs", "stub.jar"));
		}

		if (!Util.isWindows()) {
			Runtime.getRuntime().exec("chmod +x project-tmp/gradlew").waitFor();
		}

		if (Util.system(new File("project-tmp"), out, new File("project-tmp").getAbsolutePath() + File.separator
				+ (Util.isWindows() ? "gradlew.bat" : "gradlew"), "build") != 0) {
			Util.attemptDelete(new File("project-tmp"));
			return false;
		}

		Files.copy(Paths.get("project-tmp", "app", "build", "outputs", "apk", "debug", "app-debug.apk"),
				Paths.get("generated.apk"), StandardCopyOption.REPLACE_EXISTING);
		Util.attemptDelete(new File("project-tmp"));

		ApktoolWrapper.decode(Paths.get("generated.apk"), "generated", false, out);
		Files.delete(Paths.get("generated.apk"));

		ApktoolWrapper.decode(modifyingApk.toPath(), "original", true, out);

		List<Path> smaliFolders = Files.list(Paths.get("smali", "generated"))
				.filter(path -> Files.isDirectory(path) && path.getFileName().toString().startsWith("smali"))
				.collect(Collectors.toList());

		for (String deletion : deletions) {
			// file might not exist, as we could delete temporary classes that we made in
			// between compilations in the editor
			for (Path path : smaliFolders) {
				if (Files.deleteIfExists(Paths.get(path.toAbsolutePath().toString(),
						deletion.replace('.', File.separatorChar) + ".smali"))) {
					break;
				}
			}
		}

		for (Path smaliFolder : smaliFolders) {
			System.out.println("Searching through: " + smaliFolder);
			Files.walk(smaliFolder)
					.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().startsWith("ApkSpy_"))
					.forEach(path -> {
						try {
							System.out.println("Merging smali file: " + path);
							Path equivalent = null;
							for (Path otherFolder : smaliFolders) {
								Path test = Paths.get(otherFolder.toString(),
										path.toAbsolutePath().toString()
												.substring(smaliFolder.toAbsolutePath().toString().length())
												.replace("ApkSpy_", ""));
								// System.out.println(test);
								if (Files.isRegularFile(test)) {
									equivalent = test;
									break;
								}
							}
							System.out.println("Merging into file: " + equivalent);

							String modifiedContent = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
							modifiedContent = modifiedContent.replace("ApkSpy_", "");

							if (equivalent != null) {
								String originalContent = new String(Files.readAllBytes(equivalent),
										StandardCharsets.UTF_8);

								SmaliBreakdown modifiedSmali = SmaliBreakdown.breakdown(modifiedContent);

								ClassBreakdown relative = classes.get(modifiedSmali.getClassName());

								// check to make sure it's not an inner class
								if (relative != null) {
									System.out.println("Merging smali for class: " + modifiedSmali.getClassName());

									List<SmaliMethod> methods = modifiedSmali.getChangedMethods(relative);

									System.out.println(
											"Originally changed methods: " + relative.getChangedMethods().size());
									System.out.println("Merging method count: " + methods.size());

									StringBuilder builder = new StringBuilder(originalContent);
									for (SmaliMethod method : methods) {
										SmaliBreakdown originalSmali = SmaliBreakdown.breakdown(builder.toString());
										SmaliMethod equivalentMethod = originalSmali.getEquivalentMethod(method);

										builder.delete(equivalentMethod.getStart(), equivalentMethod.getEnd());
										builder.insert(equivalentMethod.getStart(), method.getContent());
									}

									Files.write(equivalent, builder.toString().getBytes(StandardCharsets.UTF_8));
								}
							} else {
								equivalent = Paths.get(smaliFolder.toString(),
										path.toAbsolutePath().toString()
												.substring(smaliFolder.toAbsolutePath().toString().length())
												.replace("ApkSpy_", ""));
								Files.createDirectories(equivalent.getParent());
								Files.copy(path, equivalent);
							}
						} catch (IOException e) {
							e.printStackTrace();
							System.exit(1);
						}
					});
		}

		ApktoolWrapper.build(Paths.get("smali", "original"), outputLocation, out);
		// TODO Util.attemptDelete(new File("smali"));

		out.write("Finished creating APK!".getBytes(StandardCharsets.UTF_8));
		return true;
	}
}
