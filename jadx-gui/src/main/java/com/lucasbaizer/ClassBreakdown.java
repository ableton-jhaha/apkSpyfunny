package com.lucasbaizer;

import java.util.ArrayList;
import java.util.List;

import jadx.gui.utils.DiffMatchPatch;
import jadx.gui.utils.DiffMatchPatch.Diff;

public class ClassBreakdown implements Cloneable {
	private String imports;
	private String classDeclaration;
	private String memberVariables;
	private List<String> methods;

	public static ClassBreakdown breakdown(String content) {
		String[] split = content.split("\n");

		String imports = "";
		String classDeclaration = "";
		String memberVariables = "";
		List<String> methods = new ArrayList<>();
		String currentMethod = "";
		for (String line : split) {
			if (!line.startsWith(" ")) {
				if (line.contains("class ")) {
					classDeclaration = line.substring(0, line.indexOf("{")).trim();
				} else {
					imports += line.trim() + "\n";
				}
			} else {
				if (line.startsWith("    ") && !line.startsWith("     ")) {
					if (line.trim().equals("}")) {
						methods.add(currentMethod);
						currentMethod = "";
					} else if (line.endsWith(";")) {
						memberVariables += line.trim() + "\n";
					} else {
						currentMethod += line.substring(4);
					}
				} else {
					currentMethod += line.substring(4);
				}
			}
		}

		if (!currentMethod.isEmpty()) {
			methods.add(currentMethod);
		}

		return new ClassBreakdown(imports, classDeclaration, memberVariables, methods);
	}

	public ClassBreakdown(String imports, String classDeclaration, String memberVariables, List<String> methods) {
		this.imports = imports;
		this.classDeclaration = classDeclaration;
		this.memberVariables = memberVariables;
		this.methods = methods;
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

	public ClassBreakdown addOrReplaceMethod(String newMethod) {
		ClassBreakdown clone = this.clone();

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

		ClassBreakdown clone = this.clone();
		clone.imports = dmp.diffText2(diffs);
		return clone;
	}

	public ClassBreakdown mergeMemberVariables(String memberVariables) {
		DiffMatchPatch dmp = new DiffMatchPatch();
		List<Diff> diffs = dmp.diffMain(this.memberVariables, memberVariables);

		ClassBreakdown clone = this.clone();
		clone.memberVariables = dmp.diffText2(diffs);
		return clone;
	}

	public ClassBreakdown mergeMethodStubs(List<String> methods) {
		ClassBreakdown clone = this.clone();
		outer: for (String newMethod : methods) {
			String header = newMethod.trim().split("\n")[0].trim();
			for (int i = 0; i < methods.size(); i++) {
				String otherHeader = methods.get(i).split("\n")[0].trim();
				if (header.equals(otherHeader)) {
					continue outer;
				}
			}

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
			} else if (containing.contains("void ")) {
				stub += "    return;\n";
			} else {
				stub += "    return null;\n";
			}
			stub += "}";
			clone.methods.add(stub);
		}

		return clone;
	}

	public ClassBreakdown accept(ClassBreakdown other) {
		ClassBreakdown breakdown = this.mergeImports(other.imports).mergeMemberVariables(other.memberVariables);
		for (String method : other.methods) {
			breakdown = breakdown.addOrReplaceMethod(method);
		}
		return breakdown;
	}

	@Override
	public String toString() {
		String str = this.imports + "\n";
		str += this.classDeclaration + " {\n";
		for (String member : this.memberVariables.split("\n")) {
			str += "    " + member + "\n";
		}
		str += "\n";
		for (String method : this.methods) {
			for (String split : method.split("\n")) {
				str += "    " + split + "\n";
			}
			str += "    }\n\n";
		}
		str += "}";
		return str;
	}

	@Override
	public ClassBreakdown clone() {
		return new ClassBreakdown(this.imports, this.classDeclaration, this.memberVariables,
				new ArrayList<>(this.methods));
	}
}
