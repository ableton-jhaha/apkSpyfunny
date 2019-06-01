package jadx.gui.ui;

import java.io.IOException;
import java.io.OutputStream;

import com.lucasbaizer.ApkSpy;
import com.lucasbaizer.ChangeCache;
import com.lucasbaizer.ClassBreakdown;

import jadx.core.codegen.CodeWriter;
import jadx.gui.treemodel.JClass;
import jadx.gui.treemodel.JNode;
import jadx.gui.ui.codearea.AbstractCodeArea;

public class EditMethodDialog extends ApkSpyDialog {
	private static final long serialVersionUID = -8177172816914615698L;

	private transient JClass cls;
	private transient AbstractCodeArea selectedCodeArea;

	public EditMethodDialog(MainWindow mainWindow, JNode jnode, JClass cls, AbstractCodeArea selectedCodeArea) {
		super(mainWindow, jnode, "Edit Method");

		this.cls = cls;
		this.selectedCodeArea = selectedCodeArea;
	}

	@Override
	protected void onSave() {
		ClassBreakdown original = ClassBreakdown.breakdown(cls.getFullName(), cls.getContent());
		ClassBreakdown changed = ClassBreakdown.breakdown(cls.getFullName(), this.getCodeArea().getText());

		ClassBreakdown completed = original.mergeImports(changed.getImports())
				.mergeMethods(changed.getChangedMethods());

		CodeWriter writer = new CodeWriter();
		writer.add(completed.toString());
		writer = writer.finish();
		cls.getCls().getClassNode().setCode(writer);

		int caret = selectedCodeArea.getCaretPosition();
		selectedCodeArea.setText(completed.toString());

		selectedCodeArea.setCaretPosition(caret);

		ChangeCache.putChange(cls.getFullName(),
				changed.mergeMemberVariables(original.getMemberVariables()).mergeMethodStubs(original.getMethods()),
				changed.getMethods().get(0));

		dispose();
	}

	@Override
	protected void onCompile() {
		try {
			ClassBreakdown original = ClassBreakdown.breakdown(cls.getFullName(), cls.getContent());
			ClassBreakdown changed = ClassBreakdown.breakdown(cls.getFullName(), this.getCodeArea().getText());

			if (ApkSpy.lint(this.getMainWindow().getProject().getFilePath().toString(), cls.getFullName(),
					changed.mergeMemberVariables(original.getMemberVariables()).mergeMethodStubs(original.getMethods()),
					new OutputStream() {
						@Override
						public void write(int b) throws IOException {
							System.out.print((char) b);
							getOutput().append(Character.toString((char) b));
						}
					})) {
				this.getOutput().append("Successfully compiled!\n");
			} else {
				this.getOutput().append("Encounted errors while compiling!\n");
			}
		} catch (IOException | InterruptedException ex) {
			ex.printStackTrace();
		}
	}
}
