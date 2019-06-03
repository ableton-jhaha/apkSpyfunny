package com.lucasbaizer;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.lucasbaizer.DiffMatchPatch.Diff;

public class ClassBreakdown implements Cloneable {
	private String className;
	private String simpleName;
	private String imports;
	private String classDeclaration;
	private String memberVariables;
	private List<String> changedMethods;
	private List<String> methods;
	private List<ClassBreakdown> innerClasses;

	public static ClassBreakdown breakdown(String className, String simpleName, String content) {
		String[] split = content.split("\n");

		String imports = "";
		String classDeclaration = "";
		String memberVariables = "";
		List<String> methods = new ArrayList<>();
		List<ClassBreakdown> innerClasses = new ArrayList<>();
		String currentBlock = "";
		int blockType = 0;
		boolean allowRoot = true;
		for (String line : split) {
			if (line.trim().startsWith("/*") || line.trim().startsWith("//")) {
				continue;
			}
			if (allowRoot) {
				if (!line.startsWith(" ")) {
					if (line.contains("class ") || line.contains("interface") || line.contains("enum ")
							|| line.contains("@interface ")) {
						classDeclaration = line.substring(0, line.indexOf("{")).trim();
						if (simpleName == null) {
							Matcher m = Pattern.compile(".*(class|interface|enum|@interface) (.+?) .*").matcher(line);
							if (m.find()) {
								simpleName = m.group(2);
							}
						}
						allowRoot = false;
					} else {
						imports += line.trim() + "\n";
					}
				}
			} else {
				if (line.startsWith("    ") && !line.startsWith("     ")) {
					if (line.trim().equals("}")) {
						if (blockType == 1) {
							methods.add(currentBlock.trim() + "\n}");
						} else if (blockType == 2) {
							innerClasses.add(ClassBreakdown.breakdown(null, null, currentBlock.trim() + "\n}"));
						}
						currentBlock = "";
						blockType = 0;
					} else if (line.trim().endsWith(";")) {
						memberVariables += line.trim() + "\n";
					} else {
						if (line.contains("class ")) {
							blockType = 2;
						} else {
							blockType = 1;
						}
						currentBlock += StringUtils.stripEnd(line.substring(4), "\r\n ") + "\n";
					}
				} else if (line.startsWith("     ")) {
					currentBlock += StringUtils.stripEnd(line.substring(4), "\r\n ") + "\n";
				}
			}
		}

		if (!currentBlock.isEmpty()) {
			if (blockType == 1) {
				methods.add(currentBlock);
			} else if (blockType == 2) {
				innerClasses.add(ClassBreakdown.breakdown(null, null, currentBlock.trim() + "\n}"));
			}
		}

		return new ClassBreakdown(imports, classDeclaration, className, simpleName, memberVariables, methods,
				innerClasses);
	}

	public ClassBreakdown(String imports, String classDeclaration, String className, String simpleName,
			String memberVariables, List<String> methods, List<ClassBreakdown> innerClasses) {
		this.imports = imports;
		this.classDeclaration = classDeclaration;
		this.className = className;
		this.simpleName = simpleName;
		this.memberVariables = memberVariables;
		this.methods = methods;
		this.changedMethods = methods;
		this.innerClasses = innerClasses;
	}

	public ClassBreakdown(ClassBreakdown old) {
		this.imports = old.imports;
		this.classDeclaration = old.classDeclaration;
		this.className = old.className;
		this.simpleName = old.simpleName;
		this.memberVariables = old.memberVariables;
		this.methods = new ArrayList<>(old.methods);
		this.changedMethods = new ArrayList<>(old.changedMethods);
		this.innerClasses = new ArrayList<>(old.innerClasses);
	}

	public String getImports() {
		return imports;
	}

	public void setImports(String imports) {
		this.imports = imports;
	}

	public String getClassDeclaration() {
		return classDeclaration;
	}

	public void setClassDeclaration(String classDeclaration) {
		this.classDeclaration = classDeclaration;
	}

	public String getMemberVariables() {
		return memberVariables;
	}

	public void setMemberVariables(String memberVariables) {
		this.memberVariables = memberVariables;
	}

	public List<String> getMethods() {
		return methods;
	}

	public void setMethods(List<String> methods) {
		this.methods = methods;
	}

	public String getFullName() {
		return className;
	}

	public void setFullName(String className) {
		this.className = className;
	}

	public List<String> getChangedMethods() {
		return changedMethods;
	}

	public void setChangedMethods(List<String> methods) {
		this.changedMethods = methods;
	}

	public String getSimpleName() {
		return simpleName;
	}

