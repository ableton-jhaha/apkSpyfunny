package jadx.api;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.lang3.StringUtils;
import org.jf.baksmali.Adaptors.ClassDefinition;
import org.jf.baksmali.BaksmaliOptions;
import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.dexbacked.DexBackedClassDef;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.jf.util.IndentingWriter;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import jadx.core.Jadx;
import jadx.core.ProcessClass;
import jadx.core.dex.attributes.AFlag;
import jadx.core.dex.nodes.ClassNode;
import jadx.core.dex.nodes.FieldNode;
import jadx.core.dex.nodes.MethodNode;
import jadx.core.dex.nodes.RootNode;
import jadx.core.dex.visitors.IDexTreeVisitor;
import jadx.core.dex.visitors.SaveCode;
import jadx.core.export.ExportGradleProject;
import jadx.core.utils.Utils;
import jadx.core.utils.exceptions.JadxRuntimeException;
import jadx.core.utils.files.InputFile;
import jadx.core.xmlgen.BinaryXMLParser;
import jadx.core.xmlgen.ResourcesSaver;

/**
 * Jadx API usage example:
 *
 * <pre>
 * <code>
 * JadxArgs args = new JadxArgs();
 * args.getInputFiles().add(new File("test.apk"));
 * args.setOutDir(new File("jadx-test-output"));
 *
 * JadxDecompiler jadx = new JadxDecompiler(args);
 * jadx.load();
 * jadx.save();
 * </code>
 * </pre>
 * <p>
 * Instead of 'save()' you can iterate over decompiled classes:
 *
 * <pre>
 * <code>
 *  for(JavaClass cls : jadx.getClasses()) {
 *      System.out.println(cls.getCode());
 *  }
 * </code>
 * </pre>
 */
public final class JadxDecompiler {
	private static final Logger LOG = LoggerFactory.getLogger(JadxDecompiler.class);

	private JadxArgs args;

	private final List<InputFile> inputFiles = new ArrayList<>();

	private RootNode root;
	private List<IDexTreeVisitor> passes;

	private List<JavaClass> classes;
	private List<ResourceFile> resources;

	private BinaryXMLParser xmlParser;

	private Map<ClassNode, JavaClass> classesMap = new ConcurrentHashMap<>();
	private Map<MethodNode, JavaMethod> methodsMap = new ConcurrentHashMap<>();
	private Map<FieldNode, JavaField> fieldsMap = new ConcurrentHashMap<>();

	public JadxDecompiler() {
		this(new JadxArgs());
	}

	public JadxDecompiler(JadxArgs args) {
		this.args = args;
	}

	public void load() {
		reset();
		JadxArgsValidator.validate(args);
		init();
		LOG.info("loading ...");

		loadFiles(args.getInputFiles());

		root = new RootNode(args);
		root.load(inputFiles);

		root.initClassPath();
		root.loadResources(getResources());

		initVisitors();
	}

	void init() {
		this.passes = Jadx.getPassesList(args);
	}

	void reset() {
		classes = null;
		resources = null;
		xmlParser = null;
		root = null;
		passes = null;
	}

	public static String getVersion() {
		return Jadx.getVersion();
	}

	private void loadFiles(List<File> files) {
		if (files.isEmpty()) {
			throw new JadxRuntimeException("Empty file list");
		}
		inputFiles.clear();
		for (File file : files) {
			try {
				InputFile.addFilesFrom(file, inputFiles, args.isSkipSources());
			} catch (Exception e) {
				throw new JadxRuntimeException("Error load file: " + file, e);
			}
		}
	}

	public void save() {
		save(!args.isSkipSources(), !args.isSkipResources());
	}

	public void saveSources() {
		save(true, false);
	}

	public void saveResources() {
		save(false, true);
	}

	private void save(boolean saveSources, boolean saveResources) {
		ExecutorService ex = getSaveExecutor(saveSources, saveResources);
		ex.shutdown();
		try {
			ex.awaitTermination(1, TimeUnit.DAYS);
		} catch (InterruptedException e) {
			LOG.error("Save interrupted", e);
			Thread.currentThread().interrupt();
		}
	}

	public ExecutorService getSaveExecutor() {
		return getSaveExecutor(!args.isSkipSources(), !args.isSkipResources());
	}

