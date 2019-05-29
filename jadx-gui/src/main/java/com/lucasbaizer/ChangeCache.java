package com.lucasbaizer;

import java.util.HashMap;
import java.util.Map;

public class ChangeCache {
	private static final Map<String, ClassBreakdown> CHANGES = new HashMap<>();

	public static Map<String, ClassBreakdown> getChanges() {
		return CHANGES;
	}

	public static void putChange(String className, ClassBreakdown content, String method) {
		if (CHANGES.containsKey(className)) {
			ClassBreakdown original = CHANGES.get(className);

			CHANGES.put(className, original.addOrReplaceMethod(method).mergeImports(content.getImports()));
		} else {
			CHANGES.put(className, content);
		}
	}
}