	public void setSimpleName(String simpleName) {
		this.simpleName = simpleName;
	}

	public ClassBreakdown addOrReplaceMethod(String newMethod) {
		ClassBreakdown clone = new ClassBreakdown(this);

		String header = newMethod.trim().split("\n")[0].trim();
		for (int i = 0; i < methods.size(); i++) {
			String otherHeader = methods.get(i).split("\n")[0].trim();
			if (header.equals(otherHeader)) {
				clone.methods.set(i, newMethod);
				return clone;
			}
		}

		clone.methods.add(newMethod);
		return clone;
	}

	public ClassBreakdown mergeImports(String imports) {
		DiffMatchPatch dmp = new DiffMatchPatch();
		List<Diff> diffs = dmp.diffMain(this.imports, imports);

		ClassBreakdown clone = new ClassBreakdown(this);
		clone.imports = dmp.diffText2(diffs);
		return clone;
	}

	public ClassBreakdown mergeMemberVariables(String memberVariables) {
		DiffMatchPatch dmp = new DiffMatchPatch();
		List<Diff> diffs = dmp.diffMain(this.memberVariables, memberVariables);

		ClassBreakdown clone = new ClassBreakdown(this);
		clone.memberVariables = dmp.diffText2(diffs);
		return clone;
	}

	private String toStub(String method) {
		String header = method.trim().split("\n")[0].trim();
		String containing = header.substring(0, header.indexOf('('));

		String stub = header + "\n";
		if (containing.contains("byte ") || containing.contains("short ") || containing.contains("int ")
				|| containing.contains("long ")) {
			stub += "    return 0;\n";
		} else if (containing.contains("float ")) {
			stub += "    return 0.0f;\n";
		} else if (containing.contains("double ")) {
			stub += "    return 0.0;\n";
		} else if (containing.contains("char ")) {
			stub += "    return ' ';\n";
		} else if (containing.contains("boolean ")) {
			stub += "    return false;\n";
		} else if (containing.contains("void ") || containing.contains(this.simpleName)) {
			stub += "    return;\n";
		} else {
			stub += "    return null;\n";
		}
		stub += "}";

		return stub;
	}

	public ClassBreakdown mergeMethodStubs(List<String> methods) {
		ClassBreakdown clone = new ClassBreakdown(this);
		outer: for (String newMethod : methods) {
			String header = newMethod.trim().split("\n")[0].trim();
			for (int i = 0; i < this.methods.size(); i++) {
				String otherHeader = this.methods.get(i).split("\n")[0].trim();
				if (header.equals(otherHeader)) {
					continue outer;
				}
			}

			clone.methods.add(this.toStub(newMethod));
		}

		return clone;
	}

	public ClassBreakdown mergeMethods(List<String> methods) {
		ClassBreakdown breakdown = new ClassBreakdown(this);
		for (String method : methods) {
			breakdown = breakdown.addOrReplaceMethod(method);
		}
		return breakdown;
	}

	public ClassBreakdown mergeInnerClassStubs(ClassBreakdown original) {
		ClassBreakdown breakdown = new ClassBreakdown(this);
		breakdown.innerClasses = original.innerClasses.stream().map(cls -> cls.asStub()).collect(Collectors.toList());
		return breakdown;
	}

	public List<ClassBreakdown> getInnerClasses() {
		return innerClasses;
	}

	public void setInnerClasses(List<ClassBreakdown> innerClasses) {
		this.innerClasses = innerClasses;
	}

	public ClassBreakdown asStub() {
		ClassBreakdown breakdown = new ClassBreakdown(this);
		breakdown.methods = this.methods.stream().map(this::toStub).collect(Collectors.toList());
		return breakdown;
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder(this.imports);
		str.append((this.classDeclaration + " {\n").replaceAll("(.*?)(class|interface|enum|@interface) (.+?) (.+)",
				"$1$2 " + this.simpleName + " $4"));
		if (this.memberVariables.length() > 0) {
			for (String member : this.memberVariables.split("\n")) {
				str.append("    " + member + "\n");
			}
			str.append("\n");
		}
		if (this.innerClasses.size() > 0) {
			for (ClassBreakdown innerClass : this.innerClasses) {
				String toStr = innerClass.toString();
				for (String split : toStr.split("\n")) {
					str.append("    " + split + "\n");
				}
				str.append("\n");
			}
		}
		if (this.methods.size() > 0) {
			for (String method : this.methods) {
				for (String split : method.split("\n")) {
					str.append("    " + split + "\n");
				}
				str.append("\n");
			}
		}
		return str.toString().substring(0, str.length() - 1) + "}";
	}
}