	private ExecutorService getSaveExecutor(boolean saveSources, boolean saveResources) {
		if (root == null) {
			throw new JadxRuntimeException("No loaded files");
		}
		int threadsCount = args.getThreadsCount();
		LOG.debug("processing threads count: {}", threadsCount);

		LOG.info("processing ...");
		ExecutorService executor = Executors.newFixedThreadPool(threadsCount);

		File sourcesOutDir;
		File resOutDir;
		ExportGradleProject export = null;
		if (args.isExportAsGradleProject()) {
			export = new ExportGradleProject(root, args.getOutDir());
			export.init();
			sourcesOutDir = export.getSrcOutDir();
			resOutDir = export.getResOutDir();
		} else {
			sourcesOutDir = args.getOutDirSrc();
			resOutDir = args.getOutDirRes();
		}
		if (saveResources) {
			appendResourcesSave(executor, resOutDir);
		}
		if (saveSources) {
			List<String> dependencies = appendSourcesSave(executor, sourcesOutDir, export);
			if (args.isExportAsGradleProject()) {
				try {
					export.saveBuildGradle(dependencies);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return executor;
	}

	private void appendResourcesSave(ExecutorService executor, File outDir) {
		for (ResourceFile resourceFile : getResources()) {
			executor.execute(new ResourcesSaver(outDir, resourceFile));
		}
	}

	private static final XPathFactory XPATH = XPathFactory.newInstance();

	private String httpGet(String url) throws IOException {
		HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
		connection.setRequestMethod("GET");

		if (connection.getResponseCode() == 404) {
			return null;
		}

		BufferedReader rd = new BufferedReader(new InputStreamReader(connection.getInputStream()));
		String line;
		StringBuilder result = new StringBuilder();
		while ((line = rd.readLine()) != null) {
			result.append(line);
		}
		rd.close();

		return result.toString();
	}

	private Document generateDocument(String url) throws IOException, SAXException, ParserConfigurationException {
		String http = httpGet(url);
		if (http == null) {
			return null;
		}
		org.jsoup.nodes.Document document = Jsoup.parse(http);
		document.outputSettings().syntax(org.jsoup.nodes.Document.OutputSettings.Syntax.xml);
		String xhtml = document.html();

		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder = factory.newDocumentBuilder();
		return builder.parse(new InputSource(new StringReader(xhtml)));
	}

	private List<String> appendSourcesSave(ExecutorService executor, File outDir, ExportGradleProject export) {
		List<String> dependencies = new ArrayList<>();
		List<String> dependencyPackages = new ArrayList<>();

		if (export != null && args.isConvertDependencies()) {
			Set<String> packages = new HashSet<>();
			for (JavaPackage pkg : this.getPackages()) {
				packages.add(pkg.getFullName());

				String[] split = pkg.getFullName().split("\\.");
				String current = split[0];
				for (int i = 1; i < split.length - 1; i++) {
					current += "." + split[i];
					packages.add(current);
				}
			}

			for (String pkg : packages) {
				if (pkg.startsWith("android.support") && StringUtils.countMatches(pkg, ".") == 2) {
					String supportVersion = pkg.split("\\.")[2];
					dependencies.add("com.android.support:support-" + supportVersion + ":+");

					for (JavaClass cls : getClasses()) {
						if (cls.getPackage().startsWith(pkg)) {
							cls.getClassNode().add(AFlag.DONT_GENERATE);
						}
					}
				}
			}

			try {
				XPathExpression xPathFindArtifacts = XPATH.newXPath().compile("/html/body/main/pre/a");
				XPathExpression xPathFindMetadata = XPATH.newXPath()
						.compile("/html/body/main/pre/a[@href='maven-metadata.xml']");

				for (String pkg : packages) {
					String group = pkg.replace('.', '/');

					if (pkg.equals("com.google.ads.afsn") || pkg.startsWith("com.google.android")
							|| pkg.startsWith("com.google.ar") || pkg.startsWith("com.google.firebase")
							|| pkg.startsWith("com.google.gms") || pkg.startsWith("com.android")
							|| pkg.startsWith("androidx") || pkg.startsWith("android.arch")
							|| pkg.equals("com.crashlytics.sdk.android") || pkg.equals("io.fabric.sdk.android")
							|| pkg.equals("org.chromium.net") || pkg.equals("org.chromium.net")) {
						String xml = httpGet("https://dl.google.com/dl/android/maven2/" + group + "/group-index.xml");
						if (xml != null) {
							DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
							DocumentBuilder builder = factory.newDocumentBuilder();
							Document doc = builder.parse(new InputSource(new StringReader(xml)));
							if (doc != null) {
								NodeList children = doc.getChildNodes().item(0).getChildNodes();
								List<String> gradleStrings = new ArrayList<>();
								for (int i = 0; i < children.getLength(); i++) {
									Node child = children.item(i);

									if (!child.getNodeName().equals("#text")) {
										String[] versions = child.getAttributes().getNamedItem("versions")
												.getNodeValue().split(",");
										gradleStrings.add(
												pkg + ":" + child.getNodeName() + ":" + versions[versions.length - 1]);
									}
								}

								gradleStrings.sort((o1, o2) -> {
									return o1.substring(0, o1.lastIndexOf(':'))
											.compareTo(o2.substring(0, o2.lastIndexOf(':')));
								});

								List<String> returned = this.args.getGradleDependencyFunction().apply(gradleStrings);
								if (returned.size() > 0) {
									dependencyPackages.add(pkg);
									dependencies.addAll(returned);
								}
							}
						}
					} else {
						Document doc = generateDocument("http://central.maven.org/maven2/" + group);

						if (doc != null) {
							NodeList nodes = (NodeList) xPathFindArtifacts.evaluate(doc, XPathConstants.NODESET);
							if (nodes != null && nodes.getLength() > 0) {
								// start at 1 to skip the '../' link
								List<String> gradleStrings = new ArrayList<>();
								for (int i = 1; i < nodes.getLength(); i++) {
									Node node = nodes.item(i);
									String artifact = node.getTextContent().substring(0,
											node.getTextContent().length() - 1);

									Document artifactDocument = generateDocument(
											"http://central.maven.org/maven2/" + group + "/" + artifact);

									Node metadata = (Node) xPathFindMetadata.evaluate(artifactDocument,
											XPathConstants.NODE);
									if (metadata == null) {
										continue;
									}
									String mostRecentVersion = metadata.getPreviousSibling().getPreviousSibling()
											.getTextContent();
									mostRecentVersion = mostRecentVersion.substring(0, mostRecentVersion.length() - 1);

									gradleStrings.add(pkg + ":" + artifact + ":" + mostRecentVersion);
								}

								gradleStrings.sort((o1, o2) -> {
									return o1.substring(0, o1.lastIndexOf(':'))
											.compareTo(o2.substring(0, o2.lastIndexOf(':')));
								});

								List<String> returned = this.args.getGradleDependencyFunction().apply(gradleStrings);
								if (returned.size() > 0) {
									dependencyPackages.add(pkg);
									dependencies.addAll(returned);
								}
							}
						}
					}
				}
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		for (String pkg : dependencyPackages) {
			for (JavaClass cls : getClasses()) {
				if (cls.getPackage().startsWith(pkg)) {
					cls.getClassNode().add(AFlag.DONT_GENERATE);
				}
			}
		}

		final Predicate<String> classFilter = args.getClassFilter();
		for (JavaClass cls : getClasses()) {
			if (cls.getClassNode().contains(AFlag.DONT_GENERATE)) {
				continue;
			}
			if (classFilter != null && !classFilter.test(cls.getFullName())) {
				continue;
			}
			executor.execute(() -> {
				try {
					cls.decompile();
					SaveCode.save(outDir, args, cls.getClassNode());
				} catch (Exception e) {
					LOG.error("Error saving class: {}", cls.getFullName(), e);
				}
			});
		}

		return dependencies;
	}

	public List<JavaClass> getClasses() {
		if (root == null) {
			return Collections.emptyList();
		}
		if (classes == null) {
			List<ClassNode> classNodeList = root.getClasses(false);
			List<JavaClass> clsList = new ArrayList<>(classNodeList.size());
			classesMap.clear();
			for (ClassNode classNode : classNodeList) {
				if (!classNode.contains(AFlag.DONT_GENERATE)) {
					JavaClass javaClass = new JavaClass(classNode, this);
					clsList.add(javaClass);
					classesMap.put(classNode, javaClass);
				}
			}
			classes = Collections.unmodifiableList(clsList);
		}
		return classes;
	}

	public List<ResourceFile> getResources() {
		if (resources == null) {
			if (root == null) {
				return Collections.emptyList();
			}
			resources = new ResourcesLoader(this).load(inputFiles);
		}
		return resources;
	}

	public List<JavaPackage> getPackages() {
		List<JavaClass> classList = getClasses();
		if (classList.isEmpty()) {
			return Collections.emptyList();
		}
		Map<String, List<JavaClass>> map = new HashMap<>();
		for (JavaClass javaClass : classList) {
			String pkg = javaClass.getPackage();
			List<JavaClass> clsList = map.computeIfAbsent(pkg, k -> new ArrayList<>());
			clsList.add(javaClass);
		}
		List<JavaPackage> packages = new ArrayList<>(map.size());
		for (Map.Entry<String, List<JavaClass>> entry : map.entrySet()) {
			packages.add(new JavaPackage(entry.getKey(), entry.getValue()));
		}
		Collections.sort(packages);
		for (JavaPackage pkg : packages) {
			pkg.getClasses().sort(Comparator.comparing(JavaClass::getName, String.CASE_INSENSITIVE_ORDER));
		}
		return Collections.unmodifiableList(packages);
	}

	public int getErrorsCount() {
		if (root == null) {
			return 0;
		}
		return root.getErrorsCounter().getErrorCount();
	}

	public int getWarnsCount() {
		if (root == null) {
			return 0;
		}
		return root.getErrorsCounter().getWarnsCount();
	}

	public void printErrorsReport() {
		if (root == null) {
			return;
		}
		root.getClsp().printMissingClasses();
		root.getErrorsCounter().printReport();
	}

	private void initVisitors() {
		for (IDexTreeVisitor pass : passes) {
			try {
				pass.init(root);
			} catch (Exception e) {
				LOG.error("Visitor init failed: {}", pass.getClass().getSimpleName(), e);
			}
		}
	}

	void processClass(ClassNode cls) {
		ProcessClass.process(cls, passes, true);
	}

	void generateSmali(ClassNode cls) {
		Path path = cls.dex().getDexFile().getPath();
		String className = Utils.makeQualifiedObjectName(cls.getClassInfo().getType().getObject());
		try {
			DexBackedDexFile dexFile = DexFileFactory.loadDexFile(path.toFile(), Opcodes.getDefault());
			boolean decompiled = false;
			for (DexBackedClassDef classDef : dexFile.getClasses()) {
				if (classDef.getType().equals(className)) {
					ClassDefinition classDefinition = new ClassDefinition(new BaksmaliOptions(), classDef);
					StringWriter sw = new StringWriter();
					classDefinition.writeTo(new IndentingWriter(sw));
					cls.setSmali(sw.toString());
					decompiled = true;
					break;
				}
			}
			if (!decompiled) {
				LOG.error("Failed to find smali class {}", className);
			}
		} catch (Exception e) {
			LOG.error("Error generating smali", e);
		}
	}

	RootNode getRoot() {
		return root;
	}

	List<IDexTreeVisitor> getPasses() {
		return passes;
	}

	synchronized BinaryXMLParser getXmlParser() {
		if (xmlParser == null) {
			xmlParser = new BinaryXMLParser(root);
		}
		return xmlParser;
	}

	Map<ClassNode, JavaClass> getClassesMap() {
		return classesMap;
	}

	Map<MethodNode, JavaMethod> getMethodsMap() {
		return methodsMap;
	}

	JavaMethod getJavaMethodByNode(MethodNode mth) {
		JavaMethod javaMethod = methodsMap.get(mth);
		if (javaMethod != null) {
			return javaMethod;
		}
		// parent class not loaded yet
		JavaClass javaClass = classesMap.get(mth.getParentClass());
		if (javaClass != null) {
			javaClass.decompile();
			return methodsMap.get(mth);
		}
		return null;
	}

	Map<FieldNode, JavaField> getFieldsMap() {
		return fieldsMap;
	}

	JavaField getJavaFieldByNode(FieldNode fld) {
		JavaField javaField = fieldsMap.get(fld);
		if (javaField != null) {
			return javaField;
		}
		// parent class not loaded yet
		JavaClass javaClass = classesMap.get(fld.getParentClass());
		if (javaClass != null) {
			javaClass.decompile();
			return fieldsMap.get(fld);
		}
		return null;
	}

	public JadxArgs getArgs() {
		return args;
	}

	@Override
	public String toString() {
		return "jadx decompiler " + getVersion();
	}
}
