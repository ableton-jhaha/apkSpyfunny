package com.lucasbaizer;

import java.util.HashMap;
import java.util.Map;

public class ChangeCache {
	private static final Map<String, String> CHANGES = new HashMap<>();

	public static Map<String, String> getChanges() {
		return CHANGES;
	}

	public static void putChange(String className, String content, String head, String method) {
		if (CHANGES.containsKey(className)) {
			String original = CHANGES.get(className);
			String methodHeader = method.split("\n")[0].trim();
			StringBuilder builder = new StringBuilder(original);

			if (original.contains(methodHeader)) {
				int h = builder.indexOf(methodHeader);
				builder.delete(h - 4, Util.findClosingBracket(original, builder.indexOf("{", h)) + 1);
				builder.insert(h - 4, method);
			} else {
				builder.insert(builder.length() - 3, "\n\n" + method);
			}

			builder.delete(0, builder.indexOf("\n", builder.lastIndexOf("import ") + 1));
			builder.insert(0, head);

			CHANGES.put(className, builder.toString());
		} else {
			CHANGES.put(className, content);
		}
	}
}
